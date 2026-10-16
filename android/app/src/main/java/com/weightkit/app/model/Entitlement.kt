package com.weightkit.app.model

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Time source. [wallMillis] is the device clock (the user can change it);
 * [monoMillis] is a monotonic clock that the user cannot change (it restarts at reboot).
 */
interface Clock {
    fun wallMillis(): Long
    fun monoMillis(): Long
}

object SystemTimeClock : Clock {
    override fun wallMillis() = System.currentTimeMillis()
    override fun monoMillis() = android.os.SystemClock.elapsedRealtime()
}

/** Minimal persistent key-value store: SharedPreferences in the app, a map in unit tests. */
interface KeyValueStore {
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

class PrefsStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) = prefs.edit { putLong(key, value) }
    override fun getBoolean(key: String, default: Boolean) = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) = prefs.edit { putBoolean(key, value) }
}

/**
 * Pro state without any store/billing code, so it can be unit-tested with a fake clock.
 * Identical rules on iOS (Entitlement.swift).
 *
 *  • Trial: 7 × 24 h from the earliest known install time.
 *  • Reward: exactly 24 h from the moment it is granted.
 *  • Purchased overrides trial and reward; a refund/revocation (purchased = false) removes it.
 *  • Clock rollback: [now] never goes below the latest time seen, and while the wall clock is
 *    behind, time keeps advancing with the monotonic clock, so moving the date back does not
 *    freeze (= extend) the trial or the reward.
 */
class Entitlement(
    private val store: KeyValueStore,
    private val clock: Clock,
    firstLaunch: Long,
    installTime: Long,
) {
    companion object {
        const val TRIAL_DAYS = 7
        const val REWARD_HOURS = 24
        const val TRIAL_MS = TRIAL_DAYS * 24L * 3600 * 1000
        const val REWARD_MS = REWARD_HOURS * 3600L * 1000
        private const val WRITE_EVERY_MS = 60_000L
    }

    /** Local fallback, used until apps-api has answered at least once. */
    val localTrialStart: Long = minOf(firstLaunch, installTime)

    // ---- apps-api (server is the source of truth for the trial and for Pro) ----
    private var serverTrialStart by mutableStateOf<Long?>(null)
    private var serverTrialEnd by mutableStateOf<Long?>(null)
    /** server clock − device clock at the time of the answer (ms). */
    private var serverOffset by mutableLongStateOf(0L)
    /** Server time of the answer and the monotonic clock at that moment (-1 = cached answer, unknown). */
    private var serverAnchor by mutableLongStateOf(0L)
    private var serverAnchorMono by mutableLongStateOf(-1L)
    /** apps-api says this device has a verified purchase. */
    var serverPro by mutableStateOf(false)
        private set

    /**
     * Apply a hello() answer. [fresh] = it just arrived (anchor it to the monotonic clock);
     * false = the cached answer loaded at start-up (the monotonic clock may have restarted since).
     */
    fun applyServer(serverTime: Long, trialStart: Long, trialEnd: Long, clockOffset: Long, pro: Boolean, fresh: Boolean = true) {
        serverTrialStart = trialStart
        serverTrialEnd = trialEnd
        serverOffset = clockOffset
        serverAnchor = serverTime
        serverAnchorMono = if (fresh) clock.monoMillis() else -1L
        serverPro = pro
    }

    val hasServerTrial: Boolean get() = serverTrialEnd != null

    val trialStart: Long get() = serverTrialStart ?: localTrialStart
    val trialEnd: Long get() = serverTrialEnd ?: (localTrialStart + TRIAL_MS)

    /**
     * "Now" for the trial.
     *  • Server answered in this boot: server time of the answer + monotonic time since → exact,
     *    the device clock does not matter at all.
     *  • Cached answer (after a restart): device clock + server offset, but never before the
     *    server time of that answer and never before the guarded local time.
     *  • No server answer ever: the guarded local time.
     */
    private val trialNow: Long
        get() {
            if (!hasServerTrial) return now
            val mono = clock.monoMillis()
            if (serverAnchorMono >= 0 && mono >= serverAnchorMono) return serverAnchor + (mono - serverAnchorMono)
            return maxOf(clock.wallMillis() + serverOffset, serverAnchor, now)
        }

    /** Current time that cannot be moved backwards by changing the device clock. */
    val now: Long get() = nowAt(clock.monoMillis())

    private fun nowAt(mono: Long): Long {
        val lastSeen = store.getLong("lastSeen", 0L)
        val lastMono = store.getLong("lastMono", -1L)
        // Same boot (monotonic clock did not restart): real time has passed by at least mono − lastMono.
        val advanced = if (lastMono >= 0 && mono >= lastMono) lastSeen + (mono - lastMono) else lastSeen
        return maxOf(clock.wallMillis(), advanced)
    }

    /** Call on launch / resume. Persists the latest time seen together with the monotonic reading. */
    fun touchClock() {
        val mono = clock.monoMillis()
        val n = nowAt(mono)
        val lastMono = store.getLong("lastMono", -1L)
        if (n > store.getLong("lastSeen", 0L) + WRITE_EVERY_MS || lastMono < 0 || mono < lastMono) {
            store.putLong("lastSeen", n)
            store.putLong("lastMono", mono)
        }
    }

    var purchased by mutableStateOf(store.getBoolean("proCached", false))
        private set
    var rewardUntil by mutableLongStateOf(store.getLong("rewardUntil", 0L))
        private set

    fun updatePurchased(v: Boolean) {
        purchased = v
        store.putBoolean("proCached", v)
    }

    fun grantReward() {
        rewardUntil = now + REWARD_MS
        store.putLong("rewardUntil", rewardUntil)
    }

    private val owned: Boolean get() = purchased || serverPro
    val inTrial: Boolean get() = !owned && trialNow < trialEnd
    val hasReward: Boolean get() = !owned && now < rewardUntil
    val hasPro: Boolean get() = owned || inTrial || hasReward
}

