# Prompt for Claude Code – full test of WeightKit (iOS + Android)

Run Claude Code **on the Mac** in `~/AndroidStudioProjects/WeightKit` and paste everything below the line.

---

You are testing **WeightKit**, a metal weight calculator with two native apps in this folder:
- `ios/` – SwiftUI, Xcode project `ios/WeightKit.xcodeproj`, scheme `WeightKit`, iOS 16+, StoreKit 2, AdMob + UMP via SPM.
- `android/` – Jetpack Compose, Gradle (`android/gradlew`), minSdk 26, Play Billing 8, AdMob + UMP.
- `shared/` – single source of truth for data and strings (`build_data.py`, `strings.py`, `sync.sh`).

Read these first and treat them as the specification: `README.md`, `MONETIZATION.md` (pricing, Pro features, ad rules, anti-abuse – **these rules are binding**), `DATA_VERIFICATION.md`, `MANUAL_STEPS.md`.

## Goal
Test **every feature on both platforms, at least 3 times each**. Find bugs, fix them, and prove the fixes with tests. Finish with `TEST_REPORT.md`.

## Ground rules
- Do **not** change prices, the Pro feature split, the ad frequency rules or the trial length. If you think one is wrong, write it in the report instead.
- Never use real AdMob IDs and never click ads. Debug builds already use Google's test IDs; keep it that way.
- Keep iOS and Android behaviour identical. A fix on one side must be checked on the other.
- Edit data and UI text only in `shared/` and then run `sh shared/sync.sh`. Never edit the generated JSON or strings files by hand.
- Any debug or test hook you add must be compiled out of release builds (`#if DEBUG` / `BuildConfig.DEBUG`). Prove it by building Release and searching the binary or code paths.
- Commit after each phase with a clear message. Do not push.
- Use JAVA_HOME from Android Studio: `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.

## Phase 0 – Baseline
1. `python3 shared/build_data.py --check` (must report 0 failures).
2. Android: `./gradlew testDebugUnitTest assembleDebug`. iOS: `xcodebuild -project ios/WeightKit.xcodeproj -scheme WeightKit -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' build`.
3. Record warnings. Fix the warnings that are in our code.

## Phase 1 – Unit tests (logic, run 3×)
Add an XCTest target to the iOS project (`WeightKitTests`) and extend `android/app/src/test`. Cover the same cases on both platforms:
- **Formulas, all 13 shapes**: known values, for example plate 1000×1000×10 steel = 78.5 kg; round Ø20 = 2.466 kg/m; pipe 60.3×3.6 = 5.03 kg/m; hex 24 AF = 3.916 kg/m. Also zero, empty, negative and comma-decimal input ("12,5"). Also the wall-too-thick cases.
- **Standard profiles**: every item in `sections.json` gives area × 7850 within ±2 % of `steel_kg_m`. Imperial W shapes match their nominal lb/ft.
- **Units**: metric → imperial → metric round trip keeps every field within 0.01 %. Price per kg ⇄ per lb. Target weight is converted correctly.
- **Reverse calculation**: needed length, needed plate thickness, whole pieces.
- **Lists**: totals, waste %, markup %, items without a price, persistence round trip (save → reload).
- **CSV**: header, quoting of `"` and `,`, UTF-8 BOM, Cyrillic names, dot decimals.
- **Pro/trial logic** (inject a clock): trial active on day 0 and day 13.9, ended on day 14. Clock moved back 30 days does **not** extend the trial. A reward lasts exactly 24 h. Purchased overrides everything. A refund or revocation removes Pro.
- **Ad policy** (inject a clock): no ads while Pro, trial or reward is active. First full-screen ad only after 48 h. Never two full-screen ads within 48 h (including interstitial + rewarded). Banner only for free users.
Make the time source injectable if it isn't (a small `Clock` protocol/interface). Run each test suite 3 times and report any flaky test.

