#!/bin/bash
# Runs the iOS UI suite (flows + localization/a11y) N times; results in build-logs/ios-ui-runN.xcresult
cd "$(dirname "$0")/.."
for i in "$@"; do
  rm -rf build-logs/ios-ui-run$i.xcresult
  xcodebuild -project ios/WeightKit.xcodeproj -scheme WeightKit -sdk iphonesimulator \
    -destination 'platform=iOS Simulator,name=iPhone 16,OS=18.5' -derivedDataPath ios/build CODE_SIGNING_ALLOWED=NO \
    -only-testing:WeightKitUITests -resultBundlePath build-logs/ios-ui-run$i.xcresult test > build-logs/ios-ui-run$i.log 2>&1
  echo "run $i exit $?"
  python3 scripts/xcresult_summary.py build-logs/ios-ui-run$i.xcresult > test-artifacts/ui/ios-run$i.txt 2>&1
  tail -1 test-artifacts/ui/ios-run$i.txt
done
