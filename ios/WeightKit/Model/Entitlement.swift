import Foundation

/// Time source. `wall` is the device clock (the user can change it); `mono` is a monotonic clock in
/// seconds that the user cannot change (it restarts at reboot).
protocol Clock {
    var wall: Date { get }
    var mono: TimeInterval { get }
}

struct SystemClock: Clock {
    var wall: Date { Date() }
    /// CLOCK_MONOTONIC on Darwin keeps counting while the device sleeps.
    var mono: TimeInterval { TimeInterval(clock_gettime_nsec_np(CLOCK_MONOTONIC)) / 1e9 }
}

/// Minimal persistent key-value store (Keychain + UserDefaults in the app, a dictionary in tests).
protocol KeyValueStore: AnyObject {
    func double(_ key: String) -> Double?
    func set(_ key: String, _ value: Double)
    func bool(_ key: String) -> Bool
    func set(_ key: String, _ value: Bool)
}

/// Numbers go to the Keychain (survives app deletion, so a reinstall cannot restart the trial)
/// with a UserDefaults copy; flags go to UserDefaults.
final class DeviceStore: KeyValueStore {
    private let d = UserDefaults.standard

    func double(_ key: String) -> Double? {
        Keychain.date(key)?.timeIntervalSince1970 ?? (d.object(forKey: "kv_" + key) as? Double)
    }
    func set(_ key: String, _ value: Double) {
        Keychain.set(key, Date(timeIntervalSince1970: value))
        d.set(value, forKey: "kv_" + key)
    }
    func bool(_ key: String) -> Bool { d.bool(forKey: key) }
    func set(_ key: String, _ value: Bool) { d.set(value, forKey: key) }
}

/// Pro state without any StoreKit code, so it can be unit-tested with a fake clock.
///
///  • Trial (iPhone): 7 × 24 h from the moment the user started it. The trial is started through the
///    App Store with the free non-consumable `com.weightkit.app.trial14` (App Review guideline 3.1.1),
///    so its start date comes from an Apple-signed transaction and survives reinstalls and new devices.
///    Before the trial is started the user is on the free tier.
///  • Reward: exactly 24 h from the moment it is granted.
///  • Purchased overrides trial and reward; a refund/revocation (purchased = false) removes it.
///  • Clock rollback: `now` never goes below the latest time seen, and while the wall clock is
///    behind, time keeps advancing with the monotonic clock, so moving the date back does not
///    freeze (= extend) the trial or the reward.
final class Entitlement {
    static let trialDays = 7
    static let rewardHours = 24
    static let trialSeconds = TimeInterval(trialDays * 24 * 3600)
    static let rewardSeconds = TimeInterval(rewardHours * 3600)
    private static let writeEvery: TimeInterval = 60
    /// Cached start of the store trial (the App Store transaction is the source of truth).
    static let trialKey = "trialStartIAP"

    private let store: KeyValueStore
    private let clock: Clock
    /// `Date.distantPast` = the trial has not been started.
    private(set) var trialStart: Date

    init(store: KeyValueStore, clock: Clock, trialStartedAt: Date? = nil) {
        self.store = store
        self.clock = clock
        trialStart = store.double(Self.trialKey).map { Date(timeIntervalSince1970: $0) } ?? .distantPast
        if let trialStartedAt { adoptTrialStart(trialStartedAt) }
    }

    /// No real purchase can be older than the app. Xcode StoreKit testing can report 1 Jan 1970,
    /// which would otherwise end every trial at once.
    static let earliestPlausibleDownload = Date(timeIntervalSince1970: 1_735_689_600) // 2025-01-01 UTC

    var trialStarted: Bool { trialStart >= Self.earliestPlausibleDownload }

    /// Apple-signed date of the trial transaction: the earliest one counts (a second device or a
    /// reinstall cannot restart the trial). Implausible dates are ignored.
    func adoptTrialStart(_ date: Date) {
        guard date >= Self.earliestPlausibleDownload, !trialStarted || date < trialStart else { return }
        trialStart = date
        store.set(Self.trialKey, date.timeIntervalSince1970)
    }

