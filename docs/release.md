# Release build

Hatch signs release builds with a local keystore that is **never committed**. Losing it means you
cannot update an installed release build under the same package name.

## One-time keystore setup

```bash
mkdir -p "$HOME/.hatch" && chmod 700 "$HOME/.hatch"
keytool -genkeypair -noprompt \
  -keystore "$HOME/.hatch/hatch-release.jks" -alias hatch \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Hatch, O=Hatch contributors" \
  -storepass '<password>' -keypass '<password>'

cat > keystore.properties <<EOF
storeFile=$HOME/.hatch/hatch-release.jks
storePassword=<password>
keyAlias=hatch
keyPassword=<password>
EOF
chmod 600 keystore.properties "$HOME/.hatch/hatch-release.jks"
```

`keystore.properties` and `*.jks` are in `.gitignore`. Back both files up somewhere safe, outside
the repository and outside the build machine. Never attach them to a release or paste them into a
CI log.

## Build and verify

```bash
./gradlew :app:testDebugUnitTest :engine:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
"$ANDROID_HOME/build-tools/37.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

A release build is minified with R8 and resource-shrunk. JSch is kept by
`app/proguard-rules.pro`. If `keystore.properties` is absent, Gradle produces an unsigned release
APK; it is not installable until you sign it.

If you distribute the APK, ship [`LICENSE`](../LICENSE), [`NOTICE`](../NOTICE), and
[`THIRD_PARTY_NOTICES.md`](../THIRD_PARTY_NOTICES.md) with it (or add an in-app licenses screen).
The font OFL texts and the JSch BSD text are under [`licenses/`](../licenses/).

## Versioning

Update `versionCode` (integer, must increase) and `versionName` in `app/build.gradle.kts` before a
release, then tag the commit. `versionCode` is what Android uses to decide whether an APK is an
upgrade.

## Install tracks

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

A debug build installed over a release build (or vice versa) is rejected because the signing
certificates differ. Uninstall first if you are switching tracks, and expect app data loss unless
you export it separately.

## CI signing

If you build releases in CI, store the keystore and passwords as encrypted repository secrets and
materialize `keystore.properties` only inside the release job. Keep the signing key out of pull
request workflows, which should build and test only. The included CI workflow deliberately does
not sign releases.

## Distribution caveats

For Google Play, declare the `specialUse` foreground-service type and the
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` use, provide a privacy policy and Data safety form, and keep
the SSH/crypto export-compliance answer current. The `specialUse` declaration and battery
exemption are policy-sensitive; a direct APK or F-Droid channel avoids those reviews for a
personal build.

F-Droid requires a free-software license, reproducible source builds, and no proprietary
dependencies. Hatch's dependencies (AndroidX, OkHttp, JSch, OFL fonts) are compatible, but the app
would still need an F-Droid metadata recipe and a final review.
