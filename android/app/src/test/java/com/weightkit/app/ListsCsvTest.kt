package com.weightkit.app

import com.weightkit.app.export.CsvLabels
import com.weightkit.app.export.CsvWriter
import com.weightkit.app.model.CutList
import com.weightkit.app.model.ListCodec
import com.weightkit.app.model.ListItem
import com.weightkit.app.model.UnitSystem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ListsCsvTest {
    private val saved = Locale.getDefault()
    @After fun restore() = Locale.setDefault(saved)

    private val list = CutList(
        name = "Оферта \"Иванов\", 2026", wastePct = 10.0, markupPct = 20.0,
        items = listOf(
            ListItem(shape = "Ламарина", material = "Стомана S235", size = "10 × 1000 × 2000 mm", qty = 3, pieceKg = 157.0, pricePerKg = 2.0),
            ListItem(shape = "IPE", material = "Steel, \"S355\"", size = "IPE 200 × 6 m", qty = 2, pieceKg = 134.4, pricePerKg = null),
        ),
    )

    @Test fun totals_wasteMarkup_itemsWithoutPrice() {
        assertEquals(471.0 + 268.8, list.netKg, 1e-9)
        assertEquals((471.0 + 268.8) * 1.1, list.grossKg, 1e-9)
        assertTrue(list.hasCost)
        assertEquals(942.0, list.netCost, 1e-9)               // item without price adds nothing
        assertEquals(942.0 * 1.1 * 1.2, list.totalCost, 1e-9)
        assertNull(list.items[1].cost)
        val noPrice = list.copy(items = list.items.drop(1))
        assertFalse(noPrice.hasCost)
        assertEquals(0.0, noPrice.totalCost, 0.0)
        assertEquals(0.0, CutList(name = "x").grossKg, 0.0)
    }

    @Test fun persistence_roundTrip() {
        val back = ListCodec.decode(ListCodec.encode(listOf(list, CutList(name = "Empty"))))
        assertEquals(list, back[0])
        assertEquals("Empty", back[1].name)
        assertNull(back[0].items[1].pricePerKg)
        assertEquals(emptyList<CutList>(), ListCodec.decode("not json"))
    }

    private val labels = CsvLabels("Поз.", "Форма", "Материал", "Размер", "бр.", "Тегло/бр.", "Общо тегло", "Цена",
        "Отпадък", "Надценка", "Общо")

    @Test fun csv_format() {
        Locale.setDefault(Locale.GERMANY) // decimals must stay dots anyway
        val csv = CsvWriter.text(list, UnitSystem.METRIC, "лв", labels)
        assertTrue(csv.startsWith("\uFEFF"))
        assertEquals(0xEF.toByte(), csv.toByteArray(Charsets.UTF_8)[0])
        val rows = csv.removePrefix("\uFEFF").split("\r\n")
        assertEquals("\"Поз.\",\"Форма\",\"Материал\",\"Размер\",\"бр.\",\"Тегло/бр. (kg)\",\"Общо тегло (kg)\",\"Цена (лв)\"", rows[0])
        assertEquals("1,\"Ламарина\",\"Стомана S235\",\"10 × 1000 × 2000 mm\",3,157.000,471.000,942.00", rows[1])
        assertEquals("2,\"IPE\",\"Steel, \"\"S355\"\"\",\"IPE 200 × 6 m\",2,134.400,268.800,", rows[2])
        assertEquals("", rows[3])
        assertEquals("\"Отпадък\",10.0", rows[4])
        assertEquals("\"Надценка\",20.0", rows[5])
        assertEquals("\"Общо тегло (kg)\",813.780", rows[6])
        assertEquals("\"Общо (лв)\",1243.44", rows[7])
        assertEquals("", rows[8])
    }

    @Test fun csv_imperial() {
        val csv = CsvWriter.text(list, UnitSystem.IMPERIAL, "$", labels)
        assertTrue(csv.contains("(lb)"))
        assertTrue(csv.contains(",346.126,1038.377,")) // 157 kg = 346.126 lb
    }
}
