package com.weightkit.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.Intents.intending
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ListsFlowsTest : UiTestBase() {
    private fun kg(v: Double) = Fmt.weight(v, UnitSystem.METRIC)

    /** Adds plate (with price), round Ø20 × 6 m and IPE 80 × 6 m. Returns their weights. */
    private fun addThreeItems(): List<Double> {
        type("field_price", "2")
        tap("add_to_list")                                  // plate 10 × 1000 × 2000 = 157 kg, 2 €/kg
        // the toast is drawn by System UI; UiAutomator can see it on most images
        val toast = device.wait(Until.hasObject(By.textContains("Added to")), 2500) == true
        type("field_price", "")
        tap("shape_round"); type("field_d", "20"); type("field_length", "6")
        tap("add_to_list")
        tap("shape_section")
        tap("add_to_list")
        println("toast seen: $toast")
        return listOf(157.0, Math.PI / 4 * 400 * 1e-6 * 7850 * 6, 7.64e2 * 1e-6 * 7850 * 6)
    }

    /** Flow 7: add 3 items → Lists shows the list; edit name, waste, markup → totals; delete item & list. */
    @Test fun f07_addItems_editList_totals_delete() {
        launch(prefs = { putString("currency", "€") })
        val w = addThreeItems()
        tab("lists")
        assertEquals(1, count("list_row"))
        assertTrue(text("list_row"), text("list_row").contains("3 items"))
        tap("list_row")
        assertEquals(3, count("list_item"))
        type("list_name", "Оферта №1")
        type("field_waste", "10")
        type("field_markup", "20")
        val net = w.sum()
        assertTrue(text("total_weight"), text("total_weight").endsWith(kg(net * 1.1)))
        assertTrue(text("subtotal").endsWith("314.00 €"))
        assertTrue(text("total_cost").endsWith(Fmt.money(314.0 * 1.1 * 1.2, "€")))
        screenshot("f07_list_detail")
        // delete the round bar
        compose.onAllNodes(hasTestTag("item_delete"), useUnmergedTree = true)[1].performClick()
        compose.waitForIdle()
        assertEquals(2, count("list_item"))
        assertTrue(text("total_weight").endsWith(kg((net - w[1]) * 1.1)))
        // back: list shows the new name; reopen keeps waste/markup
        tap("list_back")
        assertTrue(text("list_row").contains("Оферта №1"))
        tap("list_row")
        assertEquals("10", text("field_waste"))
        tap("list_delete")
        tap("confirm_delete")
        waitFor("lists_empty")
        assertEquals(0, count("list_row"))
    }

    /** Flow 8: second list allowed in the trial, paywall for free users. */
    @Test fun f08_secondList_trial_vs_free() {
        launch()
        tap("add_to_list")
        tab("lists")
        tap("new_list")
        type("new_list_name", "Second")
        tap("dialog_save")
        assertEquals(2, count("list_row"))
        // lists survive a restart
        relaunch()
        tab("lists")
        assertEquals(2, count("list_row"))
    }

    @Test fun f08b_secondList_afterTrial_paywall() {
        launch(days = 20.0)
        tap("add_to_list")                                // the first list is free
        tab("lists")
        assertEquals(1, count("list_row"))
        tap("new_list")
        waitFor("paywall")
        assertFalse(exists("new_list_name"))
        screenshot("f08_paywall_second_list")
    }

    private fun readShared(intent: Intent): Pair<Uri, ByteArray> {
        val inner = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        val uri = inner.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        return uri to ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    }

    /** Flow 9: export PDF and CSV → share intent carries the file; content is right (title, company, rows, totals, Cyrillic). */
    @Test fun f09_exportPdfCsv_content() {
        launch(prefs = {
            putString("currency", "лв"); putString("company", "Метал ООД")
            putString("companyAddress", "ул. Индустриална 12, 1000 София")
            putString("companyContact", "0888 123 456 · office@metal.bg · ЕИК 123456789")
        })
        addThreeItems()
        tab("lists")
        tap("list_row")
        type("list_name", "Оферта \"Иванов\", 2026")
        type("field_waste", "10")
        type("field_markup", "20")
        runCatching { node("export_pdf").performScrollTo() }
        screenshot("f09_export_buttons")
        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            tap("export_csv")
            val (csvUri, csv) = readShared(Intents.getIntents().last())
            assertTrue(csvUri.toString(), csvUri.toString().endsWith(".csv"))
            val text = csv.toString(Charsets.UTF_8)
            assertEquals(0xEF.toByte(), csv[0])
            val rows = text.removePrefix("\uFEFF").split("\r\n")
            assertTrue(rows[0], rows[0].startsWith("\"") && rows[0].endsWith("(лв)\"") && rows[0].split("\",\"").size == 8)
            assertTrue(rows[1], rows[1].startsWith("1,\"Sheet / plate\",\"Carbon steel (S235/S355, A36)\",\"10 × 1000 × 2000 mm\",1,157.000,157.000,314.00"))
            assertTrue(rows[3], rows[3].startsWith("3,\"IPE\""))
            val waste = ctx.getString(R.string.waste); val markup = ctx.getString(R.string.markup)
            assertTrue(text, text.contains("\"$waste\",10.0\r\n\"$markup\",20.0"))
            assertTrue(text, text.contains("\"${ctx.getString(R.string.total)} (лв)\",414.48"))
            saveArtifact("exports/android_list.csv", csv)

            tap("export_pdf")
            val (pdfUri, pdf) = readShared(Intents.getIntents().last())
            assertTrue(pdfUri.toString().endsWith(".pdf"))
            assertEquals("%PDF", pdf.copyOfRange(0, 4).toString(Charsets.US_ASCII))
            saveArtifact("exports/android_list.pdf", pdf)
            // render page 1 so the content can be checked visually
            val f = File(ctx.cacheDir, "check.pdf").apply { writeBytes(pdf) }
            PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
                assertEquals(1, r.pageCount)
                r.openPage(0).use { p ->
                    val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    androidx.test.services.storage.TestStorage().openOutputFile("exports/android_list_pdf_page1.png").use {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        } finally {
            Intents.release()
        }
        // the real share sheet opens with the file
        tap("export_pdf")
        val chooser = device.wait(Until.hasObject(By.pkg("com.android.intentresolver").depth(0)), 5000) == true ||
            device.wait(Until.hasObject(By.pkg("android").depth(0)), 1000) == true
        screenshot("f09_share_sheet")
        assertTrue("share sheet", chooser)
        device.pressBack()
    }

    @Test fun f09b_export_afterTrial_paywall() {
        launch(days = 20.0)
        tap("add_to_list")
        tab("lists")
        tap("list_row")
        tap("export_pdf")
        waitFor("paywall")
    }
}
