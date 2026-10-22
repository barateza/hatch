package pw.barateza.hatch

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import pw.barateza.hatch.engine.SecretStore

@RunWith(AndroidJUnit4::class)
class SecretStoreInstrumentedTest {
  @Test
  fun secretsRoundTripAndAreNotStoredInPlaintext() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefsName = "hatch.test.${UUID.randomUUID()}"
    val alias = "hatch.test.${UUID.randomUUID()}"
    try {
      val store = SecretStore(context, prefsName, alias)
      store.put("key", "s3cr3t-value")
      assertEquals("s3cr3t-value", store.get("key"))
      val raw = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString("key", null)
      assertTrue(raw != null && raw.startsWith("v1:"))
      assertFalse(raw!!.contains("s3cr3t-value"))
      store.remove("key")
      assertNull(store.get("key"))
    } finally {
      context.deleteSharedPreferences(prefsName)
      runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
      }
    }
  }
}
