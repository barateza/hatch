package pw.barateza.hatch.ui.variant.grid

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import pw.barateza.hatch.theme.SpaceGrotesk

private val DialSize = 250.dp
private const val DialStart = 135f
private const val DialSweep = 270f

/** Large rotary control: tick rim, accent arc, short cursor and a lamp in the centre. */
@Composable
internal fun RotaryDial(
  skin: GridSkin,
  description: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val haptics = LocalHapticFeedback.current

  val needle by
    animateFloatAsState(
      targetValue = DialStart + DialSweep * skin.progress,
      animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow),
      label = "dial-needle",
    )
  val arcSweep by
    animateFloatAsState(
      targetValue = DialSweep * skin.progress.coerceIn(0f, 1f),
      animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
      label = "dial-arc",
    )
  val pressScale by
    animateFloatAsState(
      targetValue = if (pressed) 0.99f else 1f,
      animationSpec = tween(durationMillis = 120),
      label = "dial-press",
    )

  Box(
    modifier =
      modifier
        .size(DialSize)
        .graphicsLayer {
          scaleX = pressScale
          scaleY = pressScale
        }
        .semantics(mergeDescendants = true) {
          contentDescription = description
          role = Role.Button
        }
        .clickable(
          interactionSource = interaction,
          indication = null,
          enabled = enabled,
          onClickLabel = skin.hint.lowercase(),
          onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
          },
        ),
    contentAlignment = Alignment.Center,
  ) {
    Canvas(Modifier.fillMaxSize()) {
      val center = Offset(size.width / 2f, size.height / 2f)
      val radius = size.minDimension / 2f - 3.dp.toPx()
      val tickOuter = radius - 4.dp.toPx()
      val tickMajor = tickOuter - 11.dp.toPx()
      val tickMinor = tickOuter - 6.dp.toPx()
      val arcRadius = radius - 26.dp.toPx()
      val cursorInner = radius - 42.dp.toPx()
      val cursorOuter = radius - 34.dp.toPx()
      val ringOuter = radius * 0.62f
      val ringInner = radius * 0.58f

      // Faint paper tint inside the dial once the machine is running.
      if (skin.progress > 0.75f) {
        drawCircle(skin.accent.copy(alpha = 0.07f), radius = ringOuter, center = center)
      }

      // Rim and the concentric pair that frames the centre readout.
      drawCircle(GridColors.ink.copy(alpha = 0.20f), radius, center, style = Stroke(1.dp.toPx()))
      drawCircle(GridColors.ink.copy(alpha = 0.10f), ringOuter, center, style = Stroke(1.dp.toPx()))
      drawCircle(GridColors.ink.copy(alpha = 0.06f), ringInner, center, style = Stroke(1.dp.toPx()))

      // Tick rim: 72 ticks over 270 degrees, a major tick every 30 degrees.
      for (index in 0..72) {
        val major = index % 6 == 0
        val radians = Math.toRadians((DialStart + DialSweep * (index / 72f)).toDouble())
        val dx = cos(radians).toFloat()
        val dy = sin(radians).toFloat()
        val inner = if (major) tickMajor else tickMinor
        drawLine(
          color = GridColors.ink.copy(alpha = if (major) 0.85f else 0.32f),
          start = Offset(center.x + dx * inner, center.y + dy * inner),
          end = Offset(center.x + dx * tickOuter, center.y + dy * tickOuter),
          strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx(),
          cap = StrokeCap.Round,
        )
      }

      // Arc track, then the filled accent sweep (the retry countdown drives it too).
      val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
      val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
      drawArc(
        color = GridColors.ink.copy(alpha = 0.16f),
        startAngle = DialStart,
        sweepAngle = DialSweep,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round),
      )
      if (arcSweep > 0.5f) {
        drawArc(
          color = skin.accent,
          startAngle = DialStart,
          sweepAngle = arcSweep,
          useCenter = false,
          topLeft = arcTopLeft,
          size = arcSize,
          style = Stroke(8.dp.toPx(), cap = StrokeCap.Round),
        )
      }

      // Short cursor riding the scale, with a lamp at its tip.
      val needleRadians = Math.toRadians(needle.toDouble())
      val nx = cos(needleRadians).toFloat()
      val ny = sin(needleRadians).toFloat()
      drawLine(
        color = GridColors.ink,
        start = Offset(center.x + nx * cursorInner, center.y + ny * cursorInner),
        end = Offset(center.x + nx * cursorOuter, center.y + ny * cursorOuter),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
      )
      val lamp = Offset(center.x + nx * (cursorOuter + 1.dp.toPx()), center.y + ny * (cursorOuter + 1.dp.toPx()))
      drawCircle(skin.accent.copy(alpha = 0.30f), radius = 7.dp.toPx(), center = lamp)
      drawCircle(skin.accent, radius = 4.dp.toPx(), center = lamp)
      drawCircle(GridColors.ink, radius = 4.dp.toPx(), center = lamp, style = Stroke(1.dp.toPx()))
    }

    // Centre readout, sized to sit inside the inner ring.
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(136.dp)) {
      Text(
        text = "SSH · 3080",
        style = GridMicroLabel.copy(letterSpacing = 2.sp),
        color = GridColors.ink.copy(alpha = 0.42f),
        maxLines = 1,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = skin.dialLabel,
        fontFamily = SpaceGrotesk,
        fontWeight = FontWeight.W700,
        fontSize = dialLabelSize(skin.dialLabel),
        lineHeight = dialLabelSize(skin.dialLabel) * 1.06f,
        letterSpacing = (-0.6).sp,
        color = GridColors.ink,
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
      Spacer(Modifier.height(4.dp))
      Text(
        text = skin.hint,
        style = GridMicroLabel.copy(letterSpacing = 0.9.sp),
        color = GridColors.ink.copy(alpha = 0.48f),
        textAlign = TextAlign.Center,
        maxLines = 1,
      )
    }

    // The two ends of the rotary range live in the bottom gap of the arc.
    Text(
      text = "OFF",
      style = GridMicroLabel.copy(letterSpacing = 1.6.sp),
      color = GridColors.ink.copy(alpha = 0.32f),
      modifier = Modifier.align(Alignment.BottomStart).padding(start = 64.dp, bottom = 32.dp),
    )
    Text(
      text = "ON",
      style = GridMicroLabel.copy(letterSpacing = 1.6.sp),
      color = GridColors.ink.copy(alpha = 0.32f),
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 32.dp),
    )
  }
}

