package pw.barateza.hatch

import android.app.Application
import pw.barateza.hatch.engine.RealTunnelEngine
import pw.barateza.hatch.runtime.HatchRuntime

class HatchApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    // Demo mode swaps in DemoTunnelEngine inside TunnelController; real mode
    // uses the SSH-over-Cloudflare-Access engine below.
    HatchRuntime.init(this) { context -> RealTunnelEngine(context) }
  }
}
