# `:engine` — SSH over Cloudflare Access

The `:engine` module contains the real tunnel: one local port forward carried over an SSH session
whose transport can be a raw Cloudflare Access WebSocket.

```
local app ──HTTP──▶ 127.0.0.1:3080 (JSch local-forward listener)
                        │
                        ▼
                    JSch SSH session
                        │  raw SSH bytes
                        ▼
                 DuctSocket (java.net.Socket)
                        │  binary WebSocket frames
                        ▼
        wss://<host>/  +  Cf-Access-Token: <CF_Authorization JWT>
                        │
                        ▼
              sshd + service on the remote host
```

`RealTunnelEngine(context)` implements `TunnelEngine`. `start()` and `stop()` never block the
caller: the connect/retry loop runs on a `SupervisorJob + Dispatchers.IO` coroutine.

## Protocol

- **Discovery:** `GET https://<host>/` with redirects disabled. A `3xx` whose `Location` host is
  `*.cloudflareaccess.com` (or contains `/cdn-cgi/access/`) is the browser login URL and is exposed
  as `TunnelState.authUrl` in `Phase.NEEDS_AUTH`. `200` means Access is not configured and the
  engine continues without a token.
- **Transport:** `wss://<host>/` with `Cf-Access-Token: <jwt>` and a Hatch user agent. No path or
  query parameter is appended — the same contract as `cloudflared access ssh`. A successful
  upgrade (`101`) carries the raw SSH byte stream in binary frames. Text frames are a protocol
  violation and close the tunnel.
- **Token rejection:** Cloudflare answers the upgrade with `302`/`401`/`403`. The engine clears the
  cached token and re-enters `NEEDS_AUTH` with a freshly discovered login URL. Rejected tokens do
  not consume retry attempts.
- **Direct fallback:** when `TunnelConfig.useCloudflareAccess` is false, the engine pre-connects a
  plain TCP socket to `<host>:22`; JSch consumes that socket through a `SocketFactory`.

## Token storage

`CF_Authorization` is cached per host in `AccessTokenStore`, backed by `SecretStore`
(Android Keystore AES-GCM, preference file `hatch.access.v2`). Tokens saved by the 0.2 prototype
in `hatch_access_tokens` are migrated and the plaintext file is cleared before use.

`exp` is decoded locally (base64url payload, no signature verification) and a token is considered
unusable when it expires within 60 seconds. The endpoint validates the signature server-side.
Malformed tokens are ignored and the user is asked to sign in again.

## Host keys

- JSch uses `StrictHostKeyChecking=yes` with a `PinnedHostKeyRepository` backed by
  `HostKeyStore` (SHA-256 fingerprints in `hatch.hostkeys.v1`).
- A new host raises `UnknownHostKeyException`; the app shows the fingerprint and waits for an
  explicit trust decision.
- A changed host key raises `HostKeyMismatchException`, which is fatal: no retry, no automatic
  replacement. The user must verify the server out-of-band and forget the pin in Settings.
- There is no `acceptAnyHostKey` switch.

Key auth uses `JSch.addIdentity("hatch", privateKey, null, passphrase)` and accepts PEM/OpenSSH
keys supported by the mwiede JSch fork. Password auth uses `Session.setPassword`. The local
listener is created with `setPortForwardingL("127.0.0.1", localPort, remoteHost, remotePort)`, and
the bound port is asserted to equal the requested port (port `0` is rejected).

## Retry, network, and adaptation

- Backoff: `min(maxDelayMs, baseDelayMs * 2^(attempt-1))` with jitter in
  `[delay*(1-jitter), delay]`; `jitter` is clamped to `0..1`. `maxRetries <= 0` means unlimited.
  The retry counter resets after a session has been live for 45 s.
- No network: `WAITING_FOR_NETWORK`, countdown cleared, no attempt consumed; connectivity is
  observed through `ConnectivityManager.NetworkCallback` and the tunnel connects when the network
  returns.
- Classification (pure, unit-tested in `ErrorClassifier`): SSH auth → fatal `SSH_AUTH`; local bind
  or wrong bound port → fatal `PORT_IN_USE`; token rejection → `ACCESS_AUTH` + `NEEDS_AUTH`; DNS,
  timeouts, socket/WebSocket failures → retryable `DNS`/`TIMEOUT`/`NETWORK`; protocol issues →
  retryable `PROTOCOL`; unknown → retryable `UNKNOWN`.
- Adaptive quality: while quality is `POOR`, the SSH keepalive shortens to 10 s, the latency probe
  interval to 7 s, and the next connect timeout rises to 25 s. The OkHttp WebSocket ping interval
  is fixed per client, so a shorter interval applies on the next reconnect. Compression is not
  enabled (JSch zlib needs jzlib, which is not on the classpath).
- Quality buckets are documented and tested in `QualityMapping` (`EXCELLENT` ≤ 80 ms / 20 ms
  jitter, `GOOD` ≤ 150 / 40, `FAIR` ≤ 300 / 80, `POOR` otherwise or after three consecutive
  missed keepalive/probe samples, `UNKNOWN` before the first sample, `OFFLINE`).

## Metrics

`DuctSocket` counts payload bytes in both directions. Once per second the engine publishes
`bytesUp`/`bytesDown`, `throughputUpBps`/`throughputDownBps`, `rttMs`, `jitterMs` (EWMA of
`|rtt - lastRtt|`, alpha 0.3), `keepaliveMisses`, `reconnects`, `lastSessionMs`, and
`connectedAtMillis`.

## Known limitations

- **WebSocket ping/RTT:** OkHttp 5.x's public `WebSocket` API does not expose ping frames or
  `onPong`. The engine enables OkHttp's `pingInterval` for liveness and samples RTT with a small
  unauthenticated `GET https://<host>/` against the same edge every 15 s (7 s while degraded).
  That is an edge round trip, not a WebSocket control-frame RTT. After three consecutive probe
  failures the link is treated as lossy and the session is torn down (`POOR`, then `RETRYING`).
- Ed25519 keys are supported by mwiede JSch on modern Android, but RSA or ECDSA is the safer
  choice across the full API 26 range.
- One fixed local forward and one SSH session; no SOCKS or dynamic forwarding.
- The direct (non-Cloudflare) fallback hard-codes SSH port 22 and does not count bytes or sample
  RTT beyond the initial connect measurement.
- The Tailscale preflight belongs to the app module, not this engine.
