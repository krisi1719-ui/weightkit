package com.weightkit.app

import com.weightkit.app.model.Entitlement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** apps-api answers (hello) override the local trial; Pro from the server counts like a Play purchase. */
class ServerTrialTest {
    private val clock = FakeClock()
    private val store = MemStore()

    private fun ent(installTime: Long = clock.wall) =
        Entitlement(store, clock, clock.wall, installTime).also { it.touchClock() }

    @Test fun serverTrialStart_overridesLocal_afterClearData() {
        // "Clear data" + reinstall locally looks like a fresh install today...
        val e = ent()
        assertTrue(e.inTrial)
        // ...but the server remembers the device started 20 days ago.
        val start = clock.wall - 20 * DAY
        e.applyServer(clock.wall, start, start + 14 * DAY, clockOffset = 0, pro = false)
        assertEquals(start, e.trialStart)
        assertFalse(e.inTrial)
        assertFalse(e.hasPro)
    }

    @Test fun serverClockDecides_phoneClockWrong() {
        val e = ent()
        val serverNow = clock.wall
        // phone clock is 10 days behind the server → offset +10 days
        clock.wall -= 10 * DAY
        e.applyServer(serverNow, serverNow - 13 * DAY, serverNow + DAY, clockOffset = 10 * DAY, pro = false)
        assertTrue("1 day left by the server clock", e.inTrial)
        clock.advance(DAY)
        assertFalse(e.inTrial)
    }

    @Test fun serverPro_givesPro_withoutPlayPurchase() {
        val e = ent(installTime = clock.wall - 60 * DAY)
        val start = clock.wall - 60 * DAY
        e.applyServer(clock.wall, start, start + 14 * DAY, 0, pro = true)
        assertTrue(e.hasPro)
        assertFalse("Pro owners are not 'in trial'", e.inTrial)
        e.applyServer(clock.wall, start, start + 14 * DAY, 0, pro = false) // revoked/refunded
        assertFalse(e.hasPro)
    }

    @Test fun serverTrialDays_changeAppliesImmediately() {
        val e = ent()
        val start = clock.wall - 20 * DAY
        e.applyServer(clock.wall, start, start + 14 * DAY, 0, false)
        assertFalse(e.inTrial)
        e.applyServer(clock.wall, start, start + 30 * DAY, 0, false) // trial_days raised to 30 in apps.json
        assertTrue(e.inTrial)
    }

    @Test fun cachedServerTrial_notExtendedByClockBack() {
        val e = ent()
        val start = clock.wall - 13 * DAY
        e.applyServer(clock.wall, start, start + 14 * DAY, 0, false)
        assertTrue(e.inTrial)
        clock.advance(DAY / 2); e.touchClock()
        clock.wall -= 30 * DAY // offline, user moves the date back
        clock.mono += DAY / 2
        assertFalse(e.inTrial)
    }

    @Test fun cachedAnswerAfterReboot_clockBackDoesNotExtend() {
        val start = clock.wall - 13 * DAY
        val answeredAt = clock.wall
        // app restart after a reboot: only the cached answer, monotonic anchor unknown
        val e = ent()
        e.applyServer(answeredAt, start, start + 14 * DAY, 0, false, fresh = false)
        assertTrue(e.inTrial)
        clock.wall -= 30 * DAY // offline, date moved back: never before the server time of the answer
        assertTrue("still day 13", e.inTrial)
        clock.advance(DAY) // a real day passes
        assertFalse(e.inTrial)
    }

    @Test fun noServerAnswer_usesLocalTrial() {
        val e = ent()
        assertFalse(e.hasServerTrial)
        assertEquals(14 * DAY, e.trialEnd - e.trialStart)
        assertTrue(e.inTrial)
    }
}
