#!/bin/bash
# Signed release bundle for Google Play + release smoke test on the emulator.
# Output: android/app/build/outputs/bundle/release/app-release.aab
cd "$(dirname "$0")"
mkdir -p build-logs
LOG=build-logs/aab.log
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
( cd android && ./gradlew testDebugUnitTest processDebugManifest bundleRelease assembleRelease > ../$LOG 2>&1; echo "exit $?" >> ../$LOG )
"$JAVA_HOME/bin/jarsigner" -verify -verbose:summary android/app/build/outputs/bundle/release/app-release.aab >> $LOG 2>&1

echo "== R8 keeps WorkDatabase_Impl constructor? ==" >> $LOG
awk '/^androidx.work.impl.WorkDatabase_Impl ->/{f=1;next} /^[^ ]/{f=0} f&&/<init>/' android/app/build/outputs/mapping/release/mapping.txt | head -2 >> $LOG

echo "== AdMob app ID in release / debug manifest ==" >> $LOG
for v in release debug; do
  M=$(find android/app/build/intermediates -path "*merged_manifest*/$v/*" -name AndroidManifest.xml 2>/dev/null | head -1)
  echo "$v: $(grep -o 'ca-app-pub-[0-9]*~[0-9]*' "$M" | head -1)" >> $LOG
done

echo "== Release smoke test (3 cold starts) ==" >> $LOG
if ! $ADB devices | grep -q "emulator-.*device$"; then
  nohup ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_2 -no-window -no-audio -no-boot-anim \
    -no-snapshot-save -gpu swiftshader_indirect > build-logs/emulator.log 2>&1 &
  $ADB wait-for-device
  until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
fi
$ADB uninstall com.weightkit.app >/dev/null 2>&1
$ADB install android/app/build/outputs/apk/release/app-release.apk >> $LOG 2>&1
for i in 1 2 3; do
  $ADB shell am force-stop com.weightkit.app
  $ADB logcat -c
  $ADB shell monkey -p com.weightkit.app -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
  sleep 20
  PID=$($ADB shell pidof com.weightkit.app | tr -d '\r')
  CRASH=$($ADB logcat -d -b crash | grep -c "FATAL EXCEPTION")
  echo "run $i: pid=${PID:-NONE} fatal=$CRASH" >> $LOG
  $ADB logcat -d -b crash | head -30 >> $LOG
done
$ADB shell screencap -p /sdcard/smoke.png && $ADB pull /sdcard/smoke.png build-logs/release-smoke.png >/dev/null 2>&1
echo "SMOKE DONE" >> $LOG
tail -14 $LOG
echo "DONE - you can close this window"
