package com.weightkit.app.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.content.edit
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.Currency
import java.util.Locale
import java.util.UUID

data class CustomMaterial(val id: String = UUID.randomUUID().toString(), val name: String, val density: Double)

data class ListItem(
    val id: String = UUID.randomUUID().toString(),
    val shape: String,
    val material: String,
    val size: String,
    val qty: Int,
    val pieceKg: Double,
    /** Price per kg when added (null = no price). */
    val pricePerKg: Double?,
) {
    val totalKg: Double get() = pieceKg * qty
    val cost: Double? get() = pricePerKg?.let { it * totalKg }
}

data class CutList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val created: Long = System.currentTimeMillis(),
    val wastePct: Double = 0.0,
    val markupPct: Double = 0.0,
    val items: List<ListItem> = emptyList(),
) {
    val netKg: Double get() = items.sumOf { it.totalKg }
    val grossKg: Double get() = netKg * (1 + wastePct / 100)
    val hasCost: Boolean get() = items.any { it.pricePerKg != null }
    val netCost: Double get() = items.sumOf { it.cost ?: 0.0 }
    /** Cost incl. waste and markup. */
    val totalCost: Double get() = netCost * (1 + wastePct / 100) * (1 + markupPct / 100)
}

data class CalcResult(
    val pieceKg: Double,
    val perMeterKg: Double?,
    val totalKg: Double,
    val volumeM3: Double,
    val surfaceM2: Double?,
    val density: Double,
    val tableKgM: Double?,
    val tableLbFt: Double?,
)

sealed class CalcOutcome {
    data class Ok(val r: CalcResult) : CalcOutcome()
    object Missing : CalcOutcome()
    object Wall : CalcOutcome()
}

/**
 * All app state (settings, calculator, lists). Lives for the whole process
 * (see [AppHolder]) so it survives Activity recreation.
 */
class AppState(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("weightkit", Context.MODE_PRIVATE)
    val catalog = Catalog(ctx)

    // ---------------------------------------------------------------- settings
    var units by mutableStateOf(
        prefs.getString("units", null)?.let { runCatching { UnitSystem.valueOf(it) }.getOrNull() }
            ?: if (Locale.getDefault().country in setOf("US", "LR", "MM")) UnitSystem.IMPERIAL else UnitSystem.METRIC
    )
        private set
    var currency by mutableStateOf(
        prefs.getString("currency", null)
            ?: runCatching { Currency.getInstance(Locale.getDefault()).symbol }.getOrDefault("€")
    )
        private set
    var company by mutableStateOf(prefs.getString("company", "") ?: "")
        private set
    /** 0 = system, 1 = light, 2 = dark */
    var theme by mutableIntStateOf(prefs.getInt("theme", 0))
        private set
    val customMaterials = mutableStateListOf<CustomMaterial>()
    val firstLaunch: Long

    // ---------------------------------------------------------------- calculator
    val calc = CalcState(catalog, units)
    var shape by calc::shape
    var materialId by calc::materialId
    val fields get() = calc.fields
    var lengthText by calc::lengthText
    var qtyText by calc::qtyText
    var priceText by calc::priceText
    var targetText by calc::targetText
    var familyId by calc::familyId
    var itemIndex by calc::itemIndex
    var rebarIndex by calc::rebarIndex

    // ---------------------------------------------------------------- lists
    val lists = mutableStateListOf<CutList>()

    init {
        val fl = prefs.getLong("firstLaunch", 0L)
        val nowMs = com.weightkit.app.debug.DebugHooks.clock(ctx).wallMillis() // = System.currentTimeMillis() in release
        firstLaunch = if (fl == 0L) nowMs.also { prefs.edit { putLong("firstLaunch", it) } } else fl
        prefs.getString("customMaterials", null)?.let { s ->
            runCatching {
                val a = JSONArray(s)
                for (i in 0 until a.length()) {
                    val o = a.getJSONObject(i)
                    customMaterials.add(CustomMaterial(o.getString("id"), o.getString("name"), o.getDouble("density")))
                }
            }
        }
        loadLists()
        calc.restore(prefs.getString("calc", null))
    }

    // ---------------------------------------------------------------- settings setters

    /** Switch unit system and convert every typed value so nothing is lost. */
    fun updateUnits(new: UnitSystem) {
        if (new == units) return
        calc.switchUnits(new)
        units = new
        prefs.edit { putString("units", new.name) }
    }

    fun updateCurrency(v: String) { currency = v; prefs.edit { putString("currency", v) } }
    fun updateCompany(v: String) { company = v; prefs.edit { putString("company", v) } }
    fun updateTheme(v: Int) { theme = v; prefs.edit { putInt("theme", v) } }

    fun addCustomMaterial(name: String, density: Double) {
        customMaterials.add(CustomMaterial(name = name, density = density))
        saveCustomMaterials()
    }

    fun removeCustomMaterial(id: String) {
        customMaterials.removeAll { it.id == id }
        saveCustomMaterials()
    }

    private fun saveCustomMaterials() {
        val a = JSONArray()
        customMaterials.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("density", it.density)) }
        prefs.edit { putString("customMaterials", a.toString()) }
    }

    val allMaterials: List<Material>
        get() = catalog.materials + customMaterials.map { Material("custom_" + it.id, "custom", it.name, it.density) }

    fun material(id: String): Material = allMaterials.firstOrNull { it.id == id } ?: catalog.materials.first()

    // ---------------------------------------------------------------- calculator

    val family: SectionFamily? get() = calc.family
    val sectionItem: SectionItem? get() = calc.sectionItem
    val rebarItem: SectionItem? get() = calc.rebarItem
    val quantity: Int get() = calc.quantity
    /** Internal price per kg, null if not entered. */
    val pricePerKg: Double? get() = calc.pricePerKg

    fun compute(density: Double): CalcOutcome = calc.compute(density)

    /** Human-readable size for lists / PDF, in current units. */
    fun sizeDescription(): String = calc.sizeDescription()

    /** Called when the app goes to the background (it may be killed there). */
    fun saveCalc() = prefs.edit { putString("calc", calc.snapshot()) }

    // ---------------------------------------------------------------- lists

    /** Adds to the most recent list (creating one if needed); returns its name. */
    fun addToList(item: ListItem, defaultName: String): String {
        if (lists.isEmpty()) lists.add(CutList(name = defaultName))
        lists[0] = lists[0].copy(items = lists[0].items + item)
        saveLists()
        return lists[0].name
    }

    fun newList(name: String) { lists.add(0, CutList(name = name)); saveLists() }
    fun deleteList(id: String) { lists.removeAll { it.id == id }; saveLists() }

    fun updateList(id: String, f: (CutList) -> CutList) {
        val i = lists.indexOfFirst { it.id == id }
        if (i >= 0) { lists[i] = f(lists[i]); saveLists() }
    }

    private fun saveLists() = prefs.edit { putString("lists", ListCodec.encode(lists)) }

    private fun loadLists() {
        prefs.getString("lists", null)?.let { lists.addAll(ListCodec.decode(it)) }
    }
}

/** Process-wide holder so state survives Activity recreation. */
object AppHolder {
    @Volatile private var state: AppState? = null
    fun get(ctx: Context): AppState = state ?: synchronized(this) {
        state ?: AppState(ctx.applicationContext).also { state = it }
    }
}
