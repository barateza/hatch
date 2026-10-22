package pw.barateza.hatch.runtime

import android.content.Context
import androidx.core.content.edit
import pw.barateza.hatch.engine.SecretStore
import pw.barateza.hatch.model.SshAuthMode
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.ui.Variant

/**
 * Persistence for Hatch settings.
 *
 * Non-secret settings live in plain preferences. The SSH key, passphrase, password and harness
 * token URL live in [SecretStore] (Android Keystore AES-GCM). Values from the 0.2 prototype are
 * migrated on first construction and removed from the legacy plaintext preferences.
 */
class SettingsStore(context: Context) {
  private val appContext = context.applicationContext
  private val prefs = appContext.getSharedPreferences("hatch.settings.v1", Context.MODE_PRIVATE)
  private val secrets = SecretStore(appContext, "hatch.secrets.v1")

  init {
    migrateLegacySecrets()
  }

  var config: TunnelConfig
    get() =
      TunnelConfig(
        host = prefs.getString("host", null) ?: TunnelConfig().host,
        sshUser = prefs.getString("user", null) ?: TunnelConfig().sshUser,
        useCloudflareAccess = prefs.getBoolean("access", true),
        localPort = prefs.getInt("localPort", 3080),
        harnessUrl = secrets.get(KEY_HARNESS_URL) ?: "",
        remoteHost = prefs.getString("remoteHost", null) ?: "127.0.0.1",
        remotePort = prefs.getInt("remotePort", 3080),
        authMode = runCatching { SshAuthMode.valueOf(prefs.getString("authMode", "KEY")!!) }.getOrDefault(SshAuthMode.KEY),
        privateKey = secrets.get(KEY_PRIVATE_KEY) ?: "",
        keyPassphrase = secrets.get(KEY_PASSPHRASE) ?: "",
        password = secrets.get(KEY_PASSWORD) ?: "",
        maxRetries = prefs.getInt("maxRetries", 6),
        baseDelayMs = prefs.getLong("baseDelayMs", 1_200L),
        maxDelayMs = prefs.getLong("maxDelayMs", 45_000L),
        jitter = prefs.getFloat("jitter", 0.3f),
        keepAliveSeconds = prefs.getInt("keepAliveSeconds", 20),
        adaptive = prefs.getBoolean("adaptive", true),
        // The shipped app is always real. Demo mode is only reachable through debug builds.
        demoMode = false,
      )
    set(value) {
      prefs.edit {
        putString("host", value.host)
        putString("user", value.sshUser)
        putBoolean("access", value.useCloudflareAccess)
        putInt("localPort", value.localPort)
        putString("remoteHost", value.remoteHost)
        putInt("remotePort", value.remotePort)
        putString("authMode", value.authMode.name)
        putInt("maxRetries", value.maxRetries)
        putLong("baseDelayMs", value.baseDelayMs)
        putLong("maxDelayMs", value.maxDelayMs)
        putFloat("jitter", value.jitter)
        putInt("keepAliveSeconds", value.keepAliveSeconds)
        putBoolean("adaptive", value.adaptive)
        // Remove plaintext remnants even if a user downgraded and re-upgraded.
        remove("privateKey")
        remove("keyPassphrase")
        remove("password")
        remove("harnessUrl")
        remove("acceptAnyHostKey")
      }
      secrets.put(KEY_PRIVATE_KEY, value.privateKey)
      secrets.put(KEY_PASSPHRASE, value.keyPassphrase)
      secrets.put(KEY_PASSWORD, value.password)
      if (value.harnessUrl.isBlank()) secrets.remove(KEY_HARNESS_URL) else secrets.put(KEY_HARNESS_URL, value.harnessUrl)
    }

  var variant: Variant
    get() = runCatching { Variant.valueOf(prefs.getString("variant", Variant.TACTILE.name)!!) }.getOrDefault(Variant.TACTILE)
    set(value) = prefs.edit { putString("variant", value.name) }

  private fun migrateLegacySecrets() {
    // 0.2 stored these four values in the settings preference file in clear text.
    val legacy =
      mapOf(
        "privateKey" to KEY_PRIVATE_KEY,
        "keyPassphrase" to KEY_PASSPHRASE,
        "password" to KEY_PASSWORD,
        "harnessUrl" to KEY_HARNESS_URL,
      )
    var changed = false
    for ((legacyKey, secretKey) in legacy) {
      val plaintext = prefs.getString(legacyKey, null) ?: continue
      if (plaintext.isNotEmpty()) secrets.put(secretKey, plaintext)
      // commit() so a crash cannot leave a plaintext credential behind after migration.
      prefs.edit().remove(legacyKey).commit()
      changed = true
    }
    if (changed) prefs.edit().remove("acceptAnyHostKey").commit()
  }

  private companion object {
    const val KEY_PRIVATE_KEY = "privateKey"
    const val KEY_PASSPHRASE = "keyPassphrase"
    const val KEY_PASSWORD = "password"
    const val KEY_HARNESS_URL = "harnessUrl"
  }
}
