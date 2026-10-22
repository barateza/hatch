package pw.barateza.hatch.engine

import pw.barateza.hatch.model.Quality

/**
 * Maps link measurements to the coarse quality bucket shown in the UI.
 *
 * Thresholds (prototype, tuned for an interactive local-forward use case):
 *
 * | bucket    | round-trip time | jitter | keepalive misses |
 * |-----------|-----------------|--------|------------------|
 * | EXCELLENT | <= 80 ms        | <= 20  | 0                |
 * | GOOD      | <= 150 ms       | <= 40  | 0                |
 * | FAIR      | <= 300 ms       | <= 80  | 0                |
 * | POOR      | anything worse  |        | 3+               |
 *
 * Any missed keepalive/probe lowers a bucket that would otherwise be GOOD or EXCELLENT to FAIR.
 * With three consecutive misses the link is POOR and the engine tears the session down.
 * Unknown RTT (before the first sample) is UNKNOWN; no connectivity is OFFLINE.
 */
object QualityMapping {

  const val MAX_KEEPALIVE_MISSES: Int = 3
  const val EXCELLENT_MAX_RTT_MS: Long = 80L
  const val EXCELLENT_MAX_JITTER_MS: Long = 20L
  const val GOOD_MAX_RTT_MS: Long = 150L
  const val GOOD_MAX_JITTER_MS: Long = 40L
  const val FAIR_MAX_RTT_MS: Long = 300L
  const val FAIR_MAX_JITTER_MS: Long = 80L

  fun compute(
    rttMs: Long,
    jitterMs: Long,
    keepaliveMisses: Int,
    online: Boolean = true,
  ): Quality {
    if (!online) return Quality.OFFLINE
    if (keepaliveMisses >= MAX_KEEPALIVE_MISSES) return Quality.POOR

    if (rttMs <= 0L) {
      return if (keepaliveMisses > 0) Quality.FAIR else Quality.UNKNOWN
    }

    val base =
      when {
        rttMs <= EXCELLENT_MAX_RTT_MS && jitterMs <= EXCELLENT_MAX_JITTER_MS -> Quality.EXCELLENT
        rttMs <= GOOD_MAX_RTT_MS && jitterMs <= GOOD_MAX_JITTER_MS -> Quality.GOOD
        rttMs <= FAIR_MAX_RTT_MS && jitterMs <= FAIR_MAX_JITTER_MS -> Quality.FAIR
        else -> Quality.POOR
      }

    // A single missed keepalive is a warning sign, but not enough to declare the link POOR.
    val capped = if (keepaliveMisses > 0 && base.ordinal > Quality.FAIR.ordinal) Quality.FAIR else base
    return capped
  }
}
