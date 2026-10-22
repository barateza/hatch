# Design history

Hatch started as a one-question prototype: *what should a one-button tunnel client look like, and
does a real SSH-over-Cloudflare-Access transport hold up behind it?*

Three Compose directions were rendered from the same state machine:

| Direction | Idea |
|---|---|
| Aurora | Ambient glass; deep ink, slow aurora light, one glass dial, quiet telemetry |
| Grid | Instrument panel; paper white, hairlines, mono numerals, a rotary dial with a live RTT sparkline |
| Tactile | Warm analog hardware; cream paper, editorial serif headline, moulded button, printed receipt |

**Tactile** was selected. The hard requirement was that the main screen fits without scrolling at
411×914 dp, including a shorter 411×853 dp device.

## What changed for Tactile

- Removed `verticalScroll` entirely.
- Trimmed the receipt to four lines: Session, Round trip, Data, Quality.
- Removed the separate retries row and the duplicated local URL under the action.
- Resized the control from 252 dp to 226 dp (204 dp on short screens) while keeping the physical
  object feel.
- Tightened the endpoint block to three lines and constrained the headline to two lines.
- Auth and error states replace the receipt rather than stacking below it.
- Reserved bottom clearance so the control is never covered.

## What stayed, what was archived

The cream paper and grain, Instrument Serif headline, moulded control, printed receipt, and palette
stayed. Aurora and Grid remain in the source tree as archived exploration; release builds always
render Tactile and do not expose a variant switcher. The debug-only state extras are also ignored
in release.

## Follow-up

The transport was later exercised end-to-end from a phone, and the prototype was hardened with
Keystore-encrypted credentials, SSH host-key pinning, a foreground service, retry/backoff,
Tailscale preflight, and a debug-only demo engine for screenshots. The remaining work is tracked
in the README's limitations and roadmap.
