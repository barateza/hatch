package pw.barateza.hatch.runtime.tailscale

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class TailscaleState {
  NOT_REQUIRED,
  APP_MISSING,
  VPN_OFF,
  OTHER_VPN,
  DNS_FAILED,
  CONNECTED,
}

data class TailscaleCheck(
  val state: TailscaleState = TailscaleState.NOT_REQUIRED,
  val host: String = "",
  val message: String = "",
) {
  val needsAttention: Boolean
    get() = state != TailscaleState.NOT_REQUIRED && state != TailscaleState.CONNECTED
}

/**
 * Preflight for a Tailscale-only hostname.
 *
 * Android gives apps no public Tailscale API, so this is deliberately a best-effort check:
 * - Is the Tailscale package installed?
 * - Is any VPN active, and does its DNS look like Tailscale's MagicDNS (100.100.100.100)?
 * - For `.ts.net`, does the name resolve while the VPN is up?
 *
 * A false warning is intentional: the dialog always offers "Connect anyway". A false "connected"
 * is not, so every positive check has to see either MagicDNS or a resolved host.
 */
class TailscalePreflight(private val context: Context) {
  private val appContext = context.applicationContext

  suspend fun check(host: String): TailscaleCheck {
    if (!TailscaleHosts.isTailscaleHost(host)) return TailscaleCheck()

    if (!isTailscaleInstalled()) {
      return TailscaleCheck(
        state = TailscaleState.APP_MISSING,
        host = host,
        message =
          "This looks like a Tailscale host, but the Tailscale app is not installed. " +
            "Install Tailscale, sign in, and turn it on before connecting.",
      )
    }

    val manager = appContext.getSystemService(ConnectivityManager::class.java)
    val vpnNetworks =
      manager
        ?.allNetworks
        ?.filter { network ->
          manager.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        }
        .orEmpty()

    if (vpnNetworks.isEmpty()) {
      return TailscaleCheck(
        state = TailscaleState.VPN_OFF,
        host = host,
        message =
          "This is a Tailscale host, but no VPN is connected. Open Tailscale and turn it on, " +
            "then try again.",
      )
    }

    val magicDns = vpnNetworks.any { network ->
      val link = manager?.getLinkProperties(network)
      link?.dnsServers?.any { it.hostAddress == MAGIC_DNS } == true ||
        link?.domains?.contains(".ts.net", ignoreCase = true) == true
    }
    if (magicDns) {
      return TailscaleCheck(TailscaleState.CONNECTED, host)
    }

    // A non-MagicDNS VPN can still be Tailscale with a custom resolver. Probe the name before
    // deciding: a successful `.ts.net` resolution is enough evidence that the tailnet is up.
    if (host.trim().removeSuffix(".").endsWith(".ts.net", ignoreCase = true)) {
      val resolved =
        withTimeoutOrNull(DNS_TIMEOUT_MS) {
          withContext(Dispatchers.IO) {
            runCatching { InetAddress.getAllByName(host) }.getOrNull()
          }
        }
      if (resolved != null) return TailscaleCheck(TailscaleState.CONNECTED, host)
      return TailscaleCheck(
        state = TailscaleState.DNS_FAILED,
        host = host,
        message =
          "A VPN is active, but $host does not resolve. Check that Tailscale is connected to the " +
            "right tailnet and that MagicDNS is enabled.",
      )
    }

    return TailscaleCheck(
      state = TailscaleState.OTHER_VPN,
      host = host,
      message =
        "A VPN is active, but Hatch cannot confirm it is Tailscale. If this host is on your " +
          "tailnet, open Tailscale and connect before continuing.",
    )
  }

  fun openTailscale(): Boolean {
    val launch = appContext.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)
    if (launch != null) {
      launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      return runCatching { appContext.startActivity(launch) }.isSuccess
    }
    val install =
      Intent(Intent.ACTION_VIEW, Uri.parse("https://tailscale.com/download/android"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { appContext.startActivity(install) }.isSuccess
  }

  private fun isTailscaleInstalled(): Boolean =
    runCatching {
      appContext.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0)
      true
    }.getOrDefault(false)

  private companion object {
    const val TAILSCALE_PACKAGE = "com.tailscale.ipn"
    const val MAGIC_DNS = "100.100.100.100"
    const val DNS_TIMEOUT_MS = 2_500L
  }
}
