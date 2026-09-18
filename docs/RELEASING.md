# Releasing Auralis

## One-time: create the release keystore

```bash
keytool -genkeypair -v -keystore auralis-release.jks -alias auralis \
  -keyalg RSA -keysize 2048 -validity 10000
```

Keep `auralis-release.jks` and its passwords outside the repository (both `*.jks` and
`keystore.properties` are gitignored). Losing the keystore means existing installs cannot be
updated.

## Local signed build

Create `keystore.properties` in the repository root:

```properties
storeFile=/absolute/path/to/auralis-release.jks
storePassword=...
keyAlias=auralis
keyPassword=...
```

Then `./gradlew assembleRelease` produces `app/build/outputs/apk/release/app-release.apk`.
Without a keystore the same command produces `app-release-unsigned.apk`, which is what CI
builds on every push to prove R8 and resource shrinking still work.

The same values can be supplied as environment variables instead:
`AURALIS_KEYSTORE_FILE`, `AURALIS_KEYSTORE_PASSWORD`, `AURALIS_KEY_ALIAS`, `AURALIS_KEY_PASSWORD`.

## GitHub Actions release

`.github/workflows/release.yml` runs on every `v*` tag, builds a signed APK and attaches it
(plus a SHA-256 file) to a GitHub Release with generated notes. It needs four repository secrets:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 auralis-release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `auralis` |
| `KEY_PASSWORD` | key password |

## Jamendo client id

The Jamendo source needs a free client id from https://devportal.jamendo.com. It is not a
secret — it is compiled into the APK and anyone can read it back out — but keeping it out of
git stops the repo from handing out your rate limit. Only the **client id** is needed; the
client secret is never used and must not be added.

Without an id the Jamendo source disables itself and the rest of the app is unaffected.

| Where | How |
|---|---|
| Your machine | add `auralis.jamendoClientId=<id>` to `~/.gradle/gradle.properties` (outside the repo) |
| One-off build | `./gradlew assemblePlayDebug -Pauralis.jamendoClientId=<id>` |
| GitHub Actions | add a repository secret named `JAMENDO_CLIENT_ID`; both workflows already read it |

## Cutting a release

1. Bump `versionCode` (must increase every release) and `versionName` in `app/build.gradle.kts`.
2. Commit, then tag and push: `git tag v2.1.0 && git push origin v2.1.0`.
3. Watch the **Release** workflow; the APK appears on the Releases page when it finishes.
