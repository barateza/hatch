package pw.barateza.hatch.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pw.barateza.hatch.model.HostKeyChallenge
import pw.barateza.hatch.runtime.tailscale.TailscaleCheck
import pw.barateza.hatch.runtime.tailscale.TailscaleState
import pw.barateza.hatch.theme.InstrumentSerif
import pw.barateza.hatch.theme.Inter
import pw.barateza.hatch.theme.JetBrainsMono

private val Paper = Color(0xFFF8F1E4)
private val Ink = Color(0xFF2B261E)
private val ClayInk = Color(0xFF8E4A2F)

/**
 * First-use host-key approval. This is deliberately a blocking dialog: the engine will not send
 * credentials until the user confirms the fingerprint.
 */
@Composable
fun HostKeyDialog(
  challenge: HostKeyChallenge,
  onTrust: () -> Unit,
  onCancel: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onCancel,
    containerColor = Paper,
    titleContentColor = Ink,
    textContentColor = Ink.copy(alpha = 0.78f),
    title = {
      Text(
        "A new host key",
        fontFamily = InstrumentSerif,
        fontSize = 26.sp,
        color = Ink,
      )
    },
    text = {
      Column {
        Text(
          "Hatch has not seen this host before. Compare the fingerprint with the server before trusting it.",
          fontFamily = Inter,
          fontSize = 13.sp,
          lineHeight = 18.sp,
        )
        Spacer(Modifier.height(12.dp))
        Text(
          challenge.host,
          fontFamily = JetBrainsMono,
          fontSize = 12.sp,
          fontWeight = FontWeight.W600,
          color = Ink,
        )
        Spacer(Modifier.height(6.dp))
        Text(
          challenge.fingerprint,
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          lineHeight = 17.sp,
          color = ClayInk,
        )
        Spacer(Modifier.height(10.dp))
        Text(
          "On the server: ssh-keygen -lf /etc/ssh/ssh_host_*_key.pub",
          fontFamily = JetBrainsMono,
          fontSize = 9.5.sp,
          lineHeight = 14.sp,
          color = Ink.copy(alpha = 0.55f),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onTrust,
        colors = ButtonDefaults.textButtonColors(contentColor = ClayInk),
      ) {
        Text("TRUST AND CONNECT", fontFamily = JetBrainsMono, fontSize = 10.sp, fontWeight = FontWeight.W700)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onCancel,
        colors = ButtonDefaults.textButtonColors(contentColor = Ink.copy(alpha = 0.6f)),
      ) {
        Text("CANCEL", fontFamily = JetBrainsMono, fontSize = 10.sp)
      }
    },
  )
}

/** Warn before spending a Tailscale-host connection attempt when the tailnet looks offline. */
@Composable
fun TailscaleDialog(
  check: TailscaleCheck,
  onOpenTailscale: () -> Unit,
  onConnectAnyway: () -> Unit,
  onDismiss: () -> Unit,
) {
  val title =
    when (check.state) {
      TailscaleState.APP_MISSING -> "Tailscale is not installed"
      TailscaleState.VPN_OFF -> "Tailscale is off"
      TailscaleState.DNS_FAILED -> "Tailscale can't see this host"
      TailscaleState.OTHER_VPN -> "A VPN is active"
      else -> "Check Tailscale"
    }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Paper,
    titleContentColor = Ink,
    textContentColor = Ink.copy(alpha = 0.78f),
    title = {
      Text(title, fontFamily = InstrumentSerif, fontSize = 26.sp, color = Ink)
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(check.message, fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp)
        Text(
          check.host,
          modifier = Modifier.padding(top = 2.dp),
          fontFamily = JetBrainsMono,
          fontSize = 11.sp,
          color = Ink.copy(alpha = 0.65f),
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = onConnectAnyway,
        colors = ButtonDefaults.textButtonColors(contentColor = Ink.copy(alpha = 0.72f)),
      ) {
        Text("CONNECT ANYWAY", fontFamily = JetBrainsMono, fontSize = 10.sp)
      }
    },
    dismissButton = {
      Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(
          onClick = onOpenTailscale,
          colors = ButtonDefaults.textButtonColors(contentColor = ClayInk),
        ) {
          Text(
            if (check.state == TailscaleState.APP_MISSING) "GET TAILSCALE" else "OPEN TAILSCALE",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            fontWeight = FontWeight.W700,
          )
        }
        TextButton(
          onClick = onDismiss,
          colors = ButtonDefaults.textButtonColors(contentColor = Ink.copy(alpha = 0.55f)),
        ) {
          Text("CANCEL", fontFamily = JetBrainsMono, fontSize = 10.sp)
        }
      }
    },
  )
}
