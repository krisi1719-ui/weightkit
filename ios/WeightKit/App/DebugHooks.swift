#if DEBUG
import Foundation

/// DEBUG BUILDS ONLY – the whole file is compiled out of Release. Driven by launch arguments that
/// the UI tests pass:
///   -wkClockOffsetHours <h>   shift the wall clock (e.g. 480 = 20 days → trial over)
///   -wkReset                  start from a clean install state (defaults, Keychain, lists)
///   -wkAdsDue                 pretend the last full-screen ad was long ago (ad flows)
///   -wkAutoTrial              act as if the free store trial was started at the first launch
///   -wkOwned                  act as if Pro was bought (App Store screenshots)
enum DebugHooks {
    private static let args = ProcessInfo.processInfo.arguments

    private static func value(_ name: String) -> String? {
        guard let i = args.firstIndex(of: name), i + 1 < args.count else { return nil }
        return args[i + 1]
    }

    static var isUITest: Bool { args.contains("-wkUITest") }
    static var autoTrial: Bool { args.contains("-wkAutoTrial") }
    /// Store screenshots: behave as if Pro was bought (no trial captions, no ads).
    static var owned: Bool { args.contains("-wkOwned") }

    static let clock: Clock = {
        let offset = (Double(value("-wkClockOffsetHours") ?? "") ?? 0) * 3600
        return ShiftedClock(offset: offset)
    }()

    struct ShiftedClock: Clock {
        let offset: TimeInterval
        var wall: Date { Date().addingTimeInterval(offset) }
        var mono: TimeInterval { SystemClock().mono }
    }

    /// Last AppTransaction seen (for the UI-test state label).
    @MainActor static var appTransactionNote = "none"

    @MainActor static func stateLine(_ pro: ProStore) -> String {
        "trialStart=\(Int(pro.trialStart.timeIntervalSince1970)) now=\(Int(pro.now.timeIntervalSince1970)) " +
        "inTrial=\(pro.inTrial) hasReward=\(pro.hasReward) purchased=\(pro.purchased) hasPro=\(pro.hasPro) " +
        "appTransaction=\(appTransactionNote)"
    }

    /// One line on stdout for `xcrun simctl launch --console` (anti-abuse checks).
    @MainActor static func logState(_ pro: ProStore) {
        NSLog("%@", "WeightKitDebug trialStart=\(pro.trialStart.timeIntervalSince1970) trialEnd=\(pro.trialEnd.timeIntervalSince1970) " +
              "now=\(pro.now.timeIntervalSince1970) inTrial=\(pro.inTrial) hasReward=\(pro.hasReward) " +
              "purchased=\(pro.purchased) hasPro=\(pro.hasPro) trialStarted=\(pro.trialStarted)")
    }

    /// Runs before any store is created (WeightKitApp.init).
    static func applyLaunchArguments() {
        if args.contains("-wkReset") {
            if let id = Bundle.main.bundleIdentifier { UserDefaults.standard.removePersistentDomain(forName: id) }
            Keychain.removeAll()
            ListStore.deleteSavedLists()
        }
    }
}
#endif
