package pw.barateza.hatch.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import pw.barateza.hatch.model.SshAuthMode
import pw.barateza.hatch.model.TunnelConfig
import pw.barateza.hatch.theme.HatchPalette
import pw.barateza.hatch.theme.HatchTheme
import pw.barateza.hatch.theme.Inter
import pw.barateza.hatch.theme.JetBrainsMono
import pw.barateza.hatch.theme.SpaceGrotesk

@Composable
fun SettingsScreen(
  config: TunnelConfig,
  hostKeyFingerprint: String?,
  onForgetHostKey: () -> Unit,
  onSave: (TunnelConfig) -> Unit,
  onClose: () -> Unit,
) {
  var draft by remember(config) { mutableStateOf(config) }
  var showSecrets by remember { mutableStateOf(false) }

  var localPort by remember(config) { mutableStateOf(config.localPort.toString()) }
  var remoteHost by remember(config) { mutableStateOf(config.remoteHost) }
  var remotePort by remember(config) { mutableStateOf(config.remotePort.toString()) }

  val context = LocalContext.current

  val parsePort: (String) -> Int? = { it.toIntOrNull()?.takeIf { p -> p in 1..65535 } }
  val valid =
    draft.host.isNotBlank() &&
      draft.sshUser.isNotBlank() &&
      parsePort(localPort) != null &&
      parsePort(remotePort) != null &&
      remoteHost.isNotBlank()

  HatchTheme(darkTheme = true) {
    Surface(modifier = Modifier.fillMaxSize(), color = HatchPalette.Ink) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 22.dp),
      ) {
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(
              "Settings",
              color = Color(0xFFF2F5FA),
              fontFamily = SpaceGrotesk,
              fontWeight = FontWeight.W600,
              fontSize = 34.sp,
              letterSpacing = (-1).sp,
            )
            Text(
              "Tactile · changes apply on next connect",
              color = HatchPalette.Fog,
              fontFamily = Inter,
              fontSize = 12.sp,
            )
          }
          IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close settings", tint = HatchPalette.Fog)
          }
        }

        Spacer(Modifier.height(18.dp))

        Section("ENDPOINT")
        Field(
          label = "SSH host",
          value = draft.host,
          onChange = { draft = draft.copy(host = it.trim()) },
          placeholder = "ssh.example.com",
        )
        Field(
          label = "SSH user",
          value = draft.sshUser,
          onChange = { draft = draft.copy(sshUser = it.trim()) },
          placeholder = "user",
        )
        ToggleRow(
          title = "Cloudflare Access",
          subtitle = "Authenticate through the browser, then carry SSH over wss:// (port 443).",
          checked = draft.useCloudflareAccess,
          onChange = { draft = draft.copy(useCloudflareAccess = it) },
        )

        Section("LOCAL FORWARD")
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Field(
            label = "Listen on port",
            value = localPort,
            onChange = { localPort = it.filter(Char::isDigit).take(5) },
            placeholder = "3080",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
          )
          Field(
            label = "Remote host",
            value = remoteHost,
            onChange = { remoteHost = it.trim() },
            placeholder = "127.0.0.1",
            modifier = Modifier.weight(1f),
          )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Field(
            label = "Remote port",
            value = remotePort,
            onChange = { remotePort = it.filter(Char::isDigit).take(5) },
            placeholder = "3080",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
          )
          Spacer(Modifier.weight(1f))
        }
        Text(
          "Binds 127.0.0.1:$localPort only. Open http://127.0.0.1:$localPort while the tunnel is live.",
          color = HatchPalette.Fog,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          lineHeight = 15.sp,
        )

        Section("HARNESS")
        Field(
          label = "Harness token URL (optional)",
          value = draft.harnessUrl,
          onChange = { draft = draft.copy(harnessUrl = it.trim()) },
          placeholder = "http://127.0.0.1:3080/?token=…",
          keyboardType = KeyboardType.Uri,
        )
        Text(
          "When set, VISIT THE HATCH opens this URL. dsh web prints a fresh token at startup; " +
            "the login cookie lasts 30 days, after which a new token URL is needed. Stored " +
            "encrypted with an Android Keystore key.",
          color = HatchPalette.Fog,
          fontFamily = Inter,
          fontSize = 10.sp,
          lineHeight = 15.sp,
        )

        Section("SSH AUTHENTICATION")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          AuthChip("Private key", draft.authMode == SshAuthMode.KEY, Modifier.weight(1f)) {
            draft = draft.copy(authMode = SshAuthMode.KEY)
          }
          AuthChip("Password", draft.authMode == SshAuthMode.PASSWORD, Modifier.weight(1f)) {
            draft = draft.copy(authMode = SshAuthMode.PASSWORD)
          }
        }
        Spacer(Modifier.height(10.dp))
        if (draft.authMode == SshAuthMode.KEY) {
          val keyMask = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation()
          OutlinedTextField(
            value = draft.privateKey,
            onValueChange = { draft = draft.copy(privateKey = it) },
            modifier = Modifier.fillMaxWidth().height(130.dp),
            label = { Text("OpenSSH / PEM private key") },
            placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----", fontSize = 11.sp) },
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, lineHeight = 15.sp),
            visualTransformation = keyMask,
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          )
          Spacer(Modifier.height(10.dp))
          Field(
            label = "Key passphrase (optional)",
            value = draft.keyPassphrase,
            onChange = { draft = draft.copy(keyPassphrase = it) },
            placeholder = "••••••••",
            visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
          )
          Text(
            "Paste an RSA or ECDSA key. Ed25519 keys depend on the device's crypto providers — a real build must bundle a supported provider.",
            color = HatchPalette.Fog,
            fontFamily = Inter,
            fontSize = 10.sp,
            lineHeight = 15.sp,
          )
        } else {
          Field(
            label = "SSH password",
            value = draft.password,
            onChange = { draft = draft.copy(password = it) },
            placeholder = "••••••••",
            visualTransformation = if (showSecrets) VisualTransformation.None else PasswordVisualTransformation(),
          )
        }
        ToggleRow(
          title = "Reveal secrets",
          subtitle = "Shows the key or password on screen. Screenshots are blocked while settings are open.",
          checked = showSecrets,
          onChange = { showSecrets = it },
        )

        Section("SSH HOST KEY")
        Text(
          hostKeyFingerprint ?: "No host key pinned yet. The next connection will show a fingerprint for approval.",
          color = if (hostKeyFingerprint == null) HatchPalette.Fog else HatchPalette.Mint,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
          lineHeight = 15.sp,
        )
        if (hostKeyFingerprint != null) {
          Spacer(Modifier.height(8.dp))
          KeepAliveRow(
            title = "Forget pinned key",
            subtitle = "The next connection will ask you to approve the host again. Use only after a planned server rekey.",
            action = "FORGET",
            onClick = onForgetHostKey,
          )
        }

        Section("RESILIENCE")
        StepperRow(
          title = "Max retries",
          subtitle = "0 means retry forever.",
          value = draft.maxRetries,
          range = 0..20,
          onChange = { draft = draft.copy(maxRetries = it) },
        )
        StepperRow(
          title = "Keepalive",
          subtitle = "Seconds between SSH probes.",
          value = draft.keepAliveSeconds,
          range = 5..120,
          step = 5,
          onChange = { draft = draft.copy(keepAliveSeconds = it) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
          Field(
            label = "Base delay (ms)",
            value = draft.baseDelayMs.toString(),
            onChange = { v -> v.toLongOrNull()?.let { draft = draft.copy(baseDelayMs = it.coerceIn(200, 60_000)) } },
            placeholder = "1200",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
          )
          Field(
            label = "Max delay (ms)",
            value = draft.maxDelayMs.toString(),
            onChange = { v -> v.toLongOrNull()?.let { draft = draft.copy(maxDelayMs = it.coerceIn(1_000, 600_000)) } },
            placeholder = "45000",
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
          )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            "Jitter",
            color = Color(0xFFD7DEE9),
            fontFamily = Inter,
            fontSize = 13.sp,
            modifier = Modifier.width(70.dp),
          )
          Slider(
            value = draft.jitter,
            onValueChange = { draft = draft.copy(jitter = it) },
            valueRange = 0f..0.8f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = HatchPalette.Mint, activeTrackColor = HatchPalette.Mint),
          )
          Text(
            "${(draft.jitter * 100).toInt()}%",
            color = HatchPalette.Mint,
            fontFamily = JetBrainsMono,
            fontSize = 12.sp,
            modifier = Modifier.width(44.dp),
          )
        }
        Text(
          "Delay curve: ${curvePreview(draft)}",
          color = HatchPalette.Fog,
          fontFamily = JetBrainsMono,
          fontSize = 10.sp,
        )
        ToggleRow(
          title = "Adaptive link tuning",
          subtitle = "Shorten keepalive and extend timeouts when RTT, jitter or loss degrade.",
          checked = draft.adaptive,
          onChange = { draft = draft.copy(adaptive = it) },
        )

        Section("KEEP ALIVE")
        KeepAliveRow(
          title = "Ongoing notification",
          subtitle = "Shows the tunnel state and a Disconnect action. Android hides the foreground service without it.",
          action = "ALLOW",
          // The runtime prompt happens automatically on first real connect. This button goes to
          // app notification settings, which also works when the permission was denied twice.
          onClick = { openNotificationSettings(context) },
        )
        KeepAliveRow(
          title = "Battery optimization",
          subtitle = "Allow Hatch to run unrestricted. Some OEMs also require an app-level battery setting.",
          action = "ALLOW",
          onClick = { requestIgnoreBatteryOptimizations(context) },
        )
        Text(
          "Hatch runs a foreground service with a partial wake lock and reconnects after being " +
            "killed. Extreme battery saver or an OEM battery manager can still suspend the radio.",
          color = HatchPalette.Fog,
          fontFamily = Inter,
          fontSize = 10.sp,
          lineHeight = 15.sp,
        )
        Spacer(Modifier.height(10.dp))

        Spacer(Modifier.height(26.dp))
        Button(
          onClick = {
            val lp = parsePort(localPort) ?: config.localPort
            val rp = parsePort(remotePort) ?: config.remotePort
            onSave(
              draft.copy(
                localPort = lp,
                remotePort = rp,
                remoteHost = remoteHost.trim(),
                demoMode = false,
              ),
            )
          },
          enabled = valid,
          modifier = Modifier.fillMaxWidth().height(54.dp),
          shape = RoundedCornerShape(18.dp),
          colors = ButtonDefaults.buttonColors(containerColor = HatchPalette.Mint, contentColor = Color(0xFF04130E)),
        ) {
          Text("Save and close", fontFamily = Inter, fontWeight = FontWeight.W600, fontSize = 15.sp)
        }
        Text(
          "Hatch 0.3.0 · credentials and tokens encrypted with Android Keystore; host key pinned.",
          color = HatchPalette.Fog.copy(alpha = 0.7f),
          fontFamily = JetBrainsMono,
          fontSize = 9.sp,
          modifier = Modifier.padding(top = 10.dp, bottom = 26.dp),
        )
      }
    }
  }
}

