package pw.barateza.hatch.ui.variant.aurora

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.Quality
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelState
import pw.barateza.hatch.theme.HatchPalette
import pw.barateza.hatch.theme.Inter
import pw.barateza.hatch.theme.JetBrainsMono
import pw.barateza.hatch.theme.SpaceGrotesk
import pw.barateza.hatch.ui.HatchUiState
import pw.barateza.hatch.ui.TunnelActions
import pw.barateza.hatch.ui.common.Fmt

/**
 * AURORA — ambient glass. Deep-space ink, slow aurora light, one big physical
 * dial. The default Hatch direction.
 *
 * Prototype screen: no navigation, one action, dense but quiet telemetry.
 */
@Composable
fun AuroraVariant(
  state: HatchUiState,
  actions: TunnelActions,
  modifier: Modifier = Modifier,
) {
  val phase = state.tunnel.phase
  val accent = accentFor(phase)

  val ambient = rememberInfiniteTransition(label = "aurora-ambient")
  val drift by
    ambient.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Reverse),
      label = "drift",
    )

  Box(modifier.background(HatchPalette.Ink)) {
    AuroraBackdrop(accent = accent, drift = drift, modifier = Modifier.fillMaxSize())

    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
          .statusBarsPadding()
          .padding(horizontal = 22.dp),
    ) {
      Spacer(Modifier.height(10.dp))
      TopBar(state = state, accent = accent, onSettings = actions::openSettings)

      Spacer(Modifier.height(18.dp))
      StatusCapsule(state = state, accent = accent)

      Spacer(Modifier.height(16.dp))
      Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        HatchDial(state = state.tunnel, accent = accent, onToggle = actions::toggleTunnel)
      }

      Spacer(Modifier.height(14.dp))
      Headline(state = state, accent = accent)

      if (state.tunnel.isLive) {
        Spacer(Modifier.height(16.dp))
        OpenHarness(accent = accent, port = state.config.localPort, onClick = actions::openLocalUrl)
      }

      if (phase in setOf(Phase.NEEDS_AUTH, Phase.AUTHENTICATING)) {
        Spacer(Modifier.height(20.dp))
        AuthCard(
          authenticating = phase == Phase.AUTHENTICATING,
          accent = accent,
          onContinue = actions::reauthenticate,
        )
      }

      when (phase) {
        Phase.ERROR -> {
          Spacer(Modifier.height(20.dp))
          ErrorCard(
            state = state.tunnel,
            onRetry = actions::retryNow,
            onSettings = actions::openSettings,
          )
        }
        Phase.RETRYING -> {
          Spacer(Modifier.height(20.dp))
          RetryCard(
            state = state.tunnel,
            accent = accent,
            onRetryNow = actions::retryNow,
          )
        }
        Phase.WAITING_FOR_NETWORK -> {
          Spacer(Modifier.height(20.dp))
          WaitingCard(accent = accent)
        }
        else -> Unit
      }

      Spacer(Modifier.height(16.dp))
      MetricsCard(
        state = state.tunnel,
        accent = accent,
        uptimeMillis = state.tunnel.connectedAtMillis?.let { (state.nowMillis - it).coerceAtLeast(0) } ?: 0L,
      )

      Spacer(Modifier.height(12.dp))
      RelayCard(state = state, accent = accent)

      Spacer(Modifier.height(104.dp))
    }
  }
}

// ------------------------------------------------------------------ chrome ---

