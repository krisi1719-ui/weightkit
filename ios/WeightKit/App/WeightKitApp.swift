import SwiftUI

@main
struct WeightKitApp: App {
    @StateObject private var settings: AppSettings
    @StateObject private var pro: ProStore
    @StateObject private var lists = ListStore()
    @StateObject private var calc: CalcModel
    @StateObject private var ads = AdsManager()

    init() {
        #if DEBUG
        DebugHooks.applyLaunchArguments()
        #endif
        let s = AppSettings()
        _settings = StateObject(wrappedValue: s)
        #if DEBUG
        // UI tests: behave as if the store trial was started at the first launch
        _pro = StateObject(wrappedValue: ProStore(trialStartedAt: DebugHooks.autoTrial ? s.firstLaunch : nil))
        #else
        _pro = StateObject(wrappedValue: ProStore())
        #endif
        let c = CalcModel(units: s.units)
        c.restoreSaved()
        _calc = StateObject(wrappedValue: c)
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(settings)
                .environmentObject(pro)
                .environmentObject(lists)
                .environmentObject(calc)
                .environmentObject(ads)
                .preferredColorScheme(settings.theme.scheme)
                .tint(Color.accentColor)
        }
    }
}

struct RootView: View {
    @EnvironmentObject var settings: AppSettings
    @EnvironmentObject var calc: CalcModel
    @EnvironmentObject var pro: ProStore
    @EnvironmentObject var ads: AdsManager
    @Environment(\.scenePhase) private var scenePhase
    @State private var tab = 0
    /// One-time offer of the free trial on the very first launch (before any ad consent).
    @AppStorage("trialOfferShown") private var trialOfferShown = false
    @State private var showTrialOffer = false

    var body: some View {
        TabView(selection: $tab) {
            CalculatorView()
                .tabItem { Label(L("tab_calc"), systemImage: "scalemass") }
                .tag(0)
            ListsView()
                .tabItem { Label(L("tab_lists"), systemImage: "list.bullet.rectangle") }
                .tag(1)
            SettingsView()
                .tabItem { Label(L("tab_settings"), systemImage: "gearshape") }
                .tag(2)
        }
        .onChange(of: settings.units) { newValue in
            calc.switchUnits(to: newValue)
        }
        .onChange(of: tab) { newTab in
            // full-screen ad only at this natural break, at most every 48 h, free users only
            if newTab == 1 { ads.showInterstitialIfDue() }
        }
        .onChange(of: scenePhase) { phase in
            if phase == .active {
                pro.touchClock()
                Task {
                    await pro.refresh()
                    #if DEBUG
                    DebugHooks.logState(pro)
                    #endif
                }
                if !showTrialOffer && !offerTrialOnFirstLaunch { ads.start(pro: pro) } // free users only
            } else if phase == .background {
                calc.save() // the app may be terminated in the background
            }
        }
        .onAppear {
            if offerTrialOnFirstLaunch { trialOfferShown = true; showTrialOffer = true } else { ads.start(pro: pro) }
        }
        .sheet(isPresented: $showTrialOffer, onDismiss: { ads.start(pro: pro) }) { PaywallView() }
        #if DEBUG
        .overlay(alignment: .topLeading) {
            if DebugHooks.isUITest {
                Text(DebugHooks.stateLine(pro)).font(.system(size: 1)).opacity(0.02)
                    .allowsHitTesting(false).accessibilityIdentifier("debug_state")
            }
        }
        #endif
    }
}

extension RootView {
    private var offerTrialOnFirstLaunch: Bool {
        #if DEBUG
        if DebugHooks.isUITest { return false }
        #endif
        return !trialOfferShown && pro.canStartTrial
    }
}

/// Small caption shown on Pro features while the free trial runs.
struct TrialNotice: View {
    @EnvironmentObject var pro: ProStore
    var id = "trial_notice"
    var body: some View {
        if pro.inTrial {
            Label(L("trial_notice", pro.trialEndText), systemImage: "clock")
                .font(.caption)
                .foregroundStyle(.secondary)
                .accessibilityIdentifier(id)
        }
    }
}

struct ProBadge: View {
    var body: some View {
        Text(L("pro_badge"))
            .font(.caption2.weight(.heavy))
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(Color.accentColor, in: Capsule())
            .foregroundStyle(.white)
    }
}