## Phase 2 – UI tests (run every flow 3×, on both platforms)
iOS: XCUITest in a `WeightKitUITests` target. Android: Compose UI tests (`androidTest`) on an emulator (create an AVD if none exists, API 34+). Add accessibility identifiers / test tags where needed.

Flows:
1. First launch → calculator shows the plate result 78.5 kg (metric locale) with no ad and no paywall.
2. Every shape chip: enter dimensions → result card updates live. Switch shapes and back: values are kept.
3. Standard profiles: pick a free family (IPE) and a Pro family (HEB) **during the trial** (works, shows the trial notice) and **after the trial** (paywall opens, selection does not change).
4. Rebar picker, material picker (search, select, density shown), custom material add/delete.
5. Units toggle in the toolbar and in Settings: values convert, nothing is lost, result is equal in both systems.
6. Price → cost appears. Target weight → reverse results.
7. Add 3 items → toast → Lists tab shows the list. Edit name, waste, markup → totals correct. Delete an item and a list.
8. Second list: allowed in trial/Pro, paywall for free.
9. Export PDF and CSV → share sheet opens with a file. Open the PDF and CSV and check the content (title, company name, rows, totals, Cyrillic text).
10. Settings: theme light/dark/system (take screenshots of every screen in both themes), currency, company name.
11. Paywall: price shown from the StoreKit config (`ios/Pro.storekit`) / Play test product. Buy → Pro active, all Pro features open, banner gone. Restore works. On iOS use the StoreKit test session (`SKTestSession`) for purchase, refund and revoke.
12. Ads (test IDs): with a debug clock after the trial, open Lists → interstitial shows once; open Lists again → no ad for 48 h. Banner only on Lists. The rewarded button appears only when due → after the reward Pro is active for 24 h. No ad ever appears on the calculator.
13. Localization: run the main flow in **bg, de, fr, es, tr, ru, en**. Screenshot every screen. Check for truncated text, untranslated keys and wrong number formats (decimal comma in bg/de/ru).
14. Accessibility: largest Dynamic Type / font scale 2.0, VoiceOver/TalkBack labels on inputs and results, touch targets ≥ 44 pt / 48 dp.
15. Rotation/backgrounding/process death (Android "Don't keep activities"): state and lists survive.

## Phase 3 – Anti-abuse checks (do each 3×, record results)
- Android: `adb shell pm clear com.weightkit.app` → trial start must stay the original install time (fake the install time in a debug build, or install, wait, clear, check).
- Android: `adb uninstall` + `adb install` → expected: new trial only if backup is off. With `adb shell bmgr` backup/restore → the trial date is restored.
- iOS simulator: delete app → reinstall → trial must not restart (Keychain + AppTransaction).
- Both: set the device date back → trial and reward must not be extended.
- Both: purchase → refund/revoke (StoreKit test session / Play license tester "refund") → Pro removed on next launch.
- Both: pending purchase does not unlock Pro.
Report anything that still lets someone get Pro without paying, and fix it if it can be done without a server.

## Phase 4 – Data spot-check
Compare 20 random items from `sections.json` against public tables (EN 10365 / AISC / IS 808 / ASME B36.10M). Do **not** change values from memory. List mismatches with the source in `DATA_VERIFICATION.md`.

## Phase 5 – Release builds
- iOS: Release build for the simulator. Android: `assembleRelease` (unsigned is fine).
- Confirm that no debug hooks or test clocks are reachable in release.
- Confirm that the release AdMob IDs are still the marked TODO placeholders. List them in the report as "needs real ID".

## Deliverable: `TEST_REPORT.md`
- Table: feature × platform × run 1/2/3 → pass/fail.
- Bugs found → fix → commit hash → test that covers it.
- Anti-abuse results.
- Screenshots in `test-artifacts/` (iOS and Android, light/dark, 7 languages).
- Open issues, risks, and anything that needs a human decision.
