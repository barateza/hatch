# Hatch threat model (0.3.0 / P0 baseline)

Scope: the Android app, its local `127.0.0.1` listener, the Cloudflare Access sign-in WebView, the
SSH-over-WebSocket transport, and the credentials at rest. The remote service and Cloudflare Access
policy are operated by the user; the SSH server is assumed trusted after its host key is pinned.

## Assets

| Asset | Where it lives | Why it matters |
|---|---|---|
| SSH private key + passphrase | Android Keystore AES-GCM ciphertext in app prefs | Full SSH access to the user's server |
| SSH password | Same | Same |
| Cloudflare Access JWT (`CF_Authorization`) | Encrypted per-host cache | Reaches the protected SSH endpoint; ~24 h lifetime |
| Forwarded-service token URL | Encrypted preference | Grants access to the forwarded web UI |
| Pinned SSH host-key fingerprint | Plain preference | Detects server substitution; not secret, but integrity-critical |
| Tunnel traffic | Raw SSH inside WebSocket | The forwarded service |
| Host key approval | In-memory challenge + user decision | Prevents first-contact MITM |

## Trust boundaries

1. **App sandbox** — other apps cannot read Hatch's private files on a non-rooted device.
2. **Android Keystore** — the AES key is non-exportable; copying prefs yields ciphertext only.
3. **Auth WebView** — a browser process inside the app; it renders the Cloudflare Access page and
   can see cookies. Hatch only reads `CF_Authorization` from the app's cookie jar.
4. **Localhost listener** — `127.0.0.1:<localPort>` is reachable by *any* app on the phone. The
   only application-layer gate is the service token (and whatever the forwarded service enforces).
5. **Cloudflare edge** — terminates TLS and enforces Access before forwarding the WebSocket.
6. **SSH server** — authenticated by the pinned host key and the user's SSH credential.
7. **Tailscale, if used** — an external VPN app; Hatch can detect a VPN/MagicDNS but cannot inspect
   its identity or tailnet membership.

## Adversaries and abuse paths

| Adversary | Capability | Mitigation in 0.3.0 | Residual risk |
|---|---|---|---|
| Malicious app on the phone | Connect to `127.0.0.1:3080`; read clipboard? | Listener is loopback-only; service token required; diagnostics redacted; no exported service | Any app that obtains the service token can use the forwarded service; the token is shown in Settings and may be shoulder-surfed |
| Network attacker / MITM | Intercept TLS or impersonate SSH server | TLS verification; first-use host-key approval; fail-closed on fingerprint change; no `acceptAnyHostKey` | First connection is TOFU; a user who trusts a wrong fingerprint is compromised. No certificate pinning |
| Malicious/compromised login page | Run JS in the auth WebView, navigate to `file:`/`intent:` | HTTPS-only navigation; SSL errors cancel; file/content access off; no JS bridge; cookies cleared after capture; `FLAG_SECURE` | A compromised `cloudflareaccess.com`/identity provider remains trusted; third-party cookies enabled for the Access flow |
| Lost/stolen unlocked phone | Open Hatch, read saved token/key in Settings | Keystore-encrypted at rest; `FLAG_SECURE` on settings/auth; no backup (`allowBackup=false`) | No biometric/PIN gate; an unlocked phone can still connect and view Settings |
| Local backup/adb | Pull app data | `allowBackup=false`; Keystore key non-exportable | Rooted device or `adb backup` on an old OS can expose decrypted data while running |
| Log/crash exfiltration | Clipboard diagnostics, logcat | Diagnostics redacts JWT/token/password/private key; engine never logs secrets | A future log line with a new secret shape could bypass the regexes; review additions |
| Server rekey / DNS hijack | Present a different host key | Mismatch is fatal; user must explicitly forget the pin after verifying out-of-band | A user who blindly forgets the pin defeats the protection |
| Tailscale misconfiguration | Host is `.ts.net`/100.64/10 but VPN is down | Preflight warns; offers Open Tailscale / Connect anyway | Detection is heuristic: a custom tailnet name or another VPN can be misclassified; "Connect anyway" is always available |
| Supply chain | Malicious dependency / update | Pinned versions; signed release; R8; OSS notices | No SBOM/signature verification in this prototype baseline |

## Explicit non-goals (P1/P2)

- No root/anti-tamper protection; a rooted device can defeat app sandboxing.
- No hardware-backed user authentication/biometric gate for the SSH key.
- No certificate pinning for Cloudflare (would break rotation).
- No protection against a compromised Cloudflare account or SSH server.
- No Play policy compliance yet (`specialUse` FGS and battery-optimization exemption need
  declarations; see `docs/release.md`).
- No multi-user/profile isolation; one configured endpoint.

## Security invariants to preserve

1. A new host is never auto-trusted; a changed host key never auto-reconnects.
2. No plaintext SSH key, passphrase, password, Access JWT, or service token in preferences or logs.
3. Auth WebView accepts only `https://` and cancels TLS errors.
4. Diagnostics copied off-device pass through `DiagnosticsRedactor`.
5. Demo/prototype deep links are unreachable in release builds.
6. Release builds are signed and minified; debug keys are not used for release.
