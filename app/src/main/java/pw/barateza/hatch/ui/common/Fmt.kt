package pw.barateza.hatch.ui.common

import java.util.Locale
import pw.barateza.hatch.model.Quality

/** Small formatting helpers shared by every variant. */
object Fmt {
  fun duration(millis: Long): String {
    if (millis <= 0L) return "0s"
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return when {
      h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
      m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
      else -> "${s}s"
    }
  }

  fun clockDuration(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) {
      String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    } else {
      String.format(Locale.US, "%02d:%02d", m, s)
    }
  }

  fun bytes(value: Long): String {
    if (value < 0) return "—"
    if (value < 1024) return "$value B"
    val units = arrayOf("kB", "MB", "GB", "TB")
    var v = value.toDouble()
    var unit = -1
    while (v >= 1024 && unit < units.lastIndex) {
      v /= 1024
      unit++
    }
    return String.format(Locale.US, if (v >= 100) "%.0f %s" else "%.1f %s", v, units[unit])
  }

  fun rate(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0L) return "0"
    return "${bytes(bytesPerSecond)}/s"
  }

  fun rtt(millis: Long): String = if (millis <= 0L) "—" else "$millis ms"

  fun countdown(millis: Long?): String {
    if (millis == null) return "—"
    val seconds = (millis / 1000.0).let { kotlin.math.ceil(it).toLong() }.coerceAtLeast(0)
    return if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"
  }

  fun qualityLabel(quality: Quality): String =
    when (quality) {
      Quality.EXCELLENT -> "Excellent"
      Quality.GOOD -> "Good"
      Quality.FAIR -> "Fair"
      Quality.POOR -> "Poor"
      Quality.OFFLINE -> "Offline"
      Quality.UNKNOWN -> "Measuring"
    }

  fun qualityBars(quality: Quality): Int =
    when (quality) {
      Quality.EXCELLENT -> 4
      Quality.GOOD -> 3
      Quality.FAIR -> 2
      Quality.POOR -> 1
      Quality.OFFLINE, Quality.UNKNOWN -> 0
    }

  fun shortHost(host: String): String = host.removeSuffix(".barateza.pw")
}
