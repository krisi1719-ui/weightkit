package com.weightkit.app

import com.weightkit.app.model.CutList
import com.weightkit.app.model.ListCodec
import com.weightkit.app.model.ListItem
import com.weightkit.app.model.PriceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuotePriceTest {
    private val bar = ListItem(shape = "Round bar", material = "Steel", size = "Ø20 × 6 m", qty = 4, pieceKg = 14.8,
        pricePerKg = 3.0, priceUnit = PriceUnit.M, pieceM = 6.0)

    @Test fun costPerMetre_perPiece_perKg() {
        assertEquals(3.0 * 6 * 4, bar.cost!!, 1e-9)
        assertEquals(10.0 * 4, bar.copy(pricePerKg = 10.0, priceUnit = PriceUnit.PC).cost!!, 1e-9)
        assertEquals(2.0 * 14.8 * 4, bar.copy(pricePerKg = 2.0, priceUnit = PriceUnit.KG).cost!!, 1e-9)
        assertNull("per metre needs a length", bar.copy(pieceM = null).cost)
        assertNull(bar.copy(pricePerKg = null).cost)
    }

    @Test fun vatOnTotalWithWasteAndMarkup() {
        val l = CutList(name = "Q", wastePct = 10.0, markupPct = 20.0, vatPct = 20.0, items = listOf(bar))
        val total = 72.0 * 1.1 * 1.2
        assertEquals(total, l.totalCost, 1e-9)
        assertEquals(total * 0.2, l.vat, 1e-9)
        assertEquals(total * 1.2, l.totalWithVat, 1e-9)
        assertEquals(0.0, l.copy(vatPct = 0.0).vat, 0.0)
    }

    @Test fun codecKeepsQuoteFieldsAndPriceUnit() {
        val l = CutList(name = "Оферта", client = "Иванов ООД", quoteNo = "2026-017", vatPct = 20.0, validDays = 14,
            notes = "Доставка до обекта", items = listOf(bar))
        val back = ListCodec.decode(ListCodec.encode(listOf(l))).single()
        assertEquals(l, back)
    }

    @Test fun oldSavedListsStillLoad() {
        // saved by 1.0.x: no quote fields, no price unit
        val old = """[{"id":"a","name":"Old","created":1,"wastePct":5.0,"markupPct":0.0,"items":[
            {"id":"i","shape":"Plate","material":"Steel","size":"10×1000×2000","qty":1,"pieceKg":157.0,"pricePerKg":2.0}]}]"""
        val l = ListCodec.decode(old).single()
        assertEquals("", l.client); assertEquals(0.0, l.vatPct, 0.0); assertEquals(0, l.validDays)
        assertEquals(PriceUnit.KG, l.items[0].priceUnit)
        assertEquals(314.0, l.items[0].cost!!, 1e-9)
    }
}
