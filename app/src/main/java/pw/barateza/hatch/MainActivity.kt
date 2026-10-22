package pw.barateza.hatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import pw.barateza.hatch.runtime.HatchRuntime
import pw.barateza.hatch.ui.HatchRoot
import pw.barateza.hatch.ui.prototype.PrototypeOverrides

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val overrides = if (BuildConfig.DEBUG) PrototypeOverrides.fromIntent(intent) else PrototypeOverrides()
    setContent {
      HatchRoot(
        controller = HatchRuntime.controller,
        overrides = overrides,
      )
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (BuildConfig.DEBUG) {
      HatchRuntime.peek()?.applyPrototype(PrototypeOverrides.fromIntent(intent))
    }
  }
}
