package pw.barateza.hatch.ui.variant.tactile

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.theme.HatchPalette
import pw.barateza.hatch.theme.InstrumentSerif
import pw.barateza.hatch.theme.Inter
import pw.barateza.hatch.theme.JetBrainsMono

// ---------------------------------------------------------------------------
// Tokens
// ---------------------------------------------------------------------------

internal object Tactile {
  val cream = HatchPalette.TactileCream
  val paper = HatchPalette.TactilePaper
  val ink = HatchPalette.TactileInk
  val clay = HatchPalette.TactileClay
  val olive = HatchPalette.TactileOlive

  /** Deepened clay: the raw accent is too pale for small type on cream. */
  val clayInk = Color(0xFF8E4A2F)
  val oliveInk = Color(0xFF3F4632)
  val slip = Color(0xFFF8F1E4)
  val notice = Color(0xFFF6ECDB)
  val warmWhite = Color(0xFFFFF6EA)

  val matteFace = listOf(Color(0xFFFAF3E6), Color(0xFFEDE0CA), Color(0xFFD5C2A2))
  val matteRim = listOf(Color(0xFFFBF5E9), HatchPalette.TactilePaper, Color(0xFFD2BE9C))
  val litFace = listOf(Color(0xFFD07E57), Color(0xFFA84E2A), Color(0xFF7E3718))
  val litRim = listOf(Color(0xFFE99068), HatchPalette.TactileClay, Color(0xFF7E3718))
  val thinFace = listOf(Color(0xFF7D8568), Color(0xFF5A6248), Color(0xFF343A29))
  val thinRim = listOf(Color(0xFF98A087), HatchPalette.TactileOlive, Color(0xFF30362A))
  val alertFace = listOf(Color(0xFFF2DEC7), Color(0xFFDDB594), Color(0xFFB08258))
  val alertRim = listOf(Color(0xFFF8EAD6), Color(0xFFDDB594), Color(0xFFB08258))
}

internal val ControlDiameter = 252.dp
internal val HaloDiameter = 300.dp

// ---------------------------------------------------------------------------
// Copy
// ---------------------------------------------------------------------------

internal data class TactileCopy(
  val lead: String,
  val italic: String,
  val tail: String,
  val sub: String,
  val center: String,
  val hint: String,
) {
  val length: Int get() = lead.length + italic.length + tail.length
}

internal fun tactileCopy(phase: Phase): TactileCopy =
  when (phase) {
    Phase.IDLE -> TactileCopy(
      "Open the ", "hatch", ".",
      "One press opens a quiet corridor to your harness.",
      "open", "TAP TO OPEN",
    )
    Phase.CHECKING -> TactileCopy(
      "Checking the ", "door", ".",
      "Looking for the endpoint and any Access token kept from last time.",
      "wait", "PLEASE WAIT",
    )
    Phase.NEEDS_AUTH -> TactileCopy(
      "A letter is ", "on its way", ".",
      "Cloudflare Access would like to meet you first. We sent a single-use code to your inbox.",
      "letter", "READ THE NOTE",
    )
    Phase.NEEDS_HOST_KEY -> TactileCopy(
      "A stranger is ", "at the door", ".",
      "This host is new. Compare the fingerprint before you let Hatch in.",
      "who?", "CHECK THE NOTE",
    )
    Phase.AUTHENTICATING -> TactileCopy(
      "Verifying the ", "seal", ".",
      "The code came back clean. The edge is checking our stamp.",
      "wait", "PLEASE WAIT",
    )
    Phase.CONNECTING -> TactileCopy(
      "Opening the ", "hatch", "…",
      "The door is open. Walking through to the harness.",
      "wait", "PLEASE WAIT",
    )
    Phase.CONNECTED -> TactileCopy(
      "The wire is ", "live", ".",
      "Forwarding 127.0.0.1:3080 to the harness over SSH. Nothing is exposed.",
      "close", "TAP TO CLOSE",
    )
    Phase.DEGRADED -> TactileCopy(
      "The wire is ", "thin", ".",
      "The line has gone thin. Hatch loosened its keepalives, but the wire holds.",
      "close", "TAP TO CLOSE",
    )
    Phase.RETRYING -> TactileCopy(
      "Reconnecting, ", "calmly", ".",
      "The link slipped. Hatch will try again on its own — no need to press.",
      "hold", "ON ITS WAY",
    )
    Phase.WAITING_FOR_NETWORK -> TactileCopy(
      "Waiting for the ", "network", ".",
      "This phone is offline. Hatch will pick the wire up when the network returns.",
      "hold", "NO NETWORK",
    )
    Phase.STOPPING -> TactileCopy(
      "Closing the ", "hatch", ".",
      "Letting go of the port and putting the wire away.",
      "wait", "PLEASE WAIT",
    )
    Phase.ERROR -> TactileCopy(
      "The server said ", "no", ".",
      "Hatch stopped knocking. The note below says what the far end objected to.",
      "again", "TAP TO TRY AGAIN",
    )
  }

