#!/bin/bash
# Phase 3 anti-abuse checks on an Android emulator (debug build). Each scenario runs 3×.
# Time is faked with debug-only system properties (they survive "Clear data" and reinstall):
#   debug.weightkit.clock_days    wall-clock offset in days
#   debug.weightkit.install_days  offset of PackageInfo.firstInstallTime in days
set -u
cd "$(dirname "$0")/.."
ADB=~/Library/Android/sdk/platform-tools/adb
APK=android/app/build/outputs/apk/debug/app-debug.apk
PKG=com.weightkit.app
OUT=test-artifacts/antiabuse/android.txt
mkdir -p "$(dirname "$OUT")"; : > "$OUT"
log() { echo "$*" | tee -a "$OUT"; }

props() { $ADB shell setprop debug.weightkit.clock_days "$1"; $ADB shell setprop debug.weightkit.install_days "$2"; }
state() {   # prints the entitlement line logged by DebugHooks.logState in onResume
  $ADB logcat -c
  $ADB shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1
  sleep 3
  $ADB logcat -d -s WeightKitDebug:I | grep -o 'trialStart=.*' | tail -1
  $ADB shell am force-stop $PKG
}
field() { echo "$1" | tr ' ' '\n' | grep "^$2=" | cut -d= -f2; }
check() { # name, line, key, expected
  local v; v=$(field "$2" "$3")
  if [ "$v" = "$4" ]; then log "  PASS $1 ($3=$v)"; else log "  FAIL $1 ($3=$v, expected $4) :: $2"; fi
}
fresh() { $ADB uninstall $PKG >/dev/null 2>&1; $ADB install -r "$APK" >/dev/null; }

nobackup() { # no backup data may leak into the next "fresh" install (Android restores at install time)
  $ADB shell bmgr wipe com.android.localtransport/.LocalTransport $PKG >/dev/null 2>&1
  $ADB shell bmgr enable false >/dev/null 2>&1
}

for run in 1 2 3; do
  log "=== run $run ($(date '+%F %T'))"
  nobackup

  log "A. 'Clear data' cannot restart the trial (install faked 20 days ago)"
  props 0 -20; fresh
  s=$(state); check "first launch after install 20 days ago" "$s" inTrial false
  start1=$(field "$s" trialStart)
  $ADB shell pm clear $PKG >/dev/null
  s=$(state); check "after pm clear" "$s" inTrial false
  [ "$(field "$s" trialStart)" = "$start1" ] && log "  PASS trialStart unchanged ($start1)" || log "  FAIL trialStart changed: $start1 -> $(field "$s" trialStart)"

  log "B. Uninstall + install WITHOUT backup → new trial (documented gap)"
  $ADB shell bmgr enable false >/dev/null
  props -20 0; fresh; state >/dev/null           # first launch 20 days ago
  props 0 0;  s=$(state); check "20 days later" "$s" inTrial false
  fresh;      s=$(state); check "after reinstall, backup off (gap: new trial)" "$s" inTrial true

  log "C. Uninstall + install WITH Auto Backup → trial date restored"
  $ADB shell bmgr enable true >/dev/null
  $ADB shell bmgr transport com.android.localtransport/.LocalTransport >/dev/null 2>&1
  props -20 0; fresh; state >/dev/null
  props 0 0;  s=$(state); check "20 days later" "$s" inTrial false
  # a force-stopped app is not eligible for backup ("Backup is not allowed"): run it, go Home, then back up
  $ADB shell am start -W -n $PKG/.MainActivity >/dev/null 2>&1; sleep 3; $ADB shell input keyevent KEYCODE_HOME; sleep 2
  $ADB shell bmgr backupnow $PKG 2>&1 | grep -q "com.weightkit.app with result: Success" && log "  backup: Success" || log "  backup: FAILED"
  $ADB uninstall $PKG >/dev/null; $ADB install "$APK" >/dev/null
  # Android restores the backup automatically at install time (restore-at-install)
  s=$(state); check "after reinstall with backup" "$s" inTrial false
  nobackup

  log "D. Device date set back 30 days does not extend the trial"
  props 13.9 0; fresh
  s=$(state); check "day 13.9" "$s" inTrial true; now1=$(field "$s" now)
  props -16.1 0
  s=$(state); now2=$(field "$s" now)
  [ "$now2" -ge "$now1" ] && log "  PASS time did not go back ($now1 -> $now2)" || log "  FAIL time went back ($now1 -> $now2)"
  log "  (that time keeps running while the date stays back is covered by EntitlementTest.clockBack30Days…)"
  props 0 0
done
$ADB shell bmgr enable false >/dev/null
log "done"
