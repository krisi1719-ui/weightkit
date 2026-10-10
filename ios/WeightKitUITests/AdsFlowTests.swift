import XCTest

/// Flow 12 with Google test ad units and the debug clock. Never taps an ad – only its close button.
final class AdsFlowTests: UITestBase {
    var fullScreenAd: XCUIElement {
        app.buttons.matching(NSPredicate(format: "label ==[c] 'Close Advertisement'")).firstMatch
    }

    func closeAd() {
        let close = fullScreenAd
        if close.waitForExistence(timeout: 40) {
            // The close button is reported as disabled in the accessibility tree even after the ad's
            // countdown, so wait and tap its centre (only the close button, never the ad itself).
            for _ in 0..<8 where close.exists {
                sleep(6)
                close.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
                _ = close.waitForNonExistence(timeout: 3)
            }
            XCTAssertFalse(close.exists, "ad closed")
        }
    }

    func testF12AdsInterstitialOncePer48hBannerOnListsRewarded() {
        launch(days: 0)
        tab(1)
        XCTAssertFalse(exists("banner_ad", 3), "trial: no banner")
        XCTAssertFalse(fullScreenAd.exists)
        // trial over → consent answered in launch(); the first Lists visit only starts the 48 h clock
        launch(days: 20, reset: false)
        answerConsent()
        tab(1)
        XCTAssertTrue(exists("banner_ad", 20), "banner on Lists for free users")
        sleep(5)
        shot("f12_lists_banner")
        XCTAssertFalse(fullScreenAd.exists, "first visit only starts the 48 h clock")
        tab(0)
        XCTAssertFalse(exists("banner_ad"), "no banner on the calculator")
        // 48 h later: interstitial once when opening Lists (wait until it has loaded)
        launch(days: 22.1, reset: false)
        sleep(12)
        tab(1)
        XCTAssertTrue(fullScreenAd.waitForExistence(timeout: 20), "interstitial on Lists")
        shot("f12_interstitial")
        closeAd()
        tab(0); tab(1)
        sleep(4)
        XCTAssertFalse(fullScreenAd.exists, "not again within 48 h")
        tab(2); tap("settings_pro")
        XCTAssertFalse(exists("reward_offer", 3), "rewarded not offered within 48 h of the interstitial")
        tap("paywall_done")
        // another 48 h: rewarded offered → 24 h Pro
        launch(days: 24.2, reset: false)
        sleep(12)
        tab(2); tap("settings_pro")
        XCTAssertTrue(exists("reward_offer", 20))
        tap("reward_offer")
        XCTAssertTrue(fullScreenAd.waitForExistence(timeout: 20), "rewarded ad")
        shot("f12_rewarded")
        // The test video must finish before the close button works; on the x86 simulator it often does
        // not play. Wait up to 90 s, then record what happened (the 24 h rule itself is unit-tested).
        let close = fullScreenAd
        var waited = 0
        while close.exists && waited < 90 {
            sleep(10); waited += 10
            close.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        }
        if close.exists {
            // the SDK grants the reward when the video is watched even if the close button stays inert
            XCTContext.runActivity(named: "NOTE: rewarded close button stayed disabled in the simulator; app restarted") { _ in }
            app.terminate()
            launch(days: 24.3, reset: false)
        } else {
            sleep(2)
        }
        XCTAssertTrue(state.contains("hasReward=true"), "reward granted: " + state)
        tab(1)
        XCTAssertFalse(exists("banner_ad", 3), "reward = no ads")
        launch(days: 25.4, reset: false)                 // > 24 h after the reward
        XCTAssertTrue(state.contains("hasReward=false"), "reward expired after 24 h: " + state)
    }
}
