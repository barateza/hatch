package pw.barateza.hatch.engine

import android.content.Context
import android.util.Log
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import pw.barateza.hatch.model.ErrorKind
import pw.barateza.hatch.model.HostKeyChallenge
import pw.barateza.hatch.model.LogLevel
import pw.barateza.hatch.model.LogLine
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.Quality
import pw.barateza.hatch.model.SshAuthMode
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelError
import pw.barateza.hatch.model.TunnelMetrics
import pw.barateza.hatch.model.TunnelState

/**
 * Real SSH-over-Cloudflare-Access tunnel.
 *
 * The whole connect/retry lifecycle runs in a single IO coroutine; [start]/[stop] only mutate
 * fields and cancel/launch jobs, so callers (the Compose UI) are never blocked.
 *
 * Topology:
 *
 * ```
 * local app -> 127.0.0.1:3080 (JSch local forward listener)
 *           -> SSH session (JSch)
 *           -> DuctSocket (java.net.Socket over OkHttp WebSocket, Cf-Access-Token)
 *           -> wss://<host>/ (Cloudflare Access)
 *           -> sshd 127.0.0.1:3080
 * ```
 */
class RealTunnelEngine(context: Context) : TunnelEngine {

  private val appContext = context.applicationContext
  private val tokenStore = AccessTokenStore(appContext)
  private val hostKeyStore = HostKeyStore(appContext)
  private val networkMonitor = NetworkMonitor(appContext)

