package pw.barateza.hatch.engine

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * A [java.net.Socket] whose byte stream rides inside OkHttp WebSocket binary frames.
 *
 * JSch speaks plain SSH over `getInputStream()` / `getOutputStream()`; Cloudflare Access only
 * exposes the SSH endpoint as a WebSocket, so this adapter is the bridge between the two.
 *
 * - Inbound frames land in a **bounded** coroutine channel (backpressure: on overflow the socket
 *   fails and is closed, never grows without limit).
 * - `SO_TIMEOUT` is honoured by the input stream: a read blocks up to the timeout and then throws
 *   [SocketTimeoutException] (JSch uses this for server-alive keepalives).
 * - Peer close is EOF; peer/protocol failure is [IOException].
 *
 * OkHttp's public API does not expose ping frames or `onPong`; see the engine README. This class
 * only carries data frames.
 */
class DuctSocket(
  private val remoteHost: String,
  private val remotePort: Int = 443,
  private val inboundFrameCapacity: Int = 64,
) : Socket() {

  private val inbound = Channel<ByteArray>(capacity = inboundFrameCapacity.coerceAtLeast(1))
  private val opened = CompletableDeferred<Unit>()
  private val sentBytes = AtomicLong()
  private val receivedBytes = AtomicLong()
  private val inputLock = Any()
  private val released = AtomicBoolean(false)

  /** OkHttp needs a WebSocketListener; Java allows only one superclass, so it delegates here. */
  internal val listener: WebSocketListener = Listener()

  @Volatile private var webSocket: WebSocket? = null
  @Volatile private var open = false
  @Volatile private var closed = false
  @Volatile private var closeCause: IOException? = null
  @Volatile private var soTimeoutMs = 0
  @Volatile private var inputShutdown = false
  @Volatile private var outputShutdown = false
  @Volatile private var resolvedAddress: InetAddress? = null

  private var pendingFrame: ByteArray? = null
  private var pendingOffset = 0

  val bytesUp: Long get() = sentBytes.get()
  val bytesDown: Long get() = receivedBytes.get()

  /** The transport-level failure that ended the socket, if any. */
  fun failureOrNull(): IOException? = closeCause

  /** Connects this socket to the already-created OkHttp WebSocket. */
  internal fun bind(webSocket: WebSocket) {
    this.webSocket = webSocket
  }

  /**
   * Suspends until the WebSocket upgrade completed (HTTP 101 and the first `onOpen`), or throws
   * [WebSocketUpgradeException] (with the HTTP status, if OkHttp exposed one) / [IOException] for
   * transport failures. A timeout closes the WebSocket.
   */
  suspend fun awaitOpen(timeoutMs: Long) {
    try {
      withTimeout(timeoutMs) { opened.await() }
    } catch (timeout: TimeoutCancellationException) {
      shutdownNow()
      throw IOException("WebSocket upgrade timed out after ${timeoutMs}ms", timeout)
    } catch (t: Throwable) {
      shutdownNow()
      throw t
    }
  }

  // ---------------------------------------------------------------------------------------------
  // WebSocketListener (delegated from the inner [Listener])
  // ---------------------------------------------------------------------------------------------

  private inner class Listener : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      this@DuctSocket.webSocket = webSocket
      if (!closed) {
        open = true
        opened.complete(Unit)
      }
      // Do not close `response`: its body *is* the upgraded WebSocket stream.
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
      receivedBytes.addAndGet(bytes.size.toLong())
      if (closed) return
      val result = inbound.trySend(bytes.toByteArray())
      if (result.isFailure) {
        val overflow =
          IOException(
            "WebSocket inbound buffer overflow (${inboundFrameCapacity} frames); " +
              "the SSH peer is sending faster than the local reader consumes",
          )
        fail(overflow)
        runCatching { webSocket.close(1009, "hatch: inbound buffer overflow") }
      }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      fail(
        IOException(
          "Unexpected WebSocket text frame (${text.length} chars); raw SSH must be binary",
        ),
      )
      runCatching { webSocket.close(1003, "hatch: text frame not supported") }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      // Acknowledge the close handshake; readers see EOF.
      markClosed(null)
      runCatching { webSocket.close(1000, null) }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      markClosed(null)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      val cause: IOException =
        if (!open) {
          WebSocketUpgradeException(response?.code, t)
        } else {
          t as? IOException ?: IOException("WebSocket transport failed", t)
        }
      markClosed(cause)
      runCatching { response?.close() }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Socket surface
  // ---------------------------------------------------------------------------------------------

  override fun getInputStream(): InputStream = DuctInputStream()

  override fun getOutputStream(): OutputStream = DuctOutputStream()

  override fun isConnected(): Boolean = open

  override fun isClosed(): Boolean = closed

  override fun close() {
    val wasClosed = closed
    markClosed(null)
    if (!wasClosed) {
      runCatching { webSocket?.close(1000, "hatch: closed") }
    }
    releaseUnderlyingSocket()
  }

  /** Immediate shutdown used by engine stop/cleanup: cancels the WebSocket without a handshake. */
  fun shutdownNow() {
    markClosed(null)
    runCatching { webSocket?.cancel() }
    releaseUnderlyingSocket()
  }

  override fun shutdownInput() {
    inputShutdown = true
    inbound.close()
  }

  override fun shutdownOutput() {
    outputShutdown = true
  }

  override fun isInputShutdown(): Boolean = inputShutdown || closed

  override fun isOutputShutdown(): Boolean = outputShutdown || closed

  override fun setSoTimeout(timeout: Int) {
    require(timeout >= 0) { "timeout < 0" }
    soTimeoutMs = timeout
  }

  override fun getSoTimeout(): Int = soTimeoutMs

  // A DuctSocket has no TCP options; JSch calls setTcpNoDelay(true) after createSocket.
  override fun setTcpNoDelay(on: Boolean) = Unit

  override fun getTcpNoDelay(): Boolean = true

  override fun getInetAddress(): InetAddress {
    resolvedAddress?.let { return it }
    val resolved =
      runCatching { InetAddress.getByName(remoteHost.substringBefore('/').substringBefore(':')) }
        .getOrElse { InetAddress.getLoopbackAddress() }
    resolvedAddress = resolved
    return resolved
  }

  override fun getPort(): Int = remotePort

  override fun getLocalAddress(): InetAddress = InetAddress.getLoopbackAddress()

  override fun getLocalPort(): Int = 0

  override fun getRemoteSocketAddress(): java.net.SocketAddress =
    InetSocketAddress(getInetAddress(), remotePort)

  override fun getLocalSocketAddress(): java.net.SocketAddress =
    InetSocketAddress(InetAddress.getLoopbackAddress(), 0)

  override fun toString(): String =
    "DuctSocket(wss://$remoteHost/, open=$open, closed=$closed, up=${bytesUp}B, down=${bytesDown}B)"

  // ---------------------------------------------------------------------------------------------
  // Internals
  // ---------------------------------------------------------------------------------------------

  private fun markClosed(cause: IOException?) {
    if (cause != null && closeCause == null) closeCause = cause
    closed = true
    inbound.close(closeCause)
    if (!opened.isCompleted) {
      opened.completeExceptionally(closeCause ?: IOException("WebSocket closed before it opened"))
    }
  }

  private fun fail(cause: IOException) {
    markClosed(cause)
  }

  private fun releaseUnderlyingSocket() {
    if (released.compareAndSet(false, true)) {
      try {
        super.close()
      } catch (_: Throwable) {
        // The WebSocket is already gone; there is no meaningful recovery here.
      }
    }
  }

  /** Returns the next inbound frame, `null` on EOF, throws on timeout or transport failure. */
  private fun nextFrame(): ByteArray? {
    val timeout = soTimeoutMs
    val result =
      if (timeout <= 0) {
        runBlocking { inbound.receiveCatching() }
      } else {
        runBlocking { withTimeoutOrNull(timeout.toLong()) { inbound.receiveCatching() } }
          ?: throw SocketTimeoutException("Read timed out after ${timeout}ms")
      }

    result.getOrNull()?.let { return it }

    val cause = result.exceptionOrNull()
    if (cause != null && cause !is CancellationException) {
      throw IOException("WebSocket transport closed", cause)
    }
    return null
  }

  private inner class DuctInputStream : InputStream() {
    override fun read(): Int {
      val single = ByteArray(1)
      val read = read(single, 0, 1)
      return if (read <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()
      if (len == 0) return 0
      synchronized(inputLock) {
        if (inputShutdown) return -1
        while (true) {
          val frame = pendingFrame
          if (frame != null) {
            val count = minOf(len, frame.size - pendingOffset)
            System.arraycopy(frame, pendingOffset, b, off, count)
            pendingOffset += count
            if (pendingOffset >= frame.size) {
              pendingFrame = null
              pendingOffset = 0
            }
            return count
          }
          closeCause?.let { throw IOException("WebSocket transport failed", it) }
          val next = nextFrame() ?: return -1
          pendingFrame = next
          pendingOffset = 0
        }
      }
    }

    override fun available(): Int = pendingFrame?.let { it.size - pendingOffset } ?: 0

    override fun close() {
      shutdownInput()
    }
  }

  private inner class DuctOutputStream : OutputStream() {
    override fun write(b: Int) {
      write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
      if (off < 0 || len < 0 || off + len > b.size) throw IndexOutOfBoundsException()
      if (len == 0) return
      if (outputShutdown || closed) throw IOException("WebSocket is closed")
      val socket = webSocket ?: throw IOException("WebSocket is not open")
      val payload = b.toByteString(off, len)
      if (!socket.send(payload)) {
        throw IOException("WebSocket send queue rejected $len bytes (queue full or closing)")
      }
      sentBytes.addAndGet(len.toLong())
    }

    override fun flush() = Unit

    override fun close() {
      shutdownOutput()
    }
  }
}
