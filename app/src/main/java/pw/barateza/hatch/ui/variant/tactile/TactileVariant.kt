package pw.barateza.hatch.ui.variant.tactile

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.Quality
import pw.barateza.hatch.model.SshAuthMode
import pw.barateza.hatch.model.TunnelMetrics
import pw.barateza.hatch.model.TunnelState
import pw.barateza.hatch.theme.InstrumentSerif
import pw.barateza.hatch.theme.Inter
import pw.barateza.hatch.theme.JetBrainsMono
import pw.barateza.hatch.ui.HatchUiState
import pw.barateza.hatch.ui.TunnelActions
import pw.barateza.hatch.ui.common.Fmt

/**
 * TACTILE — a warm, analog object. Cream paper, editorial serif, and one
 * moulded hardware control in the middle. No cards, no grids, no neon.
 */
@Composable
fun TactileVariant(
  state: HatchUiState,
  actions: TunnelActions,
  modifier: Modifier = Modifier,
) {
  val tunnel = state.tunnel
  val phase = tunnel.phase
  val copy = tactileCopy(phase)
  val live = tunnel.isLive
  val uptime =
    if (live) {
      (state.nowMillis - (tunnel.connectedAtMillis ?: state.nowMillis)).coerceAtLeast(0L)
    } else {
      0L
    }
  val hint =
    if (phase == Phase.RETRYING) {
      "NEXT IN ${Fmt.countdown(tunnel.retryRemainingMs).uppercase()}"
    } else {
      copy.hint
    }
  BoxWithConstraints(modifier.fillMaxSize().background(Tactile.cream)) {
    val compact = maxHeight < 900.dp
    val controlDiameter = if (compact) 204.dp else 226.dp

    LightSystemBars()
    PaperGrain(Modifier.matchParentSize())

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .statusBarsPadding()
          .navigationBarsPadding()
          .padding(horizontal = 24.dp)
          .padding(top = 2.dp, bottom = 12.dp),
    ) {
      TactileTopBar(onSettings = actions::openSettings)
      Spacer(Modifier.height(4.dp))
      Rule()
      Spacer(Modifier.height(10.dp))
      Eyebrow()
      Spacer(Modifier.height(6.dp))
      TactileHeadline(copy, compact = compact)
      Spacer(Modifier.height(5.dp))
      Text(
        text = copy.sub,
        modifier = Modifier.fillMaxWidth(),
        color = Tactile.ink.copy(alpha = 0.74f),
        fontFamily = Inter,
        fontSize = 12.5.sp,
        lineHeight = 18.sp,
        maxLines = 2,
      )
      Spacer(Modifier.height(8.dp))
      EndpointMeta(state)

      Spacer(Modifier.weight(1f))

      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HatchControl(
          phase = phase,
          retryDelayMs = tunnel.retryDelayMs,
          retryRemainingMs = tunnel.retryRemainingMs,
          centerWord = copy.center,
          centerHint = hint,
          enabled = tunnel.canToggle,
          onClick = actions::toggleTunnel,
          diameter = controlDiameter,
        )
      }
      Spacer(Modifier.height(2.dp))
      CaptionZone(
        tunnel = tunnel,
        phase = phase,
        onVisit = actions::openLocalUrl,
        degraded = phase == Phase.DEGRADED,
      )
      Spacer(Modifier.height(6.dp))

      if (phase == Phase.NEEDS_AUTH) {
        LetterpressNotice(
          title = "Sign the letter.",
          body =
            "Prove it is you with a one-time code. Hatch keeps the door ajar until you come back.",
          cta = "CONTINUE IN BROWSER",
          onCta = actions::reauthenticate,
        )
      } else if (phase == Phase.NEEDS_HOST_KEY) {
        // The host-key approval dialog is rendered by HatchRoot above the variant.
        Unit
      } else if (phase == Phase.ERROR) {
        val error = tunnel.error
        StampNotice(
          title = error?.title ?: "The hatch did not open",
          body =
            error?.detail
              ?: "The tunnel stopped before it could open. Try once more, or check the key and port in settings.",
          tag = error?.kind?.name?.replace('_', ' ') ?: "UNKNOWN",
          retryable = error?.retryable ?: true,
          cta = "TRY AGAIN",
          onCta = actions::retryNow,
          onSettings = actions::openSettings,
        )
      } else {
        TactileReceipt(state = state, uptimeMillis = uptime)
      }

      Spacer(Modifier.height(4.dp))
      Footer(onCopyNotes = actions::copyDiagnostics)
    }
  }
}

