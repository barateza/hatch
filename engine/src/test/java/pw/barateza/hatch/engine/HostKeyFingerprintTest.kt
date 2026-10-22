package pw.barateza.hatch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostKeyFingerprintTest {
  @Test
  fun fingerprintIsStableAndPrefixed() {
    val key = byteArrayOf(1, 2, 3, 4, 5)
    val first = HostKeyFingerprint.of(key)
    assertEquals(first, HostKeyFingerprint.of(key))
    assertTrue(first.startsWith("SHA256:"))
    assertTrue(first.length > "SHA256:".length)
  }

  @Test
  fun differentKeysHaveDifferentFingerprints() {
    assertNotEquals(
      HostKeyFingerprint.of(byteArrayOf(1, 2, 3)),
      HostKeyFingerprint.of(byteArrayOf(1, 2, 4)),
    )
  }
}
