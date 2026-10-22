package pw.barateza.hatch.runtime

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import pw.barateza.hatch.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import pw.barateza.hatch.engine.HostKeyStore
import pw.barateza.hatch.engine.TunnelEngine
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.SshAuthMode
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelState
import pw.barateza.hatch.service.TunnelService
import pw.barateza.hatch.ui.HatchUiState
import pw.barateza.hatch.ui.TunnelActions
import pw.barateza.hatch.ui.Variant
import pw.barateza.hatch.ui.prototype.PrototypeOverrides
import pw.barateza.hatch.ui.prototype.PrototypeSeed
import pw.barateza.hatch.runtime.tailscale.TailscaleCheck
import pw.barateza.hatch.runtime.tailscale.TailscaleHosts
import pw.barateza.hatch.runtime.tailscale.TailscalePreflight

/**
 * Single source of truth for the prototype: owns settings, engine selection
 * (simulated vs. real), the 1 Hz UI tick, and the small set of user actions the
 * variant screens can raise.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TunnelController(
  private val appContext: Context,
  private val realEngineFactory: (Context) -> TunnelEngine,
) : TunnelActions {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
  private val settings = SettingsStore(appContext)
  private val hostKeys = HostKeyStore(appContext)
  private val tailscale = TailscalePreflight(appContext)
  private val demoEngine = DemoTunnelEngine()
  private val realEngine: TunnelEngine by lazy { realEngineFactory(appContext) }

  private val _config = MutableStateFlow(settings.config)
  private val _variant = MutableStateFlow(settings.variant)
  private val _settingsOpen = MutableStateFlow(false)
  private val _tailscalePrompt = MutableStateFlow<TailscaleCheck?>(null)
  private var pendingConnect: TunnelConfig? = null
  private var preflightJob: Job? = null

  private val ticker =
    flow {
      while (currentCoroutineContext().isActive) {
        emit(System.currentTimeMillis())
        delay(1_000L)
      }
    }

  val ui: StateFlow<HatchUiState> =
    combine(_config, _variant, ticker, _tailscalePrompt) { config, variant, now, tailscalePrompt ->
        UiTick(config, variant, now, tailscalePrompt)
      }
      .flatMapLatest { tick ->
        val config = tick.config
        val engine = engineFor(config)
        engine.state.map { tunnel ->
          HatchUiState(
            config = config,
            tunnel = tunnel.copy(localUrl = config.localUrl),
            variant = tick.variant,
            isDemo = config.demoMode,
            nowMillis = tick.nowMillis,
            tailscalePrompt = tick.tailscalePrompt,
          )
        }
      }
      .stateIn(
        scope,
        SharingStarted.Eagerly,
        HatchUiState(
          config = _config.value,
          tunnel = TunnelState(localUrl = _config.value.localUrl),
          variant = _variant.value,
          isDemo = _config.value.demoMode,
          tailscalePrompt = _tailscalePrompt.value,
        ),
      )

  private data class UiTick(
    val config: TunnelConfig,
    val variant: Variant,
    val nowMillis: Long,
    val tailscalePrompt: TailscaleCheck?,
  )

  val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

  init {
    scope.launch {
      ui.map { it.tunnel.phase }.distinctUntilChanged().collect { phase ->
        if (!_config.value.demoMode && (phase == Phase.IDLE || phase == Phase.ERROR)) {
          TunnelService.markStopped(appContext)
          appContext.stopService(Intent(appContext, TunnelService::class.java))
        }
      }
    }
  }

  private fun engineFor(config: TunnelConfig): TunnelEngine =
    if (config.demoMode) demoEngine else realEngine

  private fun activeEngine(): TunnelEngine = engineFor(_config.value)

  // ---------------------------------------------------------------- config ---

  fun updateConfig(config: TunnelConfig) {
    preflightJob?.cancel()
    pendingConnect = null
    _tailscalePrompt.value = null
    val previous = _config.value
    if (previous.demoMode != config.demoMode) {
      demoEngine.stop()
      if (!previous.demoMode) realEngine.stop()
      TunnelService.markStopped(appContext)
      appContext.stopService(Intent(appContext, TunnelService::class.java))
    }
    settings.config = config
    _config.value = config
  }

  fun setVariant(variant: Variant) {
    settings.variant = variant
    _variant.value = variant
  }

  fun openSettingsInternal() {
    _settingsOpen.value = true
  }

  fun closeSettings() {
    _settingsOpen.value = false
  }

  // --------------------------------------------------------------- actions ---

  override fun toggleTunnel() {
    val config = _config.value
    val engine = engineFor(config)
    val state = engine.state.value
    if (state.isLive || state.isBusy) {
      preflightJob?.cancel()
      pendingConnect = null
      _tailscalePrompt.value = null
      engine.stop()
      TunnelService.markStopped(appContext)
      appContext.stopService(Intent(appContext, TunnelService::class.java))
      return
    }
    if (state.phase == Phase.NEEDS_HOST_KEY) {
      // The host-key dialog owns this state; the dial is disabled.
      return
    }
    if (state.phase == Phase.NEEDS_AUTH) {
      // The root screen launches the Access WebView when it observes authUrl.
      engine.start(config)
      return
    }
    if (!config.demoMode && (config.host.isBlank() || config.sshUser.isBlank())) {
      Toast.makeText(appContext, "Set the SSH host and user first", Toast.LENGTH_SHORT).show()
      _settingsOpen.value = true
      return
    }
    if (!config.demoMode && !hasCredentials(config)) {
      Toast.makeText(appContext, "Add your SSH credentials first", Toast.LENGTH_SHORT).show()
      _settingsOpen.value = true
      return
    }
    if (config.demoMode) {
      startTunnel(config)
      return
    }
    preflightThenStart(config)
  }

  private fun preflightThenStart(config: TunnelConfig) {
    preflightJob?.cancel()
    pendingConnect = null
    _tailscalePrompt.value = null
    if (!TailscaleHosts.isTailscaleHost(config.host)) {
      startTunnel(config)
      return
    }
    preflightJob =
      scope.launch {
        val check =
          try {
            withContext(Dispatchers.IO) { tailscale.check(config.host) }
          } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
          } catch (_: Throwable) {
            // A preflight must never block the tunnel on an Android API failure.
            TailscaleCheck()
          }
        if (check.needsAttention) {
          pendingConnect = config
          _tailscalePrompt.value = check
        } else {
          startTunnel(config)
        }
      }
  }

  private fun startTunnel(config: TunnelConfig) {
    _tailscalePrompt.value = null
    pendingConnect = null
    if (!config.demoMode) {
      ContextCompat.startForegroundService(
        appContext,
        Intent(appContext, TunnelService::class.java)
          .setAction(TunnelService.ACTION_START)
          .putExtra(TunnelService.EXTRA_LABEL, config.forwardLabel),
      )
    }
    engineFor(config).start(config)
  }

  override fun connectAnyway() {
    val config = pendingConnect ?: _config.value
    pendingConnect = null
    _tailscalePrompt.value = null
    startTunnel(config)
  }

  override fun dismissTailscalePrompt() {
    pendingConnect = null
    _tailscalePrompt.value = null
  }

  override fun openTailscale() {
    tailscale.openTailscale()
  }

  override fun trustHostKey() {
    val challenge = realEngine.state.value.hostKeyChallenge ?: return
    hostKeys.pin(challenge.host, challenge.fingerprint)
    startTunnel(_config.value)
  }

  override fun cancelHostKey() {
    realEngine.stop()
    TunnelService.markStopped(appContext)
    appContext.stopService(Intent(appContext, TunnelService::class.java))
  }

  fun hostKeyFingerprint(host: String = _config.value.host): String? = hostKeys.fingerprint(host)

  fun forgetHostKey() {
    val config = _config.value
    hostKeys.forget(config.host)
    realEngine.stop()
    TunnelService.markStopped(appContext)
    appContext.stopService(Intent(appContext, TunnelService::class.java))
    Toast.makeText(appContext, "Host key forgotten; the next connect will ask again", Toast.LENGTH_SHORT).show()
  }

  override fun retryNow() {
    val engine = activeEngine()
    engine.stop()
    scope.launch {
      delay(150)
      engine.start(_config.value)
    }
  }

  override fun openLocalUrl() {
    val config = _config.value
    if (!ui.value.tunnel.isLive) {
      Toast.makeText(appContext, "Tunnel is not live yet", Toast.LENGTH_SHORT).show()
      return
    }
    val target =
      config.harnessUrl.trim().let { raw ->
        when {
          raw.isEmpty() -> config.localUrl
          raw.startsWith("http://") || raw.startsWith("https://") -> raw
          else -> "http://$raw"
        }
      }
    runCatching {
      appContext.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    }
  }

  override fun openSettings() = openSettingsInternal()

  override fun reauthenticate() {
    val engine = activeEngine()
    engine.stop()
    scope.launch {
      delay(120)
      engine.start(_config.value)
    }
  }

  override fun copyDiagnostics() {
    val state = ui.value
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    val text = buildString {
      appendLine("Hatch ${BuildConfig.VERSION_NAME}")
      appendLine("time: ${formatter.format(Date())}")
      appendLine("phase: ${state.tunnel.phase}")
      appendLine("attempt: ${state.tunnel.attempt}/${state.tunnel.maxAttempts}")
      appendLine("quality: ${state.tunnel.quality} · rtt=${state.tunnel.metrics.rttMs}ms · jitter=${state.tunnel.metrics.jitterMs}ms")
      appendLine("endpoint: ${state.config.host} as ${state.config.sshUser}")
      appendLine("forward: ${state.config.forwardLabel}")
      appendLine("network: ${state.tunnel.networkType}")
      appendLine("host key: ${hostKeys.fingerprint(state.config.host) ?: "not pinned"}")
      appendLine("error: ${state.tunnel.error?.title ?: "none"}")
      appendLine("--- log ---")
      state.tunnel.logs.takeLast(60).forEach { line ->
        appendLine("${formatter.format(Date(line.atMillis))} ${line.level} ${line.message}")
      }
    }
    val redacted = DiagnosticsRedactor.redact(text)
    appContext.getSystemService<ClipboardManager>()?.setPrimaryClip(ClipData.newPlainText("Hatch diagnostics", redacted))
    Toast.makeText(appContext, "Diagnostics copied (secrets redacted)", Toast.LENGTH_SHORT).show()
  }

  // ------------------------------------------------------------- auth glue ---

  fun onAccessToken(jwt: String) {
    activeEngine().submitAccessToken(jwt)
  }

  /**
   * Re-posts the foreground notification. Used after POST_NOTIFICATIONS is granted: the service
   * may already be alive, but its first startForeground happened while the permission was denied.
   */
  fun refreshForegroundNotification() {
    val config = _config.value
    if (config.demoMode) return
    val state = realEngine.state.value
    if (!(state.isLive || state.isBusy)) return
    ContextCompat.startForegroundService(
      appContext,
      Intent(appContext, TunnelService::class.java)
        .setAction(TunnelService.ACTION_START)
        .putExtra(TunnelService.EXTRA_LABEL, config.forwardLabel),
    )
  }

  fun stopTunnelFromService() {
    activeEngine().stop()
    TunnelService.markStopped(appContext)
    appContext.stopService(Intent(appContext, TunnelService::class.java))
  }

  /**
   * Called by [TunnelService] when Android recreates the sticky service after killing the
   * process. Reconnects with the stored key and cached Access token when possible.
   */
  fun ensureTunnelRunning() {
    val config = _config.value
    if (config.demoMode || !hasCredentials(config)) return
    val engine = realEngine
    val state = engine.state.value
    if (state.isLive || state.isBusy) return
    engine.start(config)
  }

  private fun hasCredentials(config: TunnelConfig): Boolean =
    when (config.authMode) {
      SshAuthMode.KEY -> config.privateKey.isNotBlank()
      SshAuthMode.PASSWORD -> config.password.isNotEmpty()
    }

  // ------------------------------------------------------------- prototype ---

  fun applyPrototype(overrides: PrototypeOverrides) {
    overrides.variant?.let(::setVariant)
    if (overrides.phase != null) {
      seedDemo(overrides.phase, overrides)
    } else if (overrides.forceDemo && !_config.value.demoMode) {
      updateConfig(_config.value.copy(demoMode = true))
    }
  }

  fun cycleDemoState() {
    val next = PrototypeSeed.next(ui.value.tunnel.phase)
    seedDemo(next, PrototypeOverrides(attempt = if (next == Phase.RETRYING) 2 else 0))
  }

  fun seedDemo(phase: Phase, overrides: PrototypeOverrides = PrototypeOverrides()) {
    val config = _config.value.copy(demoMode = true)
    _config.value = config
    demoEngine.seed(PrototypeSeed.state(phase, overrides, config), config)
  }

  fun currentConfig(): TunnelConfig = _config.value
}
