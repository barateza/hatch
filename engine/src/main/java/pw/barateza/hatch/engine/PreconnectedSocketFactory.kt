package pw.barateza.hatch.engine

import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

/**
 * JSch [SocketFactory] that returns a socket the engine already connected (either a
 * [DuctSocket] riding on the Cloudflare Access WebSocket, or a pre-connected plain TCP socket
 * for the direct fallback). JSch's host/port arguments are therefore informational only.
 */
class PreconnectedSocketFactory(private val socket: Socket) : SocketFactory {

  override fun createSocket(host: String, port: Int): Socket = socket

  override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

  override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
}
