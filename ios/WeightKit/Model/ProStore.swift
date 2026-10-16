import Foundation
import Security
import StoreKit

/// Small Keychain store. Keychain items survive app deletion and reinstall,
/// unlike UserDefaults, so a reinstall cannot restart the free trial.
enum Keychain {
    private static let service = "com.weightkit.app.state"

    static func date(_ key: String) -> Date? {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: key,
                                kSecReturnData as String: true,
                                kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data, let s = String(data: data, encoding: .utf8),
              let t = TimeInterval(s) else { return nil }
        return Date(timeIntervalSince1970: t)
    }

    static func set(_ key: String, _ date: Date) {
        let data = Data(String(date.timeIntervalSince1970).utf8)
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrService as String: service,
                                kSecAttrAccount as String: key]
        let attrs: [String: Any] = [kSecValueData as String: data,
                                    kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock]
        if SecItemUpdate(q as CFDictionary, attrs as CFDictionary) == errSecItemNotFound {
            SecItemAdd(q.merging(attrs) { $1 } as CFDictionary, nil)
        }
    }

    #if DEBUG
    /// Debug / tests only: forget everything (simulates a device that never had the app).
    static func removeAll() {
        SecItemDelete([kSecClass as String: kSecClassGenericPassword,
                       kSecAttrService as String: service] as CFDictionary)
    }
    #endif
}

/// One-time "Pro" purchase (non-consumable) + 7-day free trial + 24 h Pro from a rewarded ad.
///
/// Trial (App Review guideline 3.1.1): the user starts it by "buying" the free non-consumable
/// `com.weightkit.app.trial14` (reference name "14-day Trial"). Its Apple-signed purchase date is the
/// trial start, so reinstalling, "Clear data" or a new device cannot restart it.
/// Anti-abuse (no server), rules in `Entitlement`:
///  • Clock rollback: "now" never goes below the latest time the app has seen and keeps advancing
///    with the monotonic clock while the device date is set back.
///  • Purchase state is always re-read from StoreKit entitlements (refunds/revocations remove Pro);
///    the cached flag is only used until the first refresh.
@MainActor
final class ProStore: ObservableObject {
    static let productID = "com.weightkit.app.pro"
    static let trialProductID = "com.weightkit.app.trial14"
    static let trialDays = Entitlement.trialDays
    static let rewardHours = Entitlement.rewardHours

    @Published private(set) var product: Product?
    @Published private(set) var trialProduct: Product?
    @Published var busy = false
    @Published var errorMessage: String?

    private var updates: Task<Void, Never>?
    /// Product + entitlement load started at launch (tests await it).
    private(set) var initialLoad: Task<Void, Never>?
    private let ent: Entitlement
    /// Refreshes can overlap (launch, foreground, Transaction.updates). Only the newest one, or a
    /// purchase made after it started, may set the state — a stale result must not undo a refund.
    private var generation = 0

    /// - trialStartedAt: DEBUG/UI tests only – pretend the store trial was started at this date.
    init(store: KeyValueStore = DeviceStore(), clock: Clock = AppClock.current, trialStartedAt: Date? = nil) {
        ent = Entitlement(store: store, clock: clock, trialStartedAt: trialStartedAt)
        ent.touchClock()
        updates = Task { [weak self] in
            for await result in Transaction.updates {
                if case .verified(let t) = result { await t.finish() }
                await self?.refresh()
            }
        }
        initialLoad = Task { await load() }
    }

    deinit { updates?.cancel() }

    // MARK: clock guard

    /// Call on launch and when the app returns to the foreground.
    func touchClock() { ent.touchClock() }

    /// Current time that cannot be moved backwards by changing the device clock.
    var now: Date { ent.now }

    // MARK: state

    var purchased: Bool { ent.purchased }
    var trialStart: Date { ent.trialStart }
    var trialEnd: Date { ent.trialEnd }
    var trialStarted: Bool { ent.trialStarted }
    var rewardUntil: Date { ent.rewardUntil }
    var inTrial: Bool { ent.inTrial }
    var hasReward: Bool { ent.hasReward }
    var hasPro: Bool { ent.hasPro }
    var trialEndText: String { Fmt.date(trialEnd) }
    /// The free trial can still be started on this Apple ID.
    var canStartTrial: Bool { !purchased && !trialStarted }

    func grantReward() {
        objectWillChange.send()
        ent.grantReward()
    }

    func load() async {
        if product == nil || trialProduct == nil {
            let products = (try? await Product.products(for: [Self.productID, Self.trialProductID])) ?? []
            product = products.first { $0.id == Self.productID }
            trialProduct = products.first { $0.id == Self.trialProductID }
        }
        await refresh()
    }

    func refresh() async {
        generation += 1
        let gen = generation
        var txs: [TransactionInfo] = []
        var trialDate: Date?
        for await result in Transaction.currentEntitlements {
            switch result {
            case .verified(let t):
                txs.append(TransactionInfo(productID: t.productID, verified: true, revoked: t.revocationDate != nil))
                if t.productID == Self.trialProductID {
                    trialDate = min(trialDate ?? t.originalPurchaseDate, t.originalPurchaseDate)
                }
            case .unverified(let t, _):
                txs.append(TransactionInfo(productID: t.productID, verified: false, revoked: t.revocationDate != nil))
            }
        }
        guard gen == generation else { return } // superseded
        if let trialDate { adoptTrialStart(trialDate) }
        setPurchased(PurchaseRules.ownsPro(txs, productID: Self.productID))
    }

    private func setPurchased(_ v: Bool) {
        objectWillChange.send()
        #if DEBUG
        if DebugHooks.owned { ent.setPurchased(true); return } // store screenshots only
        #endif
        ent.setPurchased(v)
    }

    private func adoptTrialStart(_ d: Date) {
        objectWillChange.send()
        ent.adoptTrialStart(d)
    }

    /// Starts the 7-day trial through the App Store (free non-consumable).
    func startTrial() async {
        errorMessage = nil
        if trialProduct == nil { await load() }
        if trialStarted { return } // already started on this Apple ID (restored by load)
        guard let trialProduct else { errorMessage = L("purchase_failed"); return }
        busy = true
        defer { busy = false }
        do {
            switch try await trialProduct.purchase() {
            case .success(let verification):
                if case .verified(let t) = verification {
                    await t.finish()
                    adoptTrialStart(t.originalPurchaseDate)
                } else {
                    errorMessage = L("purchase_failed")
                }
            case .userCancelled, .pending:
                break
            @unknown default:
                break
            }
        } catch {
            errorMessage = L("purchase_failed")
        }
    }

    func buy() async {
        errorMessage = nil
        if product == nil { await load() }
        guard let product else { errorMessage = L("purchase_failed"); return }
        busy = true
        defer { busy = false }
        do {
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                if case .verified(let t) = verification {
                    await t.finish()
                    generation += 1
                    setPurchased(true)
                } else {
                    errorMessage = L("purchase_failed")
                }
            case .userCancelled, .pending:
                // pending (Ask to Buy / unpaid) never unlocks; Transaction.updates refreshes once approved
                break
            @unknown default:
                break
            }
        } catch {
            errorMessage = L("purchase_failed")
        }
    }

    func restore() async {
        busy = true
        defer { busy = false }
        try? await AppStore.sync()
        await refresh()
    }
}
