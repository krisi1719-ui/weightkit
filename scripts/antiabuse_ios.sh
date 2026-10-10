#!/bin/bash
# Phase 3 anti-abuse checks on the iOS simulator (debug build). Each scenario runs 3×.
# Time is faked with the debug-only launch argument -wkClockOffsetHours.
set -u
cd "$(dirname "$0")/.."
DEV=${DEV:-0A3E72B9-6241-407A-A202-6F60C8BCDBFF}   # iPhone 16, iOS 18.5
# ad-hoc signed like any installable build (an unsigned build has no Keychain access)
APP=${APP:-ios/build-signed/Build/Products/Debug-iphonesimulator/WeightKit.app}
BID=com.ggshop.weightkit
OUT=test-artifacts/antiabuse/ios.txt
mkdir -p "$(dirname "$OUT")"; : > "$OUT"
log() { echo "$*" | tee -a "$OUT"; }
xcrun simctl boot $DEV 2>/dev/null; xcrun simctl bootstatus $DEV -b >/dev/null

state() { # launch with clock offset (hours), print the WeightKitDebug line
  local f; f=$(mktemp)
  xcrun simctl launch --console-pty --terminate-running-process $DEV $BID -wkClockOffsetHours "$1" > "$f" 2>&1 &
  local pid=$!
  for _ in $(seq 1 20); do grep -q "WeightKitDebug trialStart" "$f" && break; sleep 0.5; done
  kill $pid 2>/dev/null; xcrun simctl terminate $DEV $BID 2>/dev/null
  grep -o 'trialStart=.*' "$f" | tail -1; rm -f "$f"
}
field() { echo "$1" | tr ' ' '\n' | grep "^$2=" | cut -d= -f2; }
check() {
  local v; v=$(field "$2" "$3")
  if [ "$v" = "$4" ]; then log "  PASS $1 ($3=$v)"; else log "  FAIL $1 ($3=$v, expected $4) :: $2"; fi
}
fresh() { xcrun simctl uninstall $DEV $BID 2>/dev/null; xcrun simctl install $DEV "$APP"; }

for run in 1 2 3; do
  log "=== run $run ($(date '+%F %T'))"
  log "A. Delete app + reinstall does not restart the trial (Keychain)"
  # start from a device that never had the app (debug reset wipes the Keychain items too)
  fresh; xcrun simctl launch $DEV $BID -wkReset -wkClockOffsetHours -480 >/dev/null; sleep 3; xcrun simctl terminate $DEV $BID
  s=$(state -480); check "first launch 20 days ago" "$s" inTrial true
  s=$(state 0);    check "20 days later" "$s" inTrial false; start1=$(field "$s" trialStart)
  fresh
  s=$(state 0);    check "after delete + reinstall" "$s" inTrial false
  [ "$(field "$s" trialStart)" = "$start1" ] && log "  PASS trialStart kept ($start1)" || log "  FAIL trialStart $start1 -> $(field "$s" trialStart)"

  log "B. Device date set back 30 days does not extend the trial"
  fresh; xcrun simctl launch $DEV $BID -wkReset >/dev/null; sleep 3; xcrun simctl terminate $DEV $BID
  s=$(state 0); s=$(state 333.6); check "day 13.9" "$s" inTrial true; now1=$(field "$s" now)
  s=$(state -386.4); now2=$(field "$s" now)
  awk "BEGIN{exit !($now2 >= $now1)}" && log "  PASS time did not go back ($now1 -> $now2)" || log "  FAIL time went back ($now1 -> $now2)"
done
log "done"
