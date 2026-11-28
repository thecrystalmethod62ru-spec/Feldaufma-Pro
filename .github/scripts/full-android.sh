#!/usr/bin/env bash
if [ "${ANDROID_API:-36}" = "36" ]; then adb shell cmd overlay enable-exclusive --user current --category com.android.internal.systemui.navbar.threebutton; fi
test_status=0
gradle :app:connectedDebugAndroidTest --stacktrace || test_status=$?
if [ "${ANDROID_API:-36}" = "36" ]; then
  adb install -r app/build/outputs/apk/debug/app-debug.apk || test_status=1
  adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk || test_status=1
  adb shell am instrument -w -r -e class de.feldaufmass.pro.UiScreenshotTest de.feldaufmass.pro.test/androidx.test.runner.AndroidJUnitRunner > screenshot-test.log || test_status=1
  cat screenshot-test.log
  grep -F 'OK (6 tests)' screenshot-test.log || test_status=1
  mkdir -p ui-screenshots
  for name in 01-start-menu 02-normal-portrait 03-normal-landscape 04-full-portrait 05-full-landscape 06-regelprofil; do
    if ! adb exec-out run-as de.feldaufmass.pro cat files/ui-screenshots/$name.png > ui-screenshots/$name.png || ! test -s ui-screenshots/$name.png; then
      rm -f ui-screenshots/$name.png
      test_status=1
    fi
  done
fi
exit "$test_status"
