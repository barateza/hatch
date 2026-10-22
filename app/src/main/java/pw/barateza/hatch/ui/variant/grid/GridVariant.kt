package pw.barateza.hatch.ui.variant.grid

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin
import pw.barateza.hatch.model.ErrorKind
import pw.barateza.hatch.model.LogLevel
import pw.barateza.hatch.model.LogLine
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.ui.HatchUiState
import pw.barateza.hatch.ui.TunnelActions
import pw.barateza.hatch.ui.common.Fmt
import pw.barateza.hatch.theme.JetBrainsMono
import pw.barateza.hatch.theme.SpaceGrotesk

/**
 * GRID — a paper-white instrument panel for the Hatch tunnel.
 *
 * Masthead, a 270-degree rotary dial, a live RTT trace and a hairline telemetry
 * matrix. Flat, dense and engineered: no glass, no gradients, no dark mode.
 */
@Composable
fun GridVariant(
  state: HatchUiState,
  actions: TunnelActions,
  modifier: Modifier = Modifier,
) {
  val tunnel = state.tunnel
  val phase = tunnel.phase
  val skin = gridSkin(tunnel)
  val metrics = tunnel.metrics
  val rtt = metrics.rttMs

  // RTT history: a deterministic 40-point trace is seeded synchronously so the
  // very first frame (and therefore a screenshot) already shows a live signal.
  // Real engine samples are appended afterwards and the window stays at 40.
  val history =
    remember {
      mutableStateListOf<Float>().apply {
        addAll(seededRttTrace(if (rtt > 0L) rtt.toFloat() else 0f))
      }
    }
  LaunchedEffect(rtt, phase) {
    if (rtt > 0L) {
      history.add(rtt.toFloat())
      while (history.size > 40) {
        history.removeAt(0)
      }
    }
  }
  val peak = history.maxOrNull() ?: 0f

  val connectedAt = tunnel.connectedAtMillis
  val uptimeMs = if (connectedAt != null) (state.nowMillis - connectedAt).coerceAtLeast(0L) else 0L

  Box(modifier = modifier.fillMaxSize().background(GridColors.paper)) {
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 18.dp),
    ) {
      Masthead(actions = actions)
      HRule()
      StatusStrip(state = state, skin = skin)
      HRule()
      Spacer(Modifier.height(8.dp))

      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
          Micro("LOCAL PORT CONTROL", alpha = 0.42f)
          Micro("CH · 3080", alpha = 0.42f)
        }
        Spacer(Modifier.height(5.dp))
        RotaryDial(
          skin = skin,
          description = dialDescription(state),
          enabled = tunnel.canToggle,
          onClick = actions::toggleTunnel,
        )
      }

      Spacer(Modifier.height(10.dp))
      PhasePanel(state = state, actions = actions, skin = skin)
      Spacer(Modifier.height(10.dp))
      HRule()
      RttHistoryCell(
        history = history,
        rtt = rtt,
        peak = peak,
        accent = skin.accent,
        active = tunnel.isLive,
      )
      TelemetryGrid(state = state, uptimeMs = uptimeMs)
      Spacer(Modifier.height(12.dp))
      SessionLog(state = state)
      Spacer(Modifier.height(10.dp))
      HRule()
      Spacer(Modifier.height(8.dp))
      Footer(state = state)
      // Clearance for the floating prototype dock and the navigation bar.
      Spacer(Modifier.height(56.dp))
    }
  }
}

// -------------------------------------------------------------------- chrome ---

@Composable
private fun Masthead(actions: TunnelActions) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Micro("SSH RELAY / 01", alpha = 0.42f, letterSpacing = 1.6.sp)
      Text(
        text = "HATCH",
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W700,
        fontSize = 36.sp,
        lineHeight = 36.sp,
        letterSpacing = (-1.8).sp,
        color = GridColors.ink,
      )
    }
    TextAction(label = "COPY LOG", onClick = actions::copyDiagnostics)
    IconButton(onClick = actions::openSettings, modifier = Modifier.size(48.dp)) {
      Icon(
        imageVector = Icons.Rounded.Settings,
        contentDescription = "Open settings",
        modifier = Modifier.size(20.dp),
        tint = GridColors.ink,
      )
    }
  }
}