@Composable
private fun TopBar(
  state: HatchUiState,
  accent: Color,
  onSettings: () -> Unit,
) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier =
        Modifier
          .size(36.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Brush.linearGradient(listOf(HatchPalette.Mint, HatchPalette.Sky))),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        "H",
        color = HatchPalette.Ink,
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W700,
        fontSize = 19.sp,
      )
    }
    Spacer(Modifier.width(11.dp))
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(
        "HATCH",
        color = Color(0xFFF2F6FB),
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        letterSpacing = 4.5.sp,
      )
      Text(
        "PERSONAL RELAY",
        color = HatchPalette.Fog.copy(alpha = 0.8f),
        fontFamily = JetBrainsMono,
        fontSize = 8.sp,
        letterSpacing = 1.8.sp,
      )
    }
    Spacer(Modifier.weight(1f))
    Surface(
      onClick = onSettings,
      shape = CircleShape,
      color = Color.White.copy(alpha = 0.045f),
      border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
      modifier = Modifier.size(40.dp),
    ) {
      Box(contentAlignment = Alignment.Center) {
        Icon(
          Icons.Rounded.Settings,
          contentDescription = "Settings",
          tint = Color(0xFFC9D2DF),
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun StatusCapsule(state: HatchUiState, accent: Color) {
  Surface(
    shape = RoundedCornerShape(50),
    color = Color.White.copy(alpha = 0.045f),
    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.09f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Box(Modifier.size(6.dp).background(accent, CircleShape))
      Text(
        if (state.config.useCloudflareAccess) "CLOUDFLARE ACCESS" else "DIRECT SSH",
        color = Color.White.copy(alpha = 0.72f),
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 1.1.sp,
      )
      Box(Modifier.width(1.dp).height(10.dp).background(Color.White.copy(alpha = 0.14f)))
      Icon(
        Icons.Rounded.Wifi,
        contentDescription = null,
        tint = HatchPalette.Fog,
        modifier = Modifier.size(11.dp),
      )
      Text(
        state.tunnel.networkType.uppercase(),
        color = HatchPalette.Fog,
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 0.8.sp,
      )
      if (state.tunnel.isLive) {
        Box(Modifier.width(1.dp).height(10.dp).background(Color.White.copy(alpha = 0.14f)))
        Text(
          Fmt.qualityLabel(state.tunnel.quality).uppercase(),
          color = accent.copy(alpha = 0.9f),
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          letterSpacing = 0.8.sp,
        )
      }
    }
  }
}

// -------------------------------------------------------------------- dial ---

@Composable
private fun HatchDial(
  state: TunnelState,
  accent: Color,
  onToggle: () -> Unit,
) {
  val phase = state.phase
  val busy = phase in setOf(Phase.CHECKING, Phase.AUTHENTICATING, Phase.CONNECTING, Phase.STOPPING)
  val infinite = rememberInfiniteTransition(label = "dial")
  val spin by
    infinite.animateFloat(
      0f,
      360f,
      infiniteRepeatable(tween(1_500, easing = LinearEasing)),
      label = "spin",
    )
  val breathe by
    infinite.animateFloat(
      0.55f,
      1f,
      infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
      label = "breathe",
    )

  val retryProgress =
    if (phase == Phase.RETRYING) {
      val remaining = state.retryRemainingMs ?: 0L
      val total = state.retryDelayMs ?: 1L
      (1f - remaining.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
      0f
    }

  val sweep =
    when (phase) {
      Phase.CHECKING, Phase.AUTHENTICATING, Phase.CONNECTING, Phase.STOPPING -> 96f
      Phase.RETRYING -> 360f * retryProgress
      Phase.CONNECTED, Phase.DEGRADED -> 360f
      else -> 0f
    }

  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (pressed) 0.955f else 1f,
      animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
      label = "scale",
    )
  val haptics = LocalHapticFeedback.current
  val label =
    when (phase) {
      Phase.IDLE -> "TAP TO OPEN"
      Phase.CHECKING -> "CHECKING"
      Phase.NEEDS_AUTH -> "SIGN IN"
      Phase.NEEDS_HOST_KEY -> "VERIFY HOST"
      Phase.AUTHENTICATING -> "VERIFYING"
      Phase.CONNECTING -> "CONNECTING"
      Phase.CONNECTED, Phase.DEGRADED -> "TAP TO CLOSE"
      Phase.RETRYING -> "RETRY IN ${Fmt.countdown(state.retryRemainingMs).uppercase()}"
      Phase.WAITING_FOR_NETWORK -> "OFFLINE"
      Phase.STOPPING -> "CLOSING"
      Phase.ERROR -> "TRY AGAIN"
    }

  Box(Modifier.size(304.dp), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize()) {
      val c = center
      val ringRadius = size.minDimension / 2f - 34.dp.toPx()
      val outerRadius = size.minDimension / 2f - 6.dp.toPx()

      // Soft atmospheric light behind the dial.
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.18f * breathe), accent.copy(alpha = 0.05f), Color.Transparent),
            center = c,
            radius = outerRadius,
          ),
        radius = outerRadius,
        center = c,
      )

      // Outer hairline.
      drawCircle(Color.White.copy(alpha = 0.06f), outerRadius, c, style = Stroke(1.dp.toPx()))

      // Precision ticks.
      for (i in 0 until 72) {
        val major = i % 6 == 0
        rotate(i * 5f, c) {
          drawLine(
            color = Color.White.copy(alpha = if (major) 0.22f else 0.09f),
            start = Offset(c.x, c.y - ringRadius - 9.dp.toPx()),
            end = Offset(c.x, c.y - ringRadius - if (major) 2.dp.toPx() else 5.dp.toPx()),
            strokeWidth = if (major) 1.2.dp.toPx() else 1.dp.toPx(),
            cap = StrokeCap.Round,
          )
        }
      }

      // Track.
      drawCircle(Color.White.copy(alpha = 0.09f), ringRadius, c, style = Stroke(2.dp.toPx()))

      if (sweep > 0.5f) {
        val topLeft = Offset(c.x - ringRadius, c.y - ringRadius)
        val arcSize = Size(ringRadius * 2f, ringRadius * 2f)
        rotate(if (busy) spin else -90f, c) {
          drawArc(
            color = accent.copy(alpha = 0.16f),
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(12.dp.toPx(), cap = StrokeCap.Round),
          )
          drawArc(
            color = accent,
            startAngle = 0f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
          )
        }
        // Comet head at the tip of the arc.
        val angle = Math.toRadians((if (busy) spin else -90f + sweep).toDouble())
        val dot = Offset(c.x + ringRadius * cos(angle).toFloat(), c.y + ringRadius * sin(angle).toFloat())
        drawCircle(accent.copy(alpha = 0.25f), 9.dp.toPx(), dot)
        drawCircle(accent, 4.dp.toPx(), dot)
      } else if (phase == Phase.IDLE || phase == Phase.ERROR) {
        drawCircle(
          color = if (phase == Phase.ERROR) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
          radius = ringRadius,
          center = c,
          style =
            Stroke(
              width = 1.5.dp.toPx(),
              pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 11.dp.toPx())),
            ),
        )
      }
    }

    Box(
      modifier =
        Modifier
          .size(218.dp)
          .graphicsLayer {
            scaleX = scale
            scaleY = scale
          }
          .clip(CircleShape)
          .background(
            Brush.radialGradient(
              listOf(
                accent.copy(alpha = 0.32f),
                accent.copy(alpha = 0.10f),
                Color(0xFF0B1119),
                Color(0xFF070A10),
              ),
            ),
          )
          .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
          .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
            onClick = {
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              onToggle()
            },
          )
          .semantics {
            contentDescription =
              when (phase) {
                Phase.CONNECTED, Phase.DEGRADED -> "Disconnect the tunnel"
                Phase.RETRYING, Phase.ERROR -> "Retry the tunnel"
                Phase.NEEDS_AUTH -> "Sign in to Cloudflare Access"
                else -> "Open the SSH tunnel"
              }
          },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.fillMaxSize()) {
        // Top highlight for a glassy, domed surface.
        drawArc(
          brush =
            Brush.verticalGradient(
              listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
            ),
          startAngle = 200f,
          sweepAngle = 140f,
          useCenter = false,
          topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
          size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
          style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PowerGlyph(color = accent, glyphSize = 44.dp)
        Spacer(Modifier.height(12.dp))
        Text(
          label,
          color = Color.White.copy(alpha = 0.82f),
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          letterSpacing = 1.6.sp,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}

@Composable
private fun PowerGlyph(color: Color, glyphSize: Dp) {
  Canvas(Modifier.size(glyphSize)) {
    val pad = glyphSize.toPx() * 0.17f
    drawArc(
      color = color,
      startAngle = -125f,
      sweepAngle = 250f,
      useCenter = false,
      topLeft = Offset(pad, pad + 1.dp.toPx()),
      size = Size(this.size.width - pad * 2f, this.size.height - pad * 2f),
      style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
    )
    drawLine(
      color = color,
      start = Offset(center.x, pad * 0.35f),
      end = Offset(center.x, this.size.height * 0.46f),
      strokeWidth = 3.dp.toPx(),
      cap = StrokeCap.Round,
    )
  }
}

/** Slow-moving aurora field: three soft radial lights over deep ink. */
@Composable
private fun AuroraBackdrop(accent: Color, drift: Float, modifier: Modifier = Modifier) {
  Canvas(modifier) {
    drawRect(HatchPalette.Ink)
    val w = size.width
    val h = size.height

    fun glow(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), color.copy(alpha = alpha * 0.38f), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius,
          ),
        radius = radius,
        center = Offset(cx, cy),
      )
    }

    glow(
      cx = w * (0.16f + 0.10f * drift),
      cy = h * (0.16f + 0.08f * drift),
      radius = w * 0.72f,
      color = HatchPalette.Violet,
      alpha = 0.34f,
    )
    glow(
      cx = w * (0.92f - 0.10f * drift),
      cy = h * (0.40f + 0.06f * drift),
      radius = w * 0.80f,
      color = accent,
      alpha = 0.22f,
    )
    glow(
      cx = w * 0.48f,
      cy = h * (0.98f - 0.06f * drift),
      radius = w * 1.02f,
      color = HatchPalette.Sky,
      alpha = 0.10f,
    )

    drawRect(
      brush =
        Brush.verticalGradient(
          0.0f to Color.Transparent,
          0.68f to HatchPalette.Ink.copy(alpha = 0.30f),
          1.0f to HatchPalette.Ink,
        ),
    )
  }
}

