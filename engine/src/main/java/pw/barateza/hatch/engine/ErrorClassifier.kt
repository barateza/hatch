package pw.barateza.hatch.engine

import com.jcraft.jsch.JSchException
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.BindException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import pw.barateza.hatch.model.ErrorKind

/** What the retry loop should do with a classified failure. */
enum class FailureAction {
  /** Wait out the backoff and connect again. */
  RETRY,
  /** Cloudflare Access rejected the token: go back to NEEDS_AUTH (never consumes an attempt). */
  NEEDS_AUTH,
  /** Stop retrying and surface an ERROR. */
  FATAL,
}

/**
 * Pure classification of connect/transport failures. Kept free of Android APIs so it is
 * unit-testable on the JVM.
 */
data class FailureClassification(
  val kind: ErrorKind,
  val retryable: Boolean,
  val action: FailureAction,
) {
  val title: String
    get() = titleFor(kind)

  companion object {
    fun titleFor(kind: ErrorKind): String =
      when (kind) {
        ErrorKind.ACCESS_AUTH -> "Cloudflare Access login required"
        ErrorKind.HOST_KEY -> "SSH host key changed"
        ErrorKind.SSH_AUTH -> "SSH authentication failed"
        ErrorKind.NETWORK -> "Network error"
        ErrorKind.DNS -> "Cannot reach the SSH host"
        ErrorKind.TIMEOUT -> "Connection timed out"
        ErrorKind.PORT_IN_USE -> "Local port already in use"
        ErrorKind.PROTOCOL -> "Tunnel protocol error"
        ErrorKind.CONFIG -> "Invalid configuration"
        ErrorKind.UNKNOWN -> "Tunnel error"
      }
  }
}

/** Carries the HTTP status of a failed WebSocket upgrade (OkHttp gives it to `onFailure`). */
class WebSocketUpgradeException(
  val httpStatus: Int?,
  cause: Throwable? = null,
) : IOException(
  if (httpStatus != null) "WebSocket upgrade failed with HTTP $httpStatus" else "WebSocket upgrade failed",
  cause,
)

/** Invalid local configuration (bad host, missing key, fixed port is zero, ...). */
class ConfigException(message: String) : IOException(message)

/** The local listener did not bind to the fixed requested port. */
class LocalPortException(message: String) : IOException(message)

/** Pure error classifier shared by the engine and the unit tests. */
object ErrorClassifier {

