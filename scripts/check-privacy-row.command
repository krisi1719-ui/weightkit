#!/bin/bash
# Opens the installed release build, goes to Settings, scrolls down, checks the Privacy policy row.
cd "$(dirname "$0")/.."
ADB=~/Library/Android/sdk/platform-tools/adb
LOG=build-logs/privacy-row.log
: > $LOG
$ADB shell am force-stop com.weightkit.app
$ADB shell monkey -p com.weightkit.app -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 8
center() { $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml | tr '>' '\n' | grep "text=\"$1\"" | head -1 | sed -E 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/'; }
B=$(center "Settings"); echo "settings bounds: $B" >> $LOG
set -- $B; $ADB shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); sleep 2
for i in 1 2 3 4 5; do $ADB shell input swipe 540 1800 540 500 300; sleep 1; done
P=$(center "Privacy policy"); echo "privacy row bounds: ${P:-NOT FOUND}" >> $LOG
C=$(center "ggshoplimitedco@gmail.com"); echo "contact row bounds: ${C:-NOT FOUND}" >> $LOG
$ADB shell screencap -p /sdcard/s.png && $ADB pull /sdcard/s.png build-logs/privacy-row.png >/dev/null 2>&1
echo DONE >> $LOG
