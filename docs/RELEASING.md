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

## Cutting a release

1. Bump `versionCode` (must increase every release) and `versionName` in `app/build.gradle.kts`.
2. Commit, then tag and push: `git tag v2.1.0 && git push origin v2.1.0`.
3. Watch the **Release** workflow; the APK appears on the Releases page when it finishes.