// ---------------------------------------------------------------------------
// Paper and print
// ---------------------------------------------------------------------------

private data class Speck(val x: Float, val y: Float, val r: Float, val a: Float)

private data class Fiber(val y: Float, val x0: Float, val len: Float, val a: Float)

/** Deterministic cream paper: a few hundred specks and a handful of fibres. */
@Composable
internal fun PaperGrain(modifier: Modifier = Modifier.fillMaxSize()) {
  val specks =
    remember {
      val random = Random(20261017)
      List(340) {
        Speck(random.nextFloat(), random.nextFloat(), 0.35f + random.nextFloat() * 0.9f, 0.25f + random.nextFloat() * 0.75f)
      }
    }
  val fibers =
    remember {
      val random = Random(70815)
      List(20) {
        Fiber(random.nextFloat(), random.nextFloat() * 0.8f, 0.06f + random.nextFloat() * 0.2f, 0.2f + random.nextFloat() * 0.6f)
      }
    }
  Canvas(modifier) {
    for (speck in specks) {
      drawCircle(
        color = Tactile.ink.copy(alpha = 0.05f * speck.a),
        radius = speck.r.dp.toPx() * 0.5f,
        center = Offset(speck.x * size.width, speck.y * size.height),
      )
    }
    for (fiber in fibers) {
      drawLine(
        color = Tactile.ink.copy(alpha = 0.055f * fiber.a),
        start = Offset(fiber.x0 * size.width, fiber.y * size.height),
        end = Offset((fiber.x0 + fiber.len) * size.width, fiber.y * size.height),
        strokeWidth = 0.7.dp.toPx(),
      )
    }
  }
}

@Composable
internal fun MicroLabel(
  text: String,
  color: Color = Tactile.ink.copy(alpha = 0.5f),
  modifier: Modifier = Modifier,
  size: Float = 9.5f,
  spacing: Float = 1.6f,
  align: TextAlign = TextAlign.Start,
) {
  Text(
    text = text,
    modifier = modifier,
    color = color,
    fontFamily = JetBrainsMono,
    fontSize = size.sp,
    lineHeight = (size * 1.4f).sp,
    fontWeight = FontWeight.W500,
    letterSpacing = spacing.sp,
    textAlign = align,
  )
}

@Composable
internal fun Rule(
  modifier: Modifier = Modifier,
  color: Color = Tactile.ink.copy(alpha = 0.18f),
  thickness: Dp = 1.dp,
) {
  Box(modifier.fillMaxWidth().height(thickness).background(color))
}

@Composable
internal fun DottedLeader(modifier: Modifier = Modifier, color: Color = Tactile.ink.copy(alpha = 0.3f)) {
  Canvas(modifier.height(3.dp)) {
    drawLine(
      color = color,
      start = Offset(0f, size.height / 2f),
      end = Offset(size.width, size.height / 2f),
      strokeWidth = 0.9.dp.toPx(),
      cap = StrokeCap.Round,
      pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.8.dp.toPx(), 3.4.dp.toPx()), 0f),
    )
  }
}

