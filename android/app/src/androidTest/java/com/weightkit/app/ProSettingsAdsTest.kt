package com.weightkit.app

import androidx.compose.ui.test.assertIsSelected
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import com.weightkit.app.model.AdsHolder
import com.weightkit.app.model.AppHolder
import com.weightkit.app.model.ProHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProSettingsAdsTest : UiTestBase() {

    /** Flow 3: free family (IPE) and Pro family (HEB) during the trial, and after it. */
    @Test fun f03_profiles_trial() {
        launch()
        tap("shape_section")
        assertEquals("IPE", text("family_picker_value"))
        assertFalse("IPE is free: no trial notice", exists("trial_notice_profile"))
        tap("family_picker"); tap("family_picker_item_2")               // HEB
        assertEquals("HEB (HE-B)", text("family_picker_value"))
        assertEquals("HEB 100", text("profile_picker_value"))
        assertTrue(exists("trial_notice_profile"))
        assertFalse(exists("paywall"))
        assertTrue(text("result_table").endsWith("20.4 kg/m"))
        screenshot("f03_heb_trial")
        // trial ends while HEB is selected: the Pro table is no longer used
        setClock(20.0)
        relaunch()
        tap("shape_section")
        assertEquals("IPE", text("family_picker_value"))
        assertFalse(text("result_table").endsWith("20.4 kg/m"))
    }

    @Test fun f03b_profiles_afterTrial_paywall_selectionUnchanged() {
        launch(days = 20.0)
        tap("shape_section")
        tap("family_picker"); tap("family_picker_item_3")               // UPN – free
        assertEquals("UPN", text("family_picker_value"))
        tap("family_picker"); tap("family_picker_item_2")               // HEB – Pro
        waitFor("paywall")
        assertEquals("UPN", text("family_picker_value"))
        assertFalse(exists("trial_notice_profile"))
    }

    /** Flow 10: settings – currency, company name, theme. */
    @Test fun f10_settings_currency_company_theme() {
        launch(theme = 0)
        tab("settings")
        type("currency", "лв")
        type("company", "Метал ООД")
        tap("theme_2"); screenshot("f10_settings_dark")
        tap("theme_1"); screenshot("f10_settings_light")
        tap("theme_0")
        tab("calc")
        type("field_price", "1")
        assertTrue(text("result_cost").endsWith("157.00 лв"))
        relaunch()
        tab("settings")
        assertEquals("лв", text("currency"))
        assertEquals("Метал ООД", text("company"))
        node("theme_0").assertIsSelected()
    }

    /** Flow 11: paywall shows the store price; buy → Pro, banner gone; restore; refund removes Pro; pending doesn't unlock. */
    @Test fun f11_paywall_buy_restore_refund_pending() {
        launch(days = 20.0)
        tab("settings")
        tap("settings_pro")
        waitFor("paywall")
        assertTrue(text("buy_pro"), text("buy_pro").contains("$4.99"))
        screenshot("f11_paywall_free")
        // pending purchase: no Pro
        debugPrefs { putBoolean("fakeBuyPending", true) }
        tap("buy_pro")
        assertFalse(exists("pro_active"))
        assertFalse(ProHolder.get(ctx, 0).hasPro)
        // completed purchase
        debugPrefs { putBoolean("fakeBuyPending", false); putBoolean("fakePending", false) }
        tap("buy_pro")
        waitFor("pro_active")
        assertTrue(ProHolder.get(ctx, 0).purchased)
        device.pressBack(); compose.waitForIdle()
        tab("lists")
        assertFalse("no banner for Pro", exists("banner_ad"))
        tab("calc"); tap("shape_section")
        tap("family_picker"); tap("family_picker_item_2")
        assertEquals("HEB (HE-B)", text("family_picker_value"))
        assertFalse("no trial notice when bought", exists("trial_notice_profile"))
        // refund: the store no longer reports the purchase → Pro removed on the next resume
        debugPrefs { putBoolean("fakeOwned", false) }
        scenario!!.moveToState(Lifecycle.State.CREATED); scenario!!.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        assertFalse(ProHolder.get(ctx, 0).hasPro)
        // restore after reinstall / other device
        debugPrefs { putBoolean("fakeOwned", true) }
        tab("settings"); tap("settings_pro"); tap("restore")
        waitFor("pro_active")
    }

    /** Flow 12: ads with Google test IDs and a debug clock. Never clicks an ad. */
    @Test fun f12_ads_interstitialOncePer48h_bannerOnLists_rewarded() {
        launch(days = 0.0)
        tab("lists")
        assertFalse("trial: no banner", exists("banner_ad"))
        assertFalse(adShowing())
        // trial over → consent / SDK start on resume; first Lists visit only starts the 48 h clock
        setClock(20.0)
        relaunch()
        val ads = AdsHolder.get(ctx, ProHolder.get(ctx, 0))
        compose.waitUntil(20_000) { ads.canRequestAds || device.hasObject(By.textContains("consent")) }
        if (device.hasObject(By.text("Do not consent"))) {
            screenshot("f12_consent_form")
            device.findObject(By.text("Do not consent")).click() // privacy-preserving choice; test ads still serve
            compose.waitUntil(10_000) { ads.canRequestAds }
        }
        assumeTrue("Ads SDK could not start (no consent/network)", ads.canRequestAds)
        tab("calc"); tab("lists")
        compose.waitUntil(15_000) { exists("banner_ad") }
        Thread.sleep(4000)
        screenshot("f12_lists_banner")
        assertFalse("first visit only starts the 48 h clock", adShowing())
        tab("calc")
        assertFalse(exists("banner_ad"))                         // banner only on Lists
        // 48 h later: interstitial once
        setClock(22.1)
        compose.waitUntil(30_000) { ads.fullScreenDue && interstitialLoaded(ads) }
        tab("lists")
        compose.waitUntil(20_000) { adShowing() }
        assertFalse("ad.fullScreenDue is reset", ads.fullScreenDue)
        Thread.sleep(2000); screenshot("f12_interstitial")
        closeAd()
        tab("calc"); tab("lists")
        Thread.sleep(3000)
        assertFalse("not again within 48 h", adShowing())
        tab("settings"); tap("settings_pro")
        assertFalse("rewarded not offered within 48 h", exists("reward_offer"))
        device.pressBack()
        // 48 h after the interstitial: rewarded offered → 24 h Pro
        setClock(24.2)
        compose.waitUntil(30_000) { ads.rewardedReady }
        tab("calc"); tab("settings"); tap("settings_pro")
        waitFor("reward_offer", 10_000)
        tap("reward_offer")
        compose.waitUntil(15_000) { adShowing() }
        screenshot("f12_rewarded")
        // test rewarded ads grant the reward after the countdown; then close
        compose.waitUntil(60_000) { ProHolder.get(ctx, 0).hasReward }
        closeAd()
        assertTrue(ProHolder.get(ctx, 0).hasPro)
        tab("lists")
        assertFalse("reward = no ads", exists("banner_ad"))
        setClock(25.3)                                              // 24 h later
        tab("calc"); tab("lists")
        assertFalse(ProHolder.get(ctx, 0).hasReward)
    }

    /** Taps only the ad's close button (never the ad); Back does not close a Google interstitial. */
    private fun closeAd() {
        val deadline = System.currentTimeMillis() + 60_000
        while (adShowing() && System.currentTimeMillis() < deadline) {
            // Android's own "Viewing full screen – Got it" hint (System UI, not the ad) sits on top first
            device.findObject(By.res("com.android.systemui", "ok"))?.let { runCatching { it.click() } }
            // the ad's close control appears after its countdown; Back works from then on as well
            val close = device.findObject(By.descContains("close")) ?: device.findObject(By.descContains("Close"))
            if (close != null) runCatching { close.click() } else device.pressBack()
            Thread.sleep(3000)
        }
        compose.waitUntil(5_000) { !adShowing() }
    }

    private fun interstitialLoaded(ads: com.weightkit.app.model.AdsManager): Boolean =
        ads.javaClass.getDeclaredField("interstitial").apply { isAccessible = true }.get(ads) != null

    /** Flow 15: recreation, backgrounding, "Don't keep activities" and process death keep state and lists. */
    @Test fun f15_lifecycle_stateSurvives() {
        launch()
        tap("shape_round"); type("field_d", "33"); type("field_length", "2,5"); type("field_price", "3")
        tap("add_to_list")
        scenario!!.recreate(); compose.waitForIdle()
        assertEquals("33", text("field_d"))
        scenario!!.moveToState(Lifecycle.State.CREATED); scenario!!.moveToState(Lifecycle.State.RESUMED)
        assertEquals("2,5", text("field_length"))
        // "Don't keep activities": ActivityScenario refuses to run with it, so use plain intents
        scenario!!.close(); scenario = null
        shell("settings put global always_finish_activities 1")
        try {
            val launchIntent = android.content.Intent(ctx, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(launchIntent); compose.waitUntil(10_000) { exists("field_d") }
            device.pressHome(); Thread.sleep(1500)                // activity destroyed by the setting
            ctx.startActivity(launchIntent); compose.waitUntil(10_000) { exists("field_d") }
            assertEquals("33", text("field_d"))
            tab("lists"); assertEquals(1, count("list_row"))
            device.pressHome(); Thread.sleep(1000)
        } finally {
            shell("settings put global always_finish_activities 0")
        }
        scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        // process death: drop the in-memory state and rebuild it from storage, as a cold start would
        scenario!!.moveToState(Lifecycle.State.CREATED)   // onStop saves
        AppHolder.javaClass.getDeclaredField("state").apply { isAccessible = true }.set(AppHolder, null)
        relaunch()
        tab("calc")
        assertEquals("33", text("field_d"))
        assertEquals("3", text("field_price"))
        node("shape_round").assertIsSelected()
        tab("lists"); assertEquals(1, count("list_row"))
    }
}
