package pw.barateza.hatch.ui.prototype

import android.content.Intent
import java.util.concurrent.TimeUnit
import pw.barateza.hatch.model.ErrorKind
import pw.barateza.hatch.model.LogLevel
import pw.barateza.hatch.model.LogLine
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.Quality
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelError
import pw.barateza.hatch.model.TunnelMetrics
import pw.barateza.hatch.model.TunnelState
import pw.barateza.hatch.ui.Variant

/**
 * Prototype-only deep link used for screenshots and demos:
 *
 *   adb shell am start -n pw.barateza.hatch/.MainActivity \
 *     --es variant GRID --es state CONNECTED --es quality GOOD
 *
 * Anything passed here forces demo mode; release builds should gate this behind
 * BuildConfig.DEBUG before shipping.
 */
data class PrototypeOverrides(
  val variant: Variant? = null,
  val phase: Phase? = null,
  val attempt: Int = 0,
  val quality: Quality? = null,
  val forceDemo: Boolean = false,
) {
  companion object {
    fun fromIntent(intent: Intent?): PrototypeOverrides {
      if (intent == null) return PrototypeOverrides()
      val variant = Variant.fromId(intent.getStringExtra("variant"))
      val phase =
        intent.getStringExtra("state")?.let { raw ->
          runCatching { Phase.valueOf(raw.trim().uppercase()) }.getOrNull()
        }
      val quality =
        intent.getStringExtra("quality")?.let { raw ->
          runCatching { Quality.valueOf(raw.trim().uppercase()) }.getOrNull()
        }
      return PrototypeOverrides(
        variant = variant,
        phase = phase,
        attempt = intent.getIntExtra("attempt", 0),
        quality = quality,
        forceDemo = intent.getBooleanExtra("demo", false) || variant != null || phase != null,
      )
    }
  }
}

object PrototypeSeed {
  private val phases = listOf(
    Phase.IDLE,
    Phase.CHECKING,
    Phase.CONNECTING,
    Phase.CONNECTED,
    Phase.DEGRADED,
    Phase.RETRYING,
    Phase.NEEDS_AUTH,
    Phase.ERROR,
  )

  /** Next phase for the prototype dock's state cycler. */
  fun next(after: Phase): Phase {
    val index = phases.indexOf(after)
    return phases[(index + 1) % phases.size]
  }

  fun state(phase: Phase, overrides: PrototypeOverrides, config: TunnelConfig): TunnelState {
    val live = phase == Phase.CONNECTED || phase == Phase.DEGRADED
    val quality =
      when (phase) {
        Phase.IDLE, Phase.CHECKING, Phase.CONNECTING, Phase.NEEDS_AUTH, Phase.AUTHENTICATING, Phase.WAITING_FOR_NETWORK -> Quality.UNKNOWN
        Phase.ERROR -> Quality.POOR
        Phase.DEGRADED -> overrides.quality ?: Quality.POOR
        else -> overrides.quality ?: Quality.GOOD
      }
    val rtt = if (quality == Quality.POOR) 380L else if (quality == Quality.FAIR) 190L else 34L
    val logs = buildList {
      add(log("Resolving ${config.host} · Cloudflare edge"))
      if (phase != Phase.IDLE) add(log("Access session reused · expires in 23h"))
      if (phase == Phase.CONNECTING || live) {
        add(log("WebSocket upgraded · wss://${config.host}"))
        add(log("SSH handshake · curve25519-sha256"))
        add(log("Forwarding 127.0.0.1:${config.localPort} → ${config.remoteHost}:${config.remotePort}"))
      }
      if (live) add(log("Keepalive · RTT $rtt ms · link ${quality.name.lowercase()}"))
      if (phase == Phase.RETRYING) add(log("Link lost · backoff attempt ${overrides.attempt.coerceAtLeast(2)}/6"))
      if (phase == Phase.NEEDS_AUTH) add(log("Access needs a sign-in · opening browser flow"))
      if (phase == Phase.ERROR) add(log("SSH key rejected · waiting for user"))
    }
    return TunnelState(
      phase = phase,
      attempt = overrides.attempt,
      maxAttempts = config.maxRetries,
      retryDelayMs = if (phase == Phase.RETRYING) 8_000 else null,
      retryRemainingMs = if (phase == Phase.RETRYING) 6_400 else null,
      authUrl =
        if (phase == Phase.NEEDS_AUTH) {
          "https://${config.host}/"
        } else {
          null
        },
      error =
        if (phase == Phase.ERROR) {
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
          rttMs = if (live) rtt else 0,
          jitterMs = if (live) (rtt / 6).coerceAtLeast(2) else 0,
          bytesUp = if (live) 1_842_301 else 0,
          bytesDown = if (live) 24_380_977 else 0,
          keepaliveMisses = if (quality == Quality.POOR) 2 else 0,
          reconnects = if (phase == Phase.RETRYING) 2 else 1,
          throughputUpBps = if (live) 12_400 else 0,
          throughputDownBps = if (live) 184_000 else 0,
        ),
      quality = if (phase == Phase.IDLE) Quality.UNKNOWN else quality,
      connectedAtMillis = if (live) System.currentTimeMillis() - 734_000 else null,
      lastSessionMs = 734_000,
      networkType = "Wi-Fi",
      logs = logs,
      localUrl = config.localUrl,
    )
  }

  private fun log(message: String): LogLine =
    LogLine(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis((2..8).random().toLong()), LogLevel.INFO, message)
}
