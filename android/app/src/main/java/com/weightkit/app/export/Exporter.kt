package com.weightkit.app.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.graphics.withTranslation
import com.weightkit.app.R
import com.weightkit.app.model.AppState
import com.weightkit.app.model.CutList
import com.weightkit.app.model.Fmt
import java.io.File

object Exporter {
    fun share(ctx: Context, list: CutList, s: AppState, pdf: Boolean) {
        val file = if (pdf) pdf(ctx, list, s) else csv(ctx, list, s)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = if (pdf) "application/pdf" else "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, list.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, list.name))
    }

    private fun file(ctx: Context, name: String, ext: String): File {
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val safe = name.replace(Regex("[/\\\\:?*\"<>|]"), "_").ifBlank { "list" }
        return File(dir, "$safe.$ext")
    }

    // ---------------------------------------------------------------- CSV

    fun csv(ctx: Context, list: CutList, s: AppState): File {
        val labels = CsvLabels(
            item = ctx.getString(R.string.item), shape = ctx.getString(R.string.shape),
            material = ctx.getString(R.string.material), size = ctx.getString(R.string.size),
            qty = ctx.getString(R.string.qty), weightPiece = ctx.getString(R.string.weight_piece),
            totalWeight = ctx.getString(R.string.total_weight), cost = ctx.getString(R.string.cost),
            waste = ctx.getString(R.string.waste), markup = ctx.getString(R.string.markup),
            total = ctx.getString(R.string.total),
        )
        return file(ctx, list.name, "csv").apply { writeText(CsvWriter.text(list, s.units, s.currency, labels), Charsets.UTF_8) }
    }

    // ---------------------------------------------------------------- PDF (A4, 595 × 842 pt)

    fun pdf(ctx: Context, list: CutList, s: AppState): File {
        val u = s.units
        val pageW = 595
        val pageH = 842
        val margin = 40f
        val accent = Color.rgb(232, 115, 26)
        val doc = PdfDocument()

        val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.BLACK }
        val bold = TextPaint(body).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = TextPaint(bold).apply { textSize = 20f }
        val gray = TextPaint(body).apply { color = Color.DKGRAY }
        val small = TextPaint(body).apply { textSize = 8.5f; color = Color.GRAY }
        val fill = Paint().apply { style = Paint.Style.FILL }

        val cols = floatArrayOf(22f, 190f, 110f, 32f, 56f, 56f, 49f)
        val headers = listOf(
            "#", ctx.getString(R.string.size), ctx.getString(R.string.material), ctx.getString(R.string.qty),
            "${ctx.getString(R.string.weight_piece)} (${u.weightUnit})", "${ctx.getString(R.string.weight)} (${u.weightUnit})",
            ctx.getString(R.string.cost),
        )

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun text(t: String, p: TextPaint, x: Float, w: Float, right: Boolean = false): Float {
            val layout = StaticLayout.Builder.obtain(t, 0, t.length, p, w.toInt().coerceAtLeast(1))
                .setAlignment(if (right) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL)
                .build()
            page!!.canvas.withTranslation(x, y) { layout.draw(this) }
            return layout.height.toFloat()
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            y = margin
        }

        fun header() {
            fill.color = Color.rgb(237, 237, 237)
            page!!.canvas.drawRect(margin, y, pageW - margin, y + 34f, fill) // room for two-line headers
            y += 4f
            var x = margin
            for (i in cols.indices) {
                text(headers[i], bold, x + 2, cols[i] - 4, right = i >= 3)
                x += cols[i]
            }
            y += 30f
        }

        newPage()
        fill.color = accent
        page!!.canvas.drawRect(margin, y, margin + 6, y + 44, fill)
        text(ctx.getString(R.string.pdf_title), title, margin + 14, 300f)
        if (s.company.isNotBlank()) text(s.company, bold, pageW - margin - 220, 220f, right = true)
        y += 28f
        text(list.name, gray, margin + 14, 300f)
        text(ctx.getString(R.string.date) + ": " + Fmt.date(System.currentTimeMillis()), gray, pageW - margin - 220, 220f, right = true)
        y += 32f
        header()

        list.items.forEachIndexed { i, it ->
            if (y > pageH - margin - 110) { newPage(); header() }
            val values = listOf(
                (i + 1).toString(), it.shape + "\n" + it.size, it.material, it.qty.toString(),
                Fmt.fixed(it.pieceKg / u.kgPerWeight, 2), Fmt.fixed(it.totalKg / u.kgPerWeight, 2),
                it.cost?.let { c -> Fmt.fixed(c, 2) } ?: "–",
            )
            y += 5f
            var x = margin
            var rowH = 0f
            for (c in cols.indices) {
                rowH = maxOf(rowH, text(values[c], body, x + 2, cols[c] - 4, right = c >= 3))
                x += cols[c]
            }
            y += rowH + 5f
            fill.color = Color.rgb(217, 217, 217)
            page!!.canvas.drawRect(margin, y, pageW - margin, y + 0.5f, fill)
        }

        y += 14f
        val lx = pageW - margin - 260
        fun total(a: String, b: String, p: TextPaint) {
            text(a, p, lx, 150f)
            text(b, p, lx + 150, 110f, right = true)
            y += 16f
        }
        total(ctx.getString(R.string.waste), Fmt.fixed(list.wastePct, 1) + " %", body)
        total(ctx.getString(R.string.total_weight), Fmt.fixed(list.grossKg / u.kgPerWeight, 2) + " " + u.weightUnit, bold)
        if (list.hasCost) {
            total(ctx.getString(R.string.subtotal), Fmt.money(list.netCost, s.currency), body)
            total(ctx.getString(R.string.markup), Fmt.fixed(list.markupPct, 1) + " %", body)
            total(ctx.getString(R.string.total), Fmt.money(list.totalCost, s.currency), TextPaint(bold).apply { textSize = 12f })
        }
        y = pageH - margin - 12
        text("WeightKit · " + ctx.getString(R.string.data_note), small, margin, pageW - 2 * margin)

        page?.let { doc.finishPage(it) }
        val f = file(ctx, list.name, "pdf")
        f.outputStream().use { doc.writeTo(it) }
        doc.close()
        return f
    }
}
