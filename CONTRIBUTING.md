# Contributing

Thanks for taking a look. Hatch is a small Android/Kotlin project; focused bug reports and pull
requests are welcome.

## Before you start

- Read [`README.md`](README.md) and [`docs/threat-model.md`](docs/threat-model.md).
- For a security issue, follow [`SECURITY.md`](SECURITY.md) instead of opening a public issue.
- Never include a real hostname, SSH key, Access token, harness token, or screenshot of your own
  deployment in an issue, commit, or test fixture. Use `ssh.example.com` / `user` in tests.
- By contributing, you agree that your contribution is licensed under Apache-2.0.

## Build

Requirements: JDK 21 and Android SDK 37. The Gradle wrapper pins Gradle 9.3.1.

```bash
./gradlew :app:assembleDebug
```

Install the debug APK with `adb install -r app/build/outputs/apk/debug/app-debug.apk`. The app is
debuggable; the prototype state extras are ignored by release builds.

## Test

```bash
./gradlew :app:testDebugUnitTest :engine:testDebugUnitTest
./gradlew :app:lintDebug
```

Host-side Compose renders do not need a device:

```bash
./gradlew :app:updateDebugScreenshotTest
```

Review the regenerated PNGs under `app/src/screenshotTestDebug/reference/` before committing. They
are part of the public documentation; they must use `ssh.example.com`, never a real endpoint.

## Code conventions

- Kotlin official code style, 2-space indentation, trailing commas where the file already uses them.
- Keep `:engine` free of UI and Android `Activity` dependencies where possible.
- New network or credential paths need a unit test and a note in the threat model if the trust
  boundary changes.
- Do not add analytics, crash reporting, or a new network destination without discussing it first.
- Do not weaken host-key checking, the HTTPS-only auth WebView, `FLAG_SECURE`, or the encrypted
  secret store.

## Pull requests

- Keep the change focused and describe the user-visible effect.
- Include the commands you ran and their result.
- Update the README or docs when behaviour changes.
- Do not commit `keystore.properties`, `local.properties`, `*.jks`, APKs, `build/`, or screenshots
  containing personal data.

## Release signing

Release keys are private and never committed. See [`docs/release.md`](docs/release.md). Pull
request CI builds and tests only; it does not sign releases.
