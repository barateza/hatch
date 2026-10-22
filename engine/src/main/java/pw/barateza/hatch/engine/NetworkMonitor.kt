package pw.barateza.hatch.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Snapshot of the active network, with a UI-friendly transport label. */
data class NetworkStatus(
  val online: Boolean,
  val label: String,
)

/**
 * Keeps [status] in sync with the system connectivity state.
 *
 * The [ConnectivityManager.NetworkCallback] is registered for the lifetime of the engine
 * (process-scoped prototype singleton); the callback only computes a small immutable snapshot.
 */
class NetworkMonitor(context: Context) {

  private val connectivityManager =
    context.applicationContext.getSystemService(ConnectivityManager::class.java)

  private val mutableStatus = MutableStateFlow(readStatus())
  val status: StateFlow<NetworkStatus> = mutableStatus.asStateFlow()

  private var registered = false

  private val callback =
    object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        mutableStatus.value = readStatus()
      }

      override fun onLost(network: Network) {
        mutableStatus.value = readStatus()
      }

      override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
        mutableStatus.value = readStatus()
      }

      override fun onUnavailable() {
        mutableStatus.value = readStatus()
      }
    }

  init {
    start()
  }

  fun start() {
    if (registered) return
    val manager = connectivityManager
    if (manager == null) {
      mutableStatus.value = NetworkStatus(online = true, label = "unknown")
      return
    }
    val request =
      NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    try {
      manager.registerNetworkCallback(request, callback)
      registered = true
    } catch (_: Exception) {
      // Missing permission or too many callbacks: fall back to optimistic "probably online"
      // so the tunnel engine is never blocked by monitoring being unavailable.
      mutableStatus.value = NetworkStatus(online = true, label = "unknown")
    }
    mutableStatus.value = readStatus()
  }

  private fun readStatus(): NetworkStatus {
    val manager = connectivityManager ?: return NetworkStatus(online = true, label = "unknown")
    return try {
      val network = manager.activeNetwork ?: return NetworkStatus(online = false, label = "Offline")
      val capabilities =
        manager.getNetworkCapabilities(network) ?: return NetworkStatus(online = false, label = "Offline")
      if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
        NetworkStatus(online = false, label = "Offline")
      } else {
        NetworkStatus(online = true, label = transportLabel(capabilities))
      }
    } catch (_: Exception) {
      NetworkStatus(online = true, label = "unknown")
    }
  }

  private fun transportLabel(capabilities: NetworkCapabilities): String =
    when {
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "USB"
      capabilities.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) -> "Low-power"
      else -> "Other"
    }
}