@Composable
private fun Section(title: String) {
  Text(
    title,
    color = HatchPalette.Mint.copy(alpha = 0.7f),
    fontFamily = JetBrainsMono,
    fontSize = 10.sp,
    letterSpacing = 1.6.sp,
    modifier = Modifier.padding(top = 26.dp, bottom = 10.dp),
  )
}

@Composable
private fun Field(
  label: String,
  value: String,
  onChange: (String) -> Unit,
  placeholder: String,
  modifier: Modifier = Modifier,
  keyboardType: KeyboardType = KeyboardType.Text,
  visualTransformation: VisualTransformation = VisualTransformation.None,
) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    modifier = modifier.fillMaxWidth(),
    label = { Text(label, fontSize = 11.sp) },
    placeholder = { Text(placeholder, fontSize = 12.sp) },
    singleLine = true,
    shape = RoundedCornerShape(14.dp),
    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = Inter, fontSize = 14.sp),
    visualTransformation = visualTransformation,
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    colors = fieldColors(),
  )
  Spacer(Modifier.height(10.dp))
}

@Composable
private fun fieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color(0xFFF2F5FA),
    unfocusedTextColor = Color(0xFFD7DEE9),
    focusedBorderColor = HatchPalette.Mint.copy(alpha = 0.8f),
    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
    focusedLabelColor = HatchPalette.Mint,
    unfocusedLabelColor = HatchPalette.Fog,
    cursorColor = HatchPalette.Mint,
    focusedContainerColor = Color.White.copy(alpha = 0.03f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.02f),
  )