@Composable
private fun StatusStrip(state: HatchUiState, skin: GridSkin) {
  // The one animated element on the panel: a lamp that blinks while the link is
  // live. Kept local so the 1 Hz animation never recomposes the whole screen.
  val blink = rememberInfiniteTransition(label = "status-blink")
  val blinkAlpha by
    blink.animateFloat(
      initialValue = 1f,
      targetValue = 0.45f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 900, easing = LinearEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "status-dot",
    )
  val tunnel = state.tunnel
  val network =
    if (tunnel.phase == Phase.WAITING_FOR_NETWORK) "NO CARRIER" else tunnel.networkType.uppercase()
  val access = if (state.config.useCloudflareAccess) "CF ACCESS" else "DIRECT SSH"
  Row(Modifier.fillMaxWidth().height(28.dp), verticalAlignment = Alignment.CenterVertically) {
    StripSegment(Modifier.weight(1f)) {
      Box(
        Modifier
          .size(6.dp)
          .background(
            color = if (skin.blinking) skin.accent.copy(alpha = blinkAlpha) else skin.accent.copy(alpha = 0.45f),
            shape = CircleShape,
          ),
      )
      Spacer(Modifier.width(7.dp))
      Micro(skin.status, alpha = 0.85f)
    }
    StripDivider()
    StripSegment(Modifier.weight(1f)) { Micro(network, alpha = 0.52f) }
    StripDivider()
    StripSegment(Modifier.weight(1f)) { Micro(access, alpha = 0.52f) }
  }
}

@Composable
private fun StripSegment(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
  Row(
    modifier = modifier.padding(horizontal = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    content = content,
  )
}

@Composable
private fun StripDivider() {
  Box(Modifier.width(1.dp).height(14.dp).background(GridColors.rule))
}

@Composable
private fun Footer(state: HatchUiState) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Micro(if (state.isDemo) "SIM ENGINE" else "LIVE ENGINE", alpha = 0.32f, letterSpacing = 1.4.sp)
    Micro("GRID 02 / 03 · ${state.tunnel.logs.size} LOG", alpha = 0.32f, letterSpacing = 1.4.sp)
  }
}

// -------------------------------------------------------------------- phases ---

@Composable
private fun PhasePanel(state: HatchUiState, actions: TunnelActions, skin: GridSkin) {
  when (state.tunnel.phase) {
    Phase.CONNECTED, Phase.DEGRADED -> LiveActions(actions)
    Phase.NEEDS_AUTH, Phase.AUTHENTICATING -> AuthPanel(state, actions, skin)
    Phase.RETRYING, Phase.WAITING_FOR_NETWORK -> RetryPanel(state, actions, skin)
    Phase.ERROR -> FaultPanel(state, actions, skin)
    else -> NoteStrip(note = phaseNote(state), accent = skin.accent)
  }
}

@Composable
private fun LiveActions(actions: TunnelActions) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    PanelButton(
      label = "OPEN 127.0.0.1:3080 ↗",
      onClick = actions::openLocalUrl,
      accent = GridColors.lime,
      modifier = Modifier.weight(1f),
      contentDescription = "Open the forwarded harness at 127.0.0.1 port 3080",
    )
    PanelButton(
      label = "COPY LOG",
      onClick = actions::copyDiagnostics,
      filled = false,
      contentDescription = "Copy diagnostics to the clipboard",
    )
  }
}

@Composable
private fun AuthPanel(state: HatchUiState, actions: TunnelActions, skin: GridSkin) {
  val authenticating = state.tunnel.phase == Phase.AUTHENTICATING
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(1.dp, GridColors.rule)) {
    AccentRule(skin.accent)
    Column(Modifier.padding(10.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(skin.accent))
        Spacer(Modifier.width(7.dp))
        Micro(if (authenticating) "VALIDATING TOKEN" else "SECURE CHANNEL REQUIRED", alpha = 0.78f)
        Spacer(Modifier.weight(1f))
        Micro("CF ACCESS", alpha = 0.32f, letterSpacing = 0.8.sp)
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text =
          if (authenticating) {
            "The Access token returned from the browser is being validated. The dial resumes on its own."
          } else {
            "Cloudflare Access emails a one-time code for ${state.config.host}."
          },
        style = GridProse,
        color = GridColors.ink.copy(alpha = 0.62f),
      )
      Spacer(Modifier.height(8.dp))
      PanelButton(
        label = if (authenticating) "REOPEN SIGN-IN ↗" else "SIGN IN ↗",
        onClick = actions::reauthenticate,
        accent = skin.accent,
        modifier = Modifier.fillMaxWidth(),
        contentDescription = "Sign in to Cloudflare Access",
      )
    }
  }
}