/** One printed receipt line: serif label, dotted leader, mono value. */
@Composable
internal fun ReceiptRow(label: String, value: String, modifier: Modifier = Modifier, emphatic: Boolean = false) {
  Row(modifier.fillMaxWidth().heightIn(min = 24.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(label, color = Tactile.ink.copy(alpha = 0.78f), fontFamily = InstrumentSerif, fontSize = 14.5.sp, maxLines = 1)
    DottedLeader(Modifier.weight(1f).padding(horizontal = 8.dp))
    Text(
      text = value,
      color = if (emphatic) Tactile.clayInk else Tactile.ink,
      fontFamily = JetBrainsMono,
      fontSize = 11.sp,
      letterSpacing = 0.2.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

// ---------------------------------------------------------------------------
// Links and solid actions
// ---------------------------------------------------------------------------

@Composable
internal fun UnderlineLink(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  color: Color = Tactile.ink,
) {
  Row(
    modifier =
      modifier
        .heightIn(min = 48.dp)
        .clip(RoundedCornerShape(2.dp))
        .clickable(role = Role.Button, onClickLabel = text, onClick = onClick)
        .padding(horizontal = 2.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = text,
      color = color,
      fontFamily = JetBrainsMono,
      fontSize = 10.sp,
      fontWeight = FontWeight.W500,
      letterSpacing = 1.5.sp,
      textDecoration = TextDecoration.Underline,
      maxLines = 1,
    )
  }
}

@Composable
internal fun SolidAction(
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  fill: Color = Tactile.ink,
  contentColor: Color = Tactile.cream,
) {
  val haptics = LocalHapticFeedback.current
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val alpha by
    animateFloatAsState(
      targetValue = if (pressed) 0.84f else 1f,
      animationSpec = spring(stiffness = Spring.StiffnessHigh),
      label = "actionAlpha",
    )
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 46.dp)
        .clip(RoundedCornerShape(2.dp))
        .background(fill.copy(alpha = alpha))
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label) {
          haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          onClick()
        }
        .padding(horizontal = 18.dp, vertical = 13.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      color = contentColor,
      fontFamily = JetBrainsMono,
      fontSize = 10.5.sp,
      fontWeight = FontWeight.W700,
      letterSpacing = 2.2.sp,
      textAlign = TextAlign.Center,
    )
  }
}

// ---------------------------------------------------------------------------
// The physical control
// ---------------------------------------------------------------------------

private enum class FaceTone { MATTE, LIT, THIN, ALERT }

private fun tonesFor(tone: FaceTone): Pair<List<Color>, List<Color>> =
  when (tone) {
    FaceTone.MATTE -> Tactile.matteRim to Tactile.matteFace
    FaceTone.LIT -> Tactile.litRim to Tactile.litFace
    FaceTone.THIN -> Tactile.thinRim to Tactile.thinFace
    FaceTone.ALERT -> Tactile.alertRim to Tactile.alertFace
  }

@Composable
internal fun HatchControl(
  phase: Phase,
  retryDelayMs: Long?,
  retryRemainingMs: Long?,
  centerWord: String,
  centerHint: String,
  enabled: Boolean,
  onClick: () -> Unit,
  diameter: Dp = ControlDiameter,
  modifier: Modifier = Modifier,
) {
  val tone =
    when (phase) {
      Phase.CONNECTED -> FaceTone.LIT
      Phase.DEGRADED -> FaceTone.THIN
      Phase.ERROR -> FaceTone.ALERT
      else -> FaceTone.MATTE
    }
  val lit = tone == FaceTone.LIT || tone == FaceTone.THIN
  val onFace = if (lit) Tactile.warmWhite else Tactile.ink
  val haptics = LocalHapticFeedback.current
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val sink by
    animateFloatAsState(
      targetValue = if (pressed) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
      label = "controlSink",
    )
  val retryTarget =
    if (phase == Phase.RETRYING) {
      val delay = retryDelayMs ?: 0L
      val remaining = retryRemainingMs ?: delay
      if (delay <= 0L) 0f else (remaining.toFloat() / delay.toFloat()).coerceIn(0f, 1f)
    } else {
      0f
    }
  // `nowMillis` only ticks at 1 Hz; ease the arc so it drains continuously.
  val retrySweep by
    animateFloatAsState(
      targetValue = retryTarget,
      animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
      label = "retryArc",
    )
  val retryProgress = if (phase == Phase.RETRYING) retrySweep else null
  val actionLabel =
    when (phase) {
      Phase.CONNECTED, Phase.DEGRADED -> "Disconnect the tunnel"
      Phase.RETRYING -> "Retry the tunnel now"
      Phase.ERROR -> "Try the tunnel again"
      else -> "Open the tunnel"
    }

  Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
    if (lit) BreathingHalo(if (tone == FaceTone.THIN) Tactile.olive else Tactile.clay, diameter * 1.19f)
    Box(
      modifier =
        Modifier
          .matchParentSize()
          .graphicsLayer {
            val s = 1f - 0.045f * sink
            scaleX = s
            scaleY = s
            translationY = 2.5.dp.toPx() * sink
          },
      contentAlignment = Alignment.Center,
    ) {
      Canvas(Modifier.matchParentSize()) { drawControlFace(tone, sink, retryProgress) }
      Column(Modifier.clearAndSetSemantics { }, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = centerWord,
          color = onFace,
          fontFamily = InstrumentSerif,
          fontStyle = FontStyle.Italic,
          fontSize =
            when {
              diameter < 218.dp -> 42.sp
              centerWord.length > 5 -> 40.sp
              else -> 50.sp
            },
          lineHeight = 50.sp,
        )
        Spacer(Modifier.height(9.dp))
        Box(Modifier.width(24.dp).height(1.dp).background(onFace.copy(alpha = 0.5f)))
        Spacer(Modifier.height(9.dp))
        Text(
          text = centerHint,
          color = onFace.copy(alpha = if (lit) 0.95f else 1f),
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          fontWeight = FontWeight.W500,
          letterSpacing = 1.4.sp,
          maxLines = 1,
        )
      }
      Box(
        modifier =
          Modifier
            .matchParentSize()
            .clip(CircleShape)
            .semantics { contentDescription = actionLabel }
            .clickable(
              interactionSource = interaction,
              indication = null,
              enabled = enabled,
              role = Role.Button,
              onClickLabel = actionLabel,
            ) {
              haptics.performHapticFeedback(HapticFeedbackType.LongPress)
              onClick()
            },
      )
    }
  }
}

