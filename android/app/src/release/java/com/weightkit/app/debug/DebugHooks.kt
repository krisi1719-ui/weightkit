package com.weightkit.app.debug

import android.content.Context
import com.weightkit.app.model.BillingOverride
import com.weightkit.app.model.Clock
import com.weightkit.app.model.SystemTimeClock

/** Release twin of the debug hooks: real clock, real billing. */
object DebugHooks {
    @Suppress("UNUSED_PARAMETER")
    fun clock(ctx: Context): Clock = SystemTimeClock

    @Suppress("UNUSED_PARAMETER")
    fun billing(ctx: Context): BillingOverride? = null

    fun installOffsetMs(): Long = 0L

    @Suppress("UNUSED_PARAMETER")
    fun logState(pro: com.weightkit.app.model.ProManager) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun serverEnabled(ctx: Context): Boolean = true
}
