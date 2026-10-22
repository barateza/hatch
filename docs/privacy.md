# Privacy

Hatch is a local SSH client. It does not include analytics, advertising, crash reporting, or a
remote configuration service.

## What Hatch stores

- SSH host and user, ports, retry/keepalive settings, and UI preferences in app-private
  SharedPreferences.
- The SSH private key, key passphrase, SSH password, Cloudflare Access JWT, and optional harness
  token URL in app-private SharedPreferences encrypted with a non-exportable Android Keystore
  AES-GCM key.
- SSH host-key fingerprints in app-private SharedPreferences. These are not secret, but they are
  integrity-critical.

Android backup is disabled (`allowBackup=false`). Hatch does not write credentials to logs; copied
diagnostics are redacted before they leave the app.

## Network connections

Hatch connects only to the endpoint you configure:

- `https://<host>/` for Cloudflare Access discovery and latency probes,
- `wss://<host>/` for the SSH-over-WebSocket transport, or
- `<host>:22` for a direct SSH connection when Cloudflare Access is disabled.

If the host is a Tailscale name, the app asks Android whether the Tailscale package is installed
and whether a VPN transport and DNS are available. It does not contact a Tailscale API.

The app opens `http://127.0.0.1:<localPort>` in your browser on request. That listener is local to
the phone, but any app on the phone can reach it; the forwarded service's own authentication is
the gate.

## Permissions

- `INTERNET`, `ACCESS_NETWORK_STATE` — the tunnel and connectivity-aware retries.
- `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK` — keep
  the tunnel alive while the app is in the background.
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — optional user-initiated exemption for OEM battery
  managers.
- Package visibility for `com.tailscale.ipn` — the Tailscale preflight.

## Retention and deletion

Settings and secrets stay on the device until you clear app data or uninstall Hatch. There is no
server-side account or copy. Uninstalling removes the app-private files; the Keystore key is
removed with the app.

## Contact

For a privacy question, open a normal GitHub issue without including secrets. For a security
issue, use the private reporting process in [`SECURITY.md`](../SECURITY.md).
