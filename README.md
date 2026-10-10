# WeightKit – Metal Weight Calculator

Two native apps sharing one data set:

| | iPhone (primary) | Android |
|---|---|---|
| Folder | `ios/` | `android/` |
| UI | SwiftUI, iOS 16+ | Jetpack Compose (Material 3), Android 8.0+ (API 26) |
| Open with | `ios/WeightKit.xcodeproj` in Xcode 16+ | `android/` in Android Studio |
| Purchases | StoreKit 2, product `com.weightkit.app.pro` | Play Billing 8, product `weightkit_pro` |
| Bundle / package | `com.weightkit.app` | `com.weightkit.app` |

Fully offline for calculations. No account.

## Features (v1.0)
- 13 shapes: plate/sheet, round, square, hex, flat, round tube, square/rect tube, angle, channel, I/H beam, T, rebar, standard profiles.
- Standard profiles: EN IPE / HEA / HEB / UPN, AISC W, IS ISMB / ISMC, ASME pipe SCH 40 / 80, rebar. Tabulated kg/m shown next to the computed weight.
- 30 materials with grade-specific density + user-defined materials.
- Metric ⇄ imperial at any time; typed values are converted, never lost.
- Live results: total weight, per piece, per m / per ft, volume, coated surface, cost (price per kg or per lb).
- Reverse calculation: length (or plate thickness) needed for a target weight; whole pieces in a target weight.
- Lists (cut list / quote) with waste % and markup %, PDF (A4) and CSV export through the share sheet.
- 7 languages: EN, BG, DE, FR, ES, TR, RU. Light / dark / system theme.

## Monetization
Free + one-time **Pro** purchase (no subscription). First 14 days after install every Pro feature is unlocked, and Pro screens show “Pro feature – trial ends <date>”.

### Ads (free tier only)
- No ads while the user has Pro: purchase, 14-day trial, or 24 h reward.
- Full-screen ads (interstitial or rewarded) **at most once every 48 h**, the first one 48 h after ads start. The interstitial is only shown when the user opens the **Lists** tab – never on the calculator or over a result.
- One small adaptive banner, **Lists screen only**.
- Rewarded is opt-in in the Pro sheet: “Watch a short ad – Pro free for 24 h”.
- Consent: Google UMP (GDPR/UK) on both platforms; iOS also asks for tracking permission (ATT) after consent.
- Code: `ios/WeightKit/Model/AdsManager.swift`, `android/.../model/AdsManager.kt`. Debug builds always use Google test ads.

### Anti-abuse (no server)
| Trick | iOS | Android |
|---|---|---|
| Clear data / reinstall to restart the trial | Trial start = Apple-signed `AppTransaction.originalPurchaseDate` (first download by this Apple ID, also on new devices) + Keychain copy (survives reinstall) | Trial start = min(saved date, `PackageInfo.firstInstallTime`) – “Clear data” does not change install time; reinstall restores prefs via Auto Backup |
| Move the device date back | “now” never goes below the latest time the app has seen (Keychain) | same (prefs) |
| Refund after buying | entitlements re-read on every launch/foreground; revoked → Pro off | purchases re-queried on every resume; refunded → Pro off |
| Pending / unpaid purchase | only verified transactions count | only `PURCHASED` state counts; purchases are acknowledged |
| Edit cached “pro” flag | cache only until the first StoreKit refresh | cache only until the first Play query |
Remaining gap (Android): uninstall + reinstall **with backup turned off** restarts the trial – acceptable without a server.

Pro unlocks: more than one list, PDF/CSV export, reverse calculation, custom materials, the HEA/HEB, W, ISMB/ISMC and SCH 80 tables. IPE, UPN, SCH 40 and rebar are free.

## Shared data — single source of truth
```
shared/build_data.py   materials + section tables, consistency checks
shared/strings.py      all UI text in 7 languages → iOS Localizable.strings + Android strings.xml
shared/sync.sh         regenerate everything and copy into both apps
```
Never edit the generated JSON / strings files inside `ios/` or `android/` by hand – edit the Python files and run `sh shared/sync.sh`.

### Add a material
Add a line to `MATERIALS` in `shared/build_data.py` (id, group, name, density kg/m³), run `sh shared/sync.sh`.

### Add a section standard
Add a table and a `fams.append({...})` block in `sections()` of `build_data.py`. Each item needs either `area_cm2` (I/U/T profiles), `od` + `wall` (pipe) or `d` (round). Give it `source` (standard document), `pro`, `verified: False`. `build_data.py --check` fails if area, geometry and tabulated mass disagree by more than 1–2 %.

### Add a language
Add a column to every entry in `shared/strings.py` and the language code to `LANGS`; add the region to `knownRegions` in the Xcode project and to `android/app/src/main/res/xml/locales_config.xml`.

## Formulas
All in `ios/WeightKit/Model/Shapes.swift` and `android/.../model/Shapes.kt` (identical, documented in comments). Mass per metre = area [mm²] × 10⁻⁶ × density [kg/m³]. Custom I/U/T profiles ignore root radii (≈1–5 % lighter than tables); standard profiles use the tabulated area so they include them.

## Tests
Full results, bugs found and open issues: `TEST_REPORT.md`; artifacts in `test-artifacts/`.
- `python3 shared/build_data.py --check` – every section table: area × 7850 vs tabulated kg/m (±2 %) and, for parallel-flange I/H, geometry incl. root radii vs area (±1 %). Currently 169 items, 0 failures.
- Unit tests, same cases on both platforms: `android/app/src/test` (`./gradlew testDebugUnitTest`) and `ios/WeightKitTests` (XCTest, incl. StoreKit test session on `ios/Pro.storekit`). Formulas, profiles, units, reverse, lists, CSV, trial/reward/clock rules and ad policy with an injected clock.
- UI tests: `android/app/src/androidTest` (Compose, Test Orchestrator) and `ios/WeightKitUITests` (XCUITest); runners `scripts/run_android_ui.sh`, `scripts/run_ios_ui.sh`.
- Anti-abuse checks: `scripts/antiabuse_android.sh`, `scripts/antiabuse_ios.sh`.
- Test-only hooks are compiled out of release builds: Android `app/src/debug` (no-op twin in `app/src/release`), iOS `#if DEBUG`.

## Build
Double-click `build.command` in Finder: runs Android unit tests + debug APK and an iOS simulator build, logs go to `build-logs/`.
