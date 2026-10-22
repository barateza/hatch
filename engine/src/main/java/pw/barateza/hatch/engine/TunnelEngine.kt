package pw.barateza.hatch.engine

import kotlinx.coroutines.flow.StateFlow
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.model.TunnelState

/**
 * Contract implemented by the real engine (`RealTunnelEngine`) and by the
 * in-app prototype simulator (`DemoTunnelEngine`).
 *
 * Implementations are expected to be process-scoped singletons: [start] is
 * idempotent, and [stop] cancels any in-flight retry loop.
 */
interface TunnelEngine {
  val state: StateFlow<TunnelState>

  fun start(config: TunnelConfig)

  fun stop()

  /**
   * Called by the UI after a Cloudflare Access browser login succeeded.
   * [jwt] is the raw `CF_Authorization` JWT.
   */
  fun submitAccessToken(jwt: String)
}
