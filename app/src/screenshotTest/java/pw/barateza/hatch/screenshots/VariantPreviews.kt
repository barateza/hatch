package pw.barateza.hatch.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.model.Quality
import pw.barateza.hatch.theme.HatchTheme
import pw.barateza.hatch.ui.Sample
import pw.barateza.hatch.ui.TunnelActions
import pw.barateza.hatch.ui.Variant
import pw.barateza.hatch.ui.variant.aurora.AuroraVariant
import pw.barateza.hatch.ui.variant.grid.GridVariant
import pw.barateza.hatch.ui.variant.tactile.TactileVariant

/**
 * Host-side renders of the three prototype directions. Generated with:
 *   ./gradlew :app:updateDebugScreenshotTest
 * Output: reference PNGs under app/src/screenshotTestDebug/reference/
 *
 * The 1080x2400 / 420dpi spec matches the phone the prototype targets.
 */
private const val PHONE = "spec:width=1080px,height=2400px,dpi=420"

private object NoopActions : TunnelActions {
  override fun toggleTunnel() = Unit
  override fun retryNow() = Unit
  override fun openLocalUrl() = Unit
  override fun openSettings() = Unit
  override fun reauthenticate() = Unit
  override fun connectAnyway() = Unit
  override fun dismissTailscalePrompt() = Unit
  override fun openTailscale() = Unit
  override fun trustHostKey() = Unit
  override fun cancelHostKey() = Unit
  override fun copyDiagnostics() = Unit
}

@Composable
private fun PreviewFrame(variant: Variant, phase: Phase, quality: Quality = Quality.GOOD) {
  HatchTheme(darkTheme = true) {
    val state = Sample.ui(variant = variant, phase = phase, quality = quality)
    when (variant) {
      Variant.AURORA -> AuroraVariant(state, NoopActions, Modifier)
      Variant.GRID -> GridVariant(state, NoopActions, Modifier)
      Variant.TACTILE -> TactileVariant(state, NoopActions, Modifier)
    }
  }
}

// ---------------------------------------------------------------- AURORA ---

@PreviewTest
@Preview(name = "aurora-idle", device = PHONE, showBackground = true)
@Composable
fun AuroraIdlePreview() = PreviewFrame(Variant.AURORA, Phase.IDLE)

@PreviewTest
@Preview(name = "aurora-connected", device = PHONE, showBackground = true)
@Composable
fun AuroraConnectedPreview() = PreviewFrame(Variant.AURORA, Phase.CONNECTED)

@PreviewTest
@Preview(name = "aurora-retrying", device = PHONE, showBackground = true)
@Composable
fun AuroraRetryingPreview() = PreviewFrame(Variant.AURORA, Phase.RETRYING, Quality.POOR)

@PreviewTest
@Preview(name = "aurora-auth", device = PHONE, showBackground = true)
@Composable
fun AuroraAuthPreview() = PreviewFrame(Variant.AURORA, Phase.NEEDS_AUTH)

@PreviewTest
@Preview(name = "aurora-error", device = PHONE, showBackground = true)
@Composable
fun AuroraErrorPreview() = PreviewFrame(Variant.AURORA, Phase.ERROR)

// ------------------------------------------------------------------ GRID ---

@PreviewTest
@Preview(name = "grid-idle", device = PHONE, showBackground = true)
@Composable
fun GridIdlePreview() = PreviewFrame(Variant.GRID, Phase.IDLE)

@PreviewTest
@Preview(name = "grid-connected", device = PHONE, showBackground = true)
@Composable
fun GridConnectedPreview() = PreviewFrame(Variant.GRID, Phase.CONNECTED)

@PreviewTest
@Preview(name = "grid-retrying", device = PHONE, showBackground = true)
@Composable
fun GridRetryingPreview() = PreviewFrame(Variant.GRID, Phase.RETRYING, Quality.POOR)

@PreviewTest
@Preview(name = "grid-auth", device = PHONE, showBackground = true)
@Composable
fun GridAuthPreview() = PreviewFrame(Variant.GRID, Phase.NEEDS_AUTH)

@PreviewTest
@Preview(name = "grid-error", device = PHONE, showBackground = true)
@Composable
fun GridErrorPreview() = PreviewFrame(Variant.GRID, Phase.ERROR)

// --------------------------------------------------------------- TACTILE ---

@PreviewTest
@Preview(name = "tactile-idle", device = PHONE, showBackground = true)
@Composable
fun TactileIdlePreview() = PreviewFrame(Variant.TACTILE, Phase.IDLE)

@PreviewTest
@Preview(name = "tactile-connected", device = PHONE, showBackground = true)
@Composable
fun TactileConnectedPreview() = PreviewFrame(Variant.TACTILE, Phase.CONNECTED)

@PreviewTest
@Preview(name = "tactile-connected-short", device = "spec:width=1080px,height=2240px,dpi=420", showBackground = true)
@Composable
fun TactileConnectedShortPreview() = PreviewFrame(Variant.TACTILE, Phase.CONNECTED)

@PreviewTest
@Preview(name = "tactile-auth-short", device = "spec:width=1080px,height=2240px,dpi=420", showBackground = true)
@Composable
fun TactileAuthShortPreview() = PreviewFrame(Variant.TACTILE, Phase.NEEDS_AUTH)

@PreviewTest
@Preview(name = "tactile-retrying", device = PHONE, showBackground = true)
@Composable
fun TactileRetryingPreview() = PreviewFrame(Variant.TACTILE, Phase.RETRYING, Quality.POOR)

@PreviewTest
@Preview(name = "tactile-auth", device = PHONE, showBackground = true)
@Composable
fun TactileAuthPreview() = PreviewFrame(Variant.TACTILE, Phase.NEEDS_AUTH)

@PreviewTest
@Preview(name = "tactile-error", device = PHONE, showBackground = true)
@Composable
fun TactileErrorPreview() = PreviewFrame(Variant.TACTILE, Phase.ERROR)