// ----------------------------------------------------------------- headline ---

@Composable
private fun Headline(state: HatchUiState, accent: Color) {
  val tunnel = state.tunnel
  val headline =
    when (tunnel.phase) {
      Phase.IDLE -> "Ready when you are"
      Phase.CHECKING -> "Reaching the edge"
      Phase.NEEDS_AUTH -> "Prove it's you"
      Phase.NEEDS_HOST_KEY -> "A stranger at the door"
      Phase.AUTHENTICATING -> "Session accepted"
      Phase.CONNECTING -> "Opening the hatch"
      Phase.CONNECTED -> "The tunnel is live"
      Phase.DEGRADED -> "The link is thin"
      Phase.RETRYING -> "Reconnecting in ${Fmt.countdown(tunnel.retryRemainingMs)}"
      Phase.WAITING_FOR_NETWORK -> "No network"
      Phase.STOPPING -> "Closing quietly"
      Phase.ERROR -> "The hatch is stuck"
    }
  val subline =
    when (tunnel.phase) {
      Phase.IDLE -> "A private listener on this phone, forwarded through ${state.config.host}. Nothing leaves 127.0.0.1."
      Phase.CHECKING -> "Checking the Cloudflare edge and any cached Access session."
      Phase.NEEDS_AUTH -> "Cloudflare Access is protecting this endpoint. A one-time code goes to your registered email."
      Phase.NEEDS_HOST_KEY -> "This host is new. Compare its SHA-256 fingerprint before trusting it."
      Phase.AUTHENTICATING -> "Handing the captured session to the SSH engine."
      Phase.CONNECTING -> "SSH handshake over wss://, then 127.0.0.1:${state.config.localPort}."
      Phase.CONNECTED -> "Forwarding 127.0.0.1:${state.config.localPort} → ${state.config.remoteHost}:${state.config.remotePort}. Open it in any browser."
      Phase.DEGRADED -> "Latency or loss detected. Keepalive tightened and retries are armed."
      Phase.RETRYING -> "Attempt ${tunnel.attempt.coerceAtLeast(1)} of ${tunnel.maxAttempts}. Exponential backoff with jitter."
      Phase.WAITING_FOR_NETWORK -> "Hatch will resume by itself when Wi-Fi or mobile data returns."
      Phase.STOPPING -> "Releasing the local port and the SSH session."
      Phase.ERROR -> tunnel.error?.detail ?: "The last attempt failed. Retry, or check the settings."
    }

  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
    AnimatedContent(
      targetState = headline,
      transitionSpec = {
        (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 }) togetherWith
          (fadeOut(tween(140)) + slideOutVertically(tween(140)) { -it / 4 })
      },
      label = "headline",
    ) { text ->
      Text(
        text = text,
        color = Color(0xFFF3F6FB),
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W500,
        fontSize = 29.sp,
        lineHeight = 33.sp,
        letterSpacing = (-0.7).sp,
        textAlign = TextAlign.Center,
      )
    }
    Spacer(Modifier.height(7.dp))
    Text(
      text = subline,
      color = HatchPalette.Fog,
      fontFamily = Inter,
      fontSize = 12.5.sp,
      lineHeight = 18.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = 10.dp),
    )
  }
}