@Composable
private fun ToggleRow(
  title: String,
  subtitle: String,
  checked: Boolean,
  onChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, color = Color(0xFFE7ECF4), fontFamily = Inter, fontWeight = FontWeight.W500, fontSize = 14.sp)
      Text(subtitle, color = HatchPalette.Fog, fontFamily = Inter, fontSize = 11.sp, lineHeight = 15.sp)
    }
    Switch(
      checked = checked,
      onCheckedChange = onChange,
      colors =
        SwitchDefaults.colors(
          checkedThumbColor = Color(0xFF04130E),
          checkedTrackColor = HatchPalette.Mint,
          uncheckedThumbColor = HatchPalette.Fog,
          uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
          uncheckedBorderColor = Color.White.copy(alpha = 0.14f),
        ),
    )
  }
}

@Composable
private fun AuthChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Box(
    modifier =
      modifier
        .clickable(onClick = onClick)
        .background(
          if (selected) HatchPalette.Mint.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.03f),
          RoundedCornerShape(12.dp),
        )
        .border(
          1.dp,
          if (selected) HatchPalette.Mint.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.10f),
          RoundedCornerShape(12.dp),
        )
        .padding(vertical = 11.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      label,
      color = if (selected) HatchPalette.Mint else Color(0xFFD7DEE9),
      fontFamily = Inter,
      fontSize = 12.sp,
      fontWeight = if (selected) FontWeight.W600 else FontWeight.W400,
    )
  }
}

