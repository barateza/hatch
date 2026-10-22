package pw.barateza.hatch.engine

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Exponential backoff with bounded jitter.
 *
 * The delay for a failed attempt is:
 *
 * ```
 * base = min(maxDelayMs, baseDelayMs * 2^(attempt - 1))
 * delay = base - random(0..1) * base * jitterFraction
 * ```
 *
 * `attempt` is 1-based: the first failure waits `baseDelayMs`, the second `2 * baseDelayMs`,
 * and so on. `jitterFraction` is clamped to `[0, 1]`, so the result is always inside
 * `[base * (1 - jitterFraction), base]`.
 */
object Backoff {

  /** The delay before jitter, i.e. `min(maxDelayMs, baseDelayMs * 2^(attempt - 1))`. */
  fun rawDelayMs(attempt: Int, baseDelayMs: Long, maxDelayMs: Long): Long {
    if (baseDelayMs <= 0L) return 0L
    val cap = maxDelayMs.coerceAtLeast(0L)
    if (cap == 0L) return 0L

    val exponent = (attempt.coerceAtLeast(1) - 1).coerceAtMost(62)
    var delay = baseDelayMs
    var step = 0
    while (step < exponent && delay < cap) {
      if (delay > Long.MAX_VALUE / 2L) {
        delay = cap
        break
      }
      delay *= 2L
      step += 1
    }
    return delay.coerceAtMost(cap)
  }

  /**
   * The jittered delay. [random] must return a value in `[0, 1]`; it is clamped defensively.
   */
  fun delayMs(
    attempt: Int,
    baseDelayMs: Long,
    maxDelayMs: Long,
    jitterFraction: Float,
    random: () -> Double = { Random.nextDouble() },
  ): Long {
    val base = rawDelayMs(attempt, baseDelayMs, maxDelayMs)
    if (base <= 0L) return 0L

    val jitter = jitterFraction.coerceIn(0f, 1f).toDouble()
    if (jitter <= 0.0) return base

    val randomValue = random().coerceIn(0.0, 1.0)
    val lowerBound = kotlin.math.ceil(base * (1.0 - jitter)).toLong().coerceAtMost(base)
    val delay = (base * (1.0 - jitter * randomValue)).roundToLong()
    return delay.coerceIn(lowerBound, base)
  }
}