/** Store-independent view of one purchase, so the ownership rule can be unit-tested. */
data class PurchaseInfo(val products: List<String>, val purchased: Boolean)

object PurchaseRules {
    /** Only purchases in PURCHASED state count; PENDING (unpaid) purchases never unlock Pro. */
    fun ownsPro(purchases: List<PurchaseInfo>, productId: String): Boolean =
        purchases.any { productId in it.products && it.purchased }
}

/**
 * Free-tier ad rules (see MONETIZATION.md §4), store independent. Identical on iOS (AdPolicy.swift).
 *  • No ads while the user has Pro (purchase, trial or reward).
 *  • Full-screen ads (interstitial and rewarded share one limit): at most once per 48 h;
 *    the first one only 48 h after ads became possible.
 *  • The banner is shown to free users only.
 */
class AdPolicy(private val store: KeyValueStore, private val now: () -> Long) {
    companion object {
        const val FULL_SCREEN_GAP_MS = 48L * 3600 * 1000
    }

    fun adsActive(canRequestAds: Boolean, hasPro: Boolean) = canRequestAds && !hasPro

    fun showBanner(canRequestAds: Boolean, hasPro: Boolean) = adsActive(canRequestAds, hasPro)

    private fun lastFullScreen(): Long {
        val t = store.getLong("lastFullScreenAd", 0L)
        if (t == 0L) {
            // start the 48 h clock the first time ads could be shown
            val n = now()
            store.putLong("lastFullScreenAd", n)
            return n
        }
        return t
    }

    fun fullScreenDue(canRequestAds: Boolean, hasPro: Boolean): Boolean =
        adsActive(canRequestAds, hasPro) && now() - lastFullScreen() >= FULL_SCREEN_GAP_MS

    /** Call when a full-screen ad (interstitial OR rewarded) is presented. */
    fun markFullScreen() = store.putLong("lastFullScreenAd", now())
}
