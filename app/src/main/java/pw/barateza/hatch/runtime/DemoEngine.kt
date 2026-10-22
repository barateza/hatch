package pw.barateza.hatch.runtime

import kotlin.math.roundToLong
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pw.barateza.hatch.engine.TunnelEngine
import pw.barateza.hatch.model.LogLevel
import pw.barateza.hatch.model.LogLine
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.Quality
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelMetrics
import pw.barateza.hatch.model.TunnelState

/**
 * Prototype-only simulator. It produces the same state stream as the real
 * engine so all three UI directions can be driven without credentials.
 *
 * It is also the thing behind the prototype dock's "state" button.
 */
class DemoTunnelEngine(
  private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : TunnelEngine {
  private val _state = MutableStateFlow(TunnelState())
  override val state: StateFlow<TunnelState> = _state.asStateFlow()

  private var job: Job? = null
  private var config = TunnelConfig(demoMode = true)
  private var seeded = false
  private val random = Random(7)
  private var logTick = 0

  /** Freeze the simulator on a handcrafted state (used by screenshots). */
  fun seed(state: TunnelState, config: TunnelConfig = this.config) {
    job?.cancel()
    job = null
    this.config = config
    seeded = true
    _state.value = state.copy(localUrl = config.localUrl, maxAttempts = config.maxRetries)
  }

  override fun start(config: TunnelConfig) {
    this.config = config
    job?.cancel()
    seeded = false
    job = scope.launch { simulate(config) }
  }

  override fun stop() {
    job?.cancel()
    job = null
    seeded = false
    _state.update { it.copy(phase = Phase.STOPPING) }
    scope.launch {
      delay(220)
      _state.value = TunnelState(localUrl = config.localUrl, maxAttempts = config.maxRetries, networkType = "Wi-Fi")
    }
  }

  override fun submitAccessToken(jwt: String) {
    if (_state.value.phase == Phase.NEEDS_AUTH) {
      _state.update { it.copy(phase = Phase.AUTHENTICATING, authUrl = null) }
      job?.cancel()
      job = scope.launch { simulate(config) }
    }
  }

  private suspend fun simulate(config: TunnelConfig) {
    logTick = 0
    _state.value =
      TunnelState(
        phase = Phase.CHECKING,
        maxAttempts = config.maxRetries,
        localUrl = config.localUrl,
        networkType = "Wi-Fi",
        logs = listOf(log(LogLevel.INFO, "Resolving ${config.host} · Cloudflare edge")),
      )
    delay(700)
    _state.update {
      it.copy(
        phase = Phase.CONNECTING,
        logs = it.logs + log(LogLevel.INFO, "Access session reused · expires in 23h") + log(LogLevel.INFO, "Opening wss://${config.host}"),
      )
    }
    delay(1_350)
    _state.update {
      it.copy(
        phase = Phase.CONNECTED,
        connectedAtMillis = System.currentTimeMillis(),
        quality = Quality.GOOD,
        logs =
          it.logs +
            log(LogLevel.INFO, "WebSocket upgraded · 101") +
            log(LogLevel.INFO, "SSH handshake · curve25519-sha256") +
            log(LogLevel.INFO, "Forwarding 127.0.0.1:${config.localPort} → ${config.remoteHost}:${config.remotePort}"),
      )
    }

    var tick = 0
    while (scope.isActive) {
      delay(1_000)
      tick++
      val rtt = (26 + 9 * kotlin.math.sin(tick / 3.0) + random.nextInt(0, 7)).roundToLong().coerceAtLeast(14)
      val jitter = (3 + random.nextInt(0, 9)).toLong()
      _state.update { current ->
        val metrics = current.metrics
        current.copy(
          quality = if (jitter > 9) Quality.FAIR else Quality.GOOD,
          metrics =
            metrics.copy(
              rttMs = rtt,
              jitterMs = jitter,
              bytesUp = metrics.bytesUp + random.nextLong(2_000, 24_000),
              bytesDown = metrics.bytesDown + random.nextLong(40_000, 380_000),
              throughputUpBps = random.nextLong(6_000, 22_000),
              throughputDownBps = random.nextLong(120_000, 420_000),
              reconnects = 1,
            ),
        )
      }
      if (tick % 18 == 0) {
        _state.update {
          it.copy(logs = (it.logs + log(LogLevel.INFO, "Keepalive · RTT ${it.metrics.rttMs} ms · link ${it.quality.name.lowercase()}")).takeLast(40))
        }
      }
    }
  }

  private fun log(level: LogLevel, message: String): LogLine =
    LogLine(System.currentTimeMillis(), level, message)
}