/** Slow breathing light behind the control. The one infinite animation on screen. */
@Composable
private fun BreathingHalo(color: Color, size: Dp = HaloDiameter) {
  val transition = rememberInfiniteTransition(label = "halo")
  val breath by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 4600, easing = FastOutSlowInEasing),
          repeatMode = RepeatMode.Reverse,
        ),
      label = "breath",
    )
  Canvas(Modifier.size(size)) {
    val center = Offset(this.size.width / 2f, this.size.height / 2f)
    val radius = this.size.minDimension / 2f
    drawCircle(
      brush =
        Brush.radialGradient(
          colors =
            listOf(
              color.copy(alpha = 0.14f + 0.10f * breath),
              color.copy(alpha = 0.05f + 0.04f * breath),
              Color.Transparent,
            ),
          center = center,
          radius = radius,
        ),
      radius = radius,
      center = center,
    )
  }
}

private fun DrawScope.drawControlFace(tone: FaceTone, sink: Float, retryProgress: Float?) {
  val center = Offset(size.width / 2f, size.height / 2f)
  val rimR = size.minDimension / 2f - 2.dp.toPx()
  val faceR = rimR * 0.862f
  val cast = (8f - 3f * sink).dp.toPx()
  val (rimColors, faceColors) = tonesFor(tone)
  val lit = tone == FaceTone.LIT || tone == FaceTone.THIN

  // Cast shadow: stacked offset discs, steadier than a real blur on cream.
  drawCircle(Tactile.ink.copy(alpha = 0.03f), radius = faceR * 1.02f, center = center + Offset(0f, cast))
  drawCircle(Tactile.ink.copy(alpha = 0.04f), radius = faceR * 0.99f, center = center + Offset(0.5.dp.toPx(), cast * 0.72f))
  drawCircle(Tactile.ink.copy(alpha = 0.05f), radius = faceR * 0.96f, center = center + Offset(0f, cast * 0.42f))

  drawCircle(
    brush = Brush.linearGradient(rimColors, start = Offset(center.x, center.y - rimR), end = Offset(center.x, center.y + rimR)),
    radius = rimR,
    center = center,
  )

  // A fine edge so the object holds its shape on cream.
  drawCircle(Tactile.ink.copy(alpha = 0.10f), radius = rimR, center = center, style = Stroke(1.dp.toPx()))

  // Knurled rim, like a bakelite knob.
  val tickColor = if (lit) Tactile.warmWhite.copy(alpha = 0.14f) else Tactile.ink.copy(alpha = 0.06f)
  val step = (PI * 2.0 / 64).toFloat()
  for (i in 0 until 64) {
    val angle = i * step
    val dx = cos(angle)
    val dy = sin(angle)
    drawLine(
      color = tickColor,
      start = Offset(center.x + dx * rimR * 0.93f, center.y + dy * rimR * 0.93f),
      end = Offset(center.x + dx * rimR * 0.985f, center.y + dy * rimR * 0.985f),
      strokeWidth = 0.8.dp.toPx(),
    )
  }

  drawCircle(
    brush =
      Brush.radialGradient(
        colors = faceColors,
        center = Offset(center.x - faceR * 0.4f, center.y - faceR * 0.45f),
        radius = faceR * 1.85f,
      ),
    radius = faceR,
    center = center,
  )

  // Bevel: a dark seat on the lower edge, light catching the upper left.
  drawCircle(
    color = Tactile.ink.copy(alpha = if (lit) 0.22f else 0.10f),
    radius = faceR,
    center = center,
    style = Stroke(width = 1.2.dp.toPx()),
  )
  drawArc(
    color = Tactile.ink.copy(alpha = 0.10f),
    startAngle = 25f,
    sweepAngle = 130f,
    useCenter = false,
    topLeft = Offset(center.x - faceR, center.y - faceR),
    size = Size(faceR * 2f, faceR * 2f),
    style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round),
  )
  drawArc(
    color = Tactile.warmWhite.copy(alpha = 0.30f),
    startAngle = 196f,
    sweepAngle = 74f,
    useCenter = false,
    topLeft = Offset(center.x - faceR * 0.98f, center.y - faceR * 0.98f),
    size = Size(faceR * 1.96f, faceR * 1.96f),
    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
  )

  // Index mark on the rim.
  drawCircle(
    color = if (retryProgress != null) Tactile.clayInk else Tactile.clay.copy(alpha = 0.75f),
    radius = 2.dp.toPx(),
    center = Offset(center.x, center.y - rimR * 0.955f),
  )

  // Retry countdown: a dashed, hand-drawn ring draining with the backoff.
  if (retryProgress != null) {
    val ringR = rimR * 0.999f
    val topLeft = Offset(center.x - ringR, center.y - ringR)
    val arcSize = Size(ringR * 2f, ringR * 2f)
    val dash = PathEffect.dashPathEffect(floatArrayOf(3.6.dp.toPx(), 3.2.dp.toPx()), 0f)
    val sweep = 360f * retryProgress.coerceIn(0f, 1f)
    drawArc(Tactile.clay.copy(alpha = 0.28f), -90f, 360f, false, topLeft, arcSize, style = Stroke(1.dp.toPx()))
    drawArc(Tactile.clayInk, -90f, sweep, false, topLeft, arcSize, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash))
    drawArc(
      color = Tactile.clayInk.copy(alpha = 0.4f),
      startAngle = -89f,
      sweepAngle = sweep,
      useCenter = false,
      topLeft = Offset(topLeft.x + 1.dp.toPx(), topLeft.y - 0.6.dp.toPx()),
      size = arcSize,
      style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round, pathEffect = dash),
    )
  }
}