@Composable
private fun OpenHarness(accent: Color, port: Int, onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    shape = RoundedCornerShape(50),
    color = accent.copy(alpha = 0.12f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(
        "OPEN 127.0.0.1:$port",
        color = accent,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        letterSpacing = 0.9.sp,
      )
      Icon(
        Icons.Rounded.ArrowOutward,
        contentDescription = null,
        tint = accent,
        modifier = Modifier.size(14.dp),
      )
    }
  }
}

// ------------------------------------------------------------------- cards ---

@Composable
private fun MetricsCard(
  state: TunnelState,
  accent: Color,
  uptimeMillis: Long,
) {
  val metrics = state.metrics
  GlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Metric(
        label = "RTT",
        value = if (metrics.rttMs > 0) "${metrics.rttMs}" else "—",
        unit = "ms",
        sub = if (metrics.jitterMs > 0) "±${metrics.jitterMs} ms" else "measuring",
        modifier = Modifier.weight(1f),
      )
      VDivider()
      Metric(
        label = "DOWN",
        value = if (metrics.throughputDownBps > 0) Fmt.bytes(metrics.throughputDownBps) else "—",
        unit = if (metrics.throughputDownBps > 0) "/s" else "",
        sub = "total ${Fmt.bytes(metrics.bytesDown)}",
        modifier = Modifier.weight(1f),
      )
      VDivider()
      Metric(
        label = "UP",
        value = if (metrics.throughputUpBps > 0) Fmt.bytes(metrics.throughputUpBps) else "—",
        unit = if (metrics.throughputUpBps > 0) "/s" else "",
        sub = "total ${Fmt.bytes(metrics.bytesUp)}",
        modifier = Modifier.weight(1f),
      )
    }

    Spacer(Modifier.height(14.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
    Spacer(Modifier.height(12.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "LINK",
        color = HatchPalette.Fog,
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 1.4.sp,
      )
      Spacer(Modifier.width(9.dp))
      QualityBars(quality = state.quality, accent = accent)
      Spacer(Modifier.width(8.dp))
      Text(
        Fmt.qualityLabel(state.quality),
        color = Color.White.copy(alpha = 0.82f),
        fontFamily = Inter,
        fontSize = 11.sp,
      )
      Spacer(Modifier.weight(1f))
      Text(
        if (uptimeMillis > 0) "UP ${Fmt.clockDuration(uptimeMillis)}" else "NOT CONNECTED",
        color = HatchPalette.Fog,
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 0.6.sp,
      )
    }
  }
}

