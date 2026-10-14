#!/bin/bash
# Android vitals check: Pixel API 35, Pixel API 36 (gesture + 3-button nav) and Pixel Tablet API 36
# (portrait + landscape). Debug build installed with adb, VitalsScreensTest walks every screen.
# Output: build-logs/vitals/<run>/ (screenshots + report_*.txt), log: build-logs/vitals.log
cd "$(dirname "$0")"
mkdir -p build-logs/vitals
LOG=$PWD/build-logs/vitals.log
: >> $LOG
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
SDK=~/Library/Android/sdk
export ANDROID_HOME=$SDK ANDROID_SDK_ROOT=$SDK
ADB=$SDK/platform-tools/adb
EMU=$SDK/emulator/emulator
log() { echo "$(date +%H:%M:%S) $*" | tee -a $LOG; }

caffeinate -dimsu -w $$ &   # keep the Mac awake while this runs
ONLY=$1
log "disk: $(df -h ~ | tail -1)"
# ---------------------------------------------------------------- 1. code search (ours vs libraries)
if [ -z "$ONLY" ]; then
log "== code search"
(
  cd android
  ./gradlew -q assembleDebug assembleDebugAndroidTest > ../build-logs/vitals-build.log 2>&1 || { echo "BUILD FAILED"; tail -30 ../build-logs/vitals-build.log; }
) >> $LOG 2>&1
TERMS="setStatusBarColor setNavigationBarColor statusBarColor navigationBarColor LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES screenOrientation BitmapFactory URLConnection"
{
  echo "-- our sources (android/app/src/main):"
  for t in $TERMS; do grep -rn "$t" android/app/src/main | sed "s|^|   [$t] |"; done
  echo "-- merged debug manifest (screenOrientation / cutout):"
  M=$(find android/app/build/intermediates -path "*merged_manifest*/debug/*" -name AndroidManifest.xml | head -1)
  grep -n -B3 "screenOrientation\|layoutInDisplayCutoutMode" "$M" | grep -E "activity|screenOrientation|Cutout" | sed 's/^/   /'
} >> $LOG
echo "-- libraries (releaseRuntimeClasspath), classes that reference each term:" >> $LOG
python3 - "$LOG" "$TERMS" <<'P'
import os, re, subprocess, sys, zipfile, glob
log, terms = sys.argv[1], sys.argv[2].split()
out = subprocess.run(["./gradlew", "-q", ":app:dependencies", "--configuration", "releaseRuntimeClasspath"],
                     cwd="android", capture_output=True, text=True).stdout
deps = set()
for line in out.splitlines():
    m = re.search(r"--- ([\w.\-]+):([\w.\-]+):([\w.\-]+)(?: -> ([\w.\-]+))?", line)
    if m: deps.add((m.group(1), m.group(2), m.group(4) or m.group(3)))
cache = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
res = {}
for g, a, v in sorted(deps):
    for f in glob.glob(f"{cache}/{g}/{a}/{v}/*/*"):
        if not (f.endswith(".aar") or f.endswith(".jar")) or "sources" in f: continue
        try:
            z = zipfile.ZipFile(f); jars = []
            if f.endswith(".aar"):
                import io
                for nm in z.namelist():
                    if nm.endswith(".jar"): jars.append(zipfile.ZipFile(io.BytesIO(z.read(nm))))
            else: jars.append(z)
            for j in jars:
                for nm in j.namelist():
                    if not nm.endswith(".class"): continue
                    data = j.read(nm)
                    for t in terms:
                        if t.encode() in data: res.setdefault(t, set()).add(f"{g}:{a}:{v}  {nm[:-6]}")
        except Exception as e: pass
with open(log, "a") as fh:
    for t in terms:
        hits = sorted(res.get(t, []))
        libs = sorted({h.split("  ")[0] for h in hits})
        fh.write(f"   [{t}] {len(hits)} classes in {len(libs)} libraries: " + ", ".join(libs[:12]) + ("\n" if hits else " -\n"))
        for h in hits[:6]: fh.write(f"        {h}\n")
P

fi
# ---------------------------------------------------------------- 2. emulators
SDKM=$SDK/cmdline-tools/latest/bin/sdkmanager
if [ ! -x "$SDKM" ]; then
  log "installing cmdline-tools"
  curl -sL -o /tmp/clt.zip https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip && \
    rm -rf /tmp/clt && unzip -q /tmp/clt.zip -d /tmp/clt && mkdir -p $SDK/cmdline-tools && \
    rm -rf $SDK/cmdline-tools/latest && mv /tmp/clt/cmdline-tools $SDK/cmdline-tools/latest
