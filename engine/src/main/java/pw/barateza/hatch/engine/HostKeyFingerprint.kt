package pw.barateza.hatch.engine

import java.security.MessageDigest
import java.util.Base64

/** OpenSSH-style `SHA256:` fingerprint over the host-key blob JSch hands to a repository. */
object HostKeyFingerprint {
  fun of(keyBlob: ByteArray): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(keyBlob)
    return "SHA256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
  }
}