private fun dialLabelSize(label: String): TextUnit =
  when {
    label.length <= 5 -> 34.sp
    label.length <= 7 -> 26.sp
    else -> 22.sp
  }

/** Flat RTT history line: technical, angular, one accent lamp at the live edge. */
@Composable
internal fun RttSparkline(
  history: List<Float>,
  accent: Color,
  active: Boolean,
  modifier: Modifier = Modifier,
) {
  Canvas(modifier.fillMaxWidth().height(26.dp)) {
    val width = size.width
    val height = size.height

    // Sparse instrument grid: quarter verticals, two horizontal guides, baseline.
    for (index in 1..3) {
      val x = width * index / 4f
      drawLine(GridColors.rule.copy(alpha = 0.45f), Offset(x, 0f), Offset(x, height), 1.dp.toPx())
    }
    for (fraction in floatArrayOf(0.33f, 0.66f)) {
      drawLine(
        color = GridColors.rule.copy(alpha = 0.7f),
        start = Offset(0f, height * fraction),
        end = Offset(width, height * fraction),
        strokeWidth = 1.dp.toPx(),
      )
    }
    drawLine(GridColors.rule, Offset(0f, height), Offset(width, height), 1.dp.toPx())

    if (history.size < 2) return@Canvas

    val peak = (history.maxOrNull() ?: 1f).coerceAtLeast(1f) * 1.18f
    val step = width / (history.size - 1).toFloat()
    val line = Path()
    val area = Path()
    history.forEachIndexed { index, value ->
      val x = index * step
      val y = height - (value / peak).coerceIn(0f, 1f) * height
      if (index == 0) {
        line.moveTo(x, y)
        area.moveTo(x, height)
        area.lineTo(x, y)
      } else {
        line.lineTo(x, y)
        area.lineTo(x, y)
      }
    }
    area.lineTo(width, height)
    area.close()

    val strength = if (active) 1f else 0.4f
    drawPath(area, accent.copy(alpha = 0.18f * strength))
    drawPath(
      line,
      GridColors.ink.copy(alpha = strength),
      style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )

    val lastY = height - ((history.last()) / peak).coerceIn(0f, 1f) * height
    val last = Offset(width - 2.dp.toPx(), lastY)
    drawCircle(accent.copy(alpha = strength), radius = 2.5.dp.toPx(), center = last)
    drawCircle(
      GridColors.ink.copy(alpha = strength),
      radius = 2.5.dp.toPx(),
      center = last,
      style = Stroke(1.dp.toPx()),
    )
  }
}
