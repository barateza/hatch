package pw.barateza.hatch.engine

import org.junit.Assert.assertEquals
import org.junit.Test
import pw.barateza.hatch.model.Quality

class QualityMappingTest {

  @Test
  fun offlineWins() {
    assertEquals(
      Quality.OFFLINE,
      QualityMapping.compute(rttMs = 10L, jitterMs = 0L, keepaliveMisses = 0, online = false),
    )
  }

  @Test
  fun unknownBeforeFirstRttSample() {
    assertEquals(
      Quality.UNKNOWN,
      QualityMapping.compute(rttMs = 0L, jitterMs = 0L, keepaliveMisses = 0),
    )
  }

  @Test
  fun excellentThresholds() {
    assertEquals(80L, QualityMapping.EXCELLENT_MAX_RTT_MS)
    assertEquals(
      Quality.EXCELLENT,
      QualityMapping.compute(rttMs = 80L, jitterMs = 20L, keepaliveMisses = 0),
    )
    assertEquals(
      Quality.EXCELLENT,
      QualityMapping.compute(rttMs = 35L, jitterMs = 4L, keepaliveMisses = 0),
    )
  }

  @Test
  fun goodThresholds() {
    assertEquals(
      Quality.GOOD,
      QualityMapping.compute(rttMs = 81L, jitterMs = 20L, keepaliveMisses = 0),
    )
    assertEquals(
      Quality.GOOD,
      QualityMapping.compute(rttMs = 150L, jitterMs = 40L, keepaliveMisses = 0),
    )
    // High jitter pulls an otherwise-excellent RTT down a bucket.
    assertEquals(
      Quality.GOOD,
      QualityMapping.compute(rttMs = 70L, jitterMs = 25L, keepaliveMisses = 0),
    )
  }

  @Test
  fun fairThresholds() {
    assertEquals(
      Quality.FAIR,
      QualityMapping.compute(rttMs = 151L, jitterMs = 40L, keepaliveMisses = 0),
    )
    assertEquals(
      Quality.FAIR,
      QualityMapping.compute(rttMs = 300L, jitterMs = 80L, keepaliveMisses = 0),
    )
  }

  @Test
  fun poorWhenThresholdsExceeded() {
    assertEquals(
      Quality.POOR,
      QualityMapping.compute(rttMs = 301L, jitterMs = 0L, keepaliveMisses = 0),
    )
    assertEquals(
      Quality.POOR,
      QualityMapping.compute(rttMs = 100L, jitterMs = 81L, keepaliveMisses = 0),
    )
  }

  @Test
  fun missedKeepalivesLowerQuality() {
    // 1-2 misses cap a great link at FAIR.
    assertEquals(
      Quality.FAIR,
      QualityMapping.compute(rttMs = 40L, jitterMs = 5L, keepaliveMisses = 1),
    )
    assertEquals(
      Quality.FAIR,
      QualityMapping.compute(rttMs = 40L, jitterMs = 5L, keepaliveMisses = 2),
    )
    // A poor link stays poor.
    assertEquals(
      Quality.POOR,
      QualityMapping.compute(rttMs = 400L, jitterMs = 200L, keepaliveMisses = 1),
    )
    // Three consecutive misses means the engine treats the link as lossy.
    assertEquals(
      Quality.POOR,
      QualityMapping.compute(rttMs = 40L, jitterMs = 5L, keepaliveMisses = 3),
    )
    assertEquals(3, QualityMapping.MAX_KEEPALIVE_MISSES)
  }

  @Test
  fun missesWithUnknownRttAreAtMostFair() {
    assertEquals(
      Quality.FAIR,
      QualityMapping.compute(rttMs = 0L, jitterMs = 0L, keepaliveMisses = 1),
    )
    assertEquals(
      Quality.POOR,
      QualityMapping.compute(rttMs = 0L, jitterMs = 0L, keepaliveMisses = 3),
    )
  }
}
