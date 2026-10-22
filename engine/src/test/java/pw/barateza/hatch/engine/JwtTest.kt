package pw.barateza.hatch.engine

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JwtTest {

  private fun base64Url(value: String): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

  private fun tokenWithPayload(payload: String): String =
    "${base64Url("""{"alg":"RS256","typ":"JWT"}""")}.${base64Url(payload)}.signature"

  @Test
  fun parsesValidExpiry() {
    val token = tokenWithPayload("""{"sub":"user@example.com","exp":2000000000}""")
    assertEquals(2_000_000_000_000L, Jwt.expiryMillis(token))
    assertTrue(Jwt.isUsable(token, nowMillis = 1_900_000_000_000L, skewMillis = 60_000L))
    assertFalse(Jwt.isUsable(token, nowMillis = 2_000_000_000_000L, skewMillis = 60_000L))
  }

  @Test
  fun ignoresNestedExpAndFindsTopLevelClaim() {
    val token =
      tokenWithPayload(
        """{"sub":"x","nested":{"exp":999},"array":[{"exp":1}],"exp":1700000000}""",
      )
    assertEquals(1_700_000_000_000L, Jwt.expiryMillis(token))
  }

  @Test
  fun expiredTokenIsNotUsable() {
    val token = tokenWithPayload("""{"exp":1000}""")
    assertEquals(1_000_000L, Jwt.expiryMillis(token))
    assertFalse(Jwt.isUsable(token, nowMillis = 1_500_000_000_000L))
    assertFalse(Jwt.isUsable(token, nowMillis = 2_000_000L, skewMillis = 0L))
  }

  @Test
  fun tokenExpiringInsideSkewWindowIsNotUsable() {
    val now = 1_500_000_000_000L
    val token = tokenWithPayload("""{"exp":${now / 1000L + 30L}}""")
    assertFalse(Jwt.isUsable(token, nowMillis = now, skewMillis = 60_000L))
    assertTrue(Jwt.isUsable(token, nowMillis = now, skewMillis = 0L))
  }

  @Test
  fun rejectsMalformedTokens() {
    assertNull(Jwt.expiryMillis(null))
    assertNull(Jwt.expiryMillis(""))
    assertNull(Jwt.expiryMillis("   "))
    assertNull(Jwt.expiryMillis("not-a-jwt"))
    assertNull(Jwt.expiryMillis("onlyonepart"))
    assertNull(Jwt.expiryMillis("header.!!!not-base64!!!.signature"))
    assertNull(Jwt.expiryMillis(tokenWithPayload("this is not json")))
    assertNull(Jwt.expiryMillis(tokenWithPayload("[]")))
    assertNull(Jwt.expiryMillis(tokenWithPayload("""{"sub":"no-exp"}""")))
    assertNull(Jwt.expiryMillis(tokenWithPayload("""{"exp":"1700000000"}""")))
    assertNull(Jwt.expiryMillis(tokenWithPayload("""{"exp":null}""")))
    assertNull(Jwt.expiryMillis(tokenWithPayload("""{"exp":1.5}""")))
    assertNull(Jwt.expiryMillis(tokenWithPayload("""{"exp":-42}""")))
    assertFalse(Jwt.isUsable("garbage", nowMillis = 0L))
  }

  @Test
  fun payloadMayBeUnpaddedBase64Url() {
    // Base64url encoding of {"exp":1700000000} without '=' padding.
    val header = base64Url("""{"alg":"none"}""")
    val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1700000000}""".toByteArray())
    assertEquals(1_700_000_000_000L, Jwt.expiryMillis("$header.$payload."))
  }
}
