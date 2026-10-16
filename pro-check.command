#!/bin/bash
# Release AAB (build-aab.command) + ProFeaturesTest on the emulator; screenshots / PDF to build-logs/pro/.
cd "$(dirname "$0")"
caffeinate -dimsu -w $$ &
./build-aab.command
LOG=build-logs/pro.log
: > $LOG
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell settings put global window_animation_scale 0; $ADB shell settings put global transition_animation_scale 0
$ADB shell settings put global animator_duration_scale 0
$ADB shell cmd overlay enable com.android.internal.systemui.navbar.gestural >/dev/null 2>&1
$ADB shell settings put system accelerometer_rotation 0; $ADB shell settings put system user_rotation 0
OUT=android/app/build/outputs/connected_android_test_additional_output
rm -rf "$OUT" build-logs/pro; mkdir -p build-logs/pro
$ADB uninstall com.weightkit.app >> $LOG 2>&1   # the release build is signed with another key
( cd android && ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.weightkit.app.ProFeaturesTest >> ../$LOG 2>&1; echo "exit $?" >> ../$LOG )
find "$OUT" -type f \( -name "*.png" -o -name "*.pdf" -o -name "*.json" \) -exec cp {} build-logs/pro/ \;
ls build-logs/pro >> $LOG
echo "PRO CHECK DONE" >> $LOG
echo "DONE - you can close this window"
