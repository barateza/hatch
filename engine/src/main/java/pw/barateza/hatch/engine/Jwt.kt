package pw.barateza.hatch.engine

import java.util.Base64

/**
 * Parsing helpers for Cloudflare Access `CF_Authorization` JWTs.
 *
 * Only the (public) payload is decoded: the prototype needs `exp` to decide whether a cached
 * token is still usable. The signature is never verified locally — the tunnel endpoint is the
 * authority and rejects bad tokens on the WebSocket upgrade.
 */
object Jwt {

  /**
   * Returns the `exp` claim as epoch milliseconds, or `null` when the token is not a JWT with a
   * numeric `exp` claim. Never throws: malformed input is treated as "no usable expiry".
   */
  fun expiryMillis(jwt: String?): Long? {
    if (jwt.isNullOrBlank()) return null
    val parts = jwt.trim().split('.')
    if (parts.size < 2) return null

    val payloadBytes =
      try {
        Base64.getUrlDecoder().decode(parts[1])
      } catch (_: IllegalArgumentException) {
        return null
      }

    val payload =
      try {
        String(payloadBytes, Charsets.UTF_8)
      } catch (_: Exception) {
        return null
      }

    val seconds = extractExpSeconds(payload) ?: return null
    if (seconds <= 0L) return null
    if (seconds > Long.MAX_VALUE / 1000L) return null
    return seconds * 1000L
  }

  /**
   * True when [jwt] parses and stays valid for more than [skewMillis] from [nowMillis].
   * The skew avoids racing the server with a token that expires while we open the socket.
   */
  fun isUsable(jwt: String?, nowMillis: Long, skewMillis: Long = 60_000L): Boolean {
    val expiresAt = expiryMillis(jwt) ?: return false
    return expiresAt > nowMillis + skewMillis
  }

  /**
   * Extracts a top-level numeric `exp` claim without pulling in an Android `org.json` dependency
   * (which is not available on the plain JVM unit-test runtime). Nested `exp` keys are skipped.
   */
  internal fun extractExpSeconds(json: String): Long? {
    var index = 0
    val length = json.length

    fun skipWhitespace() {
      while (index < length && json[index].isWhitespace()) index++
    }

    fun parseString(): String? {
      if (index >= length || json[index] != '"') return null
      index++
      val builder = StringBuilder()
      while (index < length) {
        val c = json[index]
        index++
        when (c) {
          '"' -> return builder.toString()
          '\\' -> {
            if (index >= length) return null
            val escape = json[index]
            index++
            when (escape) {
              '"', '\\', '/' -> builder.append(escape)
              'b' -> builder.append('\b')
              'f' -> builder.append('\u000C')
              'n' -> builder.append('\n')
              'r' -> builder.append('\r')
              't' -> builder.append('\t')
              'u' -> {
                if (index + 4 > length) return null
                val code = json.substring(index, index + 4).toIntOrNull(16) ?: return null
                builder.append(code.toChar())
                index += 4
              }
              else -> return null
            }
          }
          else -> builder.append(c)
        }
      }
      return null
    }

    fun skipComposite(open: Char, close: Char): Boolean {
      var depth = 0
      while (index < length) {
        val c = json[index]
        if (c == '"') {
          if (parseString() == null) return false
          continue
        }
        index++
        if (c == open) depth += 1
        if (c == close) {
          depth -= 1
          if (depth == 0) return true
        }
      }
      return false
    }

    fun consume(literal: String): Boolean {
      if (!json.startsWith(literal, index)) return false
      index += literal.length
      return true
    }

    fun skipValue(): Boolean {
      skipWhitespace()
      if (index >= length) return false
      return when (json[index]) {
        '"' -> parseString() != null
        '{' -> skipComposite('{', '}')
        '[' -> skipComposite('[', ']')
        't' -> consume("true")
        'f' -> consume("false")
        'n' -> consume("null")
        else -> {
          val start = index
          if (json[index] == '-') index++
          while (index < length) {
            val c = json[index]
            if (!(c.isDigit() || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-')) break
            index++
          }
          index > start
        }
      }
    }

    skipWhitespace()
    if (index >= length || json[index] != '{') return null
    index += 1

    while (true) {
      skipWhitespace()
      if (index >= length) return null
      if (json[index] == '}') return null

      val key = parseString() ?: return null
      skipWhitespace()
      if (index >= length || json[index] != ':') return null
      index += 1
      skipWhitespace()

      if (key == "exp") {
        val start = index
        if (index < length && json[index] == '-') index++
        while (index < length && json[index].isDigit()) index++
        if (index == start) return null
        // JWT `exp` is an integer number; reject fractional/exponent forms like 1.5 or 1e9.
        if (index < length && (json[index] == '.' || json[index] == 'e' || json[index] == 'E')) {
          return null
        }
        return json.substring(start, index).toLongOrNull()
      }

      if (!skipValue()) return null
      skipWhitespace()
      if (index >= length) return null
      when (json[index]) {
        ',' -> index += 1
        '}' -> return null
        else -> return null
      }
    }
  }
}
