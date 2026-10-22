package pw.barateza.hatch.runtime.tailscale

import java.util.Locale

/**
 * Heuristics for "is this hostname an address that only Tailscale can reach?".
 *
 * Tailscale allocates 100.64.0.0/10 and, for IPv6, fd7a:115c:a1e0::/48. MagicDNS names end in
 * `.ts.net`. A tailnet can also use split DNS for a custom suffix, which cannot be detected from
 * the name alone; the user can still connect and the engine will report the real failure.
 */
object TailscaleHosts {
  fun isTailscaleHost(rawHost: String): Boolean {
    val host = normalize(rawHost)
    if (host.isEmpty()) return false
    if (host.endsWith(".ts.net")) return true
    if (host.startsWith("fd7a:115c:a1e0:")) return true
    return isCgnatV4(host)
  }

  fun isCgnatV4(rawHost: String): Boolean {
    val host = normalize(rawHost)
    val parts = host.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull()?.takeIf { value -> value in 0..255 } ?: return false }
    return octets[0] == 100 && octets[1] in 64..127
  }

  private fun normalize(rawHost: String): String =
    rawHost
      .trim()
      .removeSuffix(".")
      .removePrefix("[")
      .removeSuffix("]")
      .lowercase(Locale.ROOT)
}