  fun classify(throwable: Throwable): FailureClassification {
    val chain = throwable.causeChain()

    chain.firstInstance<WebSocketUpgradeException>()?.let { upgrade ->
      // A status means Cloudflare answered the upgrade itself; without one the cause chain
      // (DNS, timeout, TLS, ...) carries the useful information.
      if (upgrade.httpStatus != null) return fromHttpStatus(upgrade.httpStatus)
    }
    if (chain.any { it is ConfigException }) {
      return FailureClassification(ErrorKind.CONFIG, retryable = false, action = FailureAction.FATAL)
    }
    if (chain.any { it is LocalPortException }) {
      return FailureClassification(ErrorKind.PORT_IN_USE, retryable = false, action = FailureAction.FATAL)
    }

    val messages = chain.mapNotNull { it.message }.map { it.lowercase(Locale.ROOT) }
    val combined = messages.joinToString(" | ")

    // Local bind failures before anything else: the message usually contains "cannot be bound".
    val looksLikeBindFailure =
      chain.any { it is BindException } ||
        combined.contains("cannot be bound") ||
        combined.contains("address already in use") ||
        combined.contains("eaddrinuse") ||
        combined.contains("bind failed") ||
        combined.contains("unable to bind") ||
        combined.contains("failed to bind") ||
        (combined.contains("permission denied") && combined.contains("port"))
    if (looksLikeBindFailure) {
      return FailureClassification(ErrorKind.PORT_IN_USE, retryable = false, action = FailureAction.FATAL)
    }

    val isJsch = chain.any { it is JSchException }
    val looksLikeSshAuth =
      isJsch &&
        listOf(
          "auth fail",
          "auth cancel",
          "userauth fail",
          "invalid privatekey",
          "invalid private key",
          "invalid key",
          "incorrect passphrase",
          "bad passphrase",
          "passphrase",
          "key is encrypted",
          "unable to decrypt",
          "no such identity",
          "publickey",
          "authentication failed",
          "password authentication",
        ).any(combined::contains)
    if (looksLikeSshAuth) {
      return FailureClassification(ErrorKind.SSH_AUTH, retryable = false, action = FailureAction.FATAL)
    }

    val looksLikeDns =
      chain.any { it is UnknownHostException } ||
        listOf(
          "unknownhost",
          "unable to resolve host",
          "no address associated with hostname",
          "nodename nor servname",
          "name or service not known",
          "hostname not found",
          "failed to resolve",
        ).any(combined::contains)
    if (looksLikeDns) {
      return FailureClassification(ErrorKind.DNS, retryable = true, action = FailureAction.RETRY)
    }

    val looksLikeTimeout =
      chain.any { it is SocketTimeoutException } ||
        (chain.any { it is InterruptedIOException } &&
          listOf("timeout", "timed out").any(combined::contains)) ||
        listOf("timed out", "read timed out", "server is not responding", "timeout").any(combined::contains)
    if (looksLikeTimeout) {
      return FailureClassification(ErrorKind.TIMEOUT, retryable = true, action = FailureAction.RETRY)
    }

    val looksLikeProtocol =
      listOf(
        "algorithm negotiation fail",
        "host key",
        "hostkey",
        "protocol error",
        "bad packet",
        "corrupt",
        "ssh_msg",
        "identification has failed",
        "identification string",
        "kex ",
        "verify:",
      ).any(combined::contains)
    if (looksLikeProtocol) {
      return FailureClassification(ErrorKind.PROTOCOL, retryable = true, action = FailureAction.RETRY)
    }

    val looksLikeNetwork =
      chain.any {
        it is ConnectException ||
          it is NoRouteToHostException ||
          it is SocketException ||
          it is EOFException
      } ||
        listOf(
          "failed to connect",
          "connection refused",
          "connection reset",
          "broken pipe",
          "unexpected end of stream",
          "stream was reset",
          "connection closed",
          "websocket transport",
          "socket closed",
          "network is unreachable",
          "connection abort",
          "network error",
          "websocket upgrade failed",
          "eof",
        ).any(combined::contains)
    if (looksLikeNetwork) {
      return FailureClassification(ErrorKind.NETWORK, retryable = true, action = FailureAction.RETRY)
    }

    // Remaining JSch failures are protocol/session issues rather than local config.
    if (isJsch) {
      return FailureClassification(ErrorKind.PROTOCOL, retryable = true, action = FailureAction.RETRY)
    }
    if (chain.any { it is IOException }) {
      return FailureClassification(ErrorKind.NETWORK, retryable = true, action = FailureAction.RETRY)
    }
    return FailureClassification(ErrorKind.UNKNOWN, retryable = true, action = FailureAction.RETRY)
  }

  /** Classification of a failed WebSocket upgrade given the HTTP status (if any). */
  fun fromHttpStatus(httpStatus: Int?): FailureClassification =
    when {
      httpStatus == null -> FailureClassification(ErrorKind.NETWORK, retryable = true, action = FailureAction.RETRY)
      httpStatus == 302 || httpStatus == 303 || httpStatus == 307 || httpStatus == 308 ||
        httpStatus == 401 || httpStatus == 403 ->
        FailureClassification(ErrorKind.ACCESS_AUTH, retryable = true, action = FailureAction.NEEDS_AUTH)
      httpStatus == 408 || httpStatus == 425 || httpStatus == 429 || httpStatus in 500..599 ->
        FailureClassification(ErrorKind.NETWORK, retryable = true, action = FailureAction.RETRY)
      httpStatus in 400..499 ->
        FailureClassification(ErrorKind.PROTOCOL, retryable = true, action = FailureAction.RETRY)
      else -> FailureClassification(ErrorKind.PROTOCOL, retryable = true, action = FailureAction.RETRY)
    }

  private inline fun <reified T : Throwable> List<Throwable>.firstInstance(): T? =
    firstOrNull { it is T } as? T

  private fun Throwable.causeChain(): List<Throwable> {
    val result = mutableListOf<Throwable>()
    var current: Throwable? = this
    while (current != null && result.size < 8 && result.none { it === current }) {
      result.add(current)
      current = current.cause
    }
    return result
  }
}
