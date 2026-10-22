package pw.barateza.hatch.engine

import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Cloudflare Access discovery + WebSocket transport, matching the behaviour of
 * `cloudflared access ssh`:
 *
 * 1. `GET https://<host>/` with redirects disabled. A 302 whose `Location` points at
 *    `https://<team>.cloudflareaccess.com/cdn-cgi/access/login/...` is the browser login URL.
 * 2. `wss://<host>/` with `Cf-Access-Token: <jwt>` upgrades to a raw SSH byte stream.
 *
 * No path or query parameter is appended to the WebSocket URL.
 */
object CloudflareAccess {

  const val USER_AGENT: String = "hatch-android/0.1"

  /**
   * Parses the configured host into the HTTPS endpoint (`https://<host>/`). Returns null when
   * the value cannot be parsed; callers surface that as a configuration error.
   */
  fun endpointUrl(host: String): HttpUrl? {
    val cleaned =
      host.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')
    if (cleaned.isBlank() || cleaned.any { it.isWhitespace() }) return null
    return runCatching { "https://$cleaned/".toHttpUrlOrNull() }.getOrNull()
  }

  /**
   * The origin used for the WebSocket upgrade. OkHttp's [HttpUrl] only understands
   * http/https — `toHttpUrlOrNull("wss://…")` returns null, a bug that previously surfaced as
   * "Invalid SSH host". `newWebSocket` transparently upgrades an https URL over TLS, which is
   * exactly the `wss://<host>/` handshake Cloudflare expects.
   */
  fun tunnelUrl(host: String): HttpUrl? {
    val cleaned =
      host.trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("wss://")
        .removePrefix("ws://")
        .trimEnd('/')
    if (cleaned.isBlank() || cleaned.any { it.isWhitespace() }) return null
    return runCatching { "https://$cleaned/".toHttpUrlOrNull() }.getOrNull()
  }

  /**
   * Discovers the Cloudflare Access login URL.
   *
   * @return the absolute login URL, or `null` when the endpoint did not request Access at all.
   * @throws IOException for transport failures and unexpected responses (classified later).
   */
  suspend fun discoverLoginUrl(
    client: OkHttpClient,
    host: String,
    timeoutMs: Long,
  ): String? {
    val endpoint = endpointUrl(host) ?: throw ConfigException("Invalid SSH host: '$host'")
    val request =
      Request.Builder()
        .url(endpoint)
        .get()
        .header("User-Agent", USER_AGENT)
        .build()

    val response =
      withTimeout(timeoutMs) {
        client.awaitCall(request)
      }

    try {
      val location = response.header("Location")
      if (response.code in 300..399) {
        val resolved =
          location?.let { response.request.url.resolve(it) }?.toString()
            ?: throw IOException("Cloudflare Access redirect without a usable Location header")
        if (isLoginUrl(resolved)) return resolved
        throw IOException("Unexpected redirect from $host: HTTP ${response.code} -> $resolved")
      }
      if (response.code == 200) {
        // No Access application in front of this endpoint. The caller falls back to a
        // token-less WebSocket upgrade.
        return null
      }
      throw IOException("Cloudflare Access discovery failed: HTTP ${response.code}")
    } finally {
      response.close()
    }
  }

  /**
   * Opens the raw SSH WebSocket and suspends until the upgrade succeeds.
   *
   * When [jwt] is `null`/blank the `Cf-Access-Token` header is omitted (only used for the
   * no-Access fallback).
   */
  suspend fun openTunnel(
    client: OkHttpClient,
    host: String,
    jwt: String?,
    connectTimeoutMs: Long,
  ): DuctSocket {
    val url = tunnelUrl(host) ?: throw ConfigException("Invalid SSH host: '$host'")
    val requestBuilder =
      Request.Builder()
        .url(url)
        .header("User-Agent", USER_AGENT)
    if (!jwt.isNullOrBlank()) {
      requestBuilder.header("Cf-Access-Token", jwt)
    }

    val duct = DuctSocket(host, 443)
    val webSocket =
      try {
        client.newWebSocket(requestBuilder.build(), duct.listener)
      } catch (t: IllegalArgumentException) {
        duct.shutdownNow()
        throw ConfigException("Invalid tunnel URL: ${t.message}")
      }
    duct.bind(webSocket)

    try {
      duct.awaitOpen(connectTimeoutMs)
    } catch (t: Throwable) {
      duct.shutdownNow()
      throw t
    }
    return duct
  }

  private fun isLoginUrl(url: String): Boolean {
    val lower = url.lowercase(Locale.ROOT)
    return lower.contains("cloudflareaccess.com") || lower.contains("/cdn-cgi/access/")
  }
}
