package com.weightkit.app

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.weightkit.app.model.Fmt
import com.weightkit.app.model.GeoResult
import com.weightkit.app.model.Geometry
import com.weightkit.app.model.ShapeKind
import com.weightkit.app.model.UnitSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.PI

@RunWith(AndroidJUnit4::class)
class CalculatorFlowsTest : UiTestBase() {
    private fun kg(v: Double) = Fmt.weight(v, UnitSystem.METRIC)

    /** Flow 1: first launch with a metric locale – plate result, no ad, no paywall. */
    @Test fun f01_firstLaunch_metric_showsPlate_noAd_noPaywall() {
        launch(locale = "en-GB", units = null)
        // Default sheet is 10 × 1000 × 2000 mm (157 kg). With L = 1000 mm it is the 78.5 kg reference plate.
        assertEquals(kg(157.0), text("result_total"))
        assertEquals("10", text("field_t"))
        type("field_pl", "1000")
        assertEquals("78.5 kg", text("result_total"))
        assertFalse(exists("paywall"))
        assertFalse(exists("banner_ad"))
        assertFalse(adShowing())
        Thread.sleep(3000) // the consent form used to pop up here after the UMP request
        assertFalse("no consent form during the trial",
            device.hasObject(androidx.test.uiautomator.By.textContains("consent")))
        node("shape_plate").assertIsSelected()
        screenshot("f01_first_launch_en-GB")
    }

    /** Flow 1b: US locale defaults to imperial (¼" × 4 × 8 ft sheet). */
    @Test fun f01b_firstLaunch_us_isImperial() {
        launch(locale = "en-US", units = null)
        assertEquals("326.7 lb", text("result_total"))
        assertEquals("0.25", text("field_t"))
        assertFalse(exists("paywall"))
    }

    private val dims = mapOf(
        "t" to 5.0, "w" to 50.0, "pl" to 1000.0, "d" to 20.0, "a" to 10.0, "s" to 24.0, "od" to 60.3, "wt" to 3.6,
        "h" to 100.0, "b" to 50.0, "tw" to 6.0, "tf" to 8.5,
    )

    private fun expectedKg(shape: ShapeKind, lengthM: Double): Double = when (shape) {
        ShapeKind.PLATE -> 5 * 50 * 1000 * 1e-9 * 7850
        ShapeKind.REBAR -> PI / 4 * 14 * 14 * 1e-6 * 7850 * lengthM           // default Ø14
        ShapeKind.SECTION -> 7.64e2 * 1e-6 * 7850 * lengthM                   // default IPE 80
        else -> (Geometry.section(shape, dims) as GeoResult.Ok).cs.areaMM2 * 1e-6 * 7850 * lengthM
    }

    /** Flow 2: every shape chip – enter dimensions → result updates live; switching keeps values. */
    @Test fun f02_everyShapeChip_liveResult_valuesKept() {
        launch()
        for (shape in ShapeKind.entries) {
            tap("shape_${shape.id}")
            node("shape_${shape.id}").assertIsSelected()
            for (f in shape.fields) type("field_${f.key}", Fmt.edit(dims.getValue(f.key)))
            if (shape.isLinear) type("field_length", "2")
            assertEquals(shape.name, kg(expectedKg(shape, 2.0)), text("result_total"))
            // live: change one input → result changes immediately
            if (shape.isLinear) {
                type("field_length", "4")
                assertEquals(shape.name, kg(expectedKg(shape, 4.0)), text("result_total"))
                type("field_length", "2")
            }
        }
        screenshot("f02_section_ipe80")
        // back to round: its diameter is still there; flat shares w/t with plate
        tap("shape_round")
        assertEquals("20", text("field_d"))
        tap("shape_plate")
        assertEquals("5", text("field_t"))
        assertEquals("50", text("field_w"))
        // invalid and wall-too-thick states
        tap("shape_pipe")
        type("field_wt", "40")
        assertTrue(exists("result_wall"))
        type("field_wt", "")
        assertTrue(exists("result_missing"))
        node("add_to_list").assertIsNotEnabled()
    }

