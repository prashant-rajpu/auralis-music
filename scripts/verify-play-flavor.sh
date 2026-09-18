#!/usr/bin/env bash
# Fails if the Play-Store build contains any code for the unofficial endpoints that must only
# ship in the sideload (plus) edition. Run against an assembled play APK.
set -euo pipefail

APK="${1:-app/build/outputs/apk/play/release/app-play-release-unsigned.apk}"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
APKANALYZER="$SDK/cmdline-tools/latest/bin/apkanalyzer"

if [ ! -f "$APK" ]; then echo "APK not found: $APK" >&2; exit 1; fi
if [ ! -x "$APKANALYZER" ]; then echo "apkanalyzer not found at $APKANALYZER" >&2; exit 1; fi

status=0

# Only package rows ("P"): a shared helper may legitimately mention a provider by name
# (JamProtocolHelper validates YouTube links so this build can reject them).
packages=$("$APKANALYZER" dex packages --defined-only "$APK" \
  | awk '$1 == "P" { print $NF }' \
  | grep -iE 'youtube|jiosaavn|sponsorblock|netease' || true)

if [ -n "$packages" ]; then
  echo "FAIL: play build defines plus-only packages:" >&2
  echo "$packages" >&2
  status=1
else
  echo "ok: no plus-only packages"
fi

# The JioSaavn DES key is the clearest single marker of the decryption code.
# grep -c (not -q) so unzip is never killed by SIGPIPE, which would invert this test under pipefail.
key_hits=$(unzip -p "$APK" 'classes*.dex' 2>/dev/null | grep -ca '38346591' || true)
if [ "${key_hits:-0}" -gt 0 ]; then
  echo "FAIL: play build embeds the JioSaavn decryption key" >&2
  status=1
else
  echo "ok: no JioSaavn decryption key"
fi

exit $status
