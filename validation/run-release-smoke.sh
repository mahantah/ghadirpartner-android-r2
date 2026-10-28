#!/usr/bin/env bash
# Exercise minified release code with disposable signatures; the owner's key never enters CI.
set -euo pipefail
for seconds in "$RELEASE_SMOKE_STARTUP_SECONDS" "$RELEASE_SMOKE_RESUME_SECONDS"; do
  if [[ ! "$seconds" =~ ^[1-9][0-9]*$ ]] || (( seconds > 60 )); then
    echo "Invalid release smoke timing configuration" >&2
    exit 1
  fi
done
for flavor in portal automation; do
  if [[ "$flavor" == portal ]]; then package=ir.ghadirpartner.portalapp; else package=ir.ghadirpartner.automationapp; fi
  adb install -r "$RUNNER_TEMP/$flavor-smoke.apk"
  adb logcat -c
  adb shell am start -W -n "$package/ir.ghadirpartner.nativeapp.MainActivity"
  sleep "$RELEASE_SMOKE_STARTUP_SECONDS"
  adb shell pidof "$package"
  adb shell input keyevent KEYCODE_HOME
  adb shell am start -W -n "$package/ir.ghadirpartner.nativeapp.MainActivity"
  sleep "$RELEASE_SMOKE_RESUME_SECONDS"
  adb shell pidof "$package"
  adb logcat -d -b crash > "release-inputs/metadata/$flavor-smoke-crash.txt"
  test ! -s "release-inputs/metadata/$flavor-smoke-crash.txt"
done