    /** Flow 4: rebar picker, material picker (search, select, density), custom material add/delete. */
    @Test fun f04_rebar_material_customMaterial() {
        launch()
        tap("shape_rebar")
        tap("rebar_picker")
        tap("rebar_picker_item_3")
        assertEquals("Ø12 mm", text("rebar_picker_value"))
        assertTrue(text("result_table").endsWith("0.888 kg/m"))

        tap("material_row")
        waitFor("material_search")
        type("material_search", "304")
        assertTrue(exists("material_ss304"))
        assertFalse(exists("material_steel"))
        screenshot("f04_material_search")
        tap("material_ss304")
        waitGone("material_list")
        assertEquals("Stainless 304 / 1.4301", text("material_name"))
        assertEquals("7,930 kg/m³", text("material_density"))
        assertEquals(kg(PI / 4 * 144 * 1e-6 * 7930 * 6), text("result_total"))

        // custom material (Pro feature, trial active)
        tab("settings")
        tap("add_material")
        type("material_name_input", "Test alloy")
        type("material_density_input", "8000,5")
        tap("dialog_save")
        assertEquals(1, count("custom_material"))
        tab("calc")
        tap("material_row")
        waitFor("material_list")
        type("material_search", "alloy") // the custom group is at the end of a lazy list
        compose.onNodeWithText("Test alloy").performClick()
        compose.waitForIdle()
        assertEquals("Test alloy", text("material_name"))
        assertEquals("8,000.5 kg/m³", text("material_density"))
        tab("settings")
        tap("custom_material_delete")
        assertEquals(0, count("custom_material"))
        tab("calc")
        assertEquals("Carbon steel (S235/S355, A36)", text("material_name")) // falls back to steel
    }

    /** Flow 4b: after the trial, adding a custom material opens the paywall. */
    @Test fun f04b_customMaterial_afterTrial_paywall() {
        launch(days = 20.0)
        tab("settings")
        tap("add_material")
        waitFor("paywall")
        assertFalse(exists("material_name_input"))
    }

    /** Flow 5: units toggle (toolbar and Settings) converts values, nothing lost, same result. */
    @Test fun f05_unitsToggle_toolbar_and_settings() {
        launch()
        type("field_t", "12.7")
        assertEquals(kg(12.7 * 1000 * 2000 * 1e-9 * 7850), text("result_total")) // 199.4 kg
        tap("units_imperial")
        assertEquals("0.5", text("field_t"))
        assertEquals("39.3701", text("field_w"))
        assertEquals(Fmt.weight(199.39, UnitSystem.IMPERIAL), text("result_total"))
        tap("units_metric")
        assertEquals("12.7", text("field_t"))
        assertEquals("1000", text("field_w"))
        assertEquals("2000", text("field_pl"))
        assertEquals("199.4 kg", text("result_total"))
        // same via Settings
        tab("settings")
        tap("settings_units_imperial")
        tab("calc")
        assertEquals("0.5", text("field_t"))
        tab("settings")
        tap("settings_units_metric")
        tab("calc")
        assertEquals("12.7", text("field_t"))
        assertEquals("199.4 kg", text("result_total"))
        node("units_metric").assertIsSelected()
    }

    /** Flow 6: price → cost appears; target weight → reverse results. */
    @Test fun f06_price_cost_and_reverse() {
        launch(prefs = { putString("currency", "€") })
        assertFalse(exists("result_cost"))
        type("field_price", "2,5")
        assertTrue(text("result_cost").endsWith("392.50 €"))           // 157 kg × 2.5
        type("field_qty", "2")
        assertTrue(text("result_cost").endsWith("785.00 €"))
        assertTrue(text("result_piece").endsWith("157 kg"))
        type("field_qty", "1")
        type("field_target", "300")
        assertTrue(text("reverse_thickness"), text("reverse_thickness").endsWith("19.11 mm"))
        assertTrue(text("reverse_pieces").endsWith(" 1"))
        tap("shape_round")
        type("field_d", "20")
        type("field_length", "6")
        assertTrue(text("reverse_length"), text("reverse_length").endsWith("121.6 m")) // 300 / 2.466
        assertTrue(text("reverse_pieces").endsWith(" 20"))
        assertTrue(exists("trial_notice_reverse"))
        screenshot("f06_reverse")
        // imperial: per lb and lb target
        tap("units_imperial")
        assertEquals("1.134", text("field_price"))
        assertTrue(text("reverse_length"), text("reverse_length").endsWith("399.1 ft"))
    }

    /** Flow 6b: reverse calculation is Pro – locked after the trial. */
    @Test fun f06b_reverse_afterTrial_locked() {
        launch(days = 20.0)
        assertFalse(exists("field_target"))
        tap("reverse_unlock")
        waitFor("paywall")
    }
}