// ---------------------------------------------------------------------------
// Masthead
// ---------------------------------------------------------------------------

/**
 * Cream is a light surface: keep status/nav icons dark for this screen and hand
 * the shell's dark appearance back when the variant leaves composition.
 */
@Composable
private fun LightSystemBars() {
  val view = LocalView.current
  if (!view.isInEditMode) {
    DisposableEffect(view) {
      val activity =
        generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
          .filterIsInstance<Activity>()
          .firstOrNull()
      val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
      val wasLightStatus = controller?.isAppearanceLightStatusBars
      val wasLightNav = controller?.isAppearanceLightNavigationBars
      controller?.let {
        it.isAppearanceLightStatusBars = true
        it.isAppearanceLightNavigationBars = true
      }
      onDispose {
        if (controller != null) {
          wasLightStatus?.let { controller.isAppearanceLightStatusBars = it }
          wasLightNav?.let { controller.isAppearanceLightNavigationBars = it }
        }
      }
    }
  }
}

@Composable
private fun TactileTopBar(onSettings: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(6.dp).background(Tactile.clay, CircleShape))
      Spacer(Modifier.width(9.dp))
      Text(
        text = "HATCH",
        color = Tactile.ink,
        fontFamily = JetBrainsMono,
        fontSize = 11.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 2.6.sp,
      )
      Spacer(Modifier.width(10.dp))
      Text(
        text = "Nº 01",
        color = Tactile.ink.copy(alpha = 0.42f),
        fontFamily = InstrumentSerif,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp,
      )
    }
    UnderlineLink(
      text = "SETTINGS",
      onClick = onSettings,
      color = Tactile.ink.copy(alpha = 0.72f),
    )
  }
}

@Composable
private fun Eyebrow() {
  Row(verticalAlignment = Alignment.CenterVertically) {
    MicroLabel("PRIVATE WIRE", color = Tactile.clayInk, size = 9f, spacing = 2.1f)
    Spacer(Modifier.width(10.dp))
    Box(Modifier.width(18.dp).height(1.dp).background(Tactile.ink.copy(alpha = 0.3f)))
    Spacer(Modifier.width(10.dp))
    MicroLabel("SSH LOCAL FORWARD", color = Tactile.ink.copy(alpha = 0.45f), size = 9f, spacing = 2.1f)
  }
}

@Composable
private fun TactileHeadline(copy: TactileCopy, compact: Boolean) {
  val size =
    if (compact) {
      38f
    } else {
      when {
        copy.length <= 17 -> 46f
        copy.length <= 21 -> 42f
        else -> 38f
      }
    }
  val text =
    buildAnnotatedString {
      append(copy.lead)
      withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(copy.italic) }
      append(copy.tail)
    }
  Text(
    text = text,
    modifier = Modifier.fillMaxWidth(),
    color = Tactile.ink,
    fontFamily = InstrumentSerif,
    fontSize = size.sp,
    lineHeight = (size * 1.03f).sp,
    letterSpacing = (-0.4).sp,
    maxLines = 2,
  )
}

