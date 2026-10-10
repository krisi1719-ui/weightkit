package com.weightkit.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CatalogTest {
    @Test fun allFamiliesLoaded() {
        assertEquals(listOf("ipe", "hea", "heb", "upn", "w", "ismb", "ismc", "pipe40", "pipe80", "rebar"),
            catalog.families.map { it.id })
        assertEquals(169, catalog.families.sumOf { it.items.size })
        assertEquals(30, catalog.materials.size)
    }

    @Test fun everyItem_areaTimes7850_within2pct_ofTable() {
        var n = 0
        val bad = mutableListOf<String>()
        for (f in catalog.families) for (it in f.items) {
            val table = it.steelKgM ?: continue
            val calc = it.areaMM2(f.shape)!! * 1e-6 * 7850
            if (abs(calc - table) / table > 0.02) bad += "${it.name}: $calc vs $table"
            n++
        }
        assertTrue(bad.joinToString(), bad.isEmpty())
        assertEquals("pipes have no tabulated kg/m", 169 - 28, n)
    }

    @Test fun imperialW_matchesNominalLbFt() {
        val w = catalog.families.first { it.id == "w" }
        assertEquals(38, w.items.size)
        for (it in w.items) {
            val lbFt = it.areaMM2(w.shape)!! * 1e-6 * 7850 * 0.3048 / 0.45359237
            val nominal = it.nominalLbFt!!
            assertEquals(it.name.substringAfter('x').toDouble(), nominal, 0.0)
            assertTrue("${it.name}: $lbFt", abs(lbFt - nominal) / nominal <= 0.02)
        }
    }

    @Test fun proSplit_matchesMonetizationDoc() {
        val pro = catalog.families.filter { it.pro }.map { it.id }.toSet()
        assertEquals(setOf("hea", "heb", "w", "ismb", "ismc", "pipe80"), pro)
    }
}
