package pw.barateza.hatch.runtime

/**
 * Redacts bearer credentials from anything copied off-device. The engine never logs tokens or
 * keys by design; this is defence in depth for server error text, URLs and future log lines.
 */
object DiagnosticsRedactor {
  private val jwt = Regex("eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}")
  private val privateKeyBlock =
    Regex("-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z0-9 ]*PRIVATE KEY-----")
  private val tokenQuery = Regex("(?i)([?&](?:token|cf_authorization|access_token)=)[^&\\s]+")
  private val cookie = Regex("(?i)(CF_Authorization=)[^;\\s]+")
  private val assignment = Regex("(?i)\\b(password|passphrase)\\s*[:=]\\s*\\S+")

  fun redact(input: String): String =
    input
      .replace(privateKeyBlock, "<redacted-private-key>")
      .replace(jwt, "<redacted-jwt>")
      .replace(tokenQuery, "$1<redacted>")
      .replace(cookie, "$1<redacted>")
      .replace(assignment, "$1=<redacted>")
}
