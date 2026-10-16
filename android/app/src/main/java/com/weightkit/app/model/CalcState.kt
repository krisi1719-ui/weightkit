package com.weightkit.app.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import kotlin.math.roundToLong

/**
 * Calculator screen state, free of Android dependencies (unit-tested on the JVM).
 * Text fields hold values in the CURRENT display units. Mirrors iOS CalcModel.
 */
class CalcState(private val catalog: Catalog, units: UnitSystem) {
    companion object {
        const val MAX_QTY = 1_000_000
    }

    var units by mutableStateOf(units)
        private set
    var shape by mutableStateOf(ShapeKind.PLATE)
    var materialId by mutableStateOf("steel")
    val fields = mutableStateMapOf<String, String>()
    var lengthText by mutableStateOf("6")
    var qtyText by mutableStateOf("1")
    /** Price per kg/lb, per m/ft or per piece (see [priceUnit]), as typed. */
    var priceText by mutableStateOf("")
    /** What [priceText] refers to. Per metre / per piece need Pro (see [proAllowed]). */
    var priceUnit by mutableStateOf(PriceUnit.KG)
    var targetText by mutableStateOf("")
    var familyId by mutableStateOf("ipe")
    var itemIndex by mutableIntStateOf(0)
    var rebarIndex by mutableIntStateOf(4)

    /**
     * Exact base value (mm, m, price per kg, kg) behind a text that we produced by a unit switch.
     * As long as the user does not edit that text, calculations use the exact value, so switching
     * units back and forth never loses precision and both systems give the same result.
     */
    private val exact = HashMap<String, Pair<String, Double>>()

    init {
        if (units == UnitSystem.IMPERIAL) {
            lengthText = "20"
            fields.putAll(mapOf("t" to "0.25", "w" to "48", "pl" to "96"))
        } else {
            fields.putAll(mapOf("t" to "10", "w" to "1000", "pl" to "2000"))
        }
    }

    private fun base(key: String, text: String, factor: Double): Double? {
        exact[key]?.let { (shown, v) -> if (shown == text) return v }
        return Fmt.parse(text)?.let { it * factor }
    }

    private fun mmOf(key: String) = base("f:$key", fields[key] ?: "", units.mmPerDim)
    private val lengthM: Double? get() = base("length", lengthText, units.mPerLength)
    /** Price unit in effect: per metre / per piece fall back to per kg without Pro. */
    val effectivePriceUnit: PriceUnit get() = if (proAllowed || priceUnit == PriceUnit.KG) priceUnit else PriceUnit.KG

    /** Internal price (per kg, per metre or per piece – see [effectivePriceUnit]), null if not entered. */
    val pricePerKg: Double?
        get() = when (effectivePriceUnit) {
            PriceUnit.KG -> base("price", priceText, 1 / units.kgPerWeight)
            PriceUnit.M -> base("price_m", priceText, 1 / units.mPerLength)
            PriceUnit.PC -> Fmt.parse(priceText)
        }?.takeIf { it > 0 }

    /** Length of one piece in metres; null for sheets (they have no length). */
    val pieceLengthM: Double? get() = if (shape == ShapeKind.PLATE) null else lengthM?.takeIf { it > 0 }

    /** Cost of the calculated quantity in the chosen price unit; null without a price. */
    fun cost(r: CalcResult): Double? = pricePerKg?.let { p ->
        when (effectivePriceUnit) {
            PriceUnit.KG -> p * r.totalKg
            PriceUnit.M -> pieceLengthM?.let { p * it * quantity }
            PriceUnit.PC -> p * quantity
        }
    }

    fun changePriceUnit(u: PriceUnit) { if (u != priceUnit) { priceUnit = u; exact.remove("price"); exact.remove("price_m") } }
    /** Target weight in kg, null if not entered. */
    val targetKg: Double? get() = base("target", targetText, units.kgPerWeight)?.takeIf { it > 0 }

