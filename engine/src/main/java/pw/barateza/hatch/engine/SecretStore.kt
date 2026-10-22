package pw.barateza.hatch.engine

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Per-value AES-GCM at rest, keyed by a non-exportable Android Keystore key.
 *
 * Only ciphertext is written to SharedPreferences. A value is stored as `v1:` followed by the
 * base64 of `12-byte IV || AES/GCM ciphertext`. The Keystore key never leaves the TEE/StrongBox
 * (where available), so copying the preferences file does not reveal the secret.
 *
 * Decryption failures are treated as "no value": a key can be invalidated by the OS (for example
 * after a lockscreen change), and the user then has to re-enter the credential. Callers should
 * treat that as an authentication prompt, not as silent corruption.
 */
class SecretStore(
  context: Context,
  preferencesName: String,
  private val keyAlias: String = DEFAULT_KEY_ALIAS,
) {
  private val appContext = context.applicationContext
  private val preferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
  private val lock = Any()

  fun get(key: String): String? {
    val encoded = preferences.getString(key, null) ?: return null
    return runCatching { decrypt(encoded) }.getOrNull()
  }

  fun put(key: String, value: String) {
    preferences.edit().putString(key, encrypt(value)).apply()
  }

  fun remove(key: String) {
    preferences.edit().remove(key).apply()
  }

  fun contains(key: String): Boolean = preferences.contains(key)

  private fun encrypt(plaintext: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey())
    val iv = cipher.iv
    check(iv.size == GCM_IV_BYTES) { "Unexpected GCM IV length ${iv.size}" }
    val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    val packed = ByteArray(iv.size + ciphertext.size)
    System.arraycopy(iv, 0, packed, 0, iv.size)
    System.arraycopy(ciphertext, 0, packed, iv.size, ciphertext.size)
    return PREFIX + Base64.encodeToString(packed, Base64.NO_WRAP)
  }

  private fun decrypt(encoded: String): String {
    require(encoded.startsWith(PREFIX)) { "Not a Hatch secret" }
    val packed = Base64.decode(encoded.removePrefix(PREFIX), Base64.NO_WRAP)
    require(packed.size > GCM_IV_BYTES) { "Truncated Hatch secret" }
    val iv = packed.copyOfRange(0, GCM_IV_BYTES)
    val ciphertext = packed.copyOfRange(GCM_IV_BYTES, packed.size)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
    return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
  }

  private fun secretKey(): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
    synchronized(lock) {
      (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
      val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
      generator.init(
        KeyGenParameterSpec.Builder(
          keyAlias,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
          .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
          .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
          .setKeySize(256)
          .build(),
      )
      return generator.generateKey()
    }
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val DEFAULT_KEY_ALIAS = "hatch.secrets.v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"
    const val PREFIX = "v1:"
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128
  }
}
