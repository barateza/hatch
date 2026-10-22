package pw.barateza.hatch.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsRedactorTest {
  @Test
  fun tokenUrlAndJwtAreRedacted() {
    val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.signature12345"
    val input = "open http://127.0.0.1:3080/?token=example-token-123\nCookie: CF_Authorization=$jwt"
    val output = DiagnosticsRedactor.redact(input)
    assertFalse(output.contains("example-token-123"))
    assertFalse(output.contains(jwt))
    assertTrue(output.contains("token=<redacted>"))
    assertTrue(output.contains("CF_Authorization=<redacted>"))
  }

  @Test
  fun privateKeysAndPasswordsAreRedacted() {
    val input =
      "-----BEGIN OPENSSH PRIVATE KEY-----\nAAAA\n-----END OPENSSH PRIVATE KEY-----\npassword: hunter2"
    val output = DiagnosticsRedactor.redact(input)
    assertFalse(output.contains("AAAA"))
    assertFalse(output.contains("hunter2"))
    assertTrue(output.contains("<redacted-private-key>"))
    assertTrue(output.contains("password=<redacted>"))
  }

  @Test
  fun nonSecretsSurvive() {
    val input = "endpoint: ssh.example.com\nphase: CONNECTED"
    assertTrue(DiagnosticsRedactor.redact(input) == input)
  }
}