@Composable
private fun FaultPanel(state: HatchUiState, actions: TunnelActions, skin: GridSkin) {
  val error = state.tunnel.error
  val kind = error?.kind ?: ErrorKind.UNKNOWN
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(1.dp, GridColors.rule)) {
    AccentRule(skin.accent)
    Column(Modifier.padding(10.dp)) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(5.dp).background(skin.accent))
        Spacer(Modifier.width(7.dp))
        Micro("FAULT ${faultCode(kind)} · ${kind.name.replace('_', ' ')}", alpha = 0.78f)
      }
      Spacer(Modifier.height(6.dp))
      Text(
        text = error?.title ?: "Tunnel fault",
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W600,
        fontSize = 18.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.4).sp,
        color = GridColors.ink,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = error?.detail ?: "The engine stopped before the forward came up.",
        style = GridProse,
        color = GridColors.ink.copy(alpha = 0.62f),
      )
      Spacer(Modifier.height(8.dp))
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PanelButton(
          label = if (error?.retryable == false) "RETRY ANYWAY" else "RETRY",
          onClick = actions::retryNow,
          accent = GridColors.lime,
          modifier = Modifier.weight(1f),
          contentDescription = "Retry the tunnel",
        )
        PanelButton(
          label = "COPY LOG",
          onClick = actions::copyDiagnostics,
          filled = false,
          contentDescription = "Copy diagnostics to the clipboard",
        )
      }
    }
  }
}

@Composable
private fun RetryPanel(state: HatchUiState, actions: TunnelActions, skin: GridSkin) {
  val tunnel = state.tunnel
  val retrying = tunnel.phase == Phase.RETRYING
  Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).border(1.dp, GridColors.rule)) {
    AccentRule(skin.accent)
    Column(
      modifier = Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
      verticalArrangement = Arrangement.Center,
    ) {
      Micro(
        text =
          if (retrying) {
            "AUTO-RETRY ${Fmt.countdown(tunnel.retryRemainingMs).uppercase()} · ATTEMPT ${tunnel.attempt.coerceAtLeast(1)}/${tunnel.maxAttempts}"
          } else {
            "NO CARRIER · RESUMES AUTOMATICALLY"
          },
        alpha = 0.8f,
      )
      Spacer(Modifier.height(3.dp))
      Micro(
        text =
          if (retrying) {
            "EXPONENTIAL BACKOFF · BASE ${Fmt.duration(tunnel.retryDelayMs ?: 0L)}"
          } else {
            "WAITING FOR NETWORK · ${tunnel.networkType.uppercase()}"
          },
        alpha = 0.38f,
        letterSpacing = 0.8.sp,
      )
    }
    PanelButton(
      label = "RETRY",
      onClick = actions::retryNow,
      filled = false,
      contentDescription = "Retry the tunnel now",
      modifier = Modifier.align(Alignment.CenterVertically).padding(end = 10.dp),
    )
  }
}

@Composable
private fun NoteStrip(note: String, accent: Color) {
  Row(
    modifier = Modifier.fillMaxWidth().height(48.dp).border(1.dp, GridColors.rule).padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Box(Modifier.size(5.dp).background(accent))
    Spacer(Modifier.width(8.dp))
    Micro(note, alpha = 0.52f)
  }
}

/** The 3dp coloured rule that runs down the left edge of the phase panels. */
@Composable
private fun AccentRule(color: Color) {
  Box(Modifier.width(3.dp).fillMaxHeight().background(color))
}

// ----------------------------------------------------------------- telemetry ---

@Composable
private fun RttHistoryCell(
  history: List<Float>,
  rtt: Long,
  peak: Float,
  accent: Color,
  active: Boolean,
) {
  Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Micro("RTT HISTORY · 40 PT", alpha = 0.42f)
      Micro(
        text = if (rtt > 0L) "$rtt MS · PEAK ${peak.toLong()} MS" else "STANDBY",
        alpha = 0.55f,
      )
    }
    Spacer(Modifier.height(5.dp))
    RttSparkline(history = history, accent = accent, active = active)
  }
}

