package pw.barateza.hatch.ui.auth

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.util.Base64
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import pw.barateza.hatch.theme.HatchPalette
import pw.barateza.hatch.theme.HatchTheme
import pw.barateza.hatch.theme.Inter
import pw.barateza.hatch.theme.JetBrainsMono
import pw.barateza.hatch.theme.SpaceGrotesk

/**
 * In-app "browser window" for Cloudflare Access.
 *
 * Access sets an HttpOnly `CF_Authorization` cookie on the protected hostname;
 * the WebView's CookieManager can read it (page JavaScript cannot), which is how
 * the token is captured without a custom redirect scheme.
 */
class AuthActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    val url = intent.getStringExtra(EXTRA_URL)
    val host = intent.getStringExtra(EXTRA_TARGET_HOST)
    if (url.isNullOrBlank() || host.isNullOrBlank()) {
      finish()
      return
    }
    val parsed = runCatching { Uri.parse(url) }.getOrNull()
    if (parsed?.scheme != "https" || parsed.host.isNullOrBlank()) {
      finish()
      return
    }

    // The Access cookie and the identity-provider session must not appear in screenshots or
    // recents thumbnails.
    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = false
      isAppearanceLightNavigationBars = false
    }
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      window.isStatusBarContrastEnforced = false
      window.isNavigationBarContrastEnforced = false
    }

    setContent {
      HatchTheme(darkTheme = true) {
        AuthScreen(
          url = url,
          targetHost = host,
          onCaptured = { jwt ->
            clearBrowserState()
            setResult(RESULT_OK, Intent().putExtra(EXTRA_JWT, jwt))
            finish()
          },
          onClose = {
            clearBrowserState()
            setResult(RESULT_CANCELED)
            finish()
          },
        )
      }
    }
  }

  private fun clearBrowserState() {
    runCatching {
      CookieManager.getInstance().removeAllCookies(null)
      CookieManager.getInstance().flush()
      WebStorage.getInstance().deleteAllData()
    }
  }

  companion object {
    const val EXTRA_URL = "url"
    const val EXTRA_TARGET_HOST = "targetHost"
    const val EXTRA_JWT = "jwt"

    fun intent(context: Context, url: String, targetHost: String): Intent =
      Intent(context, AuthActivity::class.java)
        .putExtra(EXTRA_URL, url)
        .putExtra(EXTRA_TARGET_HOST, targetHost)
  }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthScreen(
  url: String,
  targetHost: String,
  onCaptured: (String) -> Unit,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  var progress by remember { mutableIntStateOf(0) }
  var captured by remember { mutableStateOf(false) }

  val webView =
    remember {
      WebView(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        setBackgroundColor(AndroidColor.WHITE)
        settings.apply {
          javaScriptEnabled = true
          domStorageEnabled = true
          databaseEnabled = true
          allowFileAccess = false
          allowContentAccess = false
          allowFileAccessFromFileURLs = false
          allowUniversalAccessFromFileURLs = false
          javaScriptCanOpenWindowsAutomatically = false
          setSupportMultipleWindows(false)
          userAgentString = userAgentString + " Hatch/0.3"
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        webChromeClient =
          object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
              progress = newProgress
            }
          }
        webViewClient =
          object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
              request.url.scheme != "https"

            override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: SslError?) {
              // Never allow a user to click through a certificate error on an auth screen.
              handler?.cancel()
            }
          }
        loadUrl(url)
      }
    }

  DisposableEffect(webView) {
    onDispose {
      runCatching {
        webView.stopLoading()
        webView.destroy()
      }
    }
  }

  BackHandler {
    if (webView.canGoBack()) webView.goBack() else onClose()
  }

  // Poll the cookie jar for the Access session cookie. HttpOnly is fine here:
  // CookieManager is the browser's own store.
  LaunchedEffect(webView, targetHost) {
    while (isActive && !captured) {
      val jwt = readAccessCookie(targetHost)
      if (jwt != null) {
        captured = true
        onCaptured(jwt)
        break
      }
      delay(600)
    }
  }

  Column(
    modifier = Modifier.fillMaxSize().background(HatchPalette.Ink),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      IconButton(onClick = onClose) {
        Icon(Icons.Rounded.Close, contentDescription = "Close sign-in", tint = HatchPalette.Fog)
      }
      Column(Modifier.weight(1f).padding(start = 4.dp)) {
        Text(
          "Cloudflare Access",
          color = Color(0xFFF2F5FA),
          fontFamily = SpaceGrotesk,
          fontWeight = FontWeight.W600,
          fontSize = 20.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Rounded.Lock, contentDescription = null, tint = HatchPalette.Mint, modifier = Modifier.width(11.dp))
          Spacer(Modifier.width(4.dp))
          Text(
            targetHost,
            color = HatchPalette.Mint.copy(alpha = 0.85f),
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
          )
        }
      }
      Icon(Icons.Rounded.Cloud, contentDescription = null, tint = HatchPalette.Fog.copy(alpha = 0.6f))
    }

    // Thin progress line instead of a Material indicator so it stays quiet.
    Box(Modifier.fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.06f))) {
      Box(
        Modifier
          .fillMaxWidth(progress / 100f)
          .height(2.dp)
          .background(HatchPalette.Mint.copy(alpha = 0.8f)),
      )
    }

    AndroidView(
      factory = { webView },
      modifier =
        Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(horizontal = 10.dp)
          .padding(top = 10.dp)
          .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
          .background(Color.White),
    )

    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .padding(horizontal = 22.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        Modifier
          .width(6.dp)
          .height(6.dp)
          .background(if (captured) HatchPalette.Mint else HatchPalette.Amber, RoundedCornerShape(50)),
      )
      Text(
        if (captured) "Session captured — returning to Hatch…" else "A one-time code will arrive by email. Keep this window open until it completes.",
        color = if (captured) HatchPalette.Mint else HatchPalette.Fog,
        fontFamily = Inter,
        fontSize = 11.sp,
        lineHeight = 15.sp,
      )
    }
  }
}

private fun readAccessCookie(host: String): String? {
  val cookies = CookieManager.getInstance().getCookie("https://$host") ?: return null
  val token =
    cookies
      .split(';')
      .map { it.trim() }
      .firstOrNull { it.startsWith("CF_Authorization=") }
      ?.substringAfter('=')
      ?.trim()
      ?.takeIf { it.isNotEmpty() }
      ?: return null
  if (token.count { it == '.' } != 2) return null
  val expiry = jwtExpirySeconds(token)
  if (expiry != null && expiry <= System.currentTimeMillis() / 1000 + 30) return null
  return token
}

private fun jwtExpirySeconds(jwt: String): Long? =
  runCatching {
    val payload = jwt.split('.').getOrNull(1) ?: return null
    val decoded =
      String(
        Base64.decode(
          payload.replace('-', '+').replace('_', '/'),
          Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        ),
        Charsets.UTF_8,
      )
    Regex("\"exp\"\\s*:\\s*(\\d+)").find(decoded)?.groupValues?.getOrNull(1)?.toLongOrNull()
  }.getOrNull()
