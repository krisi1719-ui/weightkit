package com.weightkit.app

import android.content.res.Configuration
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.services.storage.TestStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class LocalizationA11yTest : UiTestBase() {
    private val report = StringBuilder()

    private fun res(lang: String) =
        ctx.createConfigurationContext(Configuration(ctx.resources.configuration).apply { setLocale(Locale.forLanguageTag(lang)) }).resources

    private fun note(screen: String) {
        val cut = truncatedTexts()
        if (cut.isNotEmpty()) report.append("$screen: ${cut.joinToString(" | ")}\n")
    }

    /** All screens, both themes; returns texts that were cut off. */
    private fun allScreens(prefix: String) {
        for ((theme, tName) in listOf(1 to "light", 2 to "dark")) {
            tab("settings"); tap("theme_$theme"); note("settings")
            screenshot("${prefix}_${tName}_5_settings")
            tab("calc"); tap("shape_plate")
            screenshot("${prefix}_${tName}_1_calculator"); note("calculator")
            tap("shape_section"); tap("family_picker"); tap("family_picker_item_2")
            screenshot("${prefix}_${tName}_2_profiles"); note("profiles")
            tap("material_row"); waitFor("material_list")
            screenshot("${prefix}_${tName}_3_materials"); note("materials")
            device.pressBack(); compose.waitForIdle(); waitGone("material_list")
            tap("shape_plate")
            tab("lists"); screenshot("${prefix}_${tName}_4_lists"); note("lists")
            tap("list_row"); screenshot("${prefix}_${tName}_4b_list_detail"); note("list detail")
            tap("list_back")
            tab("settings"); tap("settings_pro"); waitFor("paywall")
            screenshot("${prefix}_${tName}_6_paywall"); note("paywall")
            device.pressBack(); compose.waitForIdle(); waitGone("paywall")
        }
    }

    private fun language(lang: String, decimalComma: Boolean) {
        launch(locale = lang, units = "METRIC", prefs = { putString("currency", "€") })
        val r = res(lang)
        // translated (not English fallback, not a raw key)
        assertEquals(r.getString(R.string.tab_settings), text("tab_settings"))
        if (!lang.startsWith("en")) assertNotEquals(res("en").getString(R.string.tab_settings) + res("en").getString(R.string.tab_lists),
            r.getString(R.string.tab_settings) + r.getString(R.string.tab_lists))
        // number format
        type("field_t", "1,3")                               // 1.3 × 1000 × 2000 mm = 20.41 kg
        val total = text("result_total")
        assertTrue("$lang: $total", total.contains(if (decimalComma) "20,41" else "20.41"))
        type("field_price", "2")
        type("field_t", "10")
        tap("add_to_list")
        allScreens(lang)
        val out = "language $lang\n" + report.ifEmpty { "no truncated text\n" }
        TestStorage().openOutputFile("l10n/truncation_$lang.txt").use { it.write(out.toByteArray()) }
        println(out)
    }

    // Flow 13
    @Test fun f13_en() = language("en-GB", decimalComma = false)
    @Test fun f13_bg() = language("bg", decimalComma = true)
    @Test fun f13_de() = language("de", decimalComma = true)
    @Test fun f13_fr() = language("fr", decimalComma = true)
    @Test fun f13_es() = language("es", decimalComma = true)
    @Test fun f13_tr() = language("tr", decimalComma = true)
    @Test fun f13_ru() = language("ru", decimalComma = true)

    /** Interactive element (or the clickable surface around an input) is at least 48 × 48 dp. */
    private fun assertTarget48(tag: String) {
        runCatching { node(tag).performScrollTo() }
        compose.waitForIdle()
        val n = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().first()
        // the touch target is the outermost clickable around the node within its card (a text field's
        // whole card is clickable and focuses it)
        var best: androidx.compose.ui.semantics.SemanticsNode? = null
        var c: androidx.compose.ui.semantics.SemanticsNode? = n
        var depth = 0
        while (c != null && depth < 4) {
            if (c.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick)) best = c
            c = c.parent; depth++
        }
        // Touch area as Compose hit-tests it (internal SemanticsNode.touchBoundsInRoot: includes the 48 dp
        // minimum-interactive-size expansion of Material components); falls back to the layout size.
        // Not boundsInRoot: that one is clipped when a card is partly scrolled out of view.
        val target = best ?: n
        val touch = target.javaClass.methods.firstOrNull { it.name.startsWith("getTouchBoundsInRoot") }
            ?.let { runCatching { it.invoke(target) as androidx.compose.ui.geometry.Rect }.getOrNull() }
        val d = ctx.resources.displayMetrics.density
        val w = maxOf(target.size.width.toFloat(), touch?.width ?: 0f) / d
        val h = maxOf(target.size.height.toFloat(), touch?.height ?: 0f) / d
        println("A11Y target $tag: layout ${target.size.width / d}×${target.size.height / d} dp, touch ${touch?.let { "${it.width / d}×${it.height / d}" }} dp")
        assertTrue("$tag touch target is $w × $h dp", w >= 47.5f && h >= 47.5f)
    }

    /** Flow 14: font scale 2.0, TalkBack labels, touch targets ≥ 48 dp. */
    @Test fun f14_accessibility_fontScale2_labels_touchTargets() {
        shell("settings put system font_scale 2.0")
        try {
            launch(prefs = { putString("currency", "€") })
            tap("add_to_list")
            allScreens("a11y_font2")
            TestStorage().openOutputFile("l10n/truncation_font2.txt").use { it.write(report.ifEmpty { "none" }.toString().toByteArray()) }
            println("font 2.0 truncation:\n$report")
            tab("calc")
            // every input has a spoken label
            val inputs = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.EditableText), useUnmergedTree = true)
                .fetchSemanticsNodes()
            assertTrue(inputs.isNotEmpty())
            for (n in inputs) {
                val label = n.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                assertTrue("input without label: ${n.config}", !label.isNullOrBlank())
            }
            // result is read as one phrase
            assertTrue(text("result_total").endsWith("kg"))
            // touch targets
            val targets = listOf("tab_calc", "tab_lists", "tab_settings", "shape_plate", "shape_round", "units_metric",
                "units_imperial", "material_row", "add_to_list", "field_t", "field_price", "field_qty")
            for (t in targets) assertTarget48(t)
            tab("lists")
            for (t in listOf("new_list", "list_row")) assertTarget48(t)
            tap("list_row")
            for (t in listOf("list_back", "list_delete", "item_delete", "export_pdf", "export_csv")) assertTarget48(t)
        } finally {
            shell("settings put system font_scale 1.0")
        }
    }
}