    /** Switch unit system and convert every typed value so nothing is lost. */
    fun switchUnits(new: UnitSystem) {
        val old = units
        if (new == old) return
        fun r4(x: Double) = (x * 10000).roundToLong() / 10000.0
        fun convert(key: String, text: String, oldF: Double, newF: Double): String? {
            val b = base(key, text, oldF) ?: return null
            val t = Fmt.edit(r4(b / newF))
            exact[key] = t to b
            return t
        }
        for (k in fields.keys.toList()) {
            convert("f:$k", fields[k] ?: "", old.mmPerDim, new.mmPerDim)?.let { fields[k] = it }
        }
        convert("length", lengthText, old.mPerLength, new.mPerLength)?.let { lengthText = it }
        when (priceUnit) {
            PriceUnit.KG -> convert("price", priceText, 1 / old.kgPerWeight, 1 / new.kgPerWeight)?.let { priceText = it }
            PriceUnit.M -> convert("price_m", priceText, 1 / old.mPerLength, 1 / new.mPerLength)?.let { priceText = it }
            PriceUnit.PC -> {}
        }
        convert("target", targetText, old.kgPerWeight, new.kgPerWeight)?.let { targetText = it }
        units = new
    }

    /** Set by the screen from ProManager.hasPro. Without Pro a Pro table (e.g. picked during the trial) is not used. */
    var proAllowed by mutableStateOf(true)

    val family: SectionFamily?
        get() = catalog.profileFamilies.firstOrNull { it.id == familyId && (!it.pro || proAllowed) }
            ?: catalog.profileFamilies.firstOrNull { !it.pro }

    val sectionItem: SectionItem?
        get() = family?.items?.takeIf { it.isNotEmpty() }?.let { it[itemIndex.coerceIn(0, it.size - 1)] }

    val rebarItem: SectionItem?
        get() = catalog.rebar?.items?.takeIf { it.isNotEmpty() }?.let { it[rebarIndex.coerceIn(0, it.size - 1)] }

    /** 1 … [MAX_QTY]; empty / invalid input counts as 1. */
    val quantity: Int get() = (Fmt.parse(qtyText) ?: 1.0).coerceIn(1.0, MAX_QTY.toDouble()).toInt()

    fun compute(density: Double): CalcOutcome {
        val mm = HashMap<String, Double>()
        for (k in fields.keys) mmOf(k)?.let { mm[k] = it }
        val qty = quantity.toDouble()

        if (shape == ShapeKind.PLATE) {
            // plate: V = t·W·L ; surface = 2·W·L + 2·t·(W+L)
            val t = mm["t"]?.takeIf { it > 0 } ?: return CalcOutcome.Missing
            val w = mm["w"]?.takeIf { it > 0 } ?: return CalcOutcome.Missing
            val l = mm["pl"]?.takeIf { it > 0 } ?: return CalcOutcome.Missing
            val vol = Geometry.plateVolumeM3(t, w, l)
            val piece = vol * density
            val surf = (2 * w * l + 2 * t * (w + l)) * 1e-6
            return CalcOutcome.Ok(CalcResult(piece, null, piece * qty, vol, surf, density, null, null))
        }

        val len = lengthM?.takeIf { it > 0 } ?: return CalcOutcome.Missing
        var tableKgM: Double? = null
        var tableLbFt: Double? = null
        val area: Double
        val perim: Double?
        when (shape) {
            ShapeKind.SECTION -> {
                val f = family ?: return CalcOutcome.Missing
                val item = sectionItem ?: return CalcOutcome.Missing
                area = item.areaMM2(f.shape) ?: return CalcOutcome.Missing
                perim = item.perimeterMM(f.shape)
                tableKgM = item.steelKgM
                tableLbFt = item.nominalLbFt
            }
            ShapeKind.REBAR -> {
                val item = rebarItem ?: return CalcOutcome.Missing
                area = item.areaMM2("rebar") ?: return CalcOutcome.Missing
                perim = item.perimeterMM("rebar")
                tableKgM = item.steelKgM
            }
            else -> when (val g = Geometry.section(shape, mm)) {
                is GeoResult.Ok -> { area = g.cs.areaMM2; perim = g.cs.perimeterMM }
                GeoResult.Wall -> return CalcOutcome.Wall
                GeoResult.Missing -> return CalcOutcome.Missing
            }
        }
        val perM = Geometry.kgPerMeter(area, density)
        val piece = perM * len
        return CalcOutcome.Ok(
            CalcResult(piece, perM, piece * qty, area * 1e-6 * len, perim?.let { it * 1e-3 * len },
                density, tableKgM, tableLbFt)
        )
    }

