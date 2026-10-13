#!/bin/bash
# Release AAB (build-aab.command) + the export flow test on the emulator: list screen with the
# export buttons and page 1 of the PDF are copied to build-logs/check/.
cd "$(dirname "$0")"
./build-aab.command
LOG=build-logs/check.log
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell settings put global window_animation_scale 0; $ADB shell settings put global transition_animation_scale 0
$ADB shell settings put global animator_duration_scale 0
OUT=android/app/build/outputs/connected_android_test_additional_output
rm -rf "$OUT" build-logs/check
$ADB uninstall com.weightkit.app >/dev/null 2>&1  # release build is signed with a different key
( cd android && ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.weightkit.app.ListsFlowsTest#f09_exportPdfCsv_content > ../$LOG 2>&1; echo "exit $?" >> ../$LOG )
mkdir -p build-logs/check
find "$OUT" -name "*.png" -exec cp {} build-logs/check/ \;
find "$OUT" -name "*.pdf" -exec cp {} build-logs/check/ \;
ls build-logs/check >> $LOG
echo "CHECK DONE" >> $LOG
echo "DONE - you can close this window"
