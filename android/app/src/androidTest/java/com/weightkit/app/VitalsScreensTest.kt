package com.weightkit.app

import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.By
import com.weightkit.app.model.AdsHolder
import com.weightkit.app.model.ProHolder
import org.junit.Test

/**
 * Android-vitals style check (not a functional test): walks every screen, saves a screenshot and
 * lists every element (text, button, field, ad banner) that lies under the status bar, the
 * navigation bar / taskbar, a display cutout or the keyboard.
 * Run with -Pandroid.testInstrumentationRunnerArguments.tag=<device_nav> (used in file names).
 * Output: vitals/<tag>/{a,b}NN_screen.png and vitals/<tag>/report_{a,b}.txt
 */
class VitalsScreensTest : UiTestBase() {

    private val tag = InstrumentationRegistry.getArguments().getString("tag") ?: "device"
    private val orient = InstrumentationRegistry.getArguments().getString("orient")

    /** Rotation through UiAutomation (reliable on phones, where the settings route is ignored). */
    private fun applyOrientation() {
        when (orient) {
            "landscape" -> device.setOrientationLandscape()
            "portrait" -> device.setOrientationPortrait()
        }
        Thread.sleep(1500)
    }

    @org.junit.After fun resetOrientation() {
        if (orient == "landscape" || orient == "portrait") runCatching { device.setOrientationNatural(); device.unfreezeRotation() }
    }
    private val report = StringBuilder()
    private var n = 0
    private var part = "a"

    private fun save() =
        TestStorage().openOutputFile("vitals/$tag/report_$part.txt").use { it.write(report.toString().toByteArray()) }

    private fun shot(file: String) {
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        TestStorage().openOutputFile("vitals/$tag/$file.png").use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
    }

    private fun allNodes(): List<SemanticsNode> =
        compose.onAllNodes(SemanticsMatcher("any") { true }, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired = false)

