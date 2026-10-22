package pw.barateza.hatch.model

/**
 * Prototype state model for the Hatch tunnel.
 *
 * This file is the shared contract between the tunnel engine (`:engine`) and the
 * Compose UI (`:app`). Keep it small and serialisable-friendly: the prototype
 * keeps everything in memory except [TunnelConfig].
 */
enum class Phase {
  /** Nothing running. */
  IDLE,
  /** Looking up the endpoint / checking for a cached Access token. */
  CHECKING,
  /** The endpoint needs a Cloudflare Access login before we can connect. */
  NEEDS_AUTH,
  /** First contact with this host; the SHA-256 fingerprint must be approved by the user. */
  NEEDS_HOST_KEY,
  /** The user finished the browser login and the engine is validating it. */
  AUTHENTICATING,
  /** Access is cleared; opening the transport and running the SSH handshake. */
  CONNECTING,
  /** Tunnel is up and forwarding. */
  CONNECTED,
  /** Tunnel is up but the link quality is poor; adaptive parameters are applied. */
  DEGRADED,
  /** Waiting out an exponential backoff delay before the next attempt. */
  RETRYING,
  /** Device offline; waiting for connectivity and not burning an attempt. */
  WAITING_FOR_NETWORK,
  /** A disconnect is in flight. */
  STOPPING,
  /** Gave up (or hit a non-retryable error). User action is required. */
  ERROR,
}

enum class Quality {
  UNKNOWN,
  OFFLINE,
  POOR,
  FAIR,
  GOOD,
  EXCELLENT,
}

enum class ErrorKind {
  ACCESS_AUTH,
  HOST_KEY,
  SSH_AUTH,
  NETWORK,
  DNS,
  TIMEOUT,
  PORT_IN_USE,
  PROTOCOL,
  CONFIG,
  UNKNOWN,
}

enum class LogLevel { INFO, WARN, ERROR }

data class TunnelError(
  val kind: ErrorKind,
  val title: String,
  val detail: String,
  val retryable: Boolean = true,
)

/** A host key the user has not pinned yet. The engine will not proceed until it is trusted. */
data class HostKeyChallenge(
  val host: String,
  val fingerprint: String,
)

data class TunnelMetrics(
  val rttMs: Long = 0L,
  val jitterMs: Long = 0L,
  val bytesUp: Long = 0L,
  val bytesDown: Long = 0L,
  val keepaliveMisses: Int = 0,
  val reconnects: Int = 0,
  val throughputUpBps: Long = 0L,
  val throughputDownBps: Long = 0L,
)

data class LogLine(
  val atMillis: Long,
  val level: LogLevel,
  val message: String,
)

data class TunnelState(
  val phase: Phase = Phase.IDLE,
  val attempt: Int = 0,
  val maxAttempts: Int = 6,
  val retryDelayMs: Long? = null,
  /** Milliseconds until the next automatic attempt; ticked by the UI layer. */
  val retryRemainingMs: Long? = null,
  /** Cloudflare Access login URL to open when [phase] is [Phase.NEEDS_AUTH]. */
  val authUrl: String? = null,
  /** Present when [phase] is [Phase.NEEDS_HOST_KEY]; the UI must show and approve the pin. */
  val hostKeyChallenge: HostKeyChallenge? = null,
  val error: TunnelError? = null,
  val metrics: TunnelMetrics = TunnelMetrics(),
  val quality: Quality = Quality.UNKNOWN,
  val connectedAtMillis: Long? = null,
  val lastSessionMs: Long = 0L,
  val networkType: String = "unknown",
  val logs: List<LogLine> = emptyList(),
  val localUrl: String = "http://127.0.0.1:3080",
) {
  val isBusy: Boolean
    get() = phase in BUSY_PHASES

  val isLive: Boolean
    get() = phase == Phase.CONNECTED || phase == Phase.DEGRADED

  val canToggle: Boolean
    get() = phase != Phase.STOPPING && phase != Phase.NEEDS_HOST_KEY

  companion object {
    private val BUSY_PHASES =
      setOf(
        Phase.CHECKING,
        Phase.AUTHENTICATING,
        Phase.CONNECTING,
        Phase.RETRYING,
        Phase.WAITING_FOR_NETWORK,
        Phase.STOPPING,
      )
  }
}

enum class SshAuthMode { KEY, PASSWORD }

/**
 * Everything the user (or a preview fixture) can configure.
 *
 * There is deliberately no built-in endpoint: the app is a generic SSH local-forward client, so a
 * fresh install must ask the user for a host and user in Settings. Forwarding defaults to
 * `127.0.0.1:3080` because that is the common local-service port used by this app's own examples.
 */
data class TunnelConfig(
  val host: String = "",
  val sshUser: String = "",
  val useCloudflareAccess: Boolean = true,
  val localPort: Int = 3080,
  /** Optional authenticated harness URL, e.g. http://127.0.0.1:3080/?token=… */
  val harnessUrl: String = "",
  val remoteHost: String = "127.0.0.1",
  val remotePort: Int = 3080,
  val authMode: SshAuthMode = SshAuthMode.KEY,
  val privateKey: String = "",
  val keyPassphrase: String = "",
  val password: String = "",
  val maxRetries: Int = 6,
  val baseDelayMs: Long = 1_200L,
  val maxDelayMs: Long = 45_000L,
  val jitter: Float = 0.30f,
  val keepAliveSeconds: Int = 20,
  val adaptive: Boolean = true,
  val demoMode: Boolean = false,
) {
  val localUrl: String get() = "http://127.0.0.1:$localPort"
  val endpointLabel: String get() = host.ifBlank { "no endpoint" }
  val forwardLabel: String get() = "127.0.0.1:$localPort → $remoteHost:$remotePort"
}
