import GoogleMobileAds
import SwiftUI
import UserMessagingPlatform

/// AdMob IDs. DEBUG builds always use Google's test IDs.
/// Release uses the real WeightKit iOS units below; the app ID comes from build setting GAD_APP_ID (project.pbxproj).
enum AdIDs {
    #if DEBUG
    static let banner = "ca-app-pub-3940256099942544/2934735716"
    static let interstitial = "ca-app-pub-3940256099942544/4411468910"
    static let rewarded = "ca-app-pub-3940256099942544/1712485313"
    #else
    // Real WeightKit (iOS) units
    static let banner = "ca-app-pub-4705847427752554/5699239107"
    static let interstitial = "ca-app-pub-4705847427752554/2961395126"
    static let rewarded = "ca-app-pub-4705847427752554/1648313457"
    #endif
}

/// Ad policy for the free tier (see README):
///  • No ads at all while the user has Pro (purchase, 14-day trial or 24 h reward).
///  • Full-screen ads (interstitial or rewarded) at most once every 48 h, and the first one
///    only 48 h after ads become possible. Never on the calculator or while a result is shown:
///    the interstitial is only shown when the user opens the Lists tab.
///  • One small banner, on the Lists screen only.
///  • Rewarded ad is opt-in: "watch an ad → Pro for 24 h".
@MainActor
final class AdsManager: NSObject, ObservableObject, FullScreenContentDelegate {
    @Published private(set) var canRequestAds = false
    @Published private(set) var rewardedReady = false

    private var interstitial: InterstitialAd?
    private var rewarded: RewardedAd?
    private var started = false
    private weak var pro: ProStore?
    private lazy var policy = AdPolicy(store: DeviceStore()) { [weak self] in self?.pro?.now ?? Date() }

    /// Consent (UMP) and SDK start. No App Tracking Transparency prompt: the app does not track (no IDFA). Called on launch and foreground. Nothing happens while the
    /// user has Pro (trial, reward, purchase): no consent form on first launch or
    /// over the calculator – only once ads are actually possible.
    func start(pro: ProStore) {
        self.pro = pro
        guard !pro.hasPro else { return }
        if started { if canRequestAds { _ = fullScreenDue; preload() }; return }
        started = true
        ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters()) { [weak self] _ in
            Task { @MainActor in
                try? await ConsentForm.loadAndPresentIfRequired(from: nil)
                self?.afterConsent()
            }
        }
        // consent from a previous session
        if ConsentInformation.shared.canRequestAds { afterConsent() }
    }

    private func afterConsent() {
        guard ConsentInformation.shared.canRequestAds, !canRequestAds else { return }
        canRequestAds = true
        _ = fullScreenDue // ads are possible from now on: starts the 48 h clock for the first full-screen ad
        MobileAds.shared.start(completionHandler: nil)
        preload()
    }

    /// Ads are only relevant for free users.
    var adsActive: Bool { policy.adsActive(canRequestAds: canRequestAds, hasPro: pro?.hasPro ?? true) }

    var fullScreenDue: Bool { policy.fullScreenDue(canRequestAds: canRequestAds, hasPro: pro?.hasPro ?? true) }

    /// Offer "watch ad → 24 h Pro" only to free users, at most every 48 h.
    var rewardedOffer: Bool { rewardedReady && fullScreenDue }

    func preload() {
        guard canRequestAds else { return }
        if interstitial == nil {
            InterstitialAd.load(with: AdIDs.interstitial, request: Request()) { [weak self] ad, _ in
                Task { @MainActor in
                    self?.interstitial = ad
                    ad?.fullScreenContentDelegate = self
                }
            }
        }
        if rewarded == nil {
            RewardedAd.load(with: AdIDs.rewarded, request: Request()) { [weak self] ad, _ in
                Task { @MainActor in
                    self?.rewarded = ad
                    ad?.fullScreenContentDelegate = self
                    self?.rewardedReady = ad != nil
                }
            }
        }
    }

    /// Called when the user opens the Lists tab (a natural break, never mid-calculation).
    func showInterstitialIfDue() {
        guard fullScreenDue, let ad = interstitial else { return } // due first: also starts the 48 h clock
        policy.markFullScreen()
        ad.present(from: nil)
    }

    func showRewarded() {
        guard fullScreenDue, let ad = rewarded else { return }
        // counts towards the 48 h limit even if the user closes it before the reward
        policy.markFullScreen()
        ad.present(from: nil) { [weak self] in
            Task { @MainActor in self?.pro?.grantReward() }
        }
    }

    // MARK: FullScreenContentDelegate

    nonisolated func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        Task { @MainActor in
            if ad === self.interstitial { self.interstitial = nil }
            if ad === self.rewarded { self.rewarded = nil; self.rewardedReady = false }
            self.preload()
        }
    }

    nonisolated func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        Task { @MainActor in
            self.interstitial = nil
            self.rewarded = nil
            self.rewardedReady = false
            self.preload()
        }
    }
}

/// Small adaptive banner (Lists screen only). Hidden for Pro users.
struct BannerAdView: UIViewRepresentable {
    func makeUIView(context: Context) -> BannerView {
        let width = UIScreen.main.bounds.width
        let v = BannerView(adSize: currentOrientationAnchoredAdaptiveBanner(width: width))
        v.adUnitID = AdIDs.banner
        v.load(Request())
        return v
    }
    func updateUIView(_ uiView: BannerView, context: Context) {}
}

struct BannerSlot: View {
    @EnvironmentObject var ads: AdsManager
    @EnvironmentObject var pro: ProStore
    var body: some View {
        if ads.adsActive {
            BannerAdView()
                .frame(height: 60)
                .frame(maxWidth: .infinity)
                .accessibilityIdentifier("banner_ad")
        }
    }
}
