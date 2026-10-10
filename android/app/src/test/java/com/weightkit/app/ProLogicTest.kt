package com.weightkit.app

import com.weightkit.app.model.AdPolicy
import com.weightkit.app.model.Entitlement
import com.weightkit.app.model.PurchaseInfo
import com.weightkit.app.model.PurchaseRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementTest {
    private val clock = FakeClock()
    private val store = MemStore()
    private val install = clock.wall

    private fun ent(firstLaunch: Long = install, installTime: Long = install) =
        Entitlement(store, clock, firstLaunch, installTime).also { it.touchClock() }

    @Test fun trial_day0_day13_9_active_day14_ended() {
        val e = ent()
        assertTrue(e.inTrial); assertTrue(e.hasPro)
        clock.advance((13.9 * DAY).toLong())
        assertTrue(e.inTrial)
        clock.advance((0.1 * DAY).toLong() - 1)
        assertTrue("1 ms before the end", e.inTrial)
        clock.advance(1)
        assertFalse(e.inTrial); assertFalse(e.hasPro)
        assertEquals(14 * DAY, e.trialEnd - e.trialStart)
    }

    @Test fun clockBack30Days_doesNotExtendTrial() {
        val e = ent()
        clock.advance((13.9 * DAY).toLong()); e.touchClock()
        clock.wall -= 30 * DAY                       // user sets the date back
        assertTrue("still day 13.9", e.inTrial)
        clock.mono += (0.1 * DAY).toLong()           // real time passes, wall clock stays behind
        clock.wall += (0.1 * DAY).toLong()
        assertFalse(e.inTrial)
        // also across an app restart (same store, same boot)
        val again = Entitlement(store, clock, clock.wall, install)
        assertFalse(again.inTrial)
    }

    @Test fun clockBack_withoutTouch_afterRelaunch() {
        val e = ent()
        clock.advance(13 * DAY); e.touchClock()
        clock.wall -= 30 * DAY; clock.mono += 2 * DAY // app closed for 2 days, date set back meanwhile
        assertFalse(Entitlement(store, clock, clock.wall, install).inTrial)
    }

    @Test fun clearData_keepsInstallTime() {
        clock.advance(20 * DAY)
        // "Clear data": first-launch pref is new, PackageInfo.firstInstallTime is not
        val e = Entitlement(MemStore(), clock, firstLaunch = clock.wall, installTime = install)
        assertEquals(install, e.trialStart)
        assertFalse(e.inTrial)
    }

    @Test fun reward_lastsExactly24h() {
        val e = ent()
        clock.advance(15 * DAY)
        assertFalse(e.hasPro)
        e.grantReward()
        assertTrue(e.hasReward); assertTrue(e.hasPro)
        clock.advance(24 * HOUR - 1)
        assertTrue(e.hasReward)
        clock.advance(1)
        assertFalse(e.hasReward); assertFalse(e.hasPro)
    }

    @Test fun reward_notExtendedByClockBack() {
        val e = ent()
        clock.advance(15 * DAY); e.grantReward(); e.touchClock()
        clock.wall -= 30 * DAY
        clock.mono += 24 * HOUR
        assertFalse(e.hasReward)
    }

    @Test fun reward_survivesRestart() {
        val e = ent()
        clock.advance(15 * DAY); e.grantReward()
        clock.advance(HOUR)
        assertTrue(Entitlement(store, clock, install, install).hasReward)
    }

    @Test fun purchased_overridesEverything_refundRemovesIt() {
        val e = ent()
        clock.advance(30 * DAY)
        e.updatePurchased(true)
        assertTrue(e.hasPro); assertFalse(e.inTrial); assertFalse(e.hasReward)
        assertTrue("cached until first store refresh", Entitlement(store, clock, install, install).purchased)
        e.updatePurchased(false) // refund / revocation seen on the next store refresh
        assertFalse(e.hasPro)
    }

    @Test fun purchased_duringTrial_hidesTrialNotice() {
        val e = ent()
        e.updatePurchased(true)
        assertFalse(e.inTrial)
        assertTrue(e.hasPro)
    }

    @Test fun pendingPurchase_doesNotUnlock() {
        val id = "weightkit_pro"
        assertFalse(PurchaseRules.ownsPro(listOf(PurchaseInfo(listOf(id), purchased = false)), id))
        assertFalse(PurchaseRules.ownsPro(listOf(PurchaseInfo(listOf("other"), purchased = true)), id))
        assertFalse(PurchaseRules.ownsPro(emptyList(), id))
        assertTrue(PurchaseRules.ownsPro(listOf(PurchaseInfo(listOf("other"), false), PurchaseInfo(listOf(id), true)), id))
    }
}

class AdPolicyTest {
    private val clock = FakeClock()
    private val store = MemStore()
    private val ent = Entitlement(store, clock, clock.wall, clock.wall).also { it.touchClock() }
    private val ads = AdPolicy(store) { ent.now }

    private fun due() = ads.fullScreenDue(canRequestAds = true, hasPro = ent.hasPro)

    @Test fun noAds_whileTrial_proOrReward() {
        assertFalse(ads.adsActive(true, ent.hasPro))            // trial
        assertFalse(ads.showBanner(true, ent.hasPro))
        clock.advance(100 * DAY)
        assertFalse("trial clock not started while Pro", store.map.containsKey("lastFullScreenAd"))
        ent.updatePurchased(true)
        assertFalse(ads.adsActive(true, ent.hasPro)); assertFalse(due()); assertFalse(ads.showBanner(true, ent.hasPro))
        ent.updatePurchased(false)
        ent.grantReward()
        assertFalse(ads.adsActive(true, ent.hasPro)); assertFalse(due()); assertFalse(ads.showBanner(true, ent.hasPro))
        assertFalse("no consent → nothing", ads.adsActive(false, false))
    }

    @Test fun bannerOnlyForFreeUsers() {
        clock.advance(14 * DAY)
        assertTrue(ads.showBanner(true, ent.hasPro))
        assertFalse(ads.showBanner(false, ent.hasPro))
    }

    @Test fun firstFullScreen_only48hAfterAdsStart_thenAtMostEvery48h() {
        clock.advance(14 * DAY)                                  // trial over → ads possible
        assertFalse(due())                                       // starts the 48 h clock
        clock.advance(48 * HOUR - 1)
        assertFalse(due())
        clock.advance(1)
        assertTrue(due())
        ads.markFullScreen()                                     // interstitial shown
        assertFalse(due())
        clock.advance(47 * HOUR)
        assertFalse("rewarded shares the limit", due())
        clock.advance(HOUR)
        assertTrue(due())
        ads.markFullScreen()                                     // rewarded shown
        clock.advance(24 * HOUR)
        assertFalse("interstitial after rewarded", due())
    }

    @Test fun clockBack_doesNotMakeAdsDueEarlier_norLater() {
        clock.advance(14 * DAY); due()
        clock.advance(48 * HOUR); ads.markFullScreen(); ent.touchClock()
        clock.wall += 10 * DAY                                   // forward: due (user's own choice)
        assertTrue(due())
        clock.wall -= 10 * DAY
        assertFalse(due())
    }
}
