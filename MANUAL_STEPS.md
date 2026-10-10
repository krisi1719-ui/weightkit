# Things only you can do

## Before the first build
1. Make sure the Mac is awake, then double-click `build.command` in Finder. It writes `build-logs/android.log` and `build-logs/ios.log`; the last line of each must be `exit 0`. (Android needs internet the first time to download Play Billing.)
2. Xcode → open `ios/WeightKit.xcodeproj` → target WeightKit → Signing & Capabilities → choose your Team. Add the **In-App Purchase** capability.

## App Store (iPhone – launch here first)
1. App Store Connect → new app, bundle ID `com.weightkit.app`, name e.g. “WeightKit: Metal Weight Calc”.
2. In-App Purchases → **Non-Consumable**, product ID `com.weightkit.app.pro`, price **$6.99** (see MONETIZATION.md), add a review screenshot of the Pro sheet.
3. Fill in the listing from `STORE_LISTING.md`, privacy labels: see the AdMob section below.
4. Screenshots: iPhone 6.9" and 6.5" (iPhone only – iPad is disabled in the project).
5. To test purchases in the simulator the scheme already uses `ios/Pro.storekit` (no account needed).

## Google Play
1. Create the upload keystore (Android Studio → Build → Generate Signed App Bundle) and keep it safe, **outside** the project folder.
2. Play Console → new app `com.weightkit.app` → Monetize → In-app products → one-time product ID **`weightkit_pro`**, price **$4.99** (see MONETIZATION.md).
3. Upload the AAB to Internal testing first (billing works only for apps uploaded to Play).
4. Data safety: see the AdMob section below (Advertising ID).

## apps-api (Android trial + purchase check)
1. Add the `weightkit` entry (below) to `D:\ANDROID APPS\_apps-server\server\apps.json` and run `deploy.bat` (see `docs\ADD_APP.md` there).
   Check: `https://apps.ggshoplimited.com/v1/health` lists `"weightkit"`.
```json
"weightkit": {
  "name": "WeightKit – Metal Weight Calculator",
  "owner": "GGSHOP LIMITED",
  "android_package": "com.weightkit.app",
  "products": { "weightkit_pro": "lifetime" },
  "trial_days": 14,
  "max_devices": 6,
  "enabled": true
}
```
2. Play Console: service account `play-purchase-verify@seniorcarebulgaria.iam.gserviceaccount.com` needs *View app information*, *View financial data, orders and cancellation survey responses*, *Manage orders and subscriptions* for this app (usually already account-wide on GGSHOP LIMITED). Takes 24–36 h.
3. Until the app is in Play Console, `purchase` returns 503 – expected.
4. Data safety: add "Device or other IDs" (hashed; app functionality, fraud prevention; not shared).

## AdMob (free tier ads)
1. AdMob → add the app twice (iOS, Android). Create per platform: 1 **Adaptive banner**, 1 **Interstitial**, 1 **Rewarded** unit.
2. Put the IDs in:
   - iOS: `ios/Info.plist` → `GADApplicationIdentifier`; `ios/WeightKit/Model/AdsManager.swift` → `AdIDs` (release branch).
   - Android: `android/app/build.gradle.kts` → `admobAppId`; `android/.../model/AdsManager.kt` → `AdIds` (BANNER / INTERSTITIAL / REWARDED).
   Debug builds keep using Google's test ads, so you never click your own real ads.
3. AdMob → Privacy & messaging → create a **GDPR** message (and **IDFA explainer** for iOS) – the apps already show it via UMP.
4. Publish `app-ads.txt` on your developer website (the URL in the store listings).
5. App Store privacy labels: now “Data used to track you: Device ID / Advertising data” (AdMob). Play Data safety: Advertising ID, collected by AdMob.

## Optional
- Check the section tables (`DATA_VERIFICATION.md`) before release.
- Final name: “WeightKit” is a working name. It's in `INFOPLIST_KEY_CFBundleDisplayName` (Xcode) and `app_name` in `shared/strings.py`.

## Status 6 Oct 2026
- Android AdMob IDs are in (app ID + banner/interstitial/rewarded) – release builds only, debug keeps Google test IDs. Internal testing: 3 (1.0.2).
- Still to do in AdMob: GDPR message (Privacy & messaging), app-ads.txt on the website, add your phones as **test devices** so you never get real ads while testing.
- iOS AdMob IDs are in (6 Oct): app ID via build setting GAD_APP_ID (Debug = Google test, Release = real), units in AdsManager.swift. Checked with build-ios-ids.command.
