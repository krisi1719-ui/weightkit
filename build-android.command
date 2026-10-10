#!/bin/bash
# Android only: unit tests + debug APK + lint. Log: build-logs/android-only.log
cd "$(dirname "$0")"
mkdir -p build-logs
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
( cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease > ../build-logs/android-only.log 2>&1; echo "exit $?" >> ../build-logs/android-only.log )
curl -s -m 15 https://apps.ggshoplimited.com/v1/health > build-logs/health.json 2>&1
tail -4 build-logs/android-only.log
echo "DONE - you can close this window"
