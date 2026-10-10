package com.weightkit.app

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.graphics.Bitmap
import android.os.LocaleList
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.services.storage.TestStorage
import androidx.test.uiautomator.UiDevice
import com.weightkit.app.debug.DebugHooks
import org.junit.After
import org.junit.Rule

const val DAY_MS = 24L * 3600 * 1000

/**
 * Every test runs in a fresh process on a cleared app (Test Orchestrator + clearPackageData), so each
 * one starts like a new install. Time and the store are driven through the debug-only hooks.
 */
abstract class UiTestBase {
    @get:Rule val compose = createEmptyComposeRule()

    protected val inst = InstrumentationRegistry.getInstrumentation()
    protected val ctx: Context = inst.targetContext
    protected val device: UiDevice = UiDevice.getInstance(inst)
    protected var scenario: ActivityScenario<MainActivity>? = null

    /**
     * @param days    debug clock offset from now (install time ≈ now): 0 = trial, 20 = trial over
     * @param units   null = let the app pick from the locale (first launch)
     */
    protected fun launch(
        days: Double = 0.0,
        locale: String = "en-GB",
        units: String? = "METRIC",
        theme: Int = 1,
        owned: Boolean = false,
        prefs: android.content.SharedPreferences.Editor.() -> Unit = {},
    ) {
        setClock(days)
        ctx.getSharedPreferences(DebugHooks.PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putBoolean("fakeBilling", true)
            putBoolean("fakeOwned", owned)
        }
        ctx.getSharedPreferences("weightkit", Context.MODE_PRIVATE).edit(commit = true) {
            putInt("theme", theme)
            if (units != null) putString("units", units)
            prefs()
        }
        setLocale(locale)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    protected fun setClock(days: Double) =
        ctx.getSharedPreferences(DebugHooks.PREFS, Context.MODE_PRIVATE).edit(commit = true) {
            putLong("clockOffsetMs", (days * DAY_MS).toLong())
        }

    protected fun debugPrefs(block: android.content.SharedPreferences.Editor.() -> Unit) =
        ctx.getSharedPreferences(DebugHooks.PREFS, Context.MODE_PRIVATE).edit(commit = true, action = block)

    protected fun setLocale(tag: String) {
        ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
    }

    protected fun relaunch() {
        scenario?.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    @After fun tearDownBase() {
        scenario?.close()
    }

    // ------------------------------------------------------------------ node helpers

    /** Merged node if there is one (text of a whole row), else the unmerged one. */
    protected fun node(tag: String): SemanticsNodeInteraction =
        if (compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag(tag)
        else compose.onNodeWithTag(tag, useUnmergedTree = true)

    protected fun exists(tag: String): Boolean = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun count(tag: String): Int = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().size

    protected fun tap(tag: String) {
        // performScrollTo only scrolls the closest scroll parent: bring the chip row into view first
        if (tag.startsWith("shape_")) runCatching { compose.onNodeWithTag("shape_row").performScrollTo() }
        val n = node(tag)
        runCatching { n.performScrollTo() }
        n.performClick()
        compose.waitForIdle()
    }

    protected fun type(tag: String, text: String) {
        val n = node(tag)
        runCatching { n.performScrollTo() }
        n.performTextReplacement(text)
        compose.waitForIdle()
    }

    protected fun text(tag: String): String = node(tag).text()

    protected fun SemanticsNodeInteraction.text(): String {
        val c = fetchSemanticsNode().config
        c.getOrNull(SemanticsProperties.EditableText)?.let { return it.text }
        return c.getOrNull(SemanticsProperties.Text)?.joinToString(" ") { it.text } ?: ""
    }

    protected fun waitFor(tag: String, timeoutMs: Long = 5000) =
        compose.waitUntil(timeoutMs) { exists(tag) }

    protected fun waitGone(tag: String, timeoutMs: Long = 5000) =
        compose.waitUntil(timeoutMs) { !exists(tag) }

    protected fun tab(name: String) = tap("tab_$name")

    // ------------------------------------------------------------------ system / artifacts

    protected fun shell(cmd: String): String =
        inst.uiAutomation.executeShellCommand(cmd).use { fd ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().readText()
        }

    protected fun resumedActivity(): Activity? {
        var a: Activity? = null
        inst.runOnMainSync {
            a = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull()
        }
        return a
    }

    /** Is a Google full-screen ad (interstitial / rewarded) on screen? */
    protected fun adShowing(): Boolean = resumedActivity()?.javaClass?.name?.contains("ads.AdActivity") == true

    protected fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(300)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        TestStorage().openOutputFile("screenshots/$name.png").use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    protected fun saveArtifact(name: String, bytes: ByteArray) =
        TestStorage().openOutputFile(name).use { it.write(bytes) }

    /** Every Text on screen that is cut off (ellipsis / clipped). */
    protected fun truncatedTexts(): List<String> {
        val nodes = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true)
            .fetchSemanticsNodes()
        val out = mutableListOf<String>()
        for (n in nodes) {
            val results = mutableListOf<TextLayoutResult>()
            n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val r = results.firstOrNull() ?: continue
            val ellipsized = (0 until r.lineCount).any { r.isLineEllipsized(it) }
            if (r.hasVisualOverflow || ellipsized) out += r.layoutInput.text.text
        }
        return out
    }
}
