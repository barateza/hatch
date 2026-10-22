package pw.barateza.hatch.engine

import android.content.Context
import java.util.Locale

/**
 * Trust-on-first-use pinning for SSH host keys.
 *
 * The pin is a SHA-256 fingerprint, not a secret, so plain preferences are fine. A changed pin is
 * never overwritten automatically: the engine fails closed until the user explicitly forgets it
 * and accepts the new key.
 */
class HostKeyStore(context: Context) {
  private val preferences =
    context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

  fun fingerprint(host: String): String? = preferences.getString(key(host), null)

  fun pin(host: String, fingerprint: String) {
    preferences.edit().putString(key(host), fingerprint).apply()
  }

  fun forget(host: String) {
    preferences.edit().remove(key(host)).apply()
  }

  fun forgetAll() {
    preferences.edit().clear().apply()
  }

  private fun key(host: String): String = host.trim().lowercase(Locale.ROOT)

  private companion object {
    const val PREFERENCES_NAME = "hatch.hostkeys.v1"
  }
}
