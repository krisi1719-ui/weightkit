package com.weightkit.app

import com.weightkit.app.model.CalcOutcome
import com.weightkit.app.model.CalcState
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.ShapeKind
import com.weightkit.app.model.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import kotlin.math.abs

class UnitsTest {
    private val saved = Locale.getDefault()
    @After fun restore() = Locale.setDefault(saved)

    private fun allFields() = CalcState(catalog, UnitSystem.METRIC).apply {
        fields.clear()
        // every key used by any shape; awkward values on purpose
        mapOf("t" to "0.5", "w" to "1250", "pl" to "2500", "d" to "12,7", "a" to "33.3", "s" to "17",
            "od" to "60.3", "wt" to "3.6", "h" to "200", "b" to "100", "tw" to "5.6", "tf" to "8.5")
            .forEach { (k, v) -> fields[k] = v }
        lengthText = "6.05"; priceText = "2,35"; targetText = "123.4"; qtyText = "7"
    }

    private fun within(a: Double, b: Double, rel: Double = 1e-4) = abs(a - b) <= abs(b) * rel

    private fun roundTrip(locale: Locale) {
        Locale.setDefault(locale)
        val c = allFields()
        val before = c.fields.toMap() + mapOf("L" to c.lengthText, "P" to c.priceText, "T" to c.targetText)
        val resultsBefore = ShapeKind.entries.map { c.shape = it; c.compute(7850.0) }
        c.switchUnits(UnitSystem.IMPERIAL)
        val resultsImperial = ShapeKind.entries.map { c.shape = it; c.compute(7850.0) }
        c.switchUnits(UnitSystem.METRIC)
        val after = c.fields.toMap() + mapOf("L" to c.lengthText, "P" to c.priceText, "T" to c.targetText)
        for ((k, v) in before) {
            val a = Fmt.parse(after[k]!!)!!
            val b = Fmt.parse(v)!!
            assert(within(a, b)) { "$k: $v → ${after[k]} ($locale)" }
        }
        // same result in both systems (kg internally)
        for (i in resultsBefore.indices) {
            val m = resultsBefore[i]; val im = resultsImperial[i]
            if (m is CalcOutcome.Ok) assertEquals("shape $i", m.r.totalKg, (im as CalcOutcome.Ok).r.totalKg, m.r.totalKg * 1e-9)
        }
    }

    @Test fun metricImperialMetric_keepsEveryField_en() = roundTrip(Locale.US)
    @Test fun metricImperialMetric_keepsEveryField_bg() = roundTrip(Locale.forLanguageTag("bg-BG"))
    @Test fun metricImperialMetric_keepsEveryField_de() = roundTrip(Locale.GERMANY)

    @Test fun repeatedSwitching_doesNotDrift() {
        Locale.setDefault(Locale.US)
        val c = allFields()
        repeat(50) { c.switchUnits(UnitSystem.IMPERIAL); c.switchUnits(UnitSystem.METRIC) }
        assertEquals("0.5", c.fields["t"])
        assertEquals("12.7", c.fields["d"])
        assertEquals("6.05", c.lengthText)
    }

    @Test fun shownImperialValues() {
        Locale.setDefault(Locale.US)
        val c = allFields()
        c.switchUnits(UnitSystem.IMPERIAL)
        assertEquals("0.5", c.fields["d"])           // 12.7 mm = 0.5 in
        assertEquals("19.8491", c.lengthText)        // 6.05 m in ft
        assertEquals(2.35 * 0.45359237, Fmt.parse(c.priceText)!!, 1e-4) // per lb
        assertEquals(123.4 / 0.45359237, Fmt.parse(c.targetText)!!, 1e-4)
    }

    @Test fun pricePerKg_perLb_sameInternalValue() {
        val c = CalcState(catalog, UnitSystem.METRIC).apply { priceText = "2" }
        assertEquals(2.0, c.pricePerKg!!, 1e-12)
        c.switchUnits(UnitSystem.IMPERIAL)
        assertEquals(2.0, c.pricePerKg!!, 1e-12)
        c.priceText = "1"                       // user types 1 per lb
        assertEquals(1 / 0.45359237, c.pricePerKg!!, 1e-9)
    }

    @Test fun targetWeight_convertedCorrectly() {
        val c = CalcState(catalog, UnitSystem.METRIC).apply { targetText = "100" }
        c.switchUnits(UnitSystem.IMPERIAL)
        assertEquals(100.0, c.targetKg!!, 1e-12)
        c.targetText = "100"                    // now 100 lb
        assertEquals(45.359237, c.targetKg!!, 1e-9)
        c.switchUnits(UnitSystem.METRIC)
        assertEquals("45.3592", c.targetText)
    }

    @Test fun editedImperialValue_isUsedAsTyped() {
        val c = CalcState(catalog, UnitSystem.METRIC).apply { shape = ShapeKind.ROUND; fields["d"] = "20" }
        c.switchUnits(UnitSystem.IMPERIAL)
        c.fields["d"] = "1"
        c.switchUnits(UnitSystem.METRIC)
        assertEquals("25.4", c.fields["d"])
    }

    @Test fun parse_rules() {
        assertEquals(12.5, Fmt.parse("12,5")!!, 0.0)
        assertEquals(12.5, Fmt.parse(" 12.5 ")!!, 0.0)
        assertEquals(1234.5, Fmt.parse("1 234,5")!!, 0.0)
        assertEquals(1234.5, Fmt.parse("1 234,5")!!, 0.0)
        assertEquals(0.5, Fmt.parse(".5")!!, 0.0)
        assertEquals(-3.0, Fmt.parse("-3")!!, 0.0)
        for (bad in listOf("", "abc", "1e3", "12f", "12d", "0x10", "NaN", "Infinity", "1.2.3", "1,234.5", ","))
            assertNull(bad, Fmt.parse(bad))
    }

    @Test fun imperialDefaults() {
        val c = CalcState(catalog, UnitSystem.IMPERIAL)
        // 0.25 × 48 × 96 in steel plate = 4 × 8 ft, ¼" ≈ 326.7 lb
        val kg = (c.compute(7850.0) as CalcOutcome.Ok).r.totalKg
        assertEquals(326.7, kg / 0.45359237, 0.1)
    }
}

class CalcSnapshotTest {
    @Test fun snapshot_roundTrip_survivesProcessDeath() {
        val a = CalcState(catalog, UnitSystem.METRIC).apply {
            shape = ShapeKind.PIPE; fields["od"] = "60,3"; fields["wt"] = "3.6"; lengthText = "4"; qtyText = "7"
            priceText = "2.5"; targetText = "100"; materialId = "ss304"; familyId = "heb"; itemIndex = 3; rebarIndex = 2
        }
        val b = CalcState(catalog, UnitSystem.METRIC).apply { restore(a.snapshot()) }
        assertEquals(a.snapshot(), b.snapshot())
        assertEquals((a.compute(7930.0) as CalcOutcome.Ok).r.totalKg, (b.compute(7930.0) as CalcOutcome.Ok).r.totalKg, 0.0)
    }

    @Test fun snapshot_fromOtherUnitSystem_orBroken_isIgnored() {
        val a = CalcState(catalog, UnitSystem.METRIC).apply { lengthText = "4" }
        val b = CalcState(catalog, UnitSystem.IMPERIAL).apply { restore(a.snapshot()); restore("{broken"); restore(null) }
        assertEquals("20", b.lengthText)
    }
}
