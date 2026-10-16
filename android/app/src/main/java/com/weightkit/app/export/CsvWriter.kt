package com.weightkit.app.export

import com.weightkit.app.model.CutList
import com.weightkit.app.model.UnitSystem
import java.util.Locale

/** Localized column / row titles for the CSV. */
data class CsvLabels(
    val item: String, val shape: String, val material: String, val size: String, val qty: String,
    val weightPiece: String, val totalWeight: String, val cost: String,
    val waste: String, val markup: String, val total: String,
    val vat: String = "VAT", val totalWithVat: String = "Total incl. VAT",
)

/**
 * CSV text: UTF-8 BOM (so Excel shows Cyrillic etc.), CRLF, every text quoted ("" escapes "),
 * numbers always with a dot decimal. Byte-identical to iOS CsvWriter.
 */
object CsvWriter {
    const val BOM = "\uFEFF"

    fun text(list: CutList, u: UnitSystem, currency: String, l: CsvLabels): String {
        fun q(v: String) = "\"" + v.replace("\"", "\"\"") + "\""
        fun n(v: Double) = String.format(Locale.US, "%.3f", v)
        fun n1(v: Double) = String.format(Locale.US, "%.1f", v)
        fun n2(v: Double) = String.format(Locale.US, "%.2f", v)
        val rows = mutableListOf<String>()
        rows += listOf(
            l.item, l.shape, l.material, l.size, l.qty,
            "${l.weightPiece} (${u.weightUnit})", "${l.totalWeight} (${u.weightUnit})", "${l.cost} ($currency)",
        ).joinToString(",") { q(it) }
        list.items.forEachIndexed { i, it ->
            rows += listOf(
                (i + 1).toString(), q(it.shape), q(it.material), q(it.size), it.qty.toString(),
                n(it.pieceKg / u.kgPerWeight), n(it.totalKg / u.kgPerWeight), it.cost?.let { c -> n2(c) } ?: "",
            ).joinToString(",")
        }
        rows += ""
        rows += q(l.waste) + "," + n1(list.wastePct)
        rows += q(l.markup) + "," + n1(list.markupPct)
        rows += q(l.totalWeight + " (${u.weightUnit})") + "," + n(list.grossKg / u.kgPerWeight)
        if (list.hasCost) rows += q(l.total + " ($currency)") + "," + n2(list.totalCost)
        if (list.hasCost && list.vatPct > 0) {
            rows += q(l.vat + " %") + "," + n1(list.vatPct)
            rows += q(l.totalWithVat + " ($currency)") + "," + n2(list.totalWithVat)
        }
        return BOM + rows.joinToString("\r\n") + "\r\n"
    }
}
