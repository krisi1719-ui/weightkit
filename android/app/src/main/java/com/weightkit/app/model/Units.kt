package com.weightkit.app.model

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

enum class UnitSystem(
    val dimUnit: String,
    val lengthUnit: String,
    val weightUnit: String,
    /** mm per display unit of dimensions */
    val mmPerDim: Double,
    /** metres per display unit of length */
    val mPerLength: Double,
    /** kg per display unit of weight */
    val kgPerWeight: Double,
) {
    METRIC("mm", "m", "kg", 1.0, 1.0, 1.0),
    IMPERIAL("in", "ft", "lb", 25.4, 0.3048, 0.45359237),
}

object Fmt {
    private fun nf(frac: Int, min: Int = 0): NumberFormat =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            maximumFractionDigits = frac
            minimumFractionDigits = min
            isGroupingUsed = true
        }

    /** Number with sensible precision for weights/lengths. */
    fun smart(v: Double): String {
        val a = abs(v)
        val frac = if (a < 1) 3 else if (a < 100) 2 else 1
        return nf(frac).format(v)
    }

    fun fixed(v: Double, frac: Int): String = nf(frac, frac).format(v)

    /** Plain value for putting back into a text field. */
    fun edit(v: Double): String =
        NumberFormat.getNumberInstance(Locale.getDefault()).apply {
            isGroupingUsed = false
            maximumFractionDigits = 4
        }.format(v)

    private val NUMBER = Regex("^[+-]?(\\d+(\\.\\d*)?|\\.\\d+)$")

    /**
     * Accepts "12,5" and "12.5" (spaces / no-break spaces ignored). Plain decimals only, the same
     * on iOS: no exponents, hex or "12f" (which Kotlin's toDouble would accept).
     */
    fun parse(s: String): Double? {
        val t = s.filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }.replace(',', '.')
        if (!NUMBER.matches(t)) return null
        return t.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun weight(kg: Double, u: UnitSystem): String = smart(kg / u.kgPerWeight) + " " + u.weightUnit

    fun money(v: Double, symbol: String): String {
        val s = fixed(v, 2)
        return if (symbol.isEmpty()) s else "$s $symbol"
    }

    fun date(millis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(millis))
}

/** Reverse calculation (Pro): what is needed for a target weight. Same on iOS. */
object Reverse {
    /** Bar length per piece [m] so that [qty] pieces weigh [targetKg]. */
    fun neededLengthM(targetKg: Double, perMeterKg: Double, qty: Int): Double? =
        if (targetKg > 0 && perMeterKg > 0 && qty > 0) targetKg / (perMeterKg * qty) else null

    /** Plate thickness (same unit as [thickness]); weight is proportional to thickness. */
    fun neededThickness(targetKg: Double, thickness: Double, pieceKg: Double, qty: Int): Double? =
        if (targetKg > 0 && thickness > 0 && pieceKg > 0 && qty > 0) thickness * targetKg / (pieceKg * qty) else null

    /** Whole pieces that fit in [targetKg]. The epsilon keeps e.g. 235.5 / 78.5 from becoming 2. */
    fun wholePieces(targetKg: Double, pieceKg: Double): Long =
        if (targetKg > 0 && pieceKg > 0) kotlin.math.floor(targetKg / pieceKg * (1 + 1e-9)).toLong() else 0
}