    var trialEnd: Date { trialStart.addingTimeInterval(Self.trialSeconds) }

    /// Current time that cannot be moved backwards by changing the device clock.
    var now: Date { now(mono: clock.mono) }

    private func now(mono: TimeInterval) -> Date {
        let lastSeen = store.double("lastSeen") ?? 0
        var advanced = lastSeen
        // Same boot (monotonic clock did not restart): real time has passed by at least mono − lastMono.
        if let lastMono = store.double("lastMono"), mono >= lastMono { advanced += mono - lastMono }
        return max(clock.wall, Date(timeIntervalSince1970: advanced))
    }

    /// Call on launch and when the app returns to the foreground.
    func touchClock() {
        let mono = clock.mono
        let n = now(mono: mono).timeIntervalSince1970
        let lastMono = store.double("lastMono")
        if n > (store.double("lastSeen") ?? 0) + Self.writeEvery || lastMono == nil || mono < lastMono! {
            store.set("lastSeen", n)
            store.set("lastMono", mono)
        }
    }

    var purchased: Bool { store.bool("proCached") }
    func setPurchased(_ v: Bool) { store.set("proCached", v) }

    var rewardUntil: Date { Date(timeIntervalSince1970: store.double("rewardUntil") ?? 0) }
    func grantReward() { store.set("rewardUntil", now.addingTimeInterval(Self.rewardSeconds).timeIntervalSince1970) }

    var inTrial: Bool { !purchased && trialStarted && now < trialEnd }
    var hasReward: Bool { !purchased && now < rewardUntil }
    var hasPro: Bool { purchased || inTrial || hasReward }
}

/// StoreKit-independent view of one transaction, so the ownership rule can be unit-tested.
struct TransactionInfo {
    let productID: String
    let verified: Bool
    let revoked: Bool
}

enum PurchaseRules {
    /// Only verified, non-revoked transactions of the Pro product count.
    static func ownsPro(_ txs: [TransactionInfo], productID: String) -> Bool {
        txs.contains { $0.productID == productID && $0.verified && !$0.revoked }
    }
}

/// Free-tier ad rules (see MONETIZATION.md §4), store independent. Identical on Android (AdPolicy).
///  • No ads while the user has Pro (purchase, trial or reward).
///  • Full-screen ads (interstitial and rewarded share one limit): at most once per 48 h;
///    the first one only 48 h after ads became possible.
///  • The banner is shown to free users only.
final class AdPolicy {
    static let fullScreenGap: TimeInterval = 48 * 3600

    private let store: KeyValueStore
    private let now: () -> Date

    init(store: KeyValueStore, now: @escaping () -> Date) {
        self.store = store
        self.now = now
    }

    func adsActive(canRequestAds: Bool, hasPro: Bool) -> Bool { canRequestAds && !hasPro }
    func showBanner(canRequestAds: Bool, hasPro: Bool) -> Bool { adsActive(canRequestAds: canRequestAds, hasPro: hasPro) }

    private var lastFullScreen: Date {
        if let t = store.double("lastFullScreenAd"), t > 0 { return Date(timeIntervalSince1970: t) }
        // start the 48 h clock the first time ads could be shown
        let n = now()
        store.set("lastFullScreenAd", n.timeIntervalSince1970)
        return n
    }

    func fullScreenDue(canRequestAds: Bool, hasPro: Bool) -> Bool {
        adsActive(canRequestAds: canRequestAds, hasPro: hasPro)
            && now().timeIntervalSince(lastFullScreen) >= Self.fullScreenGap
    }

    /// Call when a full-screen ad (interstitial OR rewarded) is presented.
    func markFullScreen() { store.set("lastFullScreenAd", now().timeIntervalSince1970) }
}
