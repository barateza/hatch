package pw.barateza.hatch.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pw.barateza.hatch.BuildConfig
import pw.barateza.hatch.model.Phase
import pw.barateza.hatch.runtime.TunnelController
import pw.barateza.hatch.theme.HatchPalette
import pw.barateza.hatch.theme.HatchTheme
import pw.barateza.hatch.ui.auth.AuthActivity
import pw.barateza.hatch.ui.prototype.PrototypeOverrides
import pw.barateza.hatch.ui.security.HostKeyDialog
import pw.barateza.hatch.ui.security.TailscaleDialog
import pw.barateza.hatch.ui.settings.SettingsScreen
import pw.barateza.hatch.ui.variant.tactile.TactileVariant

@Composable
fun HatchRoot(
  controller: TunnelController,
  overrides: PrototypeOverrides,
) {
  // Prototype seeds are a debug-only tool. Release builds ignore the extras entirely.
  if (BuildConfig.DEBUG) {
    LaunchedEffect(Unit) { controller.applyPrototype(overrides) }
  }

  val ui by controller.ui.collectAsStateWithLifecycle()
  val settingsOpen by controller.settingsOpen.collectAsStateWithLifecycle()
  val context = LocalContext.current

  // Tactile is the settled direction. Keep dark status/nav icons on its cream paper; the
  // settings sheet and the auth WebView are dark and need light icons.
  val rootView = LocalView.current
  SideEffect {
    val activity = rootView.context as? Activity ?: return@SideEffect
    val darkIcons = !settingsOpen
    val bars = WindowCompat.getInsetsController(activity.window, rootView)
    bars.isAppearanceLightStatusBars = darkIcons
    bars.isAppearanceLightNavigationBars = darkIcons
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      activity.window.isStatusBarContrastEnforced = false
      activity.window.isNavigationBarContrastEnforced = false
    }
    @Suppress("DEPRECATION")
    activity.window.navigationBarColor = android.graphics.Color.TRANSPARENT
    @Suppress("DEPRECATION")
    activity.window.statusBarColor = android.graphics.Color.TRANSPARENT
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      activity.window.navigationBarDividerColor = android.graphics.Color.TRANSPARENT
    }
    // The settings sheet contains the SSH key, passphrase and harness token URL.
    if (settingsOpen) {
      activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
      activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
  }

  val authLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
      if (result.resultCode == Activity.RESULT_OK) {
        result.data?.getStringExtra(AuthActivity.EXTRA_JWT)?.let(controller::onAccessToken)
      }
    }

  // Android 13+ hides a foreground-service notification unless POST_NOTIFICATIONS is granted.
  // Ask once, at the moment a real connection starts (not during onboarding).
  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      // The service may already be running (and its first startForeground happened while the
      // permission was still denied, so the notification was dropped). Re-post it now.
      if (granted) controller.refreshForegroundNotification()
    }
  var askedNotifications by remember { mutableStateOf(false) }
  LaunchedEffect(ui.tunnel.phase, ui.isDemo) {
    val needsPermission =
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    if (!ui.isDemo && !askedNotifications && needsPermission && ui.tunnel.phase == Phase.CHECKING) {
      askedNotifications = true
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
  }

  // Auto-open the Access flow once per challenge. In the prototype seed we stay on the state
  // screen so it can be screenshotted; the in-app CTA can still open the real WebView.
  var launchedFor by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(ui.tunnel.authUrl, ui.isDemo) {
    val url = ui.tunnel.authUrl
    if (!ui.isDemo && url != null && launchedFor != url) {
      launchedFor = url
      authLauncher.launch(AuthActivity.intent(context, url, ui.config.host))
    }
  }

  val latestUi by rememberUpdatedState(ui)
  val actions =
    remember(controller, authLauncher) {
      object : TunnelActions by controller {
        override fun reauthenticate() {
          val url = latestUi.tunnel.authUrl ?: "https://${latestUi.config.host}/"
          authLauncher.launch(AuthActivity.intent(context, url, latestUi.config.host))
        }
      }
    }

  HatchTheme(darkTheme = true) {
    Box(modifier = Modifier.fillMaxSize().background(HatchPalette.Ink)) {
      TactileVariant(ui, actions, Modifier.fillMaxSize())

      AnimatedVisibility(
        visible = settingsOpen,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
      ) {
        SettingsScreen(
          config = ui.config,
          hostKeyFingerprint = controller.hostKeyFingerprint(),
          onForgetHostKey = controller::forgetHostKey,
          onSave = { updated ->
            controller.updateConfig(updated)
            controller.closeSettings()
          },
          onClose = controller::closeSettings,
        )
      }

      ui.tailscalePrompt?.let { check ->
        TailscaleDialog(
          check = check,
          onOpenTailscale = controller::openTailscale,
          onConnectAnyway = controller::connectAnyway,
          onDismiss = controller::dismissTailscalePrompt,
        )
      }

      ui.tunnel.hostKeyChallenge?.let { challenge ->
        HostKeyDialog(
          challenge = challenge,
          onTrust = controller::trustHostKey,
          onCancel = controller::cancelHostKey,
        )
      }
    }
  }
}
