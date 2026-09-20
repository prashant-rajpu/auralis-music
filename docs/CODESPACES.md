# Building this in GitHub Codespaces

Yes, with two limits worth knowing before you start. `.devcontainer/` is
committed, so a Codespace comes up with JDK 17, the Android SDK, Node 22 and
the relay's dependencies already installed.

## What works

- **The relay.** Everything: tests, typecheck, build, the smoke test, the TURN
  check. This is the better half of the story — see below.
- **The Android app.** Compiling, unit tests, lint, and both release APKs. The
  full verification gate in [`AGENTS.md`](../AGENTS.md) runs unchanged; it was
  checked against a fresh install of exactly the SDK packages the devcontainer
  installs, so a new Codespace is not starting from a hopeful guess.

## What does not

- **No emulator.** Codespaces has no nested virtualisation, so there is no KVM
  and nothing to run an AVD on. Instrumented tests cannot run there. This costs
  less here than it would elsewhere: the migration tests deliberately drive real
  SQLite through JDBC on the JVM rather than needing a device, for exactly this
  reason.
- **No `adb` to your phone.** The Codespace cannot see your phone's USB. To
  install a build, download the APK — right-click it in the VS Code explorer
  under `app/build/outputs/apk/…` — or let CI build it and take the artifact,
  which is what the tag workflow in `.github/workflows/release.yml` is for.

## The part that makes it worth it

Codespaces forwards ports, and a forwarded port set to **Public** gets a real
`https://` URL. That is a relay address a phone can use.

```bash
cd relay
npm run build
DATA_DIR="" npm start
```

Then in the Ports panel, set 8787 to Public and copy the URL. Put it into
Settings → Together → Relay address on both phones, and you are running the
two-phone test against code you are editing, with no deploy in between. Logs
scroll past in the terminal while it happens.

The relay needs `https` for anything that is not localhost, which this gives
you, and WebSockets pass through the forwarding fine.

Turn the port back to Private when you are done. A public forwarded port is
open to anyone with the URL, and while a room still needs its six-character
code, there is no reason to leave it open.

## Machine size

`devcontainer.json` asks for **4 cores and 16 GB**. Take that seriously rather
than defaulting to the 2-core machine: Gradle, the Kotlin daemon and KSP
together do not fit in 8 GB, and what you get is not a clean out-of-memory but
a compiler that dies for no stated reason.

If you do end up on a smaller machine, `setup.sh` notices and writes a
`~/.gradle/gradle.properties` with a smaller heap and no parallelism. Slower,
but it finishes.

Note what this costs. A personal GitHub account includes 120 core-hours a
month, so a 4-core Codespace is about 30 hours before you are paying. Stop it
when you are not using it — Codespaces will do that for you after 30 minutes
idle, and you can shorten that in your settings.

Turning on **prebuilds** for this repository makes the SDK download happen once
on GitHub's side instead of every time you create a Codespace. Worth it if you
open one more than occasionally.

## If Gradle complains about a path that does not exist

Moving between machines — your laptop and a Codespace, or two Codespaces —
can leave Gradle's configuration cache holding a path from the other one. It
shows up as a transform failing on an `android.jar` under a directory that is
not there any more, which reads like a broken SDK and is not one.

```bash
./gradlew --stop
rm -rf .gradle/configuration-cache
```

Nothing is lost; it is a cache. The SDK itself is fine.

## Secrets

Add these under Settings → Secrets and variables → Codespaces. They arrive as
environment variables, which is what the build reads.

| Secret | What it does |
|---|---|
| `AURALIS_RELAY_URL` | Bakes a relay address into the APK. Settings → Together still overrides it per device. |
| `JAMENDO_CLIENT_ID` | The Jamendo catalog. Without it that source is simply absent. |

Signing keys are a different matter: `keystore.properties` and `*.jks` are
gitignored and should stay off a cloud machine. Build debug APKs in a Codespace
and sign releases where the key lives, or let the tag workflow do it with
repository secrets.

## First run

```bash
./gradlew assemblePlusDebug          # the sideload APK
./gradlew testPlayDebugUnitTest      # the unit tests
cd relay && npm test                 # the relay
```

The first Gradle run downloads a few hundred megabytes of dependencies,
including a ~49 MB WebRTC artifact. After that it is warm.