@Composable
private fun TelemetryGrid(state: HatchUiState, uptimeMs: Long) {
  val tunnel = state.tunnel
  val metrics = tunnel.metrics
  val live = tunnel.isLive
  val attempt = tunnel.attempt.coerceAtLeast(1)
  val noSignal = metrics.rttMs <= 0L
  val linkValue =
    when {
      live || !noSignal -> Fmt.qualityLabel(tunnel.quality).uppercase()
      tunnel.phase == Phase.RETRYING || tunnel.phase == Phase.WAITING_FOR_NETWORK || tunnel.phase == Phase.ERROR ->
        "NO SIGNAL"
      else -> "STANDBY"
    }
  val linkBars = if (live || !noSignal) Fmt.qualityBars(tunnel.quality) else 0
  val uptimeLabel = if (live) "UPTIME" else "LAST RUN"
  val uptimeValue =
    when {
      live -> Fmt.clockDuration(uptimeMs)
      tunnel.lastSessionMs > 0L -> Fmt.clockDuration(tunnel.lastSessionMs)
      else -> "—"
    }
  val uptimeSub =
    when {
      live -> "SESSION"
      tunnel.lastSessionMs > 0L -> "PREVIOUS"
      else -> "NO SESSION"
    }

  Column(Modifier.fillMaxWidth()) {
    HRule()
    // Intrinsic row height: label + value + sub always fit, never clipped.
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      MetricCell(
        label = "LINK",
        value = linkValue,
        bars = linkBars,
        valueSize = 13.sp,
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
      VRule()
      MetricCell(
        label = "RTT",
        value = Fmt.rtt(metrics.rttMs),
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
      VRule()
      MetricCell(
        label = "JITTER",
        value = Fmt.rtt(metrics.jitterMs),
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
    }
    HRule()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      MetricCell(
        label = "DOWN",
        value = Fmt.bytes(metrics.bytesDown),
        sub = if (metrics.throughputDownBps > 0L) Fmt.rate(metrics.throughputDownBps) else "IDLE",
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
      VRule()
      MetricCell(
        label = "UP",
        value = Fmt.bytes(metrics.bytesUp),
        sub = if (metrics.throughputUpBps > 0L) Fmt.rate(metrics.throughputUpBps) else "IDLE",
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
      VRule()
      MetricCell(
        label = uptimeLabel,
        value = uptimeValue,
        sub = uptimeSub,
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
    }
    HRule()
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
      MetricCell(
        label = "RETRIES",
        value = "$attempt / ${tunnel.maxAttempts}",
        sub = "RECONNECTS ${metrics.reconnects}",
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
      VRule()
      MetricCell(
        label = "ENDPOINT",
        value = state.config.host,
        valueSize = 13.sp,
        sub = "USER ${state.config.sshUser.uppercase()}",
        modifier = Modifier.weight(1f).fillMaxHeight(),
      )
    }
    HRule()
    ForwardRow(forwardLabel = state.config.forwardLabel)
    HRule()
  }
}

@Composable
private fun ForwardRow(forwardLabel: String) {
  Row(
    modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 11.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Micro("FORWARD", alpha = 0.42f)
    Spacer(Modifier.weight(1f))
    Mono(forwardLabel, size = 11.sp, alpha = 0.9f, letterSpacing = 0.1.sp, lineHeight = 14.sp)
  }
}

// ------------------------------------------------------------------ session ---

/**
 * Engine events as a bottom-aligned ledger.
 *
 * Short phases are padded up to five slots with real session parameters (AUTH /
 * BACKOFF / KEEPALIVE / LOCAL) so the panel always ends on a deliberate block.
 * The tall action phases (sign-in / fault) keep only their real events so their
 * cards and the footer always fit without scrolling the ledger.
 */
@Composable
private fun SessionLog(state: HatchUiState) {
  val logs = state.tunnel.logs
  val tallPanel =
    state.tunnel.phase == Phase.NEEDS_AUTH ||
      state.tunnel.phase == Phase.AUTHENTICATING ||
      state.tunnel.phase == Phase.ERROR
  val slots = if (tallPanel) logs.size.coerceIn(1, 2) else 5
  val shown = logs.takeLast(slots)
  val preamble = logPreamble(state)
  val padCount = (slots - shown.size).coerceAtLeast(0)
  val formatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
  Column(Modifier.fillMaxWidth()) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Micro("SESSION LOG", alpha = 0.42f)
      Spacer(Modifier.weight(1f))
      Micro(if (logs.isEmpty()) "IDLE" else "LAST ${shown.size} / ${logs.size}", alpha = 0.3f)
    }
    Spacer(Modifier.height(4.dp))
    repeat(slots) { slot ->
      if (slot > 0) HRule()
      val line = shown.getOrNull(slot - (slots - shown.size))
      val sys = if (padCount > 0) preamble.getOrNull(slot) else null
      Row(
        modifier = Modifier.fillMaxWidth().height(20.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        when {
          line != null ->
            LogRow(
              stamp = logStamp(line, formatter),
              level = line.level,
              message = line.message,
              emphasize = true,
            )
          sys != null -> LogRow(stamp = "SYS", level = null, message = sys, emphasize = false)
        }
      }
    }
  }
}

@Composable
private fun RowScope.LogRow(stamp: String, level: LogLevel?, message: String, emphasize: Boolean) {
  Micro(stamp, alpha = if (emphasize) 0.35f else 0.28f, letterSpacing = 0.3.sp)
  Spacer(Modifier.width(9.dp))
  Box(
    Modifier
      .size(4.dp)
      .background(if (level != null) logLevelColor(level) else GridColors.ink.copy(alpha = 0.22f)),
  )
  Spacer(Modifier.width(7.dp))
  Text(
    text = message,
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    color = GridColors.ink.copy(alpha = if (emphasize) 0.72f else 0.5f),
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = Modifier.weight(1f),
  )
}

/** Real session parameters used to pad a short ledger. */
private fun logPreamble(state: HatchUiState): List<String> {
  val config = state.config
  return listOf(
    "AUTH ${config.authMode.name} · CF ACCESS ${if (config.useCloudflareAccess) "ON" else "OFF"}",
    String.format(
      Locale.US,
      "BACKOFF %.1fs → %ds · MAX %d",
      config.baseDelayMs / 1000f,
      config.maxDelayMs / 1000L,
      config.maxRetries,
    ),
    "KEEPALIVE ${config.keepAliveSeconds}s · ADAPTIVE ${if (config.adaptive) "ON" else "OFF"}",
    "LOCAL ${config.localUrl}",
    "TARGET ${config.sshUser}@${config.host}",
  )
}

private fun logStamp(line: LogLine, formatter: SimpleDateFormat): String {
  val at = line.atMillis
  return if (at >= 1_000_000_000_000L) {
    formatter.format(Date(at))
  } else {
    // Prototype seeds use session-relative millis; render them as a precise clock.
    val millis = at.coerceAtLeast(0L)
    String.format(
      Locale.US,
      "%02d:%02d:%02d.%03d",
      millis / 3_600_000,
      (millis / 60_000) % 60,
      (millis / 1_000) % 60,
      millis % 1_000,
    )
  }
}

private fun logLevelColor(level: LogLevel): Color =
  when (level) {
    LogLevel.INFO -> GridColors.ink.copy(alpha = 0.45f)
    LogLevel.WARN -> GridColors.amber
    LogLevel.ERROR -> GridColors.coral
  }

/** Deterministic 40-sample RTT trace ending on [baselineMs] (or a 38 ms idle baseline). */
private fun seededRttTrace(baselineMs: Float): List<Float> {
  val baseline = if (baselineMs > 0f) baselineMs else 38f
  return List(40) { index ->
    if (index == 39) {
      baseline
    } else {
      val wave = sin(index * 0.52f) * 0.16f + sin(index * 1.7f) * 0.05f
      val noise = (((index * 73) % 11) - 5) / 100f
      baseline * (1f + wave + noise)
    }
  }
}

@Composable
private fun MetricCell(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
  valueSize: TextUnit = 15.sp,
  sub: String? = null,
  bars: Int? = null,
) {
  Column(
    modifier = modifier.padding(horizontal = 11.dp, vertical = 6.dp),
    verticalArrangement = Arrangement.Center,
  ) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
      Micro(label, alpha = 0.42f)
      if (bars != null) {
        Spacer(Modifier.weight(1f))
        QualityBars(bars = bars)
      }
    }
    Spacer(Modifier.height(3.dp))
    Mono(value, size = valueSize)
    if (sub != null) {
      Micro(sub, alpha = 0.38f, letterSpacing = 0.7.sp)
    }
  }
}

@Composable
private fun QualityBars(bars: Int) {
  Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
    repeat(4) { index ->
      Box(
        Modifier
          .width(3.dp)
          .height((5 + index * 3).dp)
          .background(if (index < bars) GridColors.ink else GridColors.rule),
      )
    }
  }
}