fi
AVDM=$SDK/cmdline-tools/latest/bin/avdmanager
ARCH=$( [ "$(uname -m)" = "arm64" ] && echo arm64-v8a || echo x86_64 )
# use an installed image (google_apis or google_apis_playstore); download google_apis only if neither exists
img() {
  for v in google_apis google_apis_playstore; do
    [ -f "$SDK/system-images/android-$1/$v/$ARCH/package.xml" ] && { echo "system-images;android-$1;$v;$ARCH"; return; }
  done
}
for api in 35 36; do
  [ -n "$(img $api)" ] || { log "downloading android-$api"; yes | $SDKM --install "system-images;android-$api;google_apis;$ARCH" >> $LOG 2>&1; }
  log "API $api image: $(img $api)"
done
yes | $SDKM --licenses > /dev/null 2>&1
mk() { # name api device
  $AVDM list avd -c 2>/dev/null | grep -qx "$1" && return
  log "creating AVD $1"
  echo no | $AVDM create avd -n "$1" -k "$(img $2)" -d "$3" >> $LOG 2>&1
}
mk wk_pixel_35 35 pixel_8
mk wk_pixel_36 36 pixel_8
mk wk_tablet_36 36 pixel_tablet
log "AVDs: $($AVDM list avd -c 2>/dev/null | tr '\n' ' ')"

kill_emus() {
  for d in $($ADB devices | grep emulator | cut -f1); do $ADB -s $d emu kill >/dev/null 2>&1; done
  sleep 8
}
boot() {
  kill_emus
  log "booting $1"
  nohup $EMU -avd "$1" -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect > build-logs/emulator-$1.log 2>&1 &
  $ADB wait-for-device
  local t=0
  until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 5; t=$((t+5)); [ $t -gt 600 ] && { log "boot timeout $1"; return 1; }; done
  sleep 20
  $ADB shell settings put global window_animation_scale 0; $ADB shell settings put global transition_animation_scale 0
  $ADB shell settings put global animator_duration_scale 0
  $ADB shell input keyevent 82
  log "booted $1: API $($ADB shell getprop ro.build.version.sdk | tr -d '\r'), $($ADB shell wm size | tr -d '\r' | tail -1), $($ADB shell wm density | tr -d '\r' | tail -1)"
}
nav() { # gestural | threebutton
  for m in gestural threebutton twobutton; do $ADB shell cmd overlay disable com.android.internal.systemui.navbar.$m >/dev/null 2>&1; done
  $ADB shell cmd overlay enable com.android.internal.systemui.navbar.$1
  sleep 6
}
rotate() { # 0 portrait (natural) | 1 = 90°
  $ADB shell settings put system accelerometer_rotation 0
  $ADB shell settings put system user_rotation $1
  sleep 4
}
OUT=android/app/build/outputs/connected_android_test_additional_output
run() { # tag [landscape|portrait]
  log "run $1"
  $ADB uninstall com.weightkit.app >/dev/null 2>&1
  $ADB install -r android/app/build/outputs/apk/debug/app-debug.apk >> $LOG 2>&1
  rm -rf "$OUT"
  ( cd android && ./gradlew connectedDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.weightkit.app.VitalsScreensTest \
      -Pandroid.testInstrumentationRunnerArguments.tag=$1 \
      -Pandroid.testInstrumentationRunnerArguments.orient=${2:-none} > ../build-logs/vitals-$1.log 2>&1 )
  log "  exit $? ($(grep -E 'tests completed|BUILD' build-logs/vitals-$1.log | tr '\n' ' '))"
  rm -rf build-logs/vitals/$1 2>/dev/null; mkdir -p build-logs/vitals/$1
  find "$OUT" -path "*vitals/$1/*" -type f -exec cp {} build-logs/vitals/$1/ \;
  $ADB shell screencap -p /sdcard/last.png && $ADB pull /sdcard/last.png build-logs/vitals/$1/zz_last_screen.png >/dev/null 2>&1
  cat build-logs/vitals/$1/report_*.txt >> $LOG 2>/dev/null
}

PHONES="wk_pixel_35 wk_pixel_36"; [ "$ONLY" = 36 ] && PHONES=wk_pixel_36
if [ "$ONLY" = land ]; then # phone landscape only
  boot wk_pixel_36 && { rotate 0; nav gestural; run wk_pixel_36_landscape landscape; nav threebutton; run wk_pixel_36_landscape_3button landscape; }
  kill_emus; log "VITALS DONE"; exit 0
fi
for avd in $PHONES; do
  boot $avd || continue
  rotate 0
  nav gestural;    run ${avd}_gesture
  run ${avd}_landscape landscape
  nav threebutton; run ${avd}_3button
done
if boot wk_tablet_36; then
  nav gestural
  # Pixel Tablet's natural orientation is landscape: rotation 0 = landscape, 1 = portrait
  rotate 0; run wk_tablet_36_landscape
  rotate 1; run wk_tablet_36_portrait
  nav threebutton; rotate 0; run wk_tablet_36_landscape_3button
fi
kill_emus
log "VITALS DONE"
echo "DONE - you can close this window"