@Composable
private fun Metric(
  label: String,
  value: String,
  unit: String,
  sub: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Text(
      label,
      color = HatchPalette.Fog,
      fontFamily = JetBrainsMono,
      fontSize = 8.5.sp,
      letterSpacing = 1.2.sp,
    )
    Spacer(Modifier.height(5.dp))
    Row(verticalAlignment = Alignment.Bottom) {
      Text(
        value,
        color = Color(0xFFF1F5FA),
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W500,
        fontSize = 20.sp,
        letterSpacing = (-0.4).sp,
      )
      if (unit.isNotEmpty()) {
        Spacer(Modifier.width(3.dp))
        Text(
          unit,
          color = HatchPalette.Fog,
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          modifier = Modifier.padding(bottom = 3.dp),
        )
      }
    }
    Spacer(Modifier.height(2.dp))
    Text(
      sub,
      color = HatchPalette.Fog.copy(alpha = 0.75f),
      fontFamily = JetBrainsMono,
      fontSize = 8.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun VDivider() {
  Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(38.dp).background(Color.White.copy(alpha = 0.08f)))
}

@Composable
private fun QualityBars(quality: Quality, accent: Color) {
  val filled = Fmt.qualityBars(quality)
  Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
    repeat(4) { index ->
      Box(
        Modifier
          .width(13.dp)
          .height(3.dp)
          .clip(RoundedCornerShape(50))
          .background(
            if (index < filled) accent else Color.White.copy(alpha = 0.12f),
          ),
      )
    }
  }
}