    private fun label(s: SemanticsNode): String {
        val c = s.config
        val t = c.getOrNull(SemanticsProperties.TestTag)
        val txt = c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text }
            ?: c.getOrNull(SemanticsProperties.EditableText)?.text
            ?: c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
        return listOfNotNull(t?.let { "#$it" }, txt?.take(40)?.let { "\"$it\"" }).joinToString(" ")
    }

    /** Screenshot + list of visible elements that intersect a system bar / cutout / keyboard. */
    private fun check(name: String) {
        compose.waitForIdle()
        Thread.sleep(600)
        n++
        val file = "%s%02d_%s".format(part, n, name)
        shot(file)
        val act = resumedActivity()
        val root = act?.window?.decorView
        val ins = root?.let { ViewCompat.getRootWindowInsets(it) }
        if (root == null || ins == null) {
            report.append("$file: no activity/insets (foreground: ${act?.javaClass?.name})\n"); save(); return
        }
        val w = root.width; val h = root.height
        val bars = ins.getInsets(WindowInsetsCompat.Type.systemBars())
        val cut = ins.getInsets(WindowInsetsCompat.Type.displayCutout())
        val ime = ins.getInsets(WindowInsetsCompat.Type.ime())
        val top = maxOf(bars.top, cut.top); val bottom = maxOf(bars.bottom, cut.bottom, ime.bottom)
        val left = maxOf(bars.left, cut.left); val right = maxOf(bars.right, cut.right)
        val bands = listOf(
            "status bar" to Rect(0, 0, w, top),
            (if (ime.bottom > bars.bottom) "keyboard" else "nav bar") to Rect(0, h - bottom, w, h),
            "left bar/cutout" to Rect(0, 0, left, h),
            "right bar/cutout" to Rect(w - right, 0, w, h),
        ).filter { !it.second.isEmpty }
        val hits = mutableListOf<String>()
        for (s in allNodes()) {
            val c = s.config
            val meaningful = c.getOrNull(SemanticsProperties.Text) != null || c.getOrNull(SemanticsProperties.EditableText) != null ||
                c.getOrNull(SemanticsActions.OnClick) != null || c.getOrNull(SemanticsProperties.TestTag) != null
            if (!meaningful) continue
            val b = s.boundsInWindow
            val r = Rect(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
            if (r.width() <= 1 || r.height() <= 1) continue
            if (r.width() >= w * 0.95 && r.height() >= h * 0.9) continue // whole-screen containers
            for ((band, br) in bands) {
                val i = Rect(r)
                if (i.intersect(br) && i.width() > 2 && i.height() > 2) hits += "  - ${label(s)} under $band (node $r, $band $br)"
            }
        }
        val o = if (w > h) "landscape" else "portrait"
        report.append("$file [$o ${w}x$h, status ${bars.top}, nav ${bars.bottom}/${bars.left}/${bars.right}, cutout ${cut.top}, ime ${ime.bottom}]: ")
        report.append(if (hits.isEmpty()) "OK\n" else "${hits.size} overlap(s)\n" + hits.distinct().joinToString("\n") + "\n")
        save()
    }

    private fun scrollTo(t: String) { runCatching { node(t).performScrollTo() } }

    @Test fun walkAllScreens() {
        report.append("== $tag (trial user, en-GB, light)\n")
        applyOrientation()
        launch()
        check("calculator_top")
        scrollTo("add_to_list"); check("calculator_bottom")
        // keyboard open on a field
        tap("shape_plate"); node("field_t").performClick(); Thread.sleep(1200); check("calculator_keyboard")
        // close only the keyboard (a plain Back would leave the app when no keyboard is shown)
        androidx.test.espresso.Espresso.closeSoftKeyboard(); compose.waitForIdle()
        scrollTo("material_row"); tap("material_row"); waitFor("material_list"); check("material_sheet")
        // scroll the material list to its end: the last item must sit above the navigation bar
        repeat(8) { node("material_list").performTouchInput { swipeUp() }; compose.waitForIdle() }
        check("material_sheet_end")
        device.pressBack(); compose.waitForIdle()

        tab("lists"); check("lists_empty")
        tap("new_list"); waitFor("new_list_name"); check("new_list_dialog")
        device.pressBack(); compose.waitForIdle(); if (exists("new_list_name")) device.pressBack()

        tab("calc"); type("field_price", "2"); tap("add_to_list"); Thread.sleep(2500)
        tab("lists"); check("lists_with_list")
        tap("list_row"); check("list_detail_top")
        scrollTo("export_pdf"); check("list_detail_bottom")
        tap("list_delete"); check("delete_dialog"); device.pressBack(); compose.waitForIdle()
        if (exists("list_back")) tap("list_back")

        tab("settings"); check("settings_top")
        scrollTo("add_material"); tap("add_material"); Thread.sleep(800); check("add_material_dialog")
        device.pressBack(); compose.waitForIdle(); if (exists("material_name_input")) device.pressBack()
        scrollTo("privacy_policy"); check("settings_bottom")
        scrollTo("settings_pro"); tap("settings_pro"); waitFor("paywall"); check("paywall")
        device.pressBack(); compose.waitForIdle()
        save()
    }

    /** Free user after the trial: banner ad on Lists, locked features, paywall. */
    @Test fun walkAfterTrialWithAds() {
        part = "b"
        report.append("== $tag (after trial, ads)\n")
        applyOrientation()
        launch(days = 20.0)
        val ads = AdsHolder.get(ctx, ProHolder.get(ctx, 0))
        runCatching {
            compose.waitUntil(20_000) { ads.canRequestAds || device.hasObject(By.textContains("consent")) }
        }
        if (device.hasObject(By.text("Do not consent"))) {
            check("consent_form")
            device.findObject(By.text("Do not consent")).click()
            runCatching { compose.waitUntil(10_000) { ads.canRequestAds } }
        }
        check("calculator_after_trial")
        tab("lists")
        val banner = runCatching { compose.waitUntil(20_000) { exists("banner_ad") }; true }.getOrDefault(false)
        Thread.sleep(4000)
        if (!banner) report.append("(banner did not load – no network or no fill)\n")
        check("lists_banner")
        tab("settings"); scrollTo("settings_pro"); tap("settings_pro"); waitFor("paywall"); check("paywall_after_trial")
        device.pressBack(); compose.waitForIdle()
        save()
    }
}
