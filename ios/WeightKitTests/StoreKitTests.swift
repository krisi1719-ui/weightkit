import StoreKit
import StoreKitTest
import XCTest
@testable import WeightKit

/// Real StoreKit 2 code paths against the local StoreKit configuration (ios/Pro.storekit).
@MainActor
final class StoreKitTests: XCTestCase {
    var session: SKTestSession!
    var clock: FakeClock!

    override func setUp() async throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("Pro.storekit")
        session = try SKTestSession(contentsOf: url)
        session.resetToDefaultState()
        session.disableDialogs = true
        session.askToBuyEnabled = false // global StoreKit test state: would leak from the pending test
        session.clearTransactions()
        clock = FakeClock()
        // the StoreKit test daemon applies clearTransactions asynchronously: start from a clean slate
        for _ in 0..<40 {
            var any = false
            for await _ in Transaction.currentEntitlements { any = true }
            if !any { break }
            try await Task.sleep(nanoseconds: 125_000_000)
        }
    }

    override func tearDown() async throws {
        session.clearTransactions()
    }

    /// A store whose trial is already over, so only the purchase decides.
    func expiredStore(_ store: MemStore = MemStore()) async -> ProStore {
        let p = ProStore(store: store, clock: clock, trialStartedAt: clock.wall.addingTimeInterval(-30 * day))
        await p.initialLoad?.value
        return p
    }

    /// The StoreKit test session updates entitlements asynchronously; re-read like the app does on
    /// every launch/foreground until the expected state shows up (max 10 s).
    func eventually(_ pro: ProStore, _ want: Bool) async throws -> Double {
        var waited = 0.0
        while true {
            await pro.refresh()
            if pro.purchased == want || waited >= 10 { return waited }
            try await Task.sleep(nanoseconds: 250_000_000); waited += 0.25
        }
    }

    func testProductAndPriceFromStoreKitConfig() async throws {
        let p = try await Product.products(for: [ProStore.productID])
        XCTAssertEqual(p.first?.displayPrice, "$6.99")
        XCTAssertEqual(p.first?.type, .nonConsumable)
    }

    func testFreeTrialProductStartsTheTrial() async throws {
        let t = try await Product.products(for: [ProStore.trialProductID])
        XCTAssertEqual(t.first?.price, 0)
        XCTAssertEqual(t.first?.type, .nonConsumable)
        let pro = ProStore(store: MemStore(), clock: FakeClock())
        await pro.initialLoad?.value
        XCTAssertTrue(pro.canStartTrial); XCTAssertFalse(pro.hasPro)
        await pro.startTrial()
        XCTAssertTrue(pro.trialStarted); XCTAssertTrue(pro.inTrial); XCTAssertTrue(pro.hasPro)
        XCTAssertFalse(pro.canStartTrial)
        // a reinstall (empty local store) finds the trial transaction again
        let again = ProStore(store: MemStore(), clock: FakeClock())
        await again.initialLoad?.value
        await again.refresh()
        XCTAssertTrue(again.trialStarted, "trial start restored from the App Store")
    }

    func testBuyUnlocksThenRefundRemovesPro() async throws {
        let pro = await expiredStore()
        XCTAssertFalse(pro.hasPro)
        await pro.buy()
        XCTAssertTrue(pro.purchased); XCTAssertTrue(pro.hasPro)
        await pro.refresh()
        XCTAssertTrue(pro.purchased, "still owned after refresh")

        let tx = try XCTUnwrap(session.allTransactions().first { $0.productIdentifier == ProStore.productID })
        try session.refundTransaction(identifier: tx.identifier)
        print("refund visible after \(try await eventually(pro, false)) s")
        XCTAssertFalse(pro.purchased); XCTAssertFalse(pro.hasPro)
        // next launch with a stale (or edited) "proCached" flag: StoreKit wins
        let cached = MemStore(); cached.set("proCached", true)
        let relaunch = await expiredStore(cached)
        XCTAssertFalse(relaunch.hasPro)
    }

    @available(iOS 17.0, *)
    func testRestoreFindsPurchaseMadeElsewhere() async throws {
        let bought = try await session.buyProduct(identifier: ProStore.productID)
        let pro = await expiredStore()
        await pro.restore()
        print("restore visible after \(try await eventually(pro, true)) s")
        if !pro.hasPro {
            print("DIAG bought id=\(bought.id) revoked=\(String(describing: bought.revocationDate)) askToBuy=\(session.askToBuyEnabled)")
            for t in session.allTransactions() { print("DIAG session tx \(t.identifier) \(t.productIdentifier) state=\(t.state.rawValue) pending=\(t.pendingAskToBuyConfirmation)") }
            for await r in Transaction.currentEntitlements { print("DIAG entitlement \(r)".prefix(200)) }
            for await r in Transaction.all { if case .verified(let t) = r { print("DIAG all \(t.id) rev=\(String(describing: t.revocationDate))") } }
        }
        XCTAssertTrue(pro.hasPro)
    }

    func testPendingAskToBuyDoesNotUnlock() async throws {
        session.askToBuyEnabled = true
        let pro = await expiredStore()
        await pro.buy()
        XCTAssertFalse(pro.purchased)
        await pro.refresh()
        XCTAssertFalse(pro.hasPro, "pending purchase must not unlock")
        XCTAssertNil(pro.errorMessage)
    }
}
