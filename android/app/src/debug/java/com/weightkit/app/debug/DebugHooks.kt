package com.weightkit.app.debug

import android.content.Context
import androidx.core.content.edit
import com.weightkit.app.model.BillingOverride
import com.weightkit.app.model.Clock
import com.weightkit.app.model.PurchaseInfo
import com.weightkit.app.model.ProManager
import com.weightkit.app.model.SystemTimeClock

/**
 * DEBUG BUILDS ONLY (src/debug). The release source set has a no-op twin, so none of this is in a
 * release APK. Driven by the "weightkit_debug" prefs, which UI tests write before launching:
 *   clockOffsetMs  – shifts the wall clock (e.g. +20 days = trial over)
 *   fakeBilling    – use [FakeBilling] instead of Play Billing
 *   fakeOwned / fakePending – what the fake store reports
 * and by system properties, which survive "Clear data" and reinstall (anti-abuse checks via adb):
 *   adb shell setprop debug.weightkit.clock_days <d>    extra wall-clock offset in days (may be negative)
 *   adb shell setprop debug.weightkit.install_days <d>  pretend the APK was installed <d> days earlier/later
 */
object DebugHooks {
    const val PREFS = "weightkit_debug"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun prop(name: String): Double = runCatching {
        Class.forName("android.os.SystemProperties").getMethod("get", String::class.java)
            .invoke(null, "debug.weightkit.$name") as String
    }.getOrNull()?.toDoubleOrNull() ?: 0.0

    private const val DAY = 24L * 3600 * 1000

    fun clock(ctx: Context): Clock {
        val p = prefs(ctx)
        val propOffset = (prop("clock_days") * DAY).toLong()
        return object : Clock {
            override fun wallMillis() = SystemTimeClock.wallMillis() + p.getLong("clockOffsetMs", 0L) + propOffset
            override fun monoMillis() = SystemTimeClock.monoMillis()
        }
    }

    fun installOffsetMs(): Long = (prop("install_days") * DAY).toLong()

    /** One line for adb logcat -s WeightKitDebug. */
    fun logState(pro: ProManager) = android.util.Log.i(
        "WeightKitDebug",
        "trialStart=${pro.trialStart} trialEnd=${pro.trialEnd} now=${pro.now} inTrial=${pro.inTrial} " +
            "hasReward=${pro.hasReward} purchased=${pro.purchased} hasPro=${pro.hasPro}",
    )

    fun billing(ctx: Context): BillingOverride? {
        val p = prefs(ctx)
        if (!p.getBoolean("fakeBilling", false)) return null
        return object : BillingOverride {
            override fun purchases() = listOf(
                PurchaseInfo(listOf(ProManager.PRODUCT_ID), purchased = p.getBoolean("fakeOwned", false)),
            ).filter { p.getBoolean("fakeOwned", false) || p.getBoolean("fakePending", false) }
            override fun buy() = p.edit(commit = true) {
                if (p.getBoolean("fakeBuyPending", false)) putBoolean("fakePending", true) else putBoolean("fakeOwned", true)
            }
            override val price = "$4.99"
        }
    }

    /**
     * apps-api is used unless a UI test fakes the clock or the store (those test the local rules),
     * or prefs "noServer" is set.
     */
    fun serverEnabled(ctx: Context): Boolean {
        val p = prefs(ctx)
        return !p.getBoolean("noServer", false) && !p.getBoolean("fakeBilling", false) &&
            p.getLong("clockOffsetMs", 0L) == 0L && prop("clock_days") == 0.0 && prop("install_days") == 0.0
    }
}
