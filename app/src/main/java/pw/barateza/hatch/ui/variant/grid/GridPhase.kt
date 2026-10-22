package pw.barateza.hatch.ui.variant.grid

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.TunnelState
import pw.barateza.hatch.theme.HatchPalette
import pw.barateza.hatch.theme.Inter
import pw.barateza.hatch.theme.JetBrainsMono
import pw.barateza.hatch.ui.common.Fmt

/** Palette harness for the GRID direction: paper, ink, hairline, one electric accent. */
internal object GridColors {
  val paper: Color = HatchPalette.GridPaper
  val ink: Color = HatchPalette.GridInk
  val rule: Color = HatchPalette.GridRule
  val lime: Color = HatchPalette.GridLime
  val violet: Color = HatchPalette.Violet
  val amber: Color = HatchPalette.Amber
  val coral: Color = HatchPalette.Coral
}

/** Uppercase micro-label: every technical caption in the panel. */
internal val GridMicroLabel =
  TextStyle(
    fontFamily = JetBrainsMono,
    fontWeight = FontWeight.Medium,
    fontSize = 10.sp,
    lineHeight = 12.sp,
    letterSpacing = 1.2.sp,
  )

/** Prose inside the auth / fault panels. */
internal val GridProse =
  TextStyle(
    fontFamily = Inter,
    fontWeight = FontWeight.Normal,
    fontSize = 12.5.sp,
    lineHeight = 18.sp,
  )

/**
 * One dial reading per engine phase: the label in the middle of the dial, the
 * accent colour, and how far the arc should be filled.
 */
internal data class GridSkin(
  val status: String,
  val dialLabel: String,
  val hint: String,
  val accent: Color,
  val progress: Float,
  val blinking: Boolean,
)

internal fun gridSkin(tunnel: TunnelState): GridSkin =
  when (tunnel.phase) {
    Phase.IDLE -> GridSkin("STANDBY", "ENGAGE", "TAP TO CONNECT", GridColors.ink, 0f, false)
    Phase.CHECKING -> GridSkin("CHECKING", "SCAN", "RESOLVING ENDPOINT", GridColors.ink, 0.18f, true)
    Phase.NEEDS_AUTH -> GridSkin("SIGN-IN", "SIGN IN", "EMAIL OTP REQUIRED", GridColors.violet, 0.32f, false)
    Phase.NEEDS_HOST_KEY -> GridSkin("VERIFY", "VERIFY", "NEW HOST KEY", GridColors.amber, 0.5f, false)
    Phase.AUTHENTICATING -> GridSkin("VERIFYING", "VERIFY", "VALIDATING TOKEN", GridColors.violet, 0.48f, true)
    Phase.CONNECTING -> GridSkin("LINKING", "LINKING", "SSH HANDSHAKE", GridColors.ink, 0.66f, true)
    Phase.CONNECTED -> GridSkin("LIVE", "LIVE", "TAP TO DISCONNECT", GridColors.lime, 1f, true)
    Phase.DEGRADED -> GridSkin("DEGRADED", "DEGRADED", "TAP TO DISCONNECT", GridColors.amber, 0.84f, true)
    Phase.RETRYING -> {
      val remaining = tunnel.retryRemainingMs
      val delay = tunnel.retryDelayMs
      val progress =
        if (remaining != null && delay != null && delay > 0L) {
          (remaining.toFloat() / delay.toFloat()).coerceIn(0f, 1f)
        } else {
          0.5f
        }
      val countdown = remaining?.let { Fmt.countdown(it).uppercase() }
      val label = if (countdown != null && countdown != "—") "RETRY $countdown" else "RETRY"
      GridSkin(
        status = label,
        dialLabel = label,
        hint = "ATTEMPT ${tunnel.attempt.coerceAtLeast(1)}/${tunnel.maxAttempts}",
        accent = GridColors.amber,
        progress = progress,
        blinking = true,
      )
    }
    Phase.WAITING_FOR_NETWORK ->
      GridSkin("NO CARRIER", "NO CARRIER", "RESUMES ON NETWORK", GridColors.coral, 0.06f, false)
    Phase.STOPPING -> GridSkin("STOPPING", "HALT", "CLOSING FORWARD", GridColors.ink, 0.1f, false)
    Phase.ERROR -> GridSkin("FAULT", "FAULT", "MANUAL INTERVENTION", GridColors.coral, 0.12f, false)
  }
