#!/bin/bash
# Clean Google Play screenshots in 7 languages (StoreScreenshotsTest) -> store-assets/raw/<lang>/
cd "$(dirname "$0")/.."
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
LOG=build-logs/store-shots.log
if ! $ADB devices | grep -q "emulator-.*device$"; then
  nohup ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_2 -no-window -no-audio -no-boot-anim \
    -no-snapshot-save -gpu swiftshader_indirect > build-logs/emulator.log 2>&1 &
  disown
fi
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
$ADB uninstall com.weightkit.app >/dev/null 2>&1
OUT=android/app/build/outputs/connected_android_test_additional_output
rm -rf "$OUT"
( cd android && ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.weightkit.app.StoreScreenshotsTest > ../$LOG 2>&1; echo "exit $?" >> ../$LOG )
rm -rf store-assets/raw; mkdir -p store-assets/raw
cp -R "$OUT"/debugAndroidTest/connected/*/store/* store-assets/raw/ 2>>$LOG
find store-assets/raw -name "*.png" | wc -l >> $LOG
echo DONE >> $LOG
