#!/bin/bash
# Release AAB (build-aab.command) + the export flow test on the emulator: list screen with the
# export buttons and page 1 of the PDF are copied to build-logs/check/.
cd "$(dirname "$0")"
[ "$1" = "--no-build" ] || ./build-aab.command
LOG=build-logs/check.log
: > $LOG
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell settings put global window_animation_scale 0; $ADB shell settings put global transition_animation_scale 0
$ADB shell settings put global animator_duration_scale 0
OUT=android/app/build/outputs/connected_android_test_additional_output
rm -rf "$OUT" build-logs/check
# the release build is signed with a different key: remove it for every user first
$ADB devices >> $LOG
for u in $($ADB shell pm list users | grep -o "UserInfo{[0-9]*" | grep -o "[0-9]*"); do
  echo "uninstall user $u: $($ADB shell pm uninstall --user $u com.weightkit.app 2>&1)" >> $LOG
done
echo "uninstall: $($ADB uninstall com.weightkit.app 2>&1)" >> $LOG
echo "still installed: $($ADB shell pm list packages -u com.weightkit.app)" >> $LOG
( cd android && ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.weightkit.app.ListsFlowsTest#f09_exportPdfCsv_content >> ../$LOG 2>&1; echo "exit $?" >> ../$LOG )
mkdir -p build-logs/check
find "$OUT" -name "*.png" -exec cp {} build-logs/check/ \;
find "$OUT" -name "*.pdf" -exec cp {} build-logs/check/ \;
ls build-logs/check >> $LOG
echo "CHECK DONE" >> $LOG
echo "DONE - you can close this window"
