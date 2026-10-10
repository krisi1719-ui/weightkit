package com.weightkit.app

import com.weightkit.app.model.CalcOutcome
import com.weightkit.app.model.CalcResult
import com.weightkit.app.model.CalcState
import com.weightkit.app.model.ShapeKind
import com.weightkit.app.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** All 13 shapes through the same code path the screen uses (CalcState.compute). */
class FormulaTest {
    private val steel = 7850.0

    private fun calc(shape: ShapeKind, vararg v: Pair<String, String>, length: String = "1"): CalcState =
        CalcState(catalog, UnitSystem.METRIC).apply {
            this.shape = shape
            fields.clear()
            v.forEach { (k, x) -> fields[k] = x }
            lengthText = length
        }

    private fun ok(c: CalcState): CalcResult {
        val o = c.compute(steel)
        assertTrue("expected Ok, got $o", o is CalcOutcome.Ok)
        return (o as CalcOutcome.Ok).r
    }

    private fun kgM(shape: ShapeKind, vararg v: Pair<String, String>) = ok(calc(shape, *v)).perMeterKg!!

    @Test fun plate_1000x1000x10_is_78_5kg() {
        val r = ok(calc(ShapeKind.PLATE, "t" to "10", "w" to "1000", "pl" to "1000"))
        assertEquals(78.5, r.pieceKg, 1e-9)
        assertEquals(0.01, r.volumeM3, 1e-12)
    }

    @Test fun defaultScreen_isPlate_and_quantityMultiplies() {
        val c = CalcState(catalog, UnitSystem.METRIC) // 10 × 1000 × 2000
        c.qtyText = "3"
        val r = ok(c)
        assertEquals(157.0, r.pieceKg, 1e-9)
        assertEquals(471.0, r.totalKg, 1e-9)
    }

    @Test fun round_d20() = assertEquals(2.466, kgM(ShapeKind.ROUND, "d" to "20"), 0.0005)
    @Test fun square_10() = assertEquals(0.785, kgM(ShapeKind.SQUARE, "a" to "10"), 1e-9)
    @Test fun hex_24AF() = assertEquals(3.916, kgM(ShapeKind.HEX, "s" to "24"), 0.0005)
    @Test fun flat_50x5() = assertEquals(1.9625, kgM(ShapeKind.FLAT, "w" to "50", "t" to "5"), 1e-9)
    @Test fun pipe_60_3x3_6() = assertEquals(5.03, kgM(ShapeKind.PIPE, "od" to "60.3", "wt" to "3.6"), 0.005)
    @Test fun rectTube_40x40x2() =
        assertEquals(2.386, kgM(ShapeKind.RECT_TUBE, "h" to "40", "w" to "40", "wt" to "2"), 0.0005)
    @Test fun angle_50x50x5() = assertEquals(475e-6 * steel, kgM(ShapeKind.ANGLE, "a" to "50", "b" to "50", "t" to "5"), 1e-9)
    @Test fun channel_100x50x6x8_5() =
        assertEquals(1348e-6 * steel, kgM(ShapeKind.CHANNEL, "h" to "100", "b" to "50", "tw" to "6", "tf" to "8.5"), 1e-9)
    @Test fun ibeam_200x100x5_6x8_5() =
        assertEquals(2724.8e-6 * steel, kgM(ShapeKind.IBEAM, "h" to "200", "b" to "100", "tw" to "5.6", "tf" to "8.5"), 1e-9)
    @Test fun tee_100x100x10x10() =
        assertEquals(1900e-6 * steel, kgM(ShapeKind.TEE, "h" to "100", "b" to "100", "tw" to "10", "tf" to "10"), 1e-9)

    @Test fun rebar_d12() {
        val c = calc(ShapeKind.REBAR).apply { rebarIndex = 3 }
        val r = ok(c)
        assertEquals(0.888, r.perMeterKg!!, 0.001)
        assertEquals(0.888, r.tableKgM!!, 0.0)
    }

    @Test fun section_IPE200_usesTabulatedArea() {
        val c = calc(ShapeKind.SECTION).apply { familyId = "ipe"; itemIndex = 6 }
        assertEquals("IPE 200", c.sectionItem!!.name)
        val r = ok(c)
        assertEquals(28.5e2 * 1e-6 * steel, r.perMeterKg!!, 1e-9)
        assertEquals(22.4, r.tableKgM!!, 0.0)
    }

    @Test fun proTable_pickedInTrial_notUsedWithoutPro() {
        val c = calc(ShapeKind.SECTION).apply { familyId = "heb"; itemIndex = 0 }
        assertEquals("heb", c.family!!.id)
        c.proAllowed = false                         // trial over
        assertEquals("ipe", c.family!!.id)           // falls back to the first free table
        assertEquals(7.64e2 * 1e-6 * steel, ok(c).perMeterKg!!, 1e-9)
        c.proAllowed = true                          // bought → selection comes back
        assertEquals("heb", c.family!!.id)
    }

    @Test fun commaDecimal_isAccepted() = assertEquals(
        kgM(ShapeKind.FLAT, "w" to "12.5", "t" to "5"), kgM(ShapeKind.FLAT, "w" to "12,5", "t" to "5"), 0.0,
    )

    @Test fun zeroEmptyNegative_areMissing() {
        for (v in listOf("0", "", "-5", "abc", "1e3", "12f", "0x10", " ")) {
            assertTrue("'$v'", calc(ShapeKind.ROUND, "d" to v).compute(steel) is CalcOutcome.Missing)
        }
        assertTrue(calc(ShapeKind.ROUND, "d" to "20", length = "0").compute(steel) is CalcOutcome.Missing)
        assertTrue(calc(ShapeKind.ROUND, "d" to "20", length = "").compute(steel) is CalcOutcome.Missing)
        assertTrue(calc(ShapeKind.PLATE, "t" to "10", "w" to "0", "pl" to "5").compute(steel) is CalcOutcome.Missing)
    }

    @Test fun wallTooThick() {
        fun wall(shape: ShapeKind, vararg v: Pair<String, String>) =
            assertTrue("$shape ${v.toList()}", calc(shape, *v).compute(steel) is CalcOutcome.Wall)
        wall(ShapeKind.PIPE, "od" to "10", "wt" to "6")
        wall(ShapeKind.RECT_TUBE, "h" to "40", "w" to "20", "wt" to "11")
        wall(ShapeKind.ANGLE, "a" to "50", "b" to "30", "t" to "30")
        wall(ShapeKind.CHANNEL, "h" to "20", "b" to "50", "tw" to "5", "tf" to "10")
        wall(ShapeKind.IBEAM, "h" to "100", "b" to "50", "tw" to "60", "tf" to "5")
        wall(ShapeKind.TEE, "h" to "10", "b" to "50", "tw" to "5", "tf" to "10")
        // limit case: wall = radius is a solid bar, still valid
        assertEquals(kgM(ShapeKind.ROUND, "d" to "10"), kgM(ShapeKind.PIPE, "od" to "10", "wt" to "5"), 1e-9)
    }

    @Test fun quantity_isClamped() {
        val c = calc(ShapeKind.ROUND, "d" to "20")
        for ((txt, q) in listOf("" to 1, "0" to 1, "-4" to 1, "2,7" to 2, "99999999999999999999" to CalcState.MAX_QTY)) {
            c.qtyText = txt
            assertEquals(txt, q, c.quantity)
        }
    }
}
