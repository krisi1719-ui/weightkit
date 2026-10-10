#!/bin/bash
# Android: tests + signed AAB + release smoke test + Settings rows check. iOS: Debug/Release builds + AdMob ID check.
cd "$(dirname "$0")"
rm -f build-logs/release-all.done
./build-aab.command
./scripts/check-privacy-row.command
./build-ios-ids.command
echo ALL DONE > build-logs/release-all.done
