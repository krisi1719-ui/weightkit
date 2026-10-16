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

    /** All lists + company details + logo as one JSON file, shared like a PDF (e-mail, Drive, …). */
    fun shareBackup(ctx: Context, s: AppState) {
        val f = file(ctx, "WeightKit-backup-" + java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date()), "json")
        f.writeText(s.backupJson(), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, f.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, f.name))
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
            vat = ctx.getString(R.string.vat), totalWithVat = ctx.getString(R.string.total_with_vat),
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
        val top = y
        // logo (Pro) at the top left instead of the accent bar
        val logo = if (s.hasLogo) runCatching { android.graphics.BitmapFactory.decodeFile(s.logoFile.path) }.getOrNull() else null
        var tx = margin + 14
        if (logo != null) {
            val h = 48f
            val w = (logo.width * h / logo.height).coerceAtMost(140f)
            val hh = logo.height * w / logo.width
            page!!.canvas.drawBitmap(logo, null, android.graphics.RectF(margin, top, margin + w, top + hh),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            tx = margin + w + 12
        } else {
            fill.color = accent
            page!!.canvas.drawRect(margin, top, margin + 6, top + 44, fill)
        }
        val isQuote = list.client.isNotBlank() || list.quoteNo.isNotBlank() || list.validDays > 0 || list.vatPct > 0
        val heading = if (isQuote) ctx.getString(R.string.quote_title) + (if (list.quoteNo.isNotBlank()) " № " + list.quoteNo.trim() else "")
            else ctx.getString(R.string.pdf_title)
        text(heading, title, tx, 300f)
        y = top + 28f
        y += text(list.name, gray, tx, 300f)
        if (list.client.isNotBlank()) y += text(ctx.getString(R.string.client) + ": " + list.client.trim(), bold, tx, 300f)
        val leftEnd = y
        // right column: company name, address, contact, then the date
        val rx = pageW - margin - 220
        y = top
        if (s.company.isNotBlank()) y += text(s.company.trim(), bold, rx, 220f, right = true)
        for (line in listOf(s.companyAddress, s.companyContact)) {
            if (line.isNotBlank()) y += text(line.trim(), gray, rx, 220f, right = true)
        }
        y = maxOf(y + 2f, top + 28f)
        val today = System.currentTimeMillis()
        y += text(ctx.getString(R.string.date) + ": " + Fmt.date(today), gray, rx, 220f, right = true)
        if (list.validDays > 0)
            y += text(ctx.getString(R.string.valid_until) + ": " + Fmt.date(today + list.validDays * 24L * 3600 * 1000), gray, rx, 220f, right = true)
        y = maxOf(y, top + 44f, leftEnd) + 16f
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
            if (list.vatPct > 0) {
                total(ctx.getString(R.string.total_ex_vat), Fmt.money(list.totalCost, s.currency), bold)
                total(ctx.getString(R.string.vat) + " " + Fmt.fixed(list.vatPct, 1) + " %", Fmt.money(list.vat, s.currency), body)
                total(ctx.getString(R.string.total_with_vat), Fmt.money(list.totalWithVat, s.currency), TextPaint(bold).apply { textSize = 12f })
            } else {
                total(ctx.getString(R.string.total), Fmt.money(list.totalCost, s.currency), TextPaint(bold).apply { textSize = 12f })
            }
        }
        if (list.notes.isNotBlank()) {
            y += 14f
            if (y > pageH - margin - 80) newPage()
            text(ctx.getString(R.string.notes), bold, margin, pageW - 2 * margin)
            y += 14f
            y += text(list.notes.trim(), body, margin, pageW - 2 * margin)
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
