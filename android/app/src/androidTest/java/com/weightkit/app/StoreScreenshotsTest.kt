package com.weightkit.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import com.weightkit.app.export.Exporter
import com.weightkit.app.model.AppHolder
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a functional test: produces clean Google Play screenshots per store language.
 * Run only this class:
 *   ./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.weightkit.app.StoreScreenshotsTest
 * Output: build/outputs/connected_android_test_additional_output/.../store/<lang>/NN_name.png
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest : UiTestBase() {

    private class L(
        val tag: String, val imperial: Boolean, val currency: String, val listName: String,
        val plate: List<String>, val plPrice: String,
        val family: Int, val profile: Int, val profLen: String, val profQty: String,
        val pipe: List<String>, val pipeLen: String, val pipeQty: String,
        val roundD: String, val target: String,
    )

    private val M = listOf("10", "1500", "3000")
    private val metric = { tag: String, cur: String, name: String ->
        L(tag, false, cur, name, M, "1,2", 2, 5, "6", "4", listOf("60,3", "3,6"), "6", "10", "20", "300")
    }
    private val langs = mapOf(
        "en" to L("en-US", true, "$", "Warehouse frame", listOf("0.25", "48", "96"), "0.85", 4, 9, "20", "4",
            listOf("2.375", "0.154"), "20", "10", "0.75", "500"),
        "de" to metric("de-DE", "€", "Hallenrahmen"),
        "fr" to metric("fr-FR", "€", "Charpente entrepôt"),
        "es" to metric("es-ES", "€", "Estructura nave"),
        "tr" to metric("tr-TR", "₺", "Depo çatı"),
        "ru" to metric("ru-RU", "₽", "Каркас склада"),
        "bg" to metric("bg-BG", "€", "Склад – конструкция"),
    )

    @Before fun demoStatusBar() {
        shell("settings put global sysui_demo_allowed 1")
        shell("am broadcast -a com.android.systemui.demo -e command enter")
        shell("am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941")
        shell("am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false")
        shell("am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4")
        shell("am broadcast -a com.android.systemui.demo -e command notifications -e visible false")
        shell("settings put global window_animation_scale 0")
    }

    @After fun exitDemo() { shell("am broadcast -a com.android.systemui.demo -e command exit") }

    private fun dec(v: String, l: L) = if (l.imperial) v else v   // inputs already in locale format
    private fun shot(lang: String, name: String) {
        compose.waitForIdle(); Thread.sleep(700)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        TestStorage().openOutputFile("store/$lang/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    private fun addAndWait() { tap("add_to_list"); Thread.sleep(4000) }  // let the toast disappear
    /** Restart the activity: no focused field, no cursor, no keyboard toolbar. State is kept. */
    private fun clean() { relaunch(); Thread.sleep(800) }
    private fun scrollTo(tag: String) { runCatching { node(tag).performScrollTo() }; compose.waitForIdle() }

    private fun run(lang: String) {
        val l = langs.getValue(lang)
        launch(locale = l.tag, units = if (l.imperial) "IMPERIAL" else "METRIC", owned = true,
            prefs = { putString("currency", l.currency); putString("company", "Northline Metalworks") })

        // 1 – sheet / plate with price
        tap("shape_plate")
        type("field_t", l.plate[0]); type("field_w", l.plate[1]); type("field_pl", l.plate[2])
        type("field_qty", "1"); type("field_price", l.plPrice)
        addAndWait()
        clean(); tap("shape_plate")
        shot(lang, "01_plate")

        // 2 – standard profile (HEB 200 / W8x31)
        tap("shape_section"); tap("family_picker"); tap("family_picker_item_${l.family}")
        tap("profile_picker"); tap("profile_picker_item_${l.profile}")
        type("field_length", l.profLen); type("field_qty", l.profQty)
        addAndWait()
        clean(); tap("shape_section")
        shot(lang, "02_profiles")

        // 3 – pipe
        tap("shape_pipe"); type("field_od", l.pipe[0]); type("field_wt", l.pipe[1])
        type("field_length", l.pipeLen); type("field_qty", l.pipeQty)
        addAndWait()
        clean(); tap("shape_pipe")
        shot(lang, "03_pipe")

        // 4 – materials
        tap("material_row"); waitFor("material_list")
        shot(lang, "04_materials")
        device.pressBack(); compose.waitForIdle(); waitGone("material_list")

        // 5 – reverse: how long for a target weight
        tap("shape_round"); type("field_d", l.roundD); type("field_length", if (l.imperial) "20" else "6"); type("field_qty", "1")
        type("field_target", l.target)
        clean(); tap("shape_round"); scrollTo("field_target")
        shot(lang, "05_reverse")

        // 6 – cut list with waste and markup
        tab("lists"); tap("list_row")
        type("list_name", l.listName); type("field_waste", "5"); type("field_markup", "15")
        clean(); tab("lists"); tap("list_row")
        shot(lang, "06_list")

        // 7 – PDF quote (page 1 rendered)
        val s = AppHolder.get(ctx)
        val act = resumedActivity() ?: ctx
        val pdf = Exporter.pdf(act, s.lists.first(), s)
        PdfRenderer(ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY)).use { r ->
            r.openPage(0).use { p ->
                val bmp = Bitmap.createBitmap(p.width * 3, p.height * 3, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                TestStorage().openOutputFile("store/$lang/07_pdf_page.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }

        // 8 – dark mode calculator
        tab("settings"); tap("theme_2")
        tab("calc"); tap("shape_plate")
        shot(lang, "08_dark")
    }

    @Test fun store_en() = run("en")
    @Test fun store_de() = run("de")
    @Test fun store_fr() = run("fr")
    @Test fun store_es() = run("es")
    @Test fun store_tr() = run("tr")
    @Test fun store_ru() = run("ru")
    @Test fun store_bg() = run("bg")
}
