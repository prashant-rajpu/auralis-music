#!/usr/bin/env bash
# Everything a fresh Codespace needs to build this project.
#
# Runs once, when the container is created. It is slow the first time — the Android SDK is most of
# a gigabyte — so turn on prebuilds if you open Codespaces here often and this becomes instant.
set -euo pipefail

SDK="${ANDROID_HOME:-/usr/local/lib/android-sdk}"

# Exactly what the build asks for and nothing else: compileSdk 37 and the build-tools that go with
# it. This set was checked by running the whole gate against a fresh install of it — unit tests on
# both flavors, both release APKs, lint, and the play-purity check — so it is a verified minimum
# rather than a hopeful one. Drifting from CI is how you get a green Codespace and a red pipeline.
PLATFORMS=("platforms;android-37.1")
BUILD_TOOLS="build-tools;37.0.0"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"

echo "==> Android SDK"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  sudo mkdir -p "$SDK"
  sudo chown -R "$(id -u):$(id -g)" "$SDK"

  tmp="$(mktemp -d)"
  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$tmp/tools.zip"
  unzip -q "$tmp/tools.zip" -d "$tmp"
  mkdir -p "$SDK/cmdline-tools"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/bootstrap"
  rm -rf "$tmp"

  # The downloaded zip is only there to fetch a current sdkmanager. An sdkmanager older than the
  # packages it is asked for can fail to read the repository at all, and SDK 37 is newer than any
  # zip Google publishes under a fixed URL. Outside $SDK/cmdline-tools/latest it needs --sdk_root.
  boot="$SDK/cmdline-tools/bootstrap/bin/sdkmanager"
  yes 2>/dev/null | "$boot" --sdk_root="$SDK" --licenses > /dev/null || true
  "$boot" --sdk_root="$SDK" --install "cmdline-tools;latest" > /dev/null
  rm -rf "$SDK/cmdline-tools/bootstrap"
fi

export PATH="$SDK/cmdline-tools/latest/bin:$PATH"

# Non-interactive, and piped to `yes` because there is no terminal to accept licences at.
# Recent command-line tools print a deprecation notice pointing at the new `android` binary.
# It is a notice, not a failure: sdkmanager still installs what it is asked for.
yes 2>/dev/null | sdkmanager --licenses > /dev/null || true
sdkmanager --install "platform-tools" "$BUILD_TOOLS" "${PLATFORMS[@]}" > /dev/null

# Gradle finds the SDK through ANDROID_HOME, but the Java extension is happier with this, and it
# is gitignored so it cannot follow you back to your own machine.
grep -qs "^sdk.dir=" local.properties 2>/dev/null || echo "sdk.dir=$SDK" >> local.properties

echo "==> Gradle memory"
# The repo's gradle.properties asks for a 4 GB heap, which is right on a laptop and too much on a
# small Codespace once the Kotlin and KSP daemons want their share. A user-level file overrides it
# without changing what everyone else builds with.
total_mb="$(awk '/MemTotal/ {print int($2 / 1024)}' /proc/meminfo)"
mkdir -p "$HOME/.gradle"
if [ "$total_mb" -lt 12000 ]; then
  echo "    ${total_mb} MB available — turning the heap and parallelism down"
  cat > "$HOME/.gradle/gradle.properties" <<'PROPS'
# Written by .devcontainer/setup.sh for a small machine. Delete it on a larger one.
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8
org.gradle.parallel=false
org.gradle.workers.max=2
kotlin.daemon.jvmargs=-Xmx1536m
PROPS
else
  echo "    ${total_mb} MB available — the repo defaults are fine"
fi

echo "==> Relay dependencies"
(cd relay && npm ci --no-audit --no-fund)

cat <<'DONE'

==> Ready.

  ./gradlew assemblePlusDebug                  the sideload APK
  ./gradlew testPlayDebugUnitTest              the unit tests
  cd relay && npm test                         the relay

  cd relay && npm run build && DATA_DIR="" npm start
      then forward port 8787 as **Public** and use the https URL it gives you
      as the relay address on a phone. See docs/CODESPACES.md.

  No emulator: Codespaces has no nested virtualisation, so instrumented tests
  cannot run here. Everything else can.
DONE