@Composable
private fun EndpointMeta(state: HatchUiState) {
  val config = state.config
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Bottom,
    ) {
      Text(
        text = config.endpointLabel,
        color = Tactile.ink,
        fontFamily = JetBrainsMono,
        fontSize = 13.sp,
        letterSpacing = 0.2.sp,
        maxLines = 1,
      )
      MicroLabel(
        text = state.tunnel.networkType.uppercase(),
        color = Tactile.ink.copy(alpha = 0.42f),
        size = 8.5f,
        spacing = 1.4f,
      )
    }
    Spacer(Modifier.height(3.dp))
    MicroLabel(
      text =
        when {
          config.useCloudflareAccess -> "CLOUDFLARE ACCESS · EMAIL OTP"
          config.authMode == SshAuthMode.PASSWORD -> "DIRECT SSH · PASSWORD AUTH"
          else -> "DIRECT SSH · KEY AUTH"
        },
      color = Tactile.clayInk.copy(alpha = 0.92f),
      size = 9f,
      spacing = 1.5f,
    )
    Spacer(Modifier.height(3.dp))
    Text(
      text = config.forwardLabel,
      color = Tactile.ink.copy(alpha = 0.5f),
      fontFamily = JetBrainsMono,
      fontSize = 10.sp,
      letterSpacing = 0.2.sp,
      maxLines = 1,
    )
  }
}

// ---------------------------------------------------------------------------
// Under the control
// ---------------------------------------------------------------------------

@Composable
private fun CaptionZone(
  tunnel: TunnelState,
  phase: Phase,
  onVisit: () -> Unit,
  degraded: Boolean,
) {
  Box(
    modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
    contentAlignment = Alignment.Center,
  ) {
    when (phase) {
      Phase.CONNECTED, Phase.DEGRADED ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          UnderlineLink(
            text = "VISIT THE HATCH ↗",
            onClick = onVisit,
            color = if (degraded) Tactile.oliveInk else Tactile.ink,
          )
          Spacer(Modifier.width(8.dp))
          Text(
            text = "↓ ${Fmt.rate(tunnel.metrics.throughputDownBps)}",
            color = Tactile.ink.copy(alpha = 0.45f),
            fontFamily = JetBrainsMono,
            fontSize = 9.5.sp,
            letterSpacing = 0.3.sp,
            maxLines = 1,
          )
        }
      Phase.RETRYING -> RetryCaption(tunnel)
      Phase.NEEDS_AUTH, Phase.NEEDS_HOST_KEY, Phase.ERROR -> Unit
      Phase.IDLE ->
        MicroLabel(
          text = "SSH · 127.0.0.1:3080 · NOTHING EXPOSED",
          color = Tactile.ink.copy(alpha = 0.38f),
          size = 9f,
          spacing = 1.4f,
        )
      else ->
        MicroLabel(
          text = busyLabel(phase, tunnel),
          color = Tactile.ink.copy(alpha = 0.45f),
          size = 9f,
          spacing = 1.7f,
        )
    }
  }
}

@Composable
private fun RetryCaption(tunnel: TunnelState) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        text = "next attempt in",
        color = Tactile.ink.copy(alpha = 0.85f),
        fontFamily = InstrumentSerif,
        fontSize = 17.sp,
      )
      Spacer(Modifier.width(7.dp))
      Text(
        text = Fmt.countdown(tunnel.retryRemainingMs),
        color = Tactile.clayInk,
        fontFamily = JetBrainsMono,
        fontSize = 14.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.4.sp,
      )
    }
    Spacer(Modifier.height(7.dp))
    MicroLabel(
      text = "ATTEMPT ${tunnel.attempt} OF ${tunnel.maxAttempts} · DELAY ${Fmt.countdown(tunnel.retryDelayMs).uppercase()}",
      color = Tactile.ink.copy(alpha = 0.45f),
      size = 9f,
      spacing = 1.4f,
    )
  }
}

private fun busyLabel(phase: Phase, tunnel: TunnelState): String =
  when (phase) {
    Phase.CHECKING -> "CHECKING THE ENDPOINT"
    Phase.AUTHENTICATING -> "VALIDATING THE CODE"
    Phase.CONNECTING -> "OPENING THE TRANSPORT"
    Phase.WAITING_FOR_NETWORK -> "OFFLINE · ${tunnel.networkType.uppercase()}"
    Phase.STOPPING -> "RELEASING THE PORT"
    else -> "WORKING"
  }

