package pw.barateza.hatch.runtime

import android.content.Context
import pw.barateza.hatch.engine.TunnelEngine

/**
 * Process-scoped service locator. A shipping app would use DI; the prototype
 * keeps it to one object so the foreground service and Compose UI share the
 * same engine instance.
 */
object HatchRuntime {
  private var instance: TunnelController? = null

  fun init(context: Context, engineFactory: (Context) -> TunnelEngine) {
    if (instance == null) {
      instance = TunnelController(context.applicationContext, engineFactory)
    }
  }

  val controller: TunnelController
    get() = instance ?: error("HatchRuntime.init() was not called")

  fun peek(): TunnelController? = instance
}
