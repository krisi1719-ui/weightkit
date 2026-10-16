#!/bin/bash
# Compile everything (app + instrumentation tests) and run the JVM unit tests. Log: build-logs/compile.log
cd "$(dirname "$0")"
mkdir -p build-logs
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
( cd android && ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest > ../build-logs/compile.log 2>&1; echo "exit $?" >> ../build-logs/compile.log )
grep -E "^e: |error:|FAILED|tests completed|BUILD|^exit" build-logs/compile.log | head -40 > build-logs/compile-summary.txt
echo "COMPILE DONE" >> build-logs/compile-summary.txt
echo "DONE - you can close this window"