@Composable
private fun RelayCard(state: HatchUiState, accent: Color) {
  val tunnel = state.tunnel
  val log = tunnel.logs.lastOrNull()?.message ?: "Idle · one tap opens a local-only listener"
  GlassCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "RELAY",
        color = HatchPalette.Fog,
        fontFamily = JetBrainsMono,
        fontSize = 9.sp,
        letterSpacing = 1.4.sp,
      )
      Spacer(Modifier.weight(1f))
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).background(accent, CircleShape))
        Text(
          tunnel.phase.name,
          color = accent.copy(alpha = 0.9f),
          fontFamily = JetBrainsMono,
          fontSize = 8.sp,
          letterSpacing = 0.7.sp,
        )
      }
    }

    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier
            .size(32.dp)
            .background(Color.White.copy(alpha = 0.055f), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Rounded.Lock,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(14.dp),
        )
      }
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(
          state.config.host,
          color = Color(0xFFF1F5FA),
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.W500,
          fontSize = 14.sp,
        )
        Text(
          if (state.config.useCloudflareAccess) "Cloudflare Access · wss:// · 443" else "Direct TCP · port 22",
          color = HatchPalette.Fog,
          fontFamily = Inter,
          fontSize = 10.sp,
        )
      }
      Text(
        if (state.config.useCloudflareAccess) "CF" else "SSH",
        color = accent.copy(alpha = 0.85f),
        fontFamily = JetBrainsMono,
        fontSize = 8.sp,
        letterSpacing = 1.sp,
        modifier =
          Modifier
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
      )
    }

    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(
        "127.0.0.1:${state.config.localPort}",
        color = Color.White.copy(alpha = 0.85f),
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
      )
      Spacer(Modifier.width(7.dp))
      Icon(
        Icons.Rounded.ArrowForward,
        contentDescription = null,
        tint = HatchPalette.Fog.copy(alpha = 0.6f),
        modifier = Modifier.size(11.dp),
      )
      Spacer(Modifier.width(7.dp))
      Text(
        "${state.config.remoteHost}:${state.config.remotePort}",
        color = HatchPalette.Fog,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
      )
    }

    Spacer(Modifier.height(12.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(Modifier.size(4.dp).background(accent.copy(alpha = 0.6f), CircleShape))
      Spacer(Modifier.width(8.dp))
      Text(
        log,
        color = HatchPalette.Fog.copy(alpha = 0.85f),
        fontFamily = JetBrainsMono,
        fontSize = 8.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

@Composable
private fun AuthCard(authenticating: Boolean, accent: Color, onContinue: () -> Unit) {
  GlassCard(borderColor = accent.copy(alpha = 0.30f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Box(
        modifier =
          Modifier
            .size(36.dp)
            .background(accent.copy(alpha = 0.13f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          Icons.Rounded.MailOutline,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(17.dp),
        )
      }
      Spacer(Modifier.width(12.dp))
      Column {
        Text(
          if (authenticating) "Session accepted" else "Cloudflare Access",
          color = Color(0xFFF1F5FA),
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.W500,
          fontSize = 17.sp,
        )
        Text(
          if (authenticating) "Waiting for the SSH engine to pick it up." else "A one-time code is sent to your registered email.",
          color = HatchPalette.Fog,
          fontFamily = Inter,
          fontSize = 11.sp,
          lineHeight = 15.sp,
        )
      }
    }
    if (!authenticating) {
      Spacer(Modifier.height(14.dp))
      Surface(
        onClick = onContinue,
        shape = RoundedCornerShape(14.dp),
        color = accent,
        modifier = Modifier.fillMaxWidth().height(46.dp),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Text(
            "Continue in browser",
            color = HatchPalette.Ink,
            fontFamily = Inter,
            fontWeight = FontWeight.W600,
            fontSize = 13.sp,
          )
        }
      }
      Spacer(Modifier.height(8.dp))
      Text(
        "Hatch reads the CF_Authorization cookie after sign-in. The token never leaves the app.",
        color = HatchPalette.Fog.copy(alpha = 0.75f),
        fontFamily = Inter,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
      )
    }
  }
}

@Composable
private fun ErrorCard(state: TunnelState, onRetry: () -> Unit, onSettings: () -> Unit) {
  GlassCard(borderColor = HatchPalette.Coral.copy(alpha = 0.35f)) {
    Text(
      state.error?.title ?: "Connection stopped",
      color = Color(0xFFF1F5FA),
      fontFamily = SpaceGrotesk,
      fontWeight = FontWeight.W500,
      fontSize = 17.sp,
    )
    Spacer(Modifier.height(5.dp))
    Text(
      state.error?.detail ?: "The tunnel could not be established.",
      color = HatchPalette.Fog,
      fontFamily = Inter,
      fontSize = 11.sp,
      lineHeight = 16.sp,
    )
    Spacer(Modifier.height(13.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Surface(
        onClick = onRetry,
        shape = RoundedCornerShape(12.dp),
        color = HatchPalette.Coral.copy(alpha = 0.16f),
        border = BorderStroke(1.dp, HatchPalette.Coral.copy(alpha = 0.45f)),
      ) {
        Text(
          "Try again",
          color = HatchPalette.Coral,
          fontFamily = Inter,
          fontWeight = FontWeight.W600,
          fontSize = 12.sp,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
        )
      }
      Spacer(Modifier.width(6.dp))
      TextButton(onClick = onSettings) {
        Text("Settings", color = HatchPalette.Fog, fontFamily = Inter, fontSize = 12.sp)
      }
    }
  }
}

@Composable
private fun RetryCard(state: TunnelState, accent: Color, onRetryNow: () -> Unit) {
  GlassCard(borderColor = accent.copy(alpha = 0.28f)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(
          "Automatic retry",
          color = Color(0xFFF1F5FA),
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.W500,
          fontSize = 17.sp,
        )
        Text(
          "Backoff attempt ${state.attempt.coerceAtLeast(1)} of ${state.maxAttempts}",
          color = HatchPalette.Fog,
          fontFamily = Inter,
          fontSize = 11.sp,
        )
      }
      Text(
        Fmt.countdown(state.retryRemainingMs).uppercase(),
        color = accent,
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W500,
        fontSize = 28.sp,
      )
    }
    Spacer(Modifier.height(12.dp))
    val retryDelay = state.retryDelayMs
    val progress =
      if (retryDelay != null && retryDelay > 0) {
        (1f - (state.retryRemainingMs ?: 0L).toFloat() / retryDelay.toFloat()).coerceIn(0f, 1f)
      } else {
        0f
      }
    Box(
      Modifier
        .fillMaxWidth()
        .height(3.dp)
        .clip(RoundedCornerShape(50))
        .background(Color.White.copy(alpha = 0.08f)),
    ) {
      Box(
        Modifier
          .fillMaxWidth(progress)
          .height(3.dp)
          .clip(RoundedCornerShape(50))
          .background(accent),
      )
    }
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(state.maxAttempts.coerceIn(1, 10)) { index ->
          Box(
            Modifier
              .size(5.dp)
              .background(
                if (index < state.attempt) accent else Color.White.copy(alpha = 0.14f),
                CircleShape,
              ),
          )
        }
      }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = onRetryNow) {
        Text("Retry now", color = accent, fontFamily = Inter, fontSize = 11.sp)
      }
    }
  }
}