    /** Snapshot of what the user typed, so it survives process death (saved when the app goes to the background). */
    fun snapshot(): String = JSONObject()
        .put("units", units.name).put("shape", shape.name).put("material", materialId)
        .put("fields", JSONObject(fields.toMap())).put("length", lengthText).put("qty", qtyText)
        .put("price", priceText).put("priceUnit", priceUnit.name).put("target", targetText).put("family", familyId)
        .put("item", itemIndex).put("rebar", rebarIndex).toString()

    /** Restores a [snapshot]; ignored if it was taken in another unit system or is unreadable. */
    fun restore(json: String?) {
        val o = runCatching { JSONObject(json ?: return) }.getOrNull() ?: return
        if (o.optString("units") != units.name) return
        runCatching { ShapeKind.valueOf(o.getString("shape")) }.getOrNull()?.let { shape = it }
        materialId = o.optString("material", materialId)
        o.optJSONObject("fields")?.let { f ->
            fields.clear()
            f.keys().forEach { k -> fields[k] = f.getString(k) }
        }
        lengthText = o.optString("length", lengthText)
        qtyText = o.optString("qty", qtyText)
        priceText = o.optString("price", priceText)
        runCatching { PriceUnit.valueOf(o.optString("priceUnit", "KG")) }.getOrNull()?.let { priceUnit = it }
        targetText = o.optString("target", targetText)
        familyId = o.optString("family", familyId)
        itemIndex = o.optInt("item", itemIndex)
        rebarIndex = o.optInt("rebar", rebarIndex)
    }

    /** Plate thickness in mm (for the reverse calculation). */
    val thicknessMM: Double? get() = mmOf("t")

    /** Human-readable size for lists / PDF, in current units. */
    fun sizeDescription(): String {
        val du = units.dimUnit
        fun v(k: String) = fields[k]?.takeIf { it.isNotBlank() } ?: "?"
        val len = if (shape.isLinear) " × " + (lengthText.ifBlank { "?" }) + " " + units.lengthUnit else ""
        return when (shape) {
            ShapeKind.PLATE -> "${v("t")} × ${v("w")} × ${v("pl")} $du"
            ShapeKind.ROUND -> "Ø${v("d")} $du$len"
            ShapeKind.SQUARE -> "${v("a")} × ${v("a")} $du$len"
            ShapeKind.HEX -> "SW ${v("s")} $du$len"
            ShapeKind.FLAT -> "${v("w")} × ${v("t")} $du$len"
            ShapeKind.PIPE -> "Ø${v("od")} × ${v("wt")} $du$len"
            ShapeKind.RECT_TUBE -> "${v("h")} × ${v("w")} × ${v("wt")} $du$len"
            ShapeKind.ANGLE -> "L ${v("a")} × ${v("b")} × ${v("t")} $du$len"
            ShapeKind.CHANNEL, ShapeKind.IBEAM, ShapeKind.TEE -> "${v("h")} × ${v("b")} × ${v("tw")} / ${v("tf")} $du$len"
            ShapeKind.REBAR -> (rebarItem?.name ?: "") + len
            ShapeKind.SECTION -> (sectionItem?.name ?: "") + len
        }
    }
}