// ---------------------------------------------------------------------------
// Notices
// ---------------------------------------------------------------------------

private val LetterpressTitle =
  TextStyle(
    fontFamily = InstrumentSerif,
    fontSize = 26.sp,
    lineHeight = 28.sp,
    color = Tactile.ink,
    shadow = Shadow(color = Color(0x2E2B261E), offset = Offset(0.6f, 1.3f), blurRadius = 0.5f),
  )

/** NEEDS_AUTH: a printed notice, impressed into the paper. */
@Composable
internal fun LetterpressNotice(
  title: String,
  body: String,
  cta: String,
  onCta: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(2.dp))
        .background(Tactile.notice)
        .border(1.dp, Tactile.ink.copy(alpha = 0.26f), RoundedCornerShape(2.dp))
        .padding(horizontal = 16.dp, vertical = 12.dp),
  ) {
    Column {
      Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        MicroLabel("CLOUDFLARE ACCESS", color = Tactile.clayInk, size = 9f, spacing = 1.7f)
        MicroLabel("ONE-TIME CODE", color = Tactile.ink.copy(alpha = 0.42f), size = 9f, spacing = 1.7f)
      }
      Spacer(Modifier.height(8.dp))
      Rule(color = Tactile.ink.copy(alpha = 0.32f))
      Spacer(Modifier.height(1.dp))
      Rule(color = Tactile.ink.copy(alpha = 0.12f))
      Spacer(Modifier.height(10.dp))
      Text(text = title, style = LetterpressTitle)
      Spacer(Modifier.height(5.dp))
      Text(body, color = Tactile.ink.copy(alpha = 0.76f), fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 17.sp)
      Spacer(Modifier.height(10.dp))
      SolidAction(label = cta, onClick = onCta)
    }
  }
}

