package pw.barateza.hatch.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackoffTest {

  @Test
  fun firstAttemptWaitsBaseDelay() {
    assertEquals(1_200L, Backoff.rawDelayMs(attempt = 1, baseDelayMs = 1_200L, maxDelayMs = 45_000L))
  }

  @Test
  fun growsExponentiallyAndCapsAtMaxDelay() {
    assertEquals(2_400L, Backoff.rawDelayMs(2, 1_200L, 45_000L))
    assertEquals(4_800L, Backoff.rawDelayMs(3, 1_200L, 45_000L))
    assertEquals(9_600L, Backoff.rawDelayMs(4, 1_200L, 45_000L))
    assertEquals(38_400L, Backoff.rawDelayMs(6, 1_200L, 45_000L))
    assertEquals(45_000L, Backoff.rawDelayMs(7, 1_200L, 45_000L))
    assertEquals(45_000L, Backoff.rawDelayMs(100, 1_200L, 45_000L))
  }

  @Test
  fun attemptLowerThanOneBehavesLikeFirstAttempt() {
    assertEquals(500L, Backoff.rawDelayMs(0, 500L, 10_000L))
    assertEquals(500L, Backoff.rawDelayMs(-7, 500L, 10_000L))
  }

  @Test
  fun zeroOrNegativeBaseYieldsNoDelay() {
    assertEquals(0L, Backoff.rawDelayMs(5, 0L, 10_000L))
    assertEquals(0L, Backoff.delayMs(5, -20L, 10_000L, 0.3f))
  }

  @Test
  fun zeroJitterReturnsRawDelay() {
    assertEquals(
      3_200L,
      Backoff.delayMs(attempt = 3, baseDelayMs = 800L, maxDelayMs = 30_000L, jitterFraction = 0f),
    )
  }

  @Test
  fun jitterRandomEndpointsAreRespected() {
    // random 0.0 keeps the full delay, random 1.0 applies the full jitter reduction.
    assertEquals(
      2_000L,
      Backoff.delayMs(2, 1_000L, 60_000L, 0.3f, random = { 0.0 }),
    )
    assertEquals(
      1_400L,
      Backoff.delayMs(2, 1_000L, 60_000L, 0.3f, random = { 1.0 }),
    )
    assertEquals(
      1_700L,
      Backoff.delayMs(2, 1_000L, 60_000L, 0.3f, random = { 0.5 }),
    )
  }

  @Test
  fun jitterFractionIsClampedToZeroOne() {
    // Negative jitter behaves like zero jitter.
    assertEquals(1_000L, Backoff.delayMs(1, 1_000L, 60_000L, -3f, random = { 1.0 }))
    // Jitter above one clamps to one, so random 1.0 can reach zero.
    assertEquals(0L, Backoff.delayMs(1, 1_000L, 60_000L, 4f, random = { 1.0 }))
    // Out-of-range random values are clamped defensively.
    assertEquals(1_000L, Backoff.delayMs(1, 1_000L, 60_000L, 0.5f, random = { -1.0 }))
    assertEquals(500L, Backoff.delayMs(1, 1_000L, 60_000L, 0.5f, random = { 42.0 }))
  }

  @Test
  fun delayAlwaysLiesInsideJitterWindow() {
    val base = 1_200L
    val max = 45_000L
    val jitter = 0.30f
    for (attempt in 1..10) {
      val raw = Backoff.rawDelayMs(attempt, base, max)
      val lowerBound = (raw * (1.0 - jitter)).toLong()
      var sample = 0.0
      while (sample <= 1.0) {
        val delay = Backoff.delayMs(attempt, base, max, jitter, random = { sample })
        assertTrue(
          "attempt=$attempt sample=$sample delay=$delay outside [$lowerBound, $raw]",
          delay in lowerBound..raw,
        )
        sample += 0.1
      }
    }
  }

  @Test
  fun hugeAttemptDoesNotOverflow() {
    assertEquals(45_000L, Backoff.rawDelayMs(Int.MAX_VALUE, 1_200L, 45_000L))
  }
}
