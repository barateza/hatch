# Security policy

## Supported versions

Security fixes are applied to the current `0.3.x` line. Older prototype tags are not supported.

## Reporting a vulnerability

Please **do not open a public issue** for a suspected vulnerability.

Use GitHub's private vulnerability reporting on this repository
(**Security → Report a vulnerability**). Include:

- affected version or commit,
- a description of the issue and its impact,
- reproduction steps or a proof of concept,
- any suggested fix.

Do not include real SSH keys, Access JWTs, hostnames, or personal data. If a secret may have been
exposed, rotate it first; a report is not a substitute for rotation.

This is a small project maintained on a best-effort basis. You will get an acknowledgement when
the report is seen, and credit in the fix notes if you want it.

## Scope

In scope: the Android app, the `:engine` transport, credential storage, host-key pinning, the auth
WebView, and the foreground service.

Out of scope: the security of your own SSH server, Cloudflare Access policy, Tailscale tailnet, or
the service you forward; and attacks that require a rooted device or an unlocked, compromised
phone.

The threat model and residual risks are documented in [`docs/threat-model.md`](docs/threat-model.md).
