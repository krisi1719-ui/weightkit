package com.weightkit.app.model

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.weightkit.app.BuildConfig

/** AdMob unit IDs. Debug builds always use Google's test IDs. */
object AdIds {
    private const val TEST_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_INTERSTITIAL = "ca-app-pub-3940256099942544/1033173712"
    private const val TEST_REWARDED = "ca-app-pub-3940256099942544/5224354917"

    // Real WeightKit (Android) units – used only in release builds. Debug always uses Google's test units.
    private const val BANNER = "ca-app-pub-4705847427752554/2996309280"
    private const val INTERSTITIAL = "ca-app-pub-4705847427752554/5166167896"
    private const val REWARDED = "ca-app-pub-4705847427752554/2118341884"

    val banner get() = if (BuildConfig.DEBUG) TEST_BANNER else BANNER
    val interstitial get() = if (BuildConfig.DEBUG) TEST_INTERSTITIAL else INTERSTITIAL
    val rewarded get() = if (BuildConfig.DEBUG) TEST_REWARDED else REWARDED
}

/**
 * Ad policy for the free tier:
 *  • No ads at all while the user has Pro (purchase, 14-day trial or 24 h reward).
 *  • Full-screen ads (interstitial or rewarded) at most once every 48 h; the first one only
 *    48 h after ads become possible. Never on the calculator or while a result is shown:
 *    the interstitial is only shown when the user opens the Lists tab.
 *  • One small banner, on the Lists screen only.
 *  • Rewarded ad is opt-in: "watch an ad → Pro for 24 h".
 */
class AdsManager(ctx: Context, private val pro: ProManager) {
    // application context only (no Activity is kept), so the process-wide holder does not leak
    private val app = ctx.applicationContext
    private val prefs = app.getSharedPreferences("weightkit", Context.MODE_PRIVATE)

    var canRequestAds by mutableStateOf(false)
        private set
    var rewardedReady by mutableStateOf(false)
        private set

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var started = false

    /**
     * Consent (UMP, required for EEA/UK) and SDK start. Call from the Activity on launch / resume.
     * Nothing happens while the user has Pro (trial, reward, purchase): no consent form and no ATT-like
     * prompt on first launch or over the calculator – only once ads are actually possible.
     */
    fun start(activity: Activity) {
        if (pro.hasPro) return
        if (started) { if (canRequestAds) { fullScreenDue; preload() }; return }
        started = true
        val info = UserMessagingPlatform.getConsentInformation(activity)
        info.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                if (info.canRequestAds()) afterConsent()
            }
        }, { _ -> if (info.canRequestAds()) afterConsent() })
        if (info.canRequestAds()) afterConsent()
    }

    /** True when UMP says the user must be able to change consent (EEA/UK/CH) – show "Ad privacy settings". */
    fun privacyOptionsRequired(ctx: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(ctx).privacyOptionsRequirementStatus ==
            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ -> }
    }

    private fun afterConsent() {
        if (canRequestAds) return
        canRequestAds = true
        fullScreenDue // ads are possible from now on: starts the 48 h clock for the first full-screen ad
        MobileAds.initialize(app) {}
        preload()
    }

    private val policy = AdPolicy(PrefsStore(prefs)) { pro.now }

    /** Ads are only relevant for free users. */
    val adsActive: Boolean get() = policy.adsActive(canRequestAds, pro.hasPro)

    val fullScreenDue: Boolean get() = policy.fullScreenDue(canRequestAds, pro.hasPro)

    /** Offer "watch ad → 24 h Pro" only to free users, at most every 48 h. */
    val rewardedOffer: Boolean get() = rewardedReady && fullScreenDue

    fun preload() {
        if (!canRequestAds) return
        if (interstitial == null) {
            InterstitialAd.load(app, AdIds.interstitial, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) { interstitial = null }
            })
        }
        if (rewarded == null) {
            RewardedAd.load(app, AdIds.rewarded, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedReady = true }
                override fun onAdFailedToLoad(error: LoadAdError) { rewarded = null; rewardedReady = false }
            })
        }
    }

    private fun callback(onDone: () -> Unit) = object : FullScreenContentCallback() {
        override fun onAdDismissedFullScreenContent() { onDone(); preload() }
        override fun onAdFailedToShowFullScreenContent(error: AdError) { onDone(); preload() }
    }

    /** Called when the user opens the Lists tab (natural break, never mid-calculation). */
    fun showInterstitialIfDue(activity: Activity) {
        if (!fullScreenDue) return // evaluated first: also starts the 48 h clock
        val ad = interstitial ?: return
        policy.markFullScreen()
        ad.fullScreenContentCallback = callback { interstitial = null }
        ad.show(activity)
    }

    fun showRewarded(activity: Activity) {
        if (!fullScreenDue) return
        val ad = rewarded ?: return
        // counts towards the 48 h limit even if the user closes it before the reward
        policy.markFullScreen()
        ad.fullScreenContentCallback = callback { rewarded = null; rewardedReady = false }
        ad.show(activity) { _ -> pro.grantReward() }
    }
}

// Holds only the application context (see AdsManager.app), so this is not an Activity leak.
@android.annotation.SuppressLint("StaticFieldLeak")
object AdsHolder {
    const val CONTACT_EMAIL = "ggshoplimitedco@gmail.com"
    const val PRIVACY_URL = "https://ggshop-apps.blogspot.com/p/weightkit-privacy-policy.html"

    @Volatile private var am: AdsManager? = null
    fun get(ctx: Context, pro: ProManager): AdsManager = am ?: synchronized(this) {
        am ?: AdsManager(ctx.applicationContext, pro).also { am = it }
    }
}
