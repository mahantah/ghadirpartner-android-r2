#!/usr/bin/env bash
set -eu
mkdir -p app/build/reports/startup
adb install -r app/build/outputs/apk/portal/debug/app-portal-debug.apk
adb logcat -c
adb shell am start -W -n ir.ghadirpartner.portalapp.debug/ir.ghadirpartner.nativeapp.MainActivity
sleep 40
adb logcat -d -b crash > app/build/reports/startup/crash.txt
adb exec-out screencap -p > app/build/reports/startup/launch.png
adb shell pidof ir.ghadirpartner.portalapp.debug
adb shell input keyevent KEYCODE_HOME
adb shell am start -W -n ir.ghadirpartner.portalapp.debug/ir.ghadirpartner.nativeapp.MainActivity
sleep 5
adb logcat -d -b crash > app/build/reports/startup/crash.txt
adb shell pidof ir.ghadirpartner.portalapp.debug
test ! -s app/build/reports/startup/crash.txt
adb install -r app/build/outputs/apk/androidTest/portal/debug/app-portal-debug-androidTest.apk
adb shell am instrument -w ir.ghadirpartner.portalapp.debug.test/androidx.test.runner.AndroidJUnitRunner > app/build/reports/startup/instrumentation.txt
adb exec-out run-as ir.ghadirpartner.portalapp.debug cat files/invoice-reference.pdf > app/build/reports/invoice-reference.pdf
adb exec-out run-as ir.ghadirpartner.portalapp.debug cat files/invoice-pagination.pdf > app/build/reports/invoice-pagination.pdf
adb exec-out run-as ir.ghadirpartner.portalapp.debug cat files/profile-day.png > app/build/reports/profile-day.png
adb exec-out run-as ir.ghadirpartner.portalapp.debug cat files/profile-night.png > app/build/reports/profile-night.png
adb exec-out run-as ir.ghadirpartner.portalapp.debug cat files/invoice-list.png > app/build/reports/invoice-list.png
mkdir -p app/build/reports/design
for name in design-home-day design-home-night design-catalog-serial design-catalog-panel design-catalog-agent design-checkout-cart design-checkout-cash design-checkout-check design-checkout-credit design-checkout-review design-checkout-success design-orders design-order-detail design-serials design-login design-otp; do
  if adb exec-out run-as ir.ghadirpartner.portalapp.debug cat "files/$name.png" > "app/build/reports/design/$name.png" 2>/dev/null; then
    test -s "app/build/reports/design/$name.png"
  else
    rm -f "app/build/reports/design/$name.png"
  fi
done
grep -q 'OK (7 tests)' app/build/reports/startup/instrumentation.txt
