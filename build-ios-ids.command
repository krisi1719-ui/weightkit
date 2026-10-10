#!/bin/bash
# iOS: build Debug + Release (simulator) and print the AdMob app ID baked into each Info.plist.
cd "$(dirname "$0")"
mkdir -p build-logs
LOG=build-logs/ios-ids.log
: > $LOG
for C in Debug Release; do
  xcodebuild -project ios/WeightKit.xcodeproj -scheme WeightKit -configuration $C -sdk iphonesimulator \
    -destination 'generic/platform=iOS Simulator' -derivedDataPath ios/build \
    CODE_SIGNING_ALLOWED=NO build > build-logs/ios-ids-$C.log 2>&1
  echo "$C build exit $?" >> $LOG
  P=ios/build/Build/Products/$C-iphonesimulator/WeightKit.app/Info.plist
  echo "$C GADApplicationIdentifier: $(/usr/libexec/PlistBuddy -c 'Print :GADApplicationIdentifier' "$P" 2>&1)" >> $LOG
  B=ios/build/Build/Products/$C-iphonesimulator/WeightKit.app/WeightKit
  echo "$C units in binary: $(strings "$B" ios/build/Build/Products/$C-iphonesimulator/WeightKit.app/*.dylib 2>/dev/null | grep -o 'ca-app-pub-[0-9]*/[0-9]*' | sort -u | tr '\n' ' ')" >> $LOG
done
echo DONE >> $LOG
cat $LOG
echo "DONE - you can close this window"