/** ERROR: a warm stamp, set slightly off-square. */
@Composable
internal fun StampNotice(
  title: String,
  body: String,
  tag: String,
  retryable: Boolean,
  cta: String,
  onCta: () -> Unit,
  onSettings: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Box(modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp)) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .graphicsLayer { rotationZ = -1.1f }
          .clip(RoundedCornerShape(2.dp))
          .background(Tactile.notice)
          .border(2.dp, Tactile.clayInk.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
          MicroLabel("A NOTE FROM THE EDGE", color = Tactile.clayInk, size = 9f, spacing = 1.7f)
          MicroLabel(tag, color = Tactile.ink.copy(alpha = 0.4f), size = 8.5f, spacing = 1.4f)
        }
        Spacer(Modifier.height(8.dp))
        Rule(color = Tactile.clayInk.copy(alpha = 0.5f), thickness = 2.dp)
        Spacer(Modifier.height(10.dp))
        Text(text = title, style = LetterpressTitle.copy(fontSize = 23.sp, lineHeight = 25.sp))
        Spacer(Modifier.height(5.dp))
        Text(body, color = Tactile.ink.copy(alpha = 0.78f), fontFamily = Inter, fontSize = 12.5.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(10.dp))
        SolidAction(label = cta, onClick = onCta, fill = Tactile.clayInk, contentColor = Tactile.warmWhite)
        if (!retryable) {
          Spacer(Modifier.height(8.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "this one needs a hand in",
              color = Tactile.ink.copy(alpha = 0.55f),
              fontFamily = InstrumentSerif,
              fontStyle = FontStyle.Italic,
              fontSize = 14.sp,
            )
            UnderlineLink("SETTINGS", onSettings, color = Tactile.ink.copy(alpha = 0.7f))
          }
        }
      }
    }
  }
}
