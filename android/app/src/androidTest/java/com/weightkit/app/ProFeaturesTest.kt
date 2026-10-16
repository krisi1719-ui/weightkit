package com.weightkit.app

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import com.weightkit.app.model.AppHolder
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.PriceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * v1.0.8 Pro features: price per metre / piece, quote details + VAT, logo in the PDF,
 * duplicate list, backup + restore. Screenshots under screenshots/pro_*.png, PDF page under exports/.
 */
@RunWith(AndroidJUnit4::class)
class ProFeaturesTest : UiTestBase() {

    private fun readShared(intent: Intent): ByteArray {
        val inner = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)!!
        val uri = inner.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)!!
        return ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    }

    private fun fakeLogo(): ByteArray {
        val b = Bitmap.createBitmap(400, 160, Bitmap.Config.ARGB_8888)
        Canvas(b).apply {
            drawColor(Color.rgb(28, 37, 48))
            drawText("METAL", 40f, 105f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(232, 115, 26); textSize = 80f; isFakeBoldText = true })
        }
        return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }

    @Test fun quotePdf_pricePerMetre_duplicate_backup() {
        launch(locale = "bg-BG", prefs = {
            putString("currency", "€"); putString("company", "Метал ООД")
            putString("companyAddress", "ул. Индустриална 12, 1000 София")
            putString("companyContact", "0888 123 456 · office@metal.bg · ЕИК 123456789")
        })
        val s = AppHolder.get(ctx)
        inst.runOnMainSync { assertTrue(s.setLogo(fakeLogo())) }

        // price per metre for a round bar: 4 × 6 m at 3 €/m = 72 €
        tap("shape_round"); type("field_d", "20"); type("field_length", "6"); type("field_qty", "4")
        tap("price_unit_m"); type("field_price", "3")
        compose.waitForIdle()
        assertEquals(PriceUnit.M, s.priceUnit)
        assertTrue(text("result_cost"), text("result_cost").contains("72"))
        runCatching { node("price_unit_row").performScrollTo() }
        screenshot("pro_01_price_per_metre")
        tap("add_to_list")
        // per piece: 2 plates at 150 € each
        tap("shape_plate"); type("field_qty", "2"); tap("price_unit_pc"); type("field_price", "150")
        tap("add_to_list")

        tab("lists"); tap("list_row")
        type("field_waste", "5")
        type("quote_client", "Иванов Строй ЕООД"); type("quote_no", "2026-017")
        type("quote_vat", "20"); type("quote_valid", "14")
        type("quote_notes", "Доставка до обекта в рамките на 3 работни дни. Цените са с включено рязане.")
        val l = s.lists.first()
        assertEquals(72.0 + 300.0, l.netCost, 1e-6)
        assertEquals((72.0 + 300.0) * 1.05 * 1.2, l.totalWithVat, 1e-6)
        runCatching { node("quote_section").performScrollTo() }
        screenshot("pro_02_quote_section")
        runCatching { node("total_with_vat").performScrollTo() }
        assertTrue(text("total_with_vat").endsWith(Fmt.money(l.totalWithVat, "€")))
        screenshot("pro_03_totals_vat")

        Intents.init()
        try {
            intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
            tap("export_pdf")
            val pdf = readShared(Intents.getIntents().last())
            saveArtifact("exports/pro_quote.pdf", pdf)
            val f = File(ctx.cacheDir, "q.pdf").apply { writeBytes(pdf) }
            PdfRenderer(ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
                r.openPage(0).use { p ->
                    val bmp = Bitmap.createBitmap(p.width * 2, p.height * 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    TestStorage().openOutputFile("exports/pro_quote_page1.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }

            // duplicate
            tap("list_menu"); waitFor("list_duplicate"); screenshot("pro_04_list_menu"); tap("list_duplicate")
            compose.waitForIdle()
            assertEquals(2, s.lists.size)
            assertTrue(s.lists[0].name, s.lists[0].name.endsWith("(копие)"))
            assertEquals(s.lists[1].items.size, s.lists[0].items.size)
            assertEquals("Иванов Строй ЕООД", s.lists[0].client)
            tap("list_back")

            // backup → wipe → restore
            tap("lists_menu"); waitFor("backup_export"); screenshot("pro_05_lists_menu"); tap("backup_export")
            val backup = readShared(Intents.getIntents().last()).toString(Charsets.UTF_8)
            saveArtifact("exports/pro_backup.json", backup.toByteArray())
            inst.runOnMainSync {
                s.lists.toList().forEach { s.deleteList(it.id) }
                s.setLogo(null)
                assertEquals(2, s.restoreBackup(backup))
                assertEquals(0, s.restoreBackup(backup)) // same lists again: nothing added
                assertEquals(null, s.restoreBackup("{\"x\":1}"))
            }
            assertTrue("logo restored", s.hasLogo)
            assertEquals(2, s.lists.size)
            assertTrue(s.lists.flatMap { it.items }.any { it.priceUnit == PriceUnit.M && it.pieceM == 6.0 })
        } finally {
            Intents.release()
        }
        tab("settings"); runCatching { node("logo_row").performScrollTo() }
        screenshot("pro_06_settings_logo")
    }

    /** After the trial: new price units, quote, duplicate and backup open the paywall. */
    @Test fun afterTrial_newFeaturesAreLocked() {
        launch(days = 20.0)
        runCatching { compose.waitUntil(15_000) { device.hasObject(androidx.test.uiautomator.By.textContains("consent")) || exists("price_unit_pc") } }
        device.findObject(androidx.test.uiautomator.By.text("Do not consent"))?.click()
        compose.waitForIdle()
        tap("price_unit_pc"); waitFor("paywall"); device.pressBack(); compose.waitForIdle()
        assertEquals(PriceUnit.KG, AppHolder.get(ctx).priceUnit)
        tap("add_to_list"); tab("lists"); tap("list_row")
        runCatching { node("quote_locked").performScrollTo() }
        screenshot("pro_07_quote_locked")
        tap("quote_locked"); waitFor("paywall"); device.pressBack(); compose.waitForIdle()
        tap("list_menu"); waitFor("list_duplicate"); tap("list_duplicate"); waitFor("paywall")
    }
}
