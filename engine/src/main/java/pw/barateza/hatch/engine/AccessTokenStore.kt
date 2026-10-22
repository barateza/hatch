package pw.barateza.hatch.engine

import android.content.Context
import java.util.Locale

/**
 * Per-host cache for the Cloudflare Access JWT.
 *
 * The JWT is a bearer credential, so it is encrypted through [SecretStore] instead of being kept
 * in plain preferences. Tokens cached by the 0.2 prototype are migrated on construction.
 */
class AccessTokenStore(context: Context) {
  private val appContext = context.applicationContext
  private val secrets = SecretStore(appContext, "hatch.access.v2")
  private val legacy = appContext.getSharedPreferences("hatch_access_tokens", Context.MODE_PRIVATE)

  init {
    migrateLegacyTokens()
  }

  fun load(host: String): String? = secrets.get(key(host))

  fun save(host: String, jwt: String) {
    secrets.put(key(host), jwt)
  }

  fun clear(host: String) {
    secrets.remove(key(host))
    legacy.edit().remove("$LEGACY_KEY_PREFIX:${host.trim().lowercase(Locale.ROOT)}").apply()
  }

  private fun migrateLegacyTokens() {
    val all = legacy.all
    if (all.isEmpty()) return
    for ((legacyKey, value) in all) {
      if (value !is String || !legacyKey.startsWith("$LEGACY_KEY_PREFIX:")) continue
      val host = legacyKey.substringAfter(':', missingDelimiterValue = "")
      if (host.isNotBlank()) secrets.put(key(host), value)
    }
    // commit() so the old plaintext token is gone before the engine uses the encrypted copy.
    legacy.edit().clear().commit()
  }

  private fun key(host: String): String = "$KEY_PREFIX:${host.trim().lowercase(Locale.ROOT)}"

  private companion object {
    const val KEY_PREFIX = "cf_authorization"
    const val LEGACY_KEY_PREFIX = "cf_authorization"
  }
}