// ---------------------------------------------------------------------------
// Receipt
// ---------------------------------------------------------------------------

@Composable
private fun TactileReceipt(state: HatchUiState, uptimeMillis: Long) {
  val tunnel = state.tunnel
  val live = tunnel.isLive
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .background(Tactile.slip, RoundedCornerShape(2.dp))
        .border(0.8.dp, Tactile.ink.copy(alpha = 0.16f), RoundedCornerShape(2.dp))
        .padding(horizontal = 16.dp, vertical = 10.dp),
  ) {
    Column {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        MicroLabel("SESSION RECEIPT", color = Tactile.ink.copy(alpha = 0.5f), size = 9f, spacing = 1.8f)
        MicroLabel(
          text =
            when {
              state.isDemo -> "SIMULATED"
              live -> "LIVE"
              else -> phaseTag(tunnel.phase)
            },
          color = if (live) Tactile.clayInk else Tactile.ink.copy(alpha = 0.42f),
          size = 9f,
          spacing = 1.8f,
        )
      }
      Spacer(Modifier.height(6.dp))
      Rule(color = Tactile.ink.copy(alpha = 0.28f))
      Spacer(Modifier.height(1.dp))
      Rule(color = Tactile.ink.copy(alpha = 0.12f))
      Spacer(Modifier.height(2.dp))
      ReceiptRow(label = "Session", value = sessionValue(tunnel, uptimeMillis))
      ReceiptRow(label = "Round trip", value = Fmt.rtt(tunnel.metrics.rttMs))
      ReceiptRow(label = "Data", value = dataValue(tunnel.metrics))
      ReceiptRow(
        label = "Quality",
        value = Fmt.qualityLabel(tunnel.quality).uppercase(),
        emphatic = tunnel.quality == Quality.POOR || tunnel.quality == Quality.FAIR,
      )
    }
  }
}

@Composable
private fun Footer(onCopyNotes: () -> Unit) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    MicroLabel("HATCH · PRIVATE WIRE Nº 01", color = Tactile.ink.copy(alpha = 0.36f), size = 8.5f, spacing = 1.2f)
    UnderlineLink("COPY NOTES", onCopyNotes, color = Tactile.ink.copy(alpha = 0.6f))
  }
}

private fun sessionValue(tunnel: TunnelState, uptimeMillis: Long): String =
  when {
    tunnel.isLive && uptimeMillis > 0L -> Fmt.clockDuration(uptimeMillis)
    tunnel.isLive -> "00:00"
    tunnel.lastSessionMs > 0L -> "last ${Fmt.duration(tunnel.lastSessionMs)}"
    else -> "—"
  }

private fun dataValue(metrics: TunnelMetrics): String =
  if (metrics.bytesDown <= 0L && metrics.bytesUp <= 0L) {
    "—"
  } else {
    "↓ ${Fmt.bytes(metrics.bytesDown)}  ↑ ${Fmt.bytes(metrics.bytesUp)}"
  }

private fun phaseTag(phase: Phase): String =
  when (phase) {
    Phase.IDLE -> "SEALED"
    Phase.CHECKING -> "LOOKING"
    Phase.NEEDS_AUTH -> "SEALED"
    Phase.NEEDS_HOST_KEY -> "UNVERIFIED"
    Phase.AUTHENTICATING -> "CHECKING"
    Phase.CONNECTING -> "OPENING"
    Phase.CONNECTED -> "LIVE"
    Phase.DEGRADED -> "THIN"
    Phase.RETRYING -> "WAITING"
    Phase.WAITING_FOR_NETWORK -> "OFFLINE"
    Phase.STOPPING -> "CLOSING"
    Phase.ERROR -> "STOPPED"
  }
