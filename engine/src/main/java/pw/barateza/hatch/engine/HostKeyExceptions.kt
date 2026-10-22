package pw.barateza.hatch.engine

/** First contact with a host: there is no pinned key yet, so the user must approve it. */
class UnknownHostKeyException(
  val host: String,
  val fingerprint: String,
) : Exception("No pinned host key for $host ($fingerprint)")

/** A previously pinned host now presents a different key. Fail closed; never auto-accept. */
class HostKeyMismatchException(
  val host: String,
  val expected: String,
  val actual: String,
) : Exception("Host key for $host changed (expected $expected, got $actual)")
