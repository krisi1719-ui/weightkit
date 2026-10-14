#!/bin/bash
cd "$(dirname "$0")"
SDK=~/Library/Android/sdk
{ echo "== system images"; ls -d $SDK/system-images/*/*/* 2>&1
  echo "== avds"; ls ~/.android/avd 2>&1
  for f in ~/.android/avd/*.ini; do echo "-- $f"; cat "$f"; done
  for f in ~/.android/avd/*.avd/config.ini; do echo "-- $f"; grep -E "image.sysdir|hw.device.name|hw.lcd" "$f"; done
  echo "== download speed test"; curl -s -o /dev/null -w "%{speed_download} B/s\n" --max-time 20 https://dl.google.com/android/repository/commandlinetools-mac-13114758_latest.zip
} > build-logs/probe.txt 2>&1
echo "DONE - you can close this window"