  private val baseClient =
    OkHttpClient.Builder()
      .followRedirects(false)
      .followSslRedirects(false)
      // `cloudflared access ssh` upgrades over HTTP/1.1. WebSocket Upgrade headers are not
      // meaningful on an h2 stream, so keep this client on HTTP/1.1.
      .protocols(listOf(Protocol.HTTP_1_1))
      .connectTimeout(DEFAULT_CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .readTimeout(0, TimeUnit.MILLISECONDS)
      .build()

  /** Separate client for short HTTP requests (discovery + latency probes). */
  private val probeClient =
    baseClient.newBuilder()
      .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
      .build()

  /** Survives per-start scopes; closes sessions/sockets without blocking UI callers. */
  private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** Process-lifetime collector that keeps `state.networkType` current even while idle. */
  private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

  private val stateLock = Any()
  private val mutableState = MutableStateFlow(TunnelState())
  override val state: StateFlow<TunnelState> = mutableState.asStateFlow()

  @Volatile private var generation: Int = 0
  @Volatile private var job: Job? = null
  @Volatile private var scope: CoroutineScope? = null
  @Volatile private var config: TunnelConfig = TunnelConfig()

  @Volatile private var liveSession: Session? = null
  @Volatile private var liveSocket: Socket? = null
  @Volatile private var liveConnectedAtNanos: Long? = null
  @Volatile private var authWaiter: CompletableDeferred<String>? = null
  @Volatile private var authHost: String = ""
  @Volatile private var adaptiveDegraded: Boolean = false

  init {
    engineScope.launch {
      networkMonitor.status.collect { status ->
        mutableState.update { current ->
          current.copy(
            networkType = status.label,
            quality = if (!status.online) Quality.OFFLINE else current.quality,
          )
        }
      }
    }
  }

  // ---------------------------------------------------------------------------------------------
  // TunnelEngine
  // ---------------------------------------------------------------------------------------------

  override fun start(config: TunnelConfig) {
    val newGeneration: Int
    val oldJob: Job?
    val oldSession: Session?
    val oldSocket: Socket?
    var startJob: Job? = null
    val newScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    synchronized(stateLock) {
      generation += 1
      newGeneration = generation

      oldJob = job
      oldSession = liveSession
      oldSocket = liveSocket
      liveSession = null
      liveSocket = null
      liveConnectedAtNanos = null
      runCatching { authWaiter?.cancel() }
      authWaiter = null
      authHost = ""

      this.config = config
      adaptiveDegraded = false

      // Cancel the previous loop and its network/collector children before starting a new one.
      oldJob?.cancel()
      scope?.cancel()
      scope = newScope

      val oldCleanup = closeResourcesAsync(oldSession, oldSocket)
      startJob =
        newScope.launch(start = CoroutineStart.LAZY) {
          withTimeoutOrNull(CLEANUP_JOIN_TIMEOUT_MS) { oldJob?.join() }
          withTimeoutOrNull(CLEANUP_JOIN_TIMEOUT_MS) { oldCleanup.join() }
          if (newGeneration == generation) {
            runLoop(newGeneration)
          }
        }
      job = startJob

      mutableState.update { current ->
        current.copy(
          phase = Phase.CHECKING,
          attempt = 0,
          maxAttempts = config.maxRetries,
          retryDelayMs = null,
          retryRemainingMs = null,
          authUrl = null,
          hostKeyChallenge = null,
          error = null,
          connectedAtMillis = null,
          lastSessionMs = 0L,
          metrics = TunnelMetrics(),
          quality = Quality.UNKNOWN,
          localUrl = config.localUrl,
        )
      }
    }

    log(newGeneration, LogLevel.INFO, "Start requested: ${config.endpointLabel} / ${config.forwardLabel}")
    startJob?.start()
  }

  override fun stop() {
    val stopGeneration: Int
    val session: Session?
    val socket: Socket?
    val sessionStartNanos: Long?
    val cleanup: Job

    synchronized(stateLock) {
      generation += 1
      stopGeneration = generation
      session = liveSession
      socket = liveSocket
      sessionStartNanos = liveConnectedAtNanos
      liveSession = null
      liveSocket = null
      liveConnectedAtNanos = null
      runCatching { authWaiter?.cancel() }
      authWaiter = null

      job?.cancel()
      job = null
      scope?.cancel()
      scope = null

      mutableState.update { current ->
        current.copy(
          phase = Phase.STOPPING,
          retryDelayMs = null,
          retryRemainingMs = null,
          authUrl = null,
          hostKeyChallenge = null,
        )
      }

      cleanup = closeResourcesAsync(session, socket)
    }

    log(stopGeneration, LogLevel.INFO, "Stop requested")
    cleanupScope.launch {
      withTimeoutOrNull(CLEANUP_JOIN_TIMEOUT_MS) { cleanup.join() }
      if (stopGeneration != generation) return@launch

      val sessionMs = sessionStartNanos?.let { (System.nanoTime() - it) / 1_000_000L }
      baseClient.connectionPool.evictAll()
      mutableState.update { current ->
        current.copy(
          phase = Phase.IDLE,
          connectedAtMillis = null,
          lastSessionMs = sessionMs ?: current.lastSessionMs,
          retryDelayMs = null,
          retryRemainingMs = null,
          authUrl = null,
          error = null,
          quality = Quality.UNKNOWN,
          metrics =
            current.metrics.copy(
              throughputUpBps = 0L,
              throughputDownBps = 0L,
              keepaliveMisses = 0,
            ),
        )
      }
    }
  }

  override fun submitAccessToken(jwt: String) {
    val trimmed = jwt.trim()
    val nowMillis = System.currentTimeMillis()
    val expiresAtMillis = Jwt.expiryMillis(trimmed)
    val currentGeneration = generation

    if (trimmed.isEmpty() || expiresAtMillis == null) {
      log(currentGeneration, LogLevel.WARN, "Ignoring malformed Cloudflare Access token")
      updateState(currentGeneration) { state ->
        if (state.phase == Phase.NEEDS_AUTH || state.phase == Phase.AUTHENTICATING) {
          state.copy(
            error =
              TunnelError(
                kind = ErrorKind.ACCESS_AUTH,
                title = "Cloudflare Access login failed",
                detail = "The captured CF_Authorization value is not a valid JWT. Sign in again.",
                retryable = true,
              ),
          )
        } else {
          state
        }
      }
      return
    }

    if (expiresAtMillis <= nowMillis + TOKEN_SKEW_MS) {
      log(currentGeneration, LogLevel.WARN, "Cloudflare Access token is expired; sign in again")
      updateState(currentGeneration) { state ->
        if (state.phase == Phase.NEEDS_AUTH || state.phase == Phase.AUTHENTICATING) {
          state.copy(
            error =
              TunnelError(
                kind = ErrorKind.ACCESS_AUTH,
                title = "Cloudflare Access login expired",
                detail = "The captured token has already expired. Sign in again.",
                retryable = true,
              ),
          )
        } else {
          state
        }
      }
      return
    }

    val host = authHost.ifBlank { config.host }
    tokenStore.save(host, trimmed)

    val waiter = authWaiter
    if (waiter != null && !waiter.isCompleted) {
      updateState(currentGeneration) { state ->
        if (state.phase == Phase.NEEDS_AUTH || state.phase == Phase.AUTHENTICATING) {
          state.copy(phase = Phase.AUTHENTICATING, authUrl = null, error = null)
        } else {
          state
        }
      }
      log(
        currentGeneration,
        LogLevel.INFO,
        "Cloudflare Access token captured (expires in ${minutesUntil(expiresAtMillis, nowMillis)} min)",
      )
      waiter.complete(trimmed)
    } else {
      log(currentGeneration, LogLevel.INFO, "Cloudflare Access token cached for $host")
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Main retry loop
  // ---------------------------------------------------------------------------------------------

  private suspend fun runLoop(loopGeneration: Int) {
    val cfg = config
    log(
      loopGeneration,
      LogLevel.INFO,
      "Engine started (${if (cfg.useCloudflareAccess) "Cloudflare Access WebSocket" else "direct SSH"})",
    )

    validateConfig(cfg)?.let { error ->
      updateState(loopGeneration) { state ->
        state.copy(phase = Phase.ERROR, error = error, retryDelayMs = null, retryRemainingMs = null)
      }
      log(loopGeneration, LogLevel.ERROR, "${error.title}: ${error.detail}")
      return
    }

    var attempt = 0
    var forceReauth = false
    val history = ConnectionHistory()

    while (true) {
      ensureCurrent(loopGeneration)

      // Offline: never consume an attempt, just wait for connectivity.
      if (!networkMonitor.status.value.online) {
        attempt = 0
        updateState(loopGeneration) { it.copy(attempt = 0) }
        waitForNetwork(loopGeneration)
        continue
      }

      attempt += 1
      updateState(loopGeneration) {
        it.copy(
          phase = Phase.CHECKING,
          attempt = attempt,
          error = null,
          retryDelayMs = null,
          retryRemainingMs = null,
        )
      }

      try {
        val token =
          if (cfg.useCloudflareAccess) {
            val obtained = obtainToken(loopGeneration, cfg, forceReauth)
            forceReauth = false
            obtained
          } else {
            null
          }

        updateState(loopGeneration) { it.copy(phase = Phase.CONNECTING) }
        log(
          loopGeneration,
          LogLevel.INFO,
          "Connecting (attempt $attempt${if (cfg.maxRetries > 0) "/${cfg.maxRetries}" else ", unlimited"})...",
        )

        connectAndServe(
          connectGeneration = loopGeneration,
          cfg = cfg,
          token = token,
          history = history,
          onStable = {
            attempt = 0
            updateState(loopGeneration) { it.copy(attempt = 0) }
          },
        )
        // connectAndServe always throws; this line is unreachable but keeps the compiler happy.
        throw IOException("SSH session ended unexpectedly")
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (failure: Throwable) {
        ensureCurrent(loopGeneration)

        if (!networkMonitor.status.value.online) {
          attempt = 0
          updateState(loopGeneration) { it.copy(attempt = 0) }
          log(
            loopGeneration,
            LogLevel.WARN,
            "Network unavailable (${detailOf(failure)}); pausing attempts until it returns",
          )
          waitForNetwork(loopGeneration)
          continue
        }

        when (failure) {
          is UnknownHostKeyException -> {
            updateState(loopGeneration) {
              it.copy(
                phase = Phase.NEEDS_HOST_KEY,
                attempt = 0,
                hostKeyChallenge = HostKeyChallenge(failure.host, failure.fingerprint),
                authUrl = null,
                error = null,
                retryDelayMs = null,
                retryRemainingMs = null,
              )
            }
            log(
              loopGeneration,
              LogLevel.WARN,
              "Host key is not pinned yet: ${failure.fingerprint}; waiting for user approval",
            )
            return
          }

          is HostKeyMismatchException -> {
            val error =
              TunnelError(
                kind = ErrorKind.HOST_KEY,
                title = "The host key changed",
                detail =
                  "Expected ${failure.expected}, but ${failure.host} presented ${failure.actual}. " +
                    "Refusing to connect. If the server was rekeyed on purpose, forget the pin in Settings.",
                retryable = false,
              )
            updateState(loopGeneration) {
              it.copy(
                phase = Phase.ERROR,
                error = error,
                hostKeyChallenge = null,
                authUrl = null,
                retryDelayMs = null,
                retryRemainingMs = null,
              )
            }
            log(loopGeneration, LogLevel.ERROR, "Host key mismatch: ${error.detail}")
            return
          }
        }

        val classification = ErrorClassifier.classify(failure)
        when (classification.action) {
          FailureAction.NEEDS_AUTH -> {
            tokenStore.clear(cfg.host)
            // A rejected token is not a failed connection attempt.
            attempt = (attempt - 1).coerceAtLeast(0)
            forceReauth = true
            updateState(loopGeneration) {
              it.copy(phase = Phase.CHECKING, attempt = attempt, authUrl = null, error = null)
            }
            log(
              loopGeneration,
              LogLevel.WARN,
              "Cloudflare Access rejected the token (${detailOf(failure)}); re-authenticating without consuming an attempt",
            )
            continue
          }

          FailureAction.FATAL -> {
            val error = classification.toTunnelError(failure, retryable = false)
            updateState(loopGeneration) {
              it.copy(
                phase = Phase.ERROR,
                error = error,
                authUrl = null,
                retryDelayMs = null,
                retryRemainingMs = null,
              )
            }
            log(loopGeneration, LogLevel.ERROR, "${classification.title}: ${error.detail}")
            return
          }

          FailureAction.RETRY -> {
            if (cfg.maxRetries > 0 && attempt >= cfg.maxRetries) {
              val error = classification.toTunnelError(failure, retryable = true)
              updateState(loopGeneration) {
                it.copy(
                  phase = Phase.ERROR,
                  error = error,
                  retryDelayMs = null,
                  retryRemainingMs = null,
                )
              }
              log(
                loopGeneration,
                LogLevel.ERROR,
                "Giving up after $attempt failed attempt(s): ${classification.title}: ${error.detail}",
              )
              return
            }

            val delayMs = Backoff.delayMs(attempt, cfg.baseDelayMs, cfg.maxDelayMs, cfg.jitter)
            log(
              loopGeneration,
              LogLevel.WARN,
              "Attempt $attempt failed (${classification.kind}): ${detailOf(failure)}; retrying in ${delayMs}ms",
            )
            val waited = waitForRetry(
              loopGeneration,
              delayMs,
              classification.toTunnelError(failure, retryable = true),
            )
            if (!waited) {
              // Connectivity dropped during the backoff: this cycle no longer counts.
              attempt = 0
              updateState(loopGeneration) { it.copy(attempt = 0) }
              waitForNetwork(loopGeneration)
            }
          }
        }
      }
    }
  }

  private suspend fun waitForNetwork(loopGeneration: Int) {
    updateState(loopGeneration) {
      it.copy(
        phase = Phase.WAITING_FOR_NETWORK,
        quality = Quality.OFFLINE,
        retryDelayMs = null,
        retryRemainingMs = null,
      )
    }
    log(loopGeneration, LogLevel.WARN, "Device is offline; waiting for network (attempt not consumed)")
    networkMonitor.status.first { it.online }
    ensureCurrent(loopGeneration)
    log(
      loopGeneration,
      LogLevel.INFO,
      "Network restored (${networkMonitor.status.value.label}); retrying immediately",
    )
  }

  /**
   * Waits out the exponential backoff, ticking `retryRemainingMs` every [RETRY_TICK_MS].
   *
   * @return `true` when the full delay elapsed, `false` when the device went offline during the
   *     wait (the caller then pauses without consuming the attempt).
   */
  private suspend fun waitForRetry(
    loopGeneration: Int,
    delayMs: Long,
    error: TunnelError,
  ): Boolean {
    val total = delayMs.coerceAtLeast(0L)
    updateState(loopGeneration) {
      it.copy(
        phase = Phase.RETRYING,
        retryDelayMs = total,
        retryRemainingMs = total,
        error = error,
      )
    }

    var remaining = total
    while (remaining > 0L) {
      ensureCurrent(loopGeneration)
      val step = minOf(RETRY_TICK_MS, remaining)
      delay(step)
      remaining -= step
      updateState(loopGeneration) { it.copy(retryRemainingMs = remaining.coerceAtLeast(0L)) }
      if (!networkMonitor.status.value.online) {
        return false
      }
    }
    updateState(loopGeneration) { it.copy(retryDelayMs = null, retryRemainingMs = null) }
    return true
  }

  // ---------------------------------------------------------------------------------------------
  // Auth
  // ---------------------------------------------------------------------------------------------

  private suspend fun obtainToken(
    loopGeneration: Int,
    cfg: TunnelConfig,
    forceReauth: Boolean,
  ): String? {
    if (!forceReauth) {
      val cached = tokenStore.load(cfg.host)
      if (cached != null) {
        if (Jwt.isUsable(cached, System.currentTimeMillis(), TOKEN_SKEW_MS)) {
          val expiresAt = Jwt.expiryMillis(cached)
          val suffix =
            expiresAt?.let { " (expires in ${minutesUntil(it, System.currentTimeMillis())} min)" } ?: ""
          log(loopGeneration, LogLevel.INFO, "Using cached Cloudflare Access token$suffix")
          return cached
        }
        tokenStore.clear(cfg.host)
        log(loopGeneration, LogLevel.WARN, "Cached Cloudflare Access token is expired or malformed; signing in again")
      }
    }

    updateState(loopGeneration) { it.copy(phase = Phase.CHECKING) }

    val loginUrl =
      try {
        withTimeout(DISCOVERY_TIMEOUT_MS) {
          CloudflareAccess.discoverLoginUrl(probeClient, cfg.host, DISCOVERY_TIMEOUT_MS)
        }
      } catch (timeout: TimeoutCancellationException) {
        throw IOException("Timed out discovering the Cloudflare Access login URL", timeout)
      }

    if (loginUrl == null) {
      log(
        loopGeneration,
        LogLevel.WARN,
        "Endpoint did not ask for Cloudflare Access; continuing without a token",
      )
      return null
    }

    val waiter = CompletableDeferred<String>()
    synchronized(stateLock) {
      authWaiter = waiter
      authHost = cfg.host
    }
    updateState(loopGeneration) {
      it.copy(
        phase = Phase.NEEDS_AUTH,
        authUrl = loginUrl,
        error = null,
        retryDelayMs = null,
        retryRemainingMs = null,
      )
    }
    log(loopGeneration, LogLevel.INFO, "Cloudflare Access login required; waiting for the browser login")

    val jwt = waiter.await()
    ensureCurrent(loopGeneration)

    updateState(loopGeneration) {
      it.copy(phase = Phase.AUTHENTICATING, authUrl = null, error = null)
    }
    val expiresAt = Jwt.expiryMillis(jwt)
    log(
      loopGeneration,
      LogLevel.INFO,
      "Cloudflare Access token accepted" +
        (expiresAt?.let { " (expires in ${minutesUntil(it, System.currentTimeMillis())} min)" } ?: ""),
    )
    return jwt
  }

  // ---------------------------------------------------------------------------------------------
  // Connect + run a session
  // ---------------------------------------------------------------------------------------------

  private suspend fun connectAndServe(
    connectGeneration: Int,
    cfg: TunnelConfig,
    token: String?,
    history: ConnectionHistory,
    onStable: () -> Unit,
  ): Nothing {
    val connectTimeoutMs = currentConnectTimeoutMs()
    var socket: Socket? = null
    var session: Session? = null
    var connectedAtNanos: Long? = null

    try {
      val transportStartedNanos = System.nanoTime()
      val duct: DuctSocket?
      if (cfg.useCloudflareAccess) {
        val webSocketClient =
          baseClient.newBuilder()
            .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(currentWebSocketPingMs(), TimeUnit.MILLISECONDS)
            .build()
        duct = CloudflareAccess.openTunnel(webSocketClient, cfg.host, token, connectTimeoutMs)
        socket = duct
      } else {
        socket = openDirectSocket(cfg, connectTimeoutMs)
        duct = null
      }
      liveSocket = socket
      ensureCurrent(connectGeneration)

      val transportMs = (System.nanoTime() - transportStartedNanos) / 1_000_000L
      log(
        connectGeneration,
        LogLevel.INFO,
        if (cfg.useCloudflareAccess) {
          "WebSocket transport established in ${transportMs}ms"
        } else {
          "Direct TCP transport established in ${transportMs}ms"
        },
      )

      val jsch = JSch()
      when (cfg.authMode) {
        SshAuthMode.KEY -> {
          val passphrase =
            cfg.keyPassphrase.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
          try {
            jsch.addIdentity(
              "hatch",
              cfg.privateKey.toByteArray(Charsets.UTF_8),
              null,
              passphrase,
            )
          } catch (failure: Throwable) {
            // JSch can throw low-level parsing exceptions (including AIOOBE) for malformed PEM.
            // A bad key is a configuration error: never burn retries on it.
            throw ConfigException(
              "The SSH private key could not be parsed (${failure.message ?: failure::class.java.simpleName}).",
            )
          }
        }

        SshAuthMode.PASSWORD -> Unit
      }

      val newSession = jsch.getSession(cfg.sshUser, cfg.host, SSH_PORT)
      session = newSession
      liveSession = newSession
      ensureCurrent(connectGeneration)

      val hostKeys = PinnedHostKeyRepository(hostKeyStore, cfg.host)
      newSession.hostKeyRepository = hostKeys
      newSession.setConfig("StrictHostKeyChecking", "yes")
      if (cfg.authMode == SshAuthMode.PASSWORD) {
        newSession.setPassword(cfg.password.toByteArray(Charsets.UTF_8))
      }
      newSession.setSocketFactory(PreconnectedSocketFactory(requireNotNull(socket)))

      // Setting this before connect makes JSch start its reader with the keepalive timeout;
      // the spec-mandated call after connect re-asserts/updates it below.
      newSession.setServerAliveInterval(currentKeepAliveMs())
      newSession.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)

      log(
        connectGeneration,
        LogLevel.INFO,
        "SSH handshake with ${cfg.sshUser}@${cfg.host}:$SSH_PORT ...",
      )
      try {
        newSession.connect(connectTimeoutMs.toInt())
      } catch (failure: Throwable) {
        // JSch turns a missing/changed repository entry into a generic JSchException. The
        // repository keeps the exact reason so the UI can distinguish first use from an attack.
        hostKeys.unknownFingerprint?.let { fingerprint ->
          throw UnknownHostKeyException(cfg.host, fingerprint)
        }
        hostKeys.mismatch?.let { (expected, actual) ->
          throw HostKeyMismatchException(cfg.host, expected, actual)
        }
        throw failure
      }
      ensureCurrent(connectGeneration)

      newSession.setServerAliveInterval(currentKeepAliveMs())
      newSession.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX)

      val boundPort =
        newSession.setPortForwardingL(
          "127.0.0.1",
          cfg.localPort,
          cfg.remoteHost,
          cfg.remotePort,
        )
      if (boundPort != cfg.localPort) {
        throw LocalPortException(
          "Local forward bound to $boundPort instead of the requested fixed port ${cfg.localPort}",
        )
      }

      connectedAtNanos = System.nanoTime()
      val connectedAtMillis = System.currentTimeMillis()
      liveConnectedAtNanos = connectedAtNanos

      if (history.everConnected) {
        updateState(connectGeneration) {
          it.copy(metrics = it.metrics.copy(reconnects = it.metrics.reconnects + 1))
        }
      }
      history.everConnected = true

      updateState(connectGeneration) { state ->
        state.copy(
          phase = Phase.CONNECTED,
          connectedAtMillis = connectedAtMillis,
          authUrl = null,
          error = null,
          retryDelayMs = null,
          retryRemainingMs = null,
          metrics = state.metrics.copy(keepaliveMisses = 0),
          quality =
            QualityMapping.compute(
              state.metrics.rttMs,
              state.metrics.jitterMs,
              0,
              networkMonitor.status.value.online,
            ),
        )
      }
      log(
        connectGeneration,
        LogLevel.INFO,
        "Tunnel live: local 127.0.0.1:${cfg.localPort} -> ${cfg.remoteHost}:${cfg.remotePort} (bound $boundPort)",
      )

      monitorConnection(
        monitorGeneration = connectGeneration,
        cfg = cfg,
        session = newSession,
        duct = duct,
        connectedAtNanos = connectedAtNanos,
        onStable = onStable,
      )
      throw IOException("SSH session ended unexpectedly")
    } finally {
      val startedAt = connectedAtNanos
      if (startedAt != null) {
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000L
        updateState(connectGeneration) {
          it.copy(lastSessionMs = durationMs, connectedAtMillis = null)
        }
        log(connectGeneration, LogLevel.INFO, "Tunnel session ended after ${durationMs}ms")
      }
      liveConnectedAtNanos = null
      closeResourcesBlocking(session, socket)
    }
  }

  private fun openDirectSocket(cfg: TunnelConfig, timeoutMs: Long): Socket {
    val socket = Socket()
    try {
      socket.tcpNoDelay = true
      socket.connect(InetSocketAddress(cfg.host, SSH_PORT), timeoutMs.toInt())
      socket.soTimeout = timeoutMs.toInt()
      return socket
    } catch (failure: Throwable) {
      runCatching { socket.close() }
      throw failure
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Live monitoring, metrics, adaptive quality
  // ---------------------------------------------------------------------------------------------

  private suspend fun monitorConnection(
    monitorGeneration: Int,
    cfg: TunnelConfig,
    session: Session,
    duct: DuctSocket?,
    connectedAtNanos: Long,
    onStable: () -> Unit,
  ) {
    var lastTickNanos = System.nanoTime()
    var lastUp = duct?.bytesUp ?: 0L
    var lastDown = duct?.bytesDown ?: 0L

    val initialMetrics = mutableState.value.metrics
    var rttMs = initialMetrics.rttMs
    var jitterMs = initialMetrics.jitterMs
    var keepaliveMisses = 0
    var stableReported = false
    var nextProbeAtNanos = System.nanoTime() + FIRST_PROBE_DELAY_MS * 1_000_000L

    while (true) {
      ensureCurrent(monitorGeneration)
      delay(MONITOR_TICK_MS)
      ensureCurrent(monitorGeneration)

      if (!networkMonitor.status.value.online) {
        throw IOException("Network lost while the tunnel was live")
      }
      if (!session.isConnected) {
        throw IOException("SSH session closed by the server")
      }
      duct?.failureOrNull()?.let { throw it }
      if (duct?.isClosed == true) {
        throw IOException("WebSocket transport closed")
      }

      val nowNanos = System.nanoTime()
      val up = duct?.bytesUp ?: lastUp
      val down = duct?.bytesDown ?: lastDown
      val elapsedMs = ((nowNanos - lastTickNanos) / 1_000_000L).coerceAtLeast(1L)
      val upBps = ((up - lastUp).coerceAtLeast(0L) * 1000L) / elapsedMs
      val downBps = ((down - lastDown).coerceAtLeast(0L) * 1000L) / elapsedMs
      lastTickNanos = nowNanos
      lastUp = up
      lastDown = down

      if (!stableReported && (nowNanos - connectedAtNanos) / 1_000_000L >= STABLE_RESET_MS) {
        stableReported = true
        onStable()
        log(monitorGeneration, LogLevel.INFO, "Connection stable for 45s; retry counter reset")
      }

      // OkHttp's public WebSocket API cannot send ping frames or observe onPong (see README),
      // so ongoing RTT is sampled with a tiny unauthenticated request to the same Access edge.
      if (duct != null && nowNanos >= nextProbeAtNanos) {
        nextProbeAtNanos = nowNanos + currentProbeIntervalMs() * 1_000_000L
        val sample = measureEdgeRttMs(cfg.host)
        if (sample != null) {
          if (rttMs > 0L) {
            jitterMs = ((jitterMs * 0.7) + abs(sample - rttMs) * 0.3).toLong()
          }
          rttMs = sample
          if (keepaliveMisses > 0) {
            log(monitorGeneration, LogLevel.INFO, "Latency probe recovered after $keepaliveMisses miss(es)")
          }
          keepaliveMisses = 0
        } else {
          keepaliveMisses += 1
          log(
            monitorGeneration,
            LogLevel.WARN,
            "Latency probe failed ($keepaliveMisses/${QualityMapping.MAX_KEEPALIVE_MISSES})",
          )
        }
      }

      val quality =
        QualityMapping.compute(
          rttMs = rttMs,
          jitterMs = jitterMs,
          keepaliveMisses = keepaliveMisses,
          online = true,
        )
      val targetPhase = if (quality == Quality.POOR) Phase.DEGRADED else Phase.CONNECTED

      updateState(monitorGeneration) { state ->
        state.copy(
          phase = targetPhase,
          quality = quality,
          metrics =
            state.metrics.copy(
              rttMs = rttMs,
              jitterMs = jitterMs,
              bytesUp = up,
              bytesDown = down,
              throughputUpBps = upBps,
              throughputDownBps = downBps,
              keepaliveMisses = keepaliveMisses,
            ),
        )
      }

      applyAdaptiveProfile(monitorGeneration, session, quality)

      if (keepaliveMisses >= QualityMapping.MAX_KEEPALIVE_MISSES) {
        throw IOException(
          "Latency probes failed $keepaliveMisses times in a row; treating the link as lossy",
        )
      }
    }
  }

  private fun applyAdaptiveProfile(
    profileGeneration: Int,
    session: Session,
    quality: Quality,
  ) {
    if (!config.adaptive) return

    if (quality == Quality.POOR && !adaptiveDegraded) {
      adaptiveDegraded = true
      runCatching { session.setServerAliveInterval(DEGRADED_KEEPALIVE_SECONDS * 1000) }
      runCatching { session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX) }
      log(
        profileGeneration,
        LogLevel.WARN,
        "Quality POOR: adaptive profile enabled (keepalive ${DEGRADED_KEEPALIVE_SECONDS}s, " +
          "probe ${DEGRADED_PROBE_INTERVAL_MS}ms, connect timeout ${DEGRADED_CONNECT_TIMEOUT_MS}ms; " +
          "the shorter WebSocket ping interval applies on the next reconnect)",
      )
    } else if (
      adaptiveDegraded &&
        (quality == Quality.FAIR || quality == Quality.GOOD || quality == Quality.EXCELLENT)
    ) {
      // FAIR is already usable; keeping the degraded profile forever would never restore the
      // user's configured parameters.
      restoreConfiguredProfile(profileGeneration, session)
    }
  }

  private fun restoreConfiguredProfile(
    profileGeneration: Int,
    session: Session,
  ) {
    adaptiveDegraded = false
    runCatching { session.setServerAliveInterval(config.keepAliveSeconds * 1000) }
    runCatching { session.setServerAliveCountMax(SERVER_ALIVE_COUNT_MAX) }
    log(
      profileGeneration,
      LogLevel.INFO,
      "Quality recovered: restoring configured keepalive (${config.keepAliveSeconds}s) and probe interval",
    )
  }

  private suspend fun measureEdgeRttMs(host: String): Long? {
    val endpoint = CloudflareAccess.endpointUrl(host) ?: return null
    val request =
      Request.Builder()
        .url(endpoint)
        .get()
        .header("User-Agent", CloudflareAccess.USER_AGENT)
        .build()
    val startedNanos = System.nanoTime()
    return try {
      val response = withTimeout(PROBE_TIMEOUT_MS) { probeClient.awaitCall(request) }
      val elapsedMs = (System.nanoTime() - startedNanos) / 1_000_000L
      // Drain the (tiny) redirect body so the connection can be reused by the next probe; the
      // measured RTT stops at response headers.
      runCatching { response.body.bytes() }
      response.close()
      elapsedMs
    } catch (cancellation: CancellationException) {
      if (!currentCoroutineContext().isActive) throw cancellation
      null
    } catch (_: Throwable) {
      null
    }
  }


  // ---------------------------------------------------------------------------------------------
  // Profile helpers
  // ---------------------------------------------------------------------------------------------

  private fun currentKeepAliveMs(): Int =
    (if (adaptiveDegraded) DEGRADED_KEEPALIVE_SECONDS else config.keepAliveSeconds)
      .coerceAtLeast(0) * 1000

  private fun currentWebSocketPingMs(): Long =
    (if (adaptiveDegraded) DEGRADED_KEEPALIVE_SECONDS else config.keepAliveSeconds)
      .coerceAtLeast(0)
      .toLong() * 1000L

  private fun currentProbeIntervalMs(): Long =
    if (adaptiveDegraded) DEGRADED_PROBE_INTERVAL_MS else PROBE_INTERVAL_MS

  private fun currentConnectTimeoutMs(): Long =
    if (adaptiveDegraded) DEGRADED_CONNECT_TIMEOUT_MS else DEFAULT_CONNECT_TIMEOUT_MS

  // ---------------------------------------------------------------------------------------------
  // Validation / classification helpers
  // ---------------------------------------------------------------------------------------------

  private fun validateConfig(cfg: TunnelConfig): TunnelError? {
    val detail =
      when {
        cfg.host.isBlank() -> "The SSH host is empty."
        cfg.sshUser.isBlank() -> "The SSH user is empty."
        cfg.localPort !in 1..65535 ->
          "The local port must be a fixed port in 1..65535 (0 would ask the OS for a random port)."
        cfg.remoteHost.isBlank() -> "The remote host is empty."
        cfg.remotePort !in 1..65535 -> "The remote port must be in 1..65535."
        cfg.authMode == SshAuthMode.KEY && cfg.privateKey.isBlank() ->
          "SSH key authentication needs a private key."
        cfg.authMode == SshAuthMode.PASSWORD && cfg.password.isEmpty() ->
          "SSH password authentication needs a password."
        cfg.baseDelayMs < 0L || cfg.maxDelayMs < 0L -> "Retry delays must not be negative."
        else -> null
      } ?: return null
    return TunnelError(ErrorKind.CONFIG, "Invalid configuration", detail, retryable = false)
  }

  private fun FailureClassification.toTunnelError(
    failure: Throwable,
    retryable: Boolean,
  ): TunnelError =
    TunnelError(
      kind = kind,
      title = title,
      detail = detailOf(failure),
      retryable = retryable,
    )

  private fun detailOf(failure: Throwable): String {
    val chain = mutableListOf<Throwable>()
    var current: Throwable? = failure
    while (current != null && chain.size < 6 && chain.none { it === current }) {
      chain.add(current)
      current = current.cause
    }
    val text =
      chain.mapNotNull { it.message?.takeIf { message -> message.isNotBlank() } }
        .distinct()
        .joinToString(" <- ")
    return (text.ifBlank { failure::class.java.simpleName }).take(400)
  }

  private fun minutesUntil(epochMillis: Long, nowMillis: Long): Long =
    ((epochMillis - nowMillis) / 60_000L).coerceAtLeast(0L)

  // ---------------------------------------------------------------------------------------------
  // Lifecycle plumbing
  // ---------------------------------------------------------------------------------------------

  private fun closeResourcesAsync(session: Session?, socket: Socket?): Job =
    cleanupScope.launch {
      runCatching { session?.disconnect() }
      runCatching {
        if (socket is DuctSocket) {
          socket.shutdownNow()
        } else {
          socket?.close()
        }
      }
    }

  private fun closeResourcesBlocking(session: Session?, socket: Socket?) {
    runCatching { session?.disconnect() }
    runCatching {
      if (socket is DuctSocket) {
        socket.shutdownNow()
      } else {
        socket?.close()
      }
    }
    if (liveSession === session) liveSession = null
    if (liveSocket === socket) liveSocket = null
  }

  private suspend fun ensureCurrent(expectedGeneration: Int) {
    if (expectedGeneration != generation || !currentCoroutineContext().isActive) {
      throw CancellationException("Tunnel engine generation $expectedGeneration is no longer active")
    }
  }

  private fun updateState(expectedGeneration: Int, transform: (TunnelState) -> TunnelState) {
    if (expectedGeneration != generation) return
    mutableState.update { current ->
      if (expectedGeneration != generation) current else transform(current)
    }
  }

  private fun log(expectedGeneration: Int, level: LogLevel, message: String) {
    if (expectedGeneration != generation) return
    val line = LogLine(System.currentTimeMillis(), level, message)
    mutableState.update { current ->
      if (expectedGeneration != generation) {
        current
      } else {
        current.copy(logs = (current.logs + line).takeLast(MAX_LOG_LINES))
      }
    }
    when (level) {
      LogLevel.INFO -> Log.i(TAG, message)
      LogLevel.WARN -> Log.w(TAG, message)
      LogLevel.ERROR -> Log.e(TAG, message)
    }
  }

  private class ConnectionHistory {
    var everConnected: Boolean = false
  }

  /**
   * JSch repository backed by [HostKeyStore]. It returns JSch's normal "not included" / "changed"
   * results (so JSch produces its standard exception) and records the exact fingerprint for the
   * engine to translate into a user-facing challenge.
   */
  private class PinnedHostKeyRepository(
    private val store: HostKeyStore,
    private val expectedHost: String,
  ) : HostKeyRepository {
    @Volatile var unknownFingerprint: String? = null
    @Volatile var mismatch: Pair<String, String>? = null

    override fun check(host: String, key: ByteArray): Int {
      val fingerprint = HostKeyFingerprint.of(key)
      val pinned = store.fingerprint(expectedHost) ?: store.fingerprint(host)
      return when {
        pinned == null -> {
          if (unknownFingerprint == null) unknownFingerprint = fingerprint
          HostKeyRepository.NOT_INCLUDED
        }
        pinned == fingerprint -> HostKeyRepository.OK
        else -> {
          if (mismatch == null) mismatch = pinned to fingerprint
          HostKeyRepository.CHANGED
        }
      }
    }

    override fun add(hostkey: HostKey, ui: com.jcraft.jsch.UserInfo?) = Unit

    override fun remove(host: String, type: String) = Unit

    override fun remove(host: String, type: String, key: ByteArray) = Unit

    override fun getKnownHostsRepositoryID(): String = "hatch-pinned"

    override fun getHostKey(): Array<HostKey> = emptyArray()

    override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
  }

  private companion object {
    const val TAG = "HatchTunnelEngine"
    const val MAX_LOG_LINES = 120

    const val SSH_PORT = 22
    const val SERVER_ALIVE_COUNT_MAX = 2
    const val TOKEN_SKEW_MS = 60_000L
    const val STABLE_RESET_MS = 45_000L
    const val RETRY_TICK_MS = 250L
    const val MONITOR_TICK_MS = 1_000L
    const val FIRST_PROBE_DELAY_MS = 3_000L
    const val PROBE_INTERVAL_MS = 15_000L
    const val DEGRADED_PROBE_INTERVAL_MS = 7_000L
    const val PROBE_TIMEOUT_MS = 6_000L
    const val DISCOVERY_TIMEOUT_MS = 12_000L
    const val DEFAULT_CONNECT_TIMEOUT_MS = 15_000L
    const val DEGRADED_CONNECT_TIMEOUT_MS = 25_000L
    const val DEGRADED_KEEPALIVE_SECONDS = 10
    const val CLEANUP_JOIN_TIMEOUT_MS = 10_000L
  }
}
