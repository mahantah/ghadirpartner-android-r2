#!/usr/bin/env bash
set -euo pipefail
reports=app/build/reports/release-smoke
mkdir -p "$reports"
build_tools="$ANDROID_HOME/build-tools/35.0.0"
smoke_keystore="$RUNNER_TEMP/release-smoke-only.jks"
keytool -genkeypair -keystore "$smoke_keystore" -storepass android -keypass android -alias smoke-only -dname 'CN=CI Smoke Only' -keyalg RSA -keysize 2048 -validity 2 >/dev/null 2>&1
for mode in portal automation; do
  apk="app/build/outputs/apk/$mode/release/app-$mode-release-unsigned.apk"
  "$build_tools/aapt" dump badging "$apk" > "$reports/$mode-badging.txt"
  if grep -q 'application-debuggable' "$reports/$mode-badging.txt"; then exit 1; fi
  "$build_tools/aapt" dump xmltree "$apk" AndroidManifest.xml > "$reports/$mode-manifest.txt"
  grep -q 'android:allowBackup.*0x0' "$reports/$mode-manifest.txt"
  grep -q 'android:usesCleartextTraffic.*0x0' "$reports/$mode-manifest.txt"
  signed="$RUNNER_TEMP/$mode-release-smoke.apk"
  # Ephemeral certificate only for emulator verification. Distributable inputs stay unsigned.
  "$build_tools/apksigner" sign --ks "$smoke_keystore" --ks-pass pass:android --key-pass pass:android --ks-key-alias smoke-only --out "$signed" "$apk"
  "$build_tools/apksigner" verify --verbose "$signed" > "$reports/$mode-smoke-signature.txt"
  adb install -r "$signed"
  case "$mode" in portal) package=ir.ghadirpartner.portalapp ;; automation) package=ir.ghadirpartner.automationapp ;; esac
  adb logcat -c
  adb shell am start -W -n "$package/ir.ghadirpartner.nativeapp.MainActivity"
  ready=false
  for attempt in $(seq 1 20); do
    sleep 3
    adb shell uiautomator dump /sdcard/release-window.xml >/dev/null 2>&1 || continue
    adb exec-out cat /sdcard/release-window.xml > "$reports/$mode-window.xml"
    if grep -q 'خوش آمدید' "$reports/$mode-window.xml"; then ready=true; break; fi
  done
  test "$ready" = true
  adb shell pidof "$package"
  adb shell input keyevent KEYCODE_HOME
  adb shell am start -W -n "$package/ir.ghadirpartner.nativeapp.MainActivity"
  sleep 3
  adb logcat -d -b crash > "$reports/$mode-crash.txt"
  test ! -s "$reports/$mode-crash.txt"
  adb shell am force-stop "$package"
done
rm -f "$smoke_keystore"
