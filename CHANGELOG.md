# Changelog

All notable changes to Hatch are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses semantic versioning.

## [0.3.0] — 2026-10-22

Security baseline (P0) and Tailscale preflight.

### Added

- Android Keystore AES-GCM storage for the SSH key, passphrase, password, Cloudflare Access JWT,
  and optional harness token URL; migration from the 0.2 plaintext preferences.
- SSH host-key trust-on-first-use pinning with a fingerprint dialog, explicit forget, and fatal
  mismatch handling.
- Tailscale preflight for `.ts.net` and `100.64.0.0/10` hosts: package, VPN, and MagicDNS checks
  with Open Tailscale / Connect anyway / Cancel.
- Threat model, release process, third-party notices, and a signed/minified release build.
- 0.3.0 UI copy for the host-key and Tailscale states.

### Changed

- The auth WebView is HTTPS-only, cancels TLS errors, has no file/content access or JavaScript
  bridge, and clears cookies/storage after capturing the session.
- `FLAG_SECURE` covers Settings and the auth screen.
- Diagnostics copied off-device are redacted.
- Prototype deep-link extras are debug-only; release builds always use the real engine.

### Fixed

- Malformed private keys are treated as fatal configuration errors instead of retrying.
- JSch host-key lookup handles a null host/type without crashing.

## [0.2.2] — 2026-10-17

- Optional authenticated harness token URL, encrypted in 0.3.0.
- Foreground-service notification and keep-alive improvements in 0.2.x.

## [0.2.0] — 2026-10-17

- Persistent foreground-service tunnel, wake lock, battery-optimization prompt, and sticky restart.
- Tactile becomes the only reachable UI direction.

## [0.1.0] — 2026-10-17

- Initial prototype: three Compose directions, SSH-over-WebSocket transport, Cloudflare Access
  login capture, local port forward, retry/backoff, and telemetry.
