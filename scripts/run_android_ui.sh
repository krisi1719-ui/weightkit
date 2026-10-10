#!/bin/bash
# Runs the Android instrumentation suite N times on the emulator; copies screenshots/exports/results.
cd "$(dirname "$0")/.."
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
ADB=~/Library/Android/sdk/platform-tools/adb
if ! $ADB devices | grep -q "emulator-.*device$"; then
  nohup ~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_2 -no-window -no-audio -no-boot-anim \
    -no-snapshot-save -gpu swiftshader_indirect > build-logs/emulator.log 2>&1 &
  disown
fi
$ADB wait-for-device
until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 3; done
$ADB shell settings put global window_animation_scale 0; $ADB shell settings put global transition_animation_scale 0
$ADB shell settings put global animator_duration_scale 0
OUTS=android/app/build/outputs
for i in "$@"; do
  (cd android && ./gradlew connectedDebugAndroidTest > ../build-logs/android-ui-run$i.log 2>&1)
  echo "run $i exit $?"
  mkdir -p test-artifacts/ui/android-run$i
  python3 - "$i" <<'P'
import glob,sys,xml.etree.ElementTree as ET
i=sys.argv[1]; lines=[]; p=f=0
for x in glob.glob('android/app/build/outputs/androidTest-results/connected/debug/*.xml'):
    for tc in ET.parse(x).getroot().iter('testcase'):
        bad=tc.findall('failure')+tc.findall('error'); sk=tc.findall('skipped')
        st='Skipped' if sk else ('Failed' if bad else 'Passed'); p+=st=='Passed'; f+=st=='Failed'
        msg=''
        if bad:
            t=bad[0].text or ''; msg=' | '.join([l for l in t.splitlines() if 'weightkit' in l or not l.strip().startswith('at ')][:3])[:300]
        lines.append(f"{st} {tc.get('classname').split('.')[-1]}.{tc.get('name')} {tc.get('time')}s {msg}")
lines.sort(); lines.append(f'passed {p} failed {f}')
open(f'test-artifacts/ui/android-run{i}.txt','w').write('\n'.join(lines)+'\n'); print(lines[-1])
P
  cp -R "$OUTS/connected_android_test_additional_output/debugAndroidTest/connected/"*/* test-artifacts/ui/android-run$i/ 2>/dev/null
done
