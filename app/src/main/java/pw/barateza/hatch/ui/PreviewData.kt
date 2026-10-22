package pw.barateza.hatch.ui

import pw.barateza.hatch.model.LogLevel
import pw.barateza.hatch.model.LogLine
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.Quality
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelError
import pw.barateza.hatch.model.TunnelMetrics
import pw.barateza.hatch.model.TunnelState
import pw.barateza.hatch.model.ErrorKind

/**
 * Deterministic sample data for Compose previews and for teammate screenshots.
 * Not used by the running app unless the prototype seed asks for it.
 */
object Sample {
  val config =
    TunnelConfig(
      host = "ssh.example.com",
      sshUser = "user",
      localPort = 3080,
      remoteHost = "127.0.0.1",
      remotePort = 3080,
      maxRetries = 6,
      baseDelayMs = 1_200,
      maxDelayMs = 45_000,
      keepAliveSeconds = 20,
      demoMode = true,
    )

  val logs =
    listOf(
      LogLine(1L, LogLevel.INFO, "Endpoint reachable · Cloudflare edge GRU"),
      LogLine(2L, LogLevel.INFO, "Access token accepted · expires in 23h"),
      LogLine(3L, LogLevel.INFO, "WebSocket upgraded · wss://ssh.example.com"),
      LogLine(4L, LogLevel.INFO, "SSH handshake · chacha20-poly1305"),
      LogLine(5L, LogLevel.INFO, "127.0.0.1:3080 → 127.0.0.1:3080"),
      LogLine(6L, LogLevel.INFO, "Tunnel healthy · RTT 31 ms"),
    )

  fun state(
    phase: Phase = Phase.CONNECTED,
    quality: Quality = Quality.GOOD,
    attempt: Int = 0,
    withError: Boolean = false,
  ): TunnelState {
    val live = phase == Phase.CONNECTED || phase == Phase.DEGRADED
    val effectiveQuality =
      when (phase) {
        Phase.IDLE, Phase.CHECKING, Phase.CONNECTING, Phase.NEEDS_AUTH, Phase.AUTHENTICATING, Phase.WAITING_FOR_NETWORK -> Quality.UNKNOWN
        Phase.ERROR -> Quality.POOR
        else -> quality
      }
    val effectiveLogs =
      when (phase) {
        Phase.IDLE -> listOf(LogLine(1_766_000_000_000L, LogLevel.INFO, "Idle · one tap opens a local-only listener"))
        Phase.NEEDS_AUTH -> listOf(LogLine(1_766_000_000_000L, LogLevel.INFO, "Access session required · open the browser sign-in"))
        Phase.ERROR -> listOf(LogLine(1_766_000_000_000L, LogLevel.ERROR, "SSH key rejected · retries paused until fixed"))
        else -> logs
      }
    return TunnelState(
      phase = phase,
      attempt = attempt,
      maxAttempts = 6,
      retryDelayMs = if (phase == Phase.RETRYING) 8_000 else null,
      retryRemainingMs = if (phase == Phase.RETRYING) 6_400 else null,
      authUrl = if (phase == Phase.NEEDS_AUTH) "https://ssh.example.com/" else null,
      error =
        if (withError || phase == Phase.ERROR) {
          TunnelError(
            kind = ErrorKind.SSH_AUTH,
            title = "SSH key rejected",
            detail = "The server refused the private key. Paste a valid key in Settings, or switch to password auth.",
            retryable = false,
          )
        } else {
          null
        },
      metrics =
        TunnelMetrics(
          rttMs = if (live) 31 else 0,
          jitterMs = if (live) 7 else 0,
          bytesUp = if (live) 1_842_301 else 0,
          bytesDown = if (live) 24_380_977 else 0,
          keepaliveMisses = if (quality == Quality.POOR) 2 else 0,
          reconnects = 1,
          throughputUpBps = if (live) 12_400 else 0,
          throughputDownBps = if (live) 184_000 else 0,
        ),
      quality = effectiveQuality,
      connectedAtMillis = if (live) System.currentTimeMillis() - 734_000 else null,
      lastSessionMs = if (live) 734_000 else 0,
      networkType = "Wi-Fi",
      logs = effectiveLogs,
    )
  }

  fun ui(
    variant: Variant = Variant.AURORA,
    phase: Phase = Phase.CONNECTED,
    quality: Quality = Quality.GOOD,
    attempt: Int = 0,
    withError: Boolean = false,
  ) = HatchUiState(
    config = config,
    tunnel = state(phase, quality, attempt, withError),
    variant = variant,
    isDemo = true,
  )
}
