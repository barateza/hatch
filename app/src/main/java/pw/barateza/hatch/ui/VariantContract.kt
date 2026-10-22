package pw.barateza.hatch.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelState
import pw.barateza.hatch.runtime.tailscale.TailscaleCheck

/** The three radically different prototype directions. */
enum class Variant(val label: String, val tagline: String) {
  AURORA("Aurora", "Ambient glass"),
  GRID("Grid", "Instrument panel"),
  TACTILE("Tactile", "Warm hardware"),
  ;

  companion object {
    fun fromId(id: String?): Variant? =
      entries.firstOrNull { it.name.equals(id, ignoreCase = true) || it.label.equals(id, ignoreCase = true) }
  }
}

/**
 * Everything a variant screen may render. Deliberately flat so each direction
 * can own its own visual language without reaching into Android state.
 */
data class HatchUiState(
  val config: TunnelConfig,
  val tunnel: TunnelState,
  val variant: Variant,
  /** True when the screen is being driven by the prototype seed/simulator. */
  val isDemo: Boolean = false,
  /** Wall clock, ticked once a second by the controller. */
  val nowMillis: Long = System.currentTimeMillis(),
  /** Present when a Tailscale-only host is unreachable and the user must be warned. */
  val tailscalePrompt: TailscaleCheck? = null,
)

/** The single set of user intents a variant can raise. */
interface TunnelActions {
  /** The big central button. Starts the tunnel, or disconnects when live. */
  fun toggleTunnel()

  fun retryNow()

  fun openLocalUrl()

  fun openSettings()

  /** Re-run the Cloudflare Access sign-in flow. */
  fun reauthenticate()

  /** The user saw the Tailscale warning and chose to try the tunnel anyway. */
  fun connectAnyway()

  fun dismissTailscalePrompt()

  fun openTailscale()

  /** Approve the current first-use SSH host key and retry. */
  fun trustHostKey()

  /** Abandon the first-use host-key challenge without pinning it. */
  fun cancelHostKey()

  fun copyDiagnostics()
}

/** Signature every variant implements. Must fill the whole screen. */
typealias VariantContent = @Composable (state: HatchUiState, actions: TunnelActions, modifier: Modifier) -> Unit
