# WeightKit – test report

Date: 2026-10-05 · Tested on: macOS (Intel i7, 16 GB), Xcode 16.4, iPhone 16 simulator iOS 18.5, Android emulator
`Medium_Phone_2` (Android 16 / API 36.1, Google Play image), Android Gradle Plugin 9.3, JDK from Android Studio.
Spec used: `README.md`, `MONETIZATION.md` (binding), `DATA_VERIFICATION.md`, `MANUAL_STEPS.md`.
Nothing was pushed. Prices, the Free/Pro split, ad frequency rules and the trial length were **not** changed.

## Summary

| | iOS | Android |
|---|---|---|
| Unit tests (Phase 1) | 62 tests (XCTest, incl. StoreKit test session) | 67 tests (JVM) |
| UI tests (Phase 2) | 22 tests = 15 flows | 27 tests = 15 flows |
| Runs | 3 | 3 |
| Real bugs found and fixed | 19 in total (table below) – 10 in both apps, 5 iOS only, 4 Android only |
| Open issues / decisions | see last section |

**Final state (all on the last commit's code):** iOS unit 62/62 ×3, Android unit 67/67 ×3, iOS UI 22/22 ×3,
Android UI 27/27 ×3, anti-abuse iOS 18/18 checks, Android 27/27 checks, both release builds clean.
Flaky tests: none left. Earlier flakes were traced to causes and fixed, not retried away: an app race (bug 6), StoreKit
test state leaking between tests, and one simulator start-up timeout ("Timed out while loading Accessibility" while
Gradle used the CPU) – that run was repeated on an idle machine.

## Phase 0 – baseline
- `python3 shared/build_data.py --check`: 10 families, 169 items, **0 failures** (also after every `sync.sh`).
- Android `testDebugUnitTest assembleDebug`: green. Compiler warnings in our code: none.
  Android Lint (debug) found **3 errors in our code**, all fixed: raw byte-order-mark character inside `Exporter.kt`,
  `LocalContext.current.resources.configuration` read in a composable, `ctx.getString` in a composable. Also fixed: 13
  `UseKtx`, 4 `AutoboxingStateCreation`, `ObsoleteSdkInt` (mipmap-anydpi-v26). Lint errors now: **0**.
  Remaining lint warnings are not code defects (see "Open issues": newer dependency versions, portrait lock, strings only
  used on iOS).
- iOS `xcodebuild … build`: green, **0 warnings in our code** (the only warning is Xcode's AppIntents metadata note).

## Phase 1 – unit tests (3 runs each, no flaky test)
Both platforms test the same cases with the same numbers (`android/app/src/test/…`, `ios/WeightKitTests/`):

| Area | What is covered |
|---|---|
| Formulas, all 13 shapes | plate 1000×1000×10 = 78.5 kg; round Ø20 = 2.466 kg/m; pipe 60.3×3.6 = 5.03 kg/m; hex 24 AF = 3.916 kg/m; square, flat, rect tube, angle, channel, I, T, rebar, standard profile; zero / empty / negative / `1e3` / `12f` / hex input → "missing"; `12,5`; wall-too-thick for pipe, tube, angle, channel, I, T; quantity clamp |
| Standard profiles | every one of the 141 items with a tabulated kg/m: A × 7850 within ±2 %; all 38 W shapes within ±2 % of nominal lb/ft; Pro split matches MONETIZATION.md |
| Units | metric → imperial → metric keeps every field (≤ 0.01 %, also in bg/de locales), 50 round trips without drift, same result in kg in both systems, price per kg ⇄ per lb, target weight |
| Reverse | needed length, plate thickness (also from imperial input), whole pieces incl. exact multiples |
| Lists | totals, waste %, markup %, items without price, persistence round trip, broken file ignored |
| CSV | header, quoting of `"` and `,`, UTF-8 BOM bytes, Cyrillic, dot decimals under a German locale, imperial, CRLF – byte-identical on both platforms |
| Pro / trial (injected clock) | active day 0 and 13.9, ended exactly at 14 × 24 h; date set back 30 days does not extend it (also after restart); "Clear data" / reinstall keep the start; AppTransaction date; reward exactly 24 h and not extended by the clock; purchased overrides all; refund removes Pro; pending never unlocks |
| Ad policy (injected clock) | no ads while Pro / trial / reward; first full-screen ad only 48 h after ads start; never two within 48 h incl. interstitial + rewarded; banner only for free users |
| StoreKit 2 (iOS, `SKTestSession` on `Pro.storekit`) | price $6.99 from the config, buy → Pro, refund → Pro removed, stale cached flag overridden, restore, Ask-to-Buy (pending) does not unlock |
| Process death | calculator snapshot round trip |

Run results: `test-artifacts/unit/{android,ios}-run{1,2,3}.txt` (+ 3 extra iOS stress runs).

## Phase 2 – UI tests (every flow 3×, both platforms)
iOS: XCUITest target `WeightKitUITests`; Android: Compose UI tests in `androidTest` with Test Orchestrator
(`clearPackageData` = every test starts like a new install). Accessibility identifiers / test tags are identical on both
platforms. "Free user" scenarios use the debug clock (+20 days); purchases use the StoreKit test session (iOS) and a
debug-only fake store (Android, because Play Billing only works for apps uploaded to the Play Console).

| Flow | iOS 1 | iOS 2 | iOS 3 | Android 1 | Android 2 | Android 3 |
|---|---|---|---|---|---|---|
| 1 First launch: plate result, no ad/paywall/consent | pass (2) | pass (2) | pass (2) | pass (2) | pass (2) | pass (2) |
| 2 Every shape chip, live result, values kept | pass | pass | pass | pass | pass | pass |
| 3 Profiles free/Pro in trial and after | pass | pass | pass | pass (2) | pass (2) | pass (2) |
| 4 Rebar, materials (search/select/density), custom material | pass | pass | pass | pass (2) | pass (2) | pass (2) |
| 5 Units toggle (toolbar + Settings) | pass | pass | pass | pass | pass | pass |
| 6 Price → cost, target → reverse | pass | pass | pass | pass (2) | pass (2) | pass (2) |
| 7 Lists: add 3, edit name/waste/markup, delete | pass | pass | pass | pass | pass | pass |
| 8 Second list: trial vs free | pass | pass | pass | pass (2) | pass (2) | pass (2) |
| 9 Export PDF/CSV, share sheet, content | pass | pass | pass | pass (2) | pass (2) | pass (2) |
| 10 Settings: theme, currency, company | pass | pass | pass | pass | pass | pass |
| 11 Paywall: price, buy, restore, refund, pending | pass | pass | pass | pass | pass | pass |
| 12 Ads (test IDs): consent, banner, interstitial, rewarded | pass | pass | pass | pass | pass | pass |
| 13 Localization bg/de/fr/es/tr/ru/en, light+dark | pass (7) | pass (7) | pass (7) | pass (7) | pass (7) | pass (7) |
| 14 Accessibility: largest text, labels, targets | pass* | pass* | pass* | pass | pass | pass |
| 15 Background / process death / recreate | pass | pass | pass | pass | pass | pass |

(n) = number of tests behind the flow (e.g. flow 13 = one test per language, each covering light and dark).

`pass*` = passes with one *expected failure* that is a known open issue (units toggle 31 pt, see below).

Screenshots (both themes, 7 languages, all screens, largest text): `test-artifacts/screenshots/{ios,android}/`.
Exported files: `test-artifacts/exports/` (CSV and PDF from both apps, PDF page rendered as PNG, PDF text extracted).

Export content check (flow 9 + `scripts/pdftext.swift`, list "Оферта \"Иванов\", 2026", company "Метал ООД", 3 items, waste 10 %, markup 20 %):
- **CSV: byte-identical on iOS and Android** (`cmp` = equal): UTF-8 BOM `EF BB BF`, quoted `"…"` with `""` escapes, commas
  inside fields, Cyrillic, dot decimals, CRLF, totals `228.559 kg` / `414.48 лв`.
- **PDF (A4, 1 page) on both**: title "Material list", company name, list name in Cyrillic, date, 3 rows with size and
  material, waste, total weight 228.56 kg, subtotal 314.00, markup, total 414.48 лв, footer note. Numbers in the PDF follow
  the device locale (decimal comma on a Bulgarian device), the CSV always uses dots.
- Share sheet opens with the file on both platforms (screenshots `f09_share_*`).
- Cosmetic fix on the way: the grey table header was too low for the two-line header "Per piece (kg)" (both apps).

## Bugs found → fix → commit → covering test

| # | Bug | Platform | Fix | Commit | Test |
|---|---|---|---|---|---|
| 1 | **Setting the date back extended the trial and the reward.** "Time never goes back" only froze `now` at the last seen time, so with the clock 30 days back the trial ran 30 more days. | both | `now` keeps advancing with the monotonic clock while the wall clock is behind (`Entitlement`) | 1d7fedd | `EntitlementTest.clockBack30Days_doesNotExtendTrial`, `…reward_notExtendedByClockBack` (+ iOS twins) |
| 2 | Rewarded ad counted towards the 48 h limit only when the reward was earned; closing it early allowed an interstitial right after. | both | mark the 48 h window when any full-screen ad is presented | 1d7fedd | `AdPolicyTest.firstFullScreen…` + UI flow 12 |
| 3 | Unit switch lost precision (0.5 mm → 0.0197 in → 0.50038 mm = 0.08 %), and results differed between systems. | both | exact base value kept behind every converted text | 1d7fedd | `UnitsTest.metricImperialMetric_*`, `repeatedSwitching_doesNotDrift` |
| 4 | **Crash** with a huge quantity (`Int(1e20)` traps). | iOS | quantity clamped to 1…1,000,000 (same on Android, which silently saturated) | 1d7fedd | `FormulaTests.testQuantityIsClamped` |
| 5 | "Whole pieces in target" could be one short (0.3 / 0.1 = 2.9999…). | both | `Reverse.wholePieces` with epsilon | 1d7fedd | `ReverseTest.wholePieces` |
| 6 | A purchase refresh started before a purchase/refund could finish after it and overwrite the newer state (race; seen as a flaky refund test). | both | generation counter: only the newest refresh or a purchase wins | 1d7fedd | `StoreKitTests.testBuyUnlocksThenRefundRemovesPro` (6× stress) |
| 7 | Trial end used calendar days on iOS (DST makes it 13 d 23 h or 14 d 1 h) but 14 × 24 h on Android. | iOS | fixed 14 × 24 h | 1d7fedd | `EntitlementTests.testTrialDay0Day13_9ActiveDay14Ended` |
| 8 | Play Billing never reconnected after a service disconnect, so refunds were missed until the next cold start. | Android | `enableAutoServiceReconnection()` | 1d7fedd | – (needs Play; see open issues) |
| 9 | Kotlin parser accepted `1e3`, `12f`, `0x10`; iOS did not – same input, different result. | Android | one strict decimal grammar on both | 1d7fedd | `UnitsTest.parse_rules`, `UnitsTests.testParseRules` |
| 10 | Lint errors (BOM in source, configuration/resource reads via `LocalContext`). | Android | see Phase 0 | 1d7fedd | Lint: 0 errors |
| 11 | **GDPR consent form (and on iOS the ATT prompt) appeared on first launch over the calculator**, during the trial when no ad can be shown – against MONETIZATION.md §4 "never on first launch / in the calculator". | both | consent + SDK start only once ads are possible (free user after trial / reward) | c64e997 | UI flow 1 (no consent, no ATT), flow 12 |
| 12 | Everything typed in the calculator was lost when the OS killed the app in the background. | both | small snapshot saved on background, restored on launch | c64e997 | `CalcSnapshotTest`, UI flow 15 (process death) |
| 13 | With Xcode's StoreKit configuration, `AppTransaction.originalPurchaseDate` is 1 Jan 1970 → the trial was over at first launch for every developer/QA build. A bogus Apple date would do the same in production. | iOS | dates before 2025-01-01 are ignored (Keychain still protects) | c64e997 | `EntitlementTests.testImplausibleAppTransactionDateIsIgnored`, all UI flows |
| 14 | In a list, the waste/markup decimal pads had no way to close the keyboard. | iOS | keyboard "Done" toolbar like on the calculator | c64e997 | UI flow 7 |
| 15 | Material picker had no search on Android (iOS had). | Android | search field (new string `search` added in `shared/strings.py`) | c64e997 | UI flow 4 |
| 16 | **A Pro section table (e.g. HEB) picked during the trial stayed selected and usable after the trial** – Pro for free. | both | the model only uses a Pro table while Pro is active; otherwise the first free table | 0bb6c8a | `FormulaTest.proTable_pickedInTrial_notUsedWithoutPro` (+ iOS), UI flow 3 |
| 17 | Largest text sizes: typed numbers cut off ("10…" for 1000), labels broken mid-word, result card covering 40 % of the screen. | both | label above the value at accessibility sizes; chip caption and result card capped | 0bb6c8a | UI flow 14 + screenshots |
| 18 | **The rewarded ad never appeared**: it was presented while the Pro sheet was still closing and AdMob silently refused. | iOS | present after the sheet has disappeared | 675cb24 | UI flow 12 |
| 19 | The "first full-screen ad 48 h after ads become possible" clock only started if an interstitial happened to be *loaded* at the first Lists visit, so the first ad could come much later than the rule says (found by the ads UI test). | both | clock starts when consent allows ads for a free user; "due" is checked before "loaded" | ddfb3c7 | UI flow 12 (both) |

Test-infrastructure problems found on the way (not product bugs): StoreKit test purchases and `askToBuyEnabled` leak
between test cases (global StoreKit test state) – reset in every `setUp`; `performScrollTo` only scrolls the closest
scroll parent; XCUITest typing into right-aligned fields.

## Phase 3 – anti-abuse (each 3×)
Scripts: `scripts/antiabuse_android.sh`, `scripts/antiabuse_ios.sh`; output `test-artifacts/antiabuse/`.
Time and install date are faked only through debug-only hooks (system properties / launch arguments).

| Check | iOS (simulator, signed debug build) | Android (emulator API 36, debug build) |
|---|---|---|
| Clear data | – | ✔ 3/3 – `pm clear` keeps `firstInstallTime`; trial start unchanged, trial stays over |
| Delete + reinstall | ✔ 3/3 – Keychain keeps the trial start (`keychain=true`) | backup **off**: new trial 3/3 (documented gap, as expected) · backup **on**: ✔ 3/3 – Auto Backup restores the date at install |
| Device date set back 30 days | ✔ 3/3 (time never goes back) + unit tests: time keeps running while the date stays back | ✔ 3/3 + unit tests |
| Reward + date back | unit tests ✔ | unit tests ✔ |
| Purchase → refund / revoke | ✔ `SKTestSession.refundTransaction` → Pro removed on next refresh/launch (unit + UI flow 11) | ✔ logic (unit + UI flow 11 with the debug fake store); real Play refund needs a license tester (open issue) |
| Pending purchase | ✔ Ask-to-Buy pending does not unlock (unit) | ✔ PENDING does not unlock (unit + UI flow 11) |
| Edited cached "pro" flag | ✔ StoreKit overrides it on launch (unit) | ✔ same rule (Play query), unit |
| Pro table picked in trial | ✔ fixed (bug 16) | ✔ fixed (bug 16) |

Notes:
- **Unsigned builds lose the Keychain**: with `CODE_SIGNING_ALLOWED=NO` the iOS app has no entitlements, Keychain writes
  fail and delete + reinstall restarts the trial (3/3). Every installable build (Xcode "Sign to Run Locally", TestFlight,
  App Store) is signed: 3/3 pass. Only relevant for local unsigned test builds.
- Android backup checks need the app *not* force-stopped before `bmgr backupnow` ("Backup is not allowed" otherwise), and
  backup data has to be wiped between scenarios because Android restores at install time.
- The real OS clock could not be changed (emulator Play images have no root; the Mac's clock was not touched). Date
  changes are simulated with the debug clock (wall clock only; the monotonic clock is real).

**What still lets someone get Pro without paying (no server):**
1. Android uninstall + reinstall with backup turned off → new 14-day trial (as documented in MONETIZATION.md).
2. Reboot while the date is set back: the monotonic clock restarts at boot, so the time between the last app start and
   the reboot is not counted; repeating that each day keeps the trial clock nearly frozen. Needs effort every day;
   closing it fully needs a server or a stricter "date went back = trial over" rule (decision below).
3. Modified APK / jailbreak (accepted in MONETIZATION.md §5).

## Phase 4 – data spot-check
20 random items (seed 20261004) compared against public tables: **0 mismatches**. Details and URLs in
`DATA_VERIFICATION.md` and `test-artifacts/data-spotcheck.md`. No data was changed. Caveat: W-shape areas and pipe sizes
each rest on one public source (aisc.org blocks automated access).

## Phase 5 – release builds

Both release builds succeed with 0 warnings in our code. Debug hooks are not in the release binaries (proof below);
the release AdMob IDs are still the Google **test** IDs marked TODO → **needs real ID** before publishing.

| Needs real ID | iOS | Android |
|---|---|---|
| AdMob app ID | `ios/Info.plist` `GADApplicationIdentifier` (now ca-app-pub-3940256099942544~1458002511) | `app/build.gradle.kts` `admobAppId` (now …~3347511713) |
| Banner | `AdsManager.swift` `AdIDs.banner` (#else) | `AdsManager.kt` `AdIds.BANNER` |
| Interstitial | `AdIDs.interstitial` | `AdIds.INTERSTITIAL` |
| Rewarded | `AdIDs.rewarded` | `AdIds.REWARDED` |

```
iOS release (xcodebuild -configuration Release, simulator, 2026-10-05)
- Build: succeeded, 0 warnings in our code.
- Debug hooks in the Release binary: none. `nm | swift demangle` finds 0 DebugHooks / ShiftedClock symbols;
  `strings` finds none of wkClockOffsetHours, wkReset, wkUITest, debug_state, WeightKitDebug, appTransactionNote
  (the only "removeAll*" hits are Google Mobile Ads SDK / Swift stdlib names). Same scan of the Debug build: 11 hits.
  DebugHooks.swift is wrapped in #if DEBUG; AppClock.current returns SystemClock() in Release; the -wkReset handler,
  the state label and Keychain.removeAll are #if DEBUG only.
- AdMob IDs in Release (all Google TEST IDs = TODO placeholders, need real IDs):
    GADApplicationIdentifier (ios/Info.plist):  ca-app-pub-3940256099942544~1458002511
    banner       ca-app-pub-3940256099942544/2934735716  (AdsManager.swift AdIDs, #else branch)
    interstitial ca-app-pub-3940256099942544/4411468910
    rewarded     ca-app-pub-3940256099942544/1712485313

Android release (assembleRelease, unsigned, 2026-10-05)
- app-release-unsigned.apk 11,998,513 bytes
- Debug-hook strings in release dex (weightkit_debug | debug.weightkit | fakeBilling | clockOffsetMs | WeightKitDebug): 0   (debug APK: 5)
- Lint (debug): 0 errors. Rebuilt 2026-10-05 07:30 after the last code change.
- com.weightkit.app.debug.DebugHooks in release = src/release twin only: clock() -> SystemTimeClock, billing() -> null,
  installOffsetMs() -> 0, logState() -> no-op. No prefs / system-property readers present.
- AdMob IDs in release (all Google TEST IDs = TODO placeholders, need real IDs):
    app ID (manifest, app/build.gradle.kts admobAppId): ca-app-pub-3940256099942544~3347511713
    banner       ca-app-pub-3940256099942544/9214589741  (AdsManager.kt AdIds.BANNER)
    interstitial ca-app-pub-3940256099942544/1033173712  (AdIds.INTERSTITIAL)
    rewarded     ca-app-pub-3940256099942544/5224354917  (AdIds.REWARDED)
```

## Open issues, risks, decisions for a human

**Decisions needed**
1. **First launch shows 157 kg, not 78.5 kg.** The default sheet is 10 × 1000 × **2000** mm (a standard 1 × 2 m sheet).
   The test prompt expected 78.5 kg (1000 × 1000). I did not change the default; the tests check 157 kg and 78.5 kg after
   setting L = 1000. Change the default only if 1 × 1 m is wanted.
2. **Ad consent now appears on the first launch after the trial ends**, over whichever screen is open (often the
   calculator). It is not an ad, but MONETIZATION.md says nothing ad-related on the calculator. Option: ask for consent the
   first time the user opens Lists after the trial.
3. **Material names are English in all 7 languages** ("Carbon steel (S235/S355, A36)" in Bulgarian etc.). They come from
   `build_data.py` with one name each. Needs a translated name per language in the data (format change) – not done.
4. **iOS units toggle in the navigation bar is 31 pt high** (system segmented control; Apple minimum 44 pt). Settings has
   44 pt rows for the same choice. Marked as an expected failure in UI flow 14. Redesign the toolbar control?
5. **Stricter clock rule?** Treating "date moved back by more than a day" as "trial over" would close gap 2 above, but would
   also punish users whose clock was wrong and got corrected.
6. **Custom materials created during the trial stay selectable after it** (adding is locked). Matches "data remains";
   decide if using existing ones should be Pro-only.

**Needs a person / a store account**
7. Real Play Billing: product `weightkit_pro` at $4.99, purchase, pending (slow card), refund, revoke with a license tester
   on an Internal-testing build. The Android UI tests use a debug-only fake store; the billing reconnect fix (bug 8) can
   only be verified there. iOS was tested with the local StoreKit configuration only (sandbox/TestFlight not tested).
8. Real AdMob IDs (table above), a GDPR message in AdMob → Privacy & messaging (the form shown in tests is Google's
   "Publisher Test Ads" one), `app-ads.txt`.
9. Section data: 20-item spot-check found no mismatch, but all families are still `verified: False`; W-shape areas and pipe
   sizes rest on one public source each.
10. Rewarded ad on the iOS simulator: the test video's close button never became enabled (the reward was still granted).
    Check on a real device.

**Minor / risks**
- Android, font scale 2.0: the calculator's screen title is cut off ("Sheet…") next to the units toggle (the selected chip
  shows the shape anyway).
- Android lint warnings left on purpose: newer AGP/Gradle/library versions available, `targetSdk 36` (37 exists), Google
  Mobile Ads SDK logs "out of date" (24.4.0), portrait lock (both apps are portrait-only, so "rotation" in flow 15 = no
  rotation; recreation was tested instead), 5 strings only used by iOS.
- The automatic Android "truncated text" scan in flow 13 reports almost every text (it counts texts that are only partly
  in the viewport); truncation was judged from the screenshots instead.
- Test-only debug hooks live in `android/app/src/debug` (release twin in `src/release`) and in `#if DEBUG` on iOS
  (`DebugHooks.swift`, the `-wk…` launch arguments, the invisible `debug_state` label used by the UI tests).

**How to rerun**
```
python3 shared/build_data.py --check
(cd android && ./gradlew testDebugUnitTest lintDebug assembleRelease)
xcodebuild -project ios/WeightKit.xcodeproj -scheme WeightKit -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 16' -only-testing:WeightKitTests test
scripts/run_ios_ui.sh 1 2 3          # iOS UI, ~1 h per run
scripts/run_android_ui.sh 1 2 3      # boots the emulator, ~20 min per run
scripts/antiabuse_android.sh && scripts/antiabuse_ios.sh
```

## Post-release fix – 1.0.1 (versionCode 2), 6 Oct 2026
- **Bug:** the Play build (R8) crashed on startup: `Failed to create an instance of androidx.work.impl.WorkDatabase`.
- **Cause:** AGP 9 runs R8 in full mode. The old room-runtime 2.2.5 rule (pulled in by play-services-ads → WorkManager 2.7.0) keeps the class name `WorkDatabase_Impl` but not its no-arg constructor, which Room calls by reflection. Debug builds are not minified, so the tests missed it.
- **Fix:** `android/app/proguard-rules.pro` keeps constructors of RoomDatabase subclasses, startup Initializers, ListenableWorkers and InputMergers. It is wired in with `proguardFiles(...)` in the release build type.
- **Check:** `build-aab.command` now also checks that `seeds.txt` contains `WorkDatabase_Impl()`, installs the signed release APK on the emulator and does 3 cold starts. Result: 3/3 running, 0 FATAL.
- Uploaded to internal testing as 2 (1.0.1).
