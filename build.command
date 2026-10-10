#!/bin/bash
# Builds both apps and writes logs to build-logs/. Double-click in Finder to run.
cd "$(dirname "$0")"
mkdir -p build-logs
rm -f build-logs/*.done
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
echo "== Android ==" 
( cd android && ./gradlew --no-daemon testDebugUnitTest assembleDebug > ../build-logs/android.log 2>&1; echo "exit $?" >> ../build-logs/android.log )
tail -5 build-logs/android.log
echo "== iOS =="
xcodebuild -version > build-logs/ios.log 2>&1
xcodebuild -project ios/WeightKit.xcodeproj -scheme WeightKit -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build \
  CODE_SIGNING_ALLOWED=NO build >> build-logs/ios.log 2>&1
echo "exit $?" >> build-logs/ios.log
tail -5 build-logs/ios.log
touch build-logs/all.done
echo "DONE - you can close this window"