@Composable
private fun StepperRow(
  title: String,
  subtitle: String,
  value: Int,
  range: IntRange,
  step: Int = 1,
  onChange: (Int) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, color = Color(0xFFE7ECF4), fontFamily = Inter, fontSize = 14.sp)
      Text(subtitle, color = HatchPalette.Fog, fontFamily = Inter, fontSize = 11.sp)
    }
    StepperButton("−") { onChange((value - step).coerceIn(range.first, range.last)) }
    Text(
      value.toString(),
      color = HatchPalette.Mint,
      fontFamily = JetBrainsMono,
      fontSize = 14.sp,
      modifier = Modifier.width(46.dp),
      textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
    StepperButton("+") { onChange((value + step).coerceIn(range.first, range.last)) }
  }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
  Box(
    modifier =
      Modifier
        .width(34.dp)
        .height(34.dp)
        .clickable(onClick = onClick)
        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(10.dp))
        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = Color(0xFFE7ECF4), fontFamily = Inter, fontSize = 16.sp)
  }
}

private fun curvePreview(config: TunnelConfig): String {
  val points = (1..4).map { attempt ->
    val raw = config.baseDelayMs * (1L shl (attempt - 1))
    val ms = raw.coerceAtMost(config.maxDelayMs)
    String.format(Locale.US, "%.1fs", ms / 1000f)
  }
  return points.joinToString(" → ")
}

@Composable
private fun KeepAliveRow(
  title: String,
  subtitle: String,
  action: String,
  onClick: () -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, color = Color(0xFFE7ECF4), fontFamily = Inter, fontWeight = FontWeight.W500, fontSize = 14.sp)
      Text(subtitle, color = HatchPalette.Fog, fontFamily = Inter, fontSize = 11.sp, lineHeight = 15.sp)
    }
    Box(
      modifier =
        Modifier
          .height(38.dp)
          .clip(RoundedCornerShape(50))
          .background(HatchPalette.Mint.copy(alpha = 0.12f))
          .border(1.dp, HatchPalette.Mint.copy(alpha = 0.35f), RoundedCornerShape(50))
          .clickable(onClick = onClick)
          .padding(horizontal = 14.dp),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        action,
        color = HatchPalette.Mint,
        fontFamily = JetBrainsMono,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
      )
    }
  }
}

private fun openNotificationSettings(context: Context) {
  val intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
      .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(intent) }.onFailure { openAppSettings(context) }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
  val power = context.getSystemService(PowerManager::class.java)
  if (power?.isIgnoringBatteryOptimizations(context.packageName) == true) {
    openAppSettings(context)
    return
  }
  val request =
    Intent(
      Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
      Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(request) }.onFailure { openAppSettings(context) }
}

private fun openAppSettings(context: Context) {
  val details =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.parse("package:${context.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(details) }
}
