import XCTest
@testable import WeightKit

/// Mirrors Android EntitlementTest / AdPolicyTest.
final class EntitlementTests: XCTestCase {
    var clock: FakeClock!
    var store: MemStore!
    var install: Date!

    override func setUp() { clock = FakeClock(); store = MemStore(); install = clock.wall }

    /// The user started the store trial at `install`.
    func ent() -> Entitlement {
        let e = Entitlement(store: store, clock: clock, trialStartedAt: install)
        e.touchClock()
        return e
    }

    func testTrialDay0Day6_9ActiveDay7Ended() {
        let e = ent()
        XCTAssertTrue(e.inTrial); XCTAssertTrue(e.hasPro)
        clock.advance(6.9 * day)
        XCTAssertTrue(e.inTrial)
        clock.advance(0.1 * day - 0.001)
        XCTAssertTrue(e.inTrial, "1 ms before the end")
        clock.advance(0.001)
        XCTAssertFalse(e.inTrial); XCTAssertFalse(e.hasPro)
        XCTAssertEqual(e.trialEnd.timeIntervalSince(e.trialStart), 7 * day) // fixed 7 × 24 h, also across DST
    }

    func testClockBack30DaysDoesNotExtendTrial() {
        let e = ent()
        clock.advance(6.9 * day); e.touchClock()
        clock.wall -= 30 * day
        XCTAssertTrue(e.inTrial, "still day 6.9")
        clock.mono += 0.1 * day; clock.wall += 0.1 * day
        XCTAssertFalse(e.inTrial)
        XCTAssertFalse(Entitlement(store: store, clock: clock).inTrial, "after restart")
    }

    func testClockBackWithoutTouchAfterRelaunch() {
        let e = ent()
        clock.advance(6 * day); e.touchClock()
        clock.wall -= 30 * day; clock.mono += 2 * day
        XCTAssertFalse(Entitlement(store: store, clock: clock).inTrial)
    }

    func testNoTrialUntilTheUserStartsIt() {
        let e = Entitlement(store: store, clock: clock)
        e.touchClock()
        XCTAssertFalse(e.trialStarted); XCTAssertFalse(e.inTrial); XCTAssertFalse(e.hasPro)
        clock.advance(3 * day)
        e.adoptTrialStart(clock.wall)                         // trial bought today
        XCTAssertTrue(e.inTrial)
        clock.advance(14 * day)
        XCTAssertFalse(e.inTrial)
    }

    func testReinstallKeepsTrialStart() {
        _ = ent()
        clock.advance(20 * day)
        // reinstall: the cached start (Keychain) and the App Store transaction both still say day 0
        let e = Entitlement(store: MemStore(), clock: clock, trialStartedAt: install)
        XCTAssertEqual(e.trialStart, install)
        XCTAssertFalse(e.inTrial)
        XCTAssertEqual(Entitlement(store: store, clock: clock).trialStart, install)
    }

    func testEarliestTrialTransactionWins() {
        let e = ent()
        e.adoptTrialStart(install.addingTimeInterval(-100 * day)) // trial started long ago on another device
        XCTAssertFalse(e.inTrial)
        e.adoptTrialStart(install.addingTimeInterval(10 * day))   // later dates are ignored
        XCTAssertEqual(e.trialStart, install.addingTimeInterval(-100 * day))
    }

    func testImplausibleTrialDateIsIgnored() {
        let e = ent()
        e.adoptTrialStart(Date(timeIntervalSince1970: 0))          // what Xcode StoreKit testing may report
        XCTAssertEqual(e.trialStart, install)
        XCTAssertTrue(e.inTrial)
        let fresh = Entitlement(store: MemStore(), clock: clock, trialStartedAt: Date(timeIntervalSince1970: 0))
        XCTAssertFalse(fresh.trialStarted)
    }

    func testRewardLastsExactly24h() {
        let e = ent()
        clock.advance(15 * day)
        XCTAssertFalse(e.hasPro)
        e.grantReward()
        XCTAssertTrue(e.hasReward); XCTAssertTrue(e.hasPro)
        clock.advance(24 * hour - 0.001)
        XCTAssertTrue(e.hasReward)
        clock.advance(0.001)
        XCTAssertFalse(e.hasReward); XCTAssertFalse(e.hasPro)
    }