// ------------------------------------------------------------------ controls ---

@Composable
private fun PanelButton(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  filled: Boolean = true,
  accent: Color = GridColors.lime,
  contentDescription: String? = null,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val a11yLabel = contentDescription
  val fill =
    when {
      filled && pressed -> accent.copy(alpha = 0.72f)
      filled -> accent
      pressed -> GridColors.ink.copy(alpha = 0.06f)
      else -> Color.Transparent
    }
  Box(
    modifier =
      modifier
        .heightIn(min = 48.dp)
        .background(fill)
        .border(1.dp, if (filled) GridColors.ink else GridColors.rule)
        .clickable(
          interactionSource = interaction,
          indication = null,
          onClickLabel = label.lowercase(),
          onClick = onClick,
        )
        .then(
          if (a11yLabel != null) {
            Modifier.semantics { this.contentDescription = a11yLabel }
          } else {
            Modifier
          },
        )
        .padding(horizontal = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Mono(label, size = 11.sp, letterSpacing = 1.2.sp)
  }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
  val interaction = remember { MutableInteractionSource() }
  Box(
    modifier =
      Modifier
        .heightIn(min = 48.dp)
        .clickable(
          interactionSource = interaction,
          indication = null,
          onClickLabel = label.lowercase(),
          onClick = onClick,
        )
        .padding(horizontal = 6.dp),
    contentAlignment = Alignment.Center,
  ) {
    Micro(label, alpha = 0.58f)
  }
}

// ------------------------------------------------------------------ primitives ---

/** Uppercase mono micro-label with a shared alpha convention. */
@Composable
private fun Micro(
  text: String,
  alpha: Float = 1f,
  letterSpacing: TextUnit = 1.2.sp,
  color: Color = GridColors.ink,
  maxLines: Int = 1,
  textAlign: TextAlign? = null,
) {
  Text(
    text = text,
    style = GridMicroLabel.copy(letterSpacing = letterSpacing),
    color = color.copy(alpha = alpha),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    textAlign = textAlign,
  )
}

/** Mono value readout. */
@Composable
private fun Mono(
  text: String,
  size: TextUnit = 15.sp,
  alpha: Float = 1f,
  letterSpacing: TextUnit = (-0.2).sp,
  maxLines: Int = 1,
  lineHeight: TextUnit = 18.sp,
  textAlign: TextAlign? = null,
) {
  Text(
    text = text,
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    color = GridColors.ink.copy(alpha = alpha),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
    textAlign = textAlign,
  )
}

@Composable
private fun HRule() {
  Box(Modifier.fillMaxWidth().height(1.dp).background(GridColors.rule))
}

@Composable
private fun VRule() {
  Box(Modifier.width(1.dp).fillMaxHeight().background(GridColors.rule))
}

// --------------------------------------------------------------------- copy ---

private fun dialDescription(state: HatchUiState): String {
  val tunnel = state.tunnel
  return when (tunnel.phase) {
    Phase.IDLE -> "Tunnel control, idle. Double tap to connect."
    Phase.CHECKING -> "Tunnel control, checking endpoint. Double tap to cancel."
    Phase.NEEDS_AUTH -> "Tunnel control, Cloudflare Access sign-in required. Double tap to sign in."
    Phase.NEEDS_HOST_KEY -> "Tunnel control, new SSH host key needs approval."
    Phase.AUTHENTICATING -> "Tunnel control, validating the access token."
    Phase.CONNECTING -> "Tunnel control, connecting the SSH forward."
    Phase.CONNECTED ->
      "Tunnel control, link live, ${Fmt.qualityLabel(tunnel.quality)} quality. Double tap to disconnect."
    Phase.DEGRADED ->
      "Tunnel control, link degraded, ${Fmt.qualityLabel(tunnel.quality)} quality. Double tap to disconnect."
    Phase.RETRYING ->
      "Tunnel control, retrying in ${Fmt.countdown(tunnel.retryRemainingMs)}, attempt ${tunnel.attempt.coerceAtLeast(1)} of ${tunnel.maxAttempts}. Double tap to retry now."
    Phase.WAITING_FOR_NETWORK -> "Tunnel control, no network. Double tap to retry now."
    Phase.STOPPING -> "Tunnel control, stopping."
    Phase.ERROR -> "Tunnel control, fault: ${tunnel.error?.title ?: "unknown"}. Double tap to retry."
  }
}

private fun phaseNote(state: HatchUiState): String =
  when (state.tunnel.phase) {
    Phase.IDLE -> "READY · FORWARD ARMED"
    Phase.CHECKING -> "PROBING ${state.config.host.uppercase()}"
    Phase.CONNECTING -> "SSH HANDSHAKE · WS CARRIER"
    Phase.STOPPING -> "CLOSING LOCAL FORWARD"
    else -> state.tunnel.phase.name.replace('_', ' ').uppercase()
  }

private fun faultCode(kind: ErrorKind): String = String.format(Locale.US, "0x%02X", kind.ordinal + 1)