@Composable
private fun WaitingCard(accent: Color) {
  GlassCard(borderColor = accent.copy(alpha = 0.25f)) {
    Text(
      "Waiting for a network",
      color = Color(0xFFF1F5FA),
      fontFamily = SpaceGrotesk,
      fontWeight = FontWeight.W500,
      fontSize = 17.sp,
    )
    Spacer(Modifier.height(5.dp))
    Text(
      "The radio is off or the device is offline. Hatch keeps the intent and reconnects without spending retry attempts.",
      color = HatchPalette.Fog,
      fontFamily = Inter,
      fontSize = 11.sp,
      lineHeight = 16.sp,
    )
  }
}

@Composable
private fun GlassCard(
  borderColor: Color = Color.White.copy(alpha = 0.09f),
  content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Color.White.copy(alpha = 0.045f))
        .border(1.dp, borderColor, RoundedCornerShape(24.dp))
        .padding(17.dp),
    content = content,
  )
}

// ------------------------------------------------------------------ helper ---

private fun accentFor(phase: Phase): Color =
  when (phase) {
    Phase.IDLE -> HatchPalette.Mint
    Phase.CHECKING -> HatchPalette.Sky
    Phase.NEEDS_AUTH, Phase.NEEDS_HOST_KEY, Phase.AUTHENTICATING -> HatchPalette.Amber
    Phase.CONNECTING, Phase.STOPPING -> HatchPalette.Sky
    Phase.CONNECTED -> HatchPalette.Mint
    Phase.DEGRADED, Phase.RETRYING, Phase.WAITING_FOR_NETWORK -> HatchPalette.Amber
    Phase.ERROR -> HatchPalette.Coral
  }

@Suppress("unused")
private fun TunnelConfig.forwardShort(): String = "127.0.0.1:$localPort → $remoteHost:$remotePort"