    func testRewardNotExtendedByClockBack() {
        let e = ent()
        clock.advance(15 * day); e.grantReward(); e.touchClock()
        clock.wall -= 30 * day
        clock.mono += 24 * hour
        XCTAssertFalse(e.hasReward)
    }

    func testPurchasedOverridesEverythingRefundRemovesIt() {
        let e = ent()
        clock.advance(30 * day)
        e.setPurchased(true)
        XCTAssertTrue(e.hasPro); XCTAssertFalse(e.inTrial); XCTAssertFalse(e.hasReward)
        XCTAssertTrue(Entitlement(store: store, clock: clock).purchased, "cached until first refresh")
        e.setPurchased(false)
        XCTAssertFalse(e.hasPro)
    }

    func testOwnershipRules() {
        let id = ProStore.productID
        XCTAssertFalse(PurchaseRules.ownsPro([.init(productID: id, verified: false, revoked: false)], productID: id))
        XCTAssertFalse(PurchaseRules.ownsPro([.init(productID: id, verified: true, revoked: true)], productID: id))
        XCTAssertFalse(PurchaseRules.ownsPro([.init(productID: "other", verified: true, revoked: false)], productID: id))
        XCTAssertFalse(PurchaseRules.ownsPro([], productID: id))
        XCTAssertTrue(PurchaseRules.ownsPro([.init(productID: id, verified: true, revoked: false)], productID: id))
    }
}

final class AdPolicyTests: XCTestCase {
    var clock: FakeClock!
    var store: MemStore!
    var ent: Entitlement!
    var ads: AdPolicy!

    override func setUp() {
        clock = FakeClock(); store = MemStore()
        ent = Entitlement(store: store, clock: clock, trialStartedAt: clock.wall); ent.touchClock()
        let e = ent!
        ads = AdPolicy(store: store) { e.now }
    }

    func due() -> Bool { ads.fullScreenDue(canRequestAds: true, hasPro: ent.hasPro) }

    func testNoAdsWhileTrialProOrReward() {
        XCTAssertFalse(ads.adsActive(canRequestAds: true, hasPro: ent.hasPro))
        XCTAssertFalse(ads.showBanner(canRequestAds: true, hasPro: ent.hasPro))
        clock.advance(100 * day)
        ent.setPurchased(true)
        XCTAssertFalse(due()); XCTAssertFalse(ads.showBanner(canRequestAds: true, hasPro: ent.hasPro))
        XCTAssertNil(store.double("lastFullScreenAd"), "48 h clock not started while Pro")
        ent.setPurchased(false)
        ent.grantReward()
        XCTAssertFalse(due()); XCTAssertFalse(ads.showBanner(canRequestAds: true, hasPro: ent.hasPro))
        XCTAssertFalse(ads.adsActive(canRequestAds: false, hasPro: false), "no consent → nothing")
    }

    func testBannerOnlyForFreeUsers() {
        clock.advance(14 * day)
        XCTAssertTrue(ads.showBanner(canRequestAds: true, hasPro: ent.hasPro))
        XCTAssertFalse(ads.showBanner(canRequestAds: false, hasPro: ent.hasPro))
    }

    func testFirstFullScreenOnly48hAfterAdsStartThenAtMostEvery48h() {
        clock.advance(14 * day)
        XCTAssertFalse(due())
        clock.advance(48 * hour - 0.001)
        XCTAssertFalse(due())
        clock.advance(0.001)
        XCTAssertTrue(due())
        ads.markFullScreen()                    // interstitial
        XCTAssertFalse(due())
        clock.advance(47 * hour)
        XCTAssertFalse(due(), "rewarded shares the limit")
        clock.advance(hour)
        XCTAssertTrue(due())
        ads.markFullScreen()                    // rewarded
        clock.advance(24 * hour)
        XCTAssertFalse(due(), "interstitial after rewarded")
    }

    func testClockBackDoesNotMakeAdsDueEarlier() {
        clock.advance(14 * day); _ = due()
        clock.advance(48 * hour); ads.markFullScreen(); ent.touchClock()
        clock.wall += 10 * day
        XCTAssertTrue(due())
        clock.wall -= 10 * day
        XCTAssertFalse(due())
    }
}
