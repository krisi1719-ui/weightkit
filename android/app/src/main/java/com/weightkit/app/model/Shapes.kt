package com.weightkit.app.model

import com.weightkit.app.R
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sqrt

/** One input dimension. `key` is shared across shapes so values survive switching shapes. */
data class DimField(val key: String, val labelRes: Int, val symbol: String)

enum class ShapeKind(val titleRes: Int) {
    PLATE(R.string.shape_plate),
    ROUND(R.string.shape_round),
    SQUARE(R.string.shape_square),
    HEX(R.string.shape_hex),
    FLAT(R.string.shape_flat),
    PIPE(R.string.shape_pipe),
    RECT_TUBE(R.string.shape_rect_tube),
    ANGLE(R.string.shape_angle),
    CHANNEL(R.string.shape_channel),
    IBEAM(R.string.shape_ibeam),
    TEE(R.string.shape_tee),
    REBAR(R.string.shape_rebar),
    SECTION(R.string.shape_section);

    /** Stable id, identical to the iOS raw value (test tags, saved state). */
    val id: String get() = if (this == RECT_TUBE) "rectTube" else name.lowercase()

    /** Plate has no bar length: its length/width are dimensions. */
    val isLinear: Boolean get() = this != PLATE

    val fields: List<DimField>
        get() = when (this) {
            PLATE -> listOf(
                DimField("t", R.string.dim_thickness, "t"),
                DimField("w", R.string.dim_width, "W"),
                DimField("pl", R.string.dim_length, "L"),
            )
            ROUND -> listOf(DimField("d", R.string.dim_diameter, "D"))
            SQUARE -> listOf(DimField("a", R.string.dim_side, "A"))
            HEX -> listOf(DimField("s", R.string.dim_af, "S"))
            FLAT -> listOf(DimField("w", R.string.dim_width, "W"), DimField("t", R.string.dim_thickness, "t"))
            PIPE -> listOf(DimField("od", R.string.dim_od, "D"), DimField("wt", R.string.dim_wall, "t"))
            RECT_TUBE -> listOf(
                DimField("h", R.string.dim_height, "H"),
                DimField("w", R.string.dim_width, "W"),
                DimField("wt", R.string.dim_wall, "t"),
            )
            ANGLE -> listOf(
                DimField("a", R.string.dim_leg_a, "A"),
                DimField("b", R.string.dim_leg_b, "B"),
                DimField("t", R.string.dim_thickness, "t"),
            )
            CHANNEL, IBEAM, TEE -> listOf(
                DimField("h", R.string.dim_height, "H"),
                DimField("b", R.string.dim_flange_width, "B"),
                DimField("tw", R.string.dim_web, "s"),
                DimField("tf", R.string.dim_flange, "t"),
            )
            REBAR, SECTION -> emptyList()
        }
}

/** Cross-section of a linear shape, mm / mm². Perimeter = coated surface per length (outer + inner). */
data class CrossSection(val areaMM2: Double, val perimeterMM: Double?)

sealed class GeoResult {
    data class Ok(val cs: CrossSection) : GeoResult()
    object Missing : GeoResult()
    object Wall : GeoResult()
}

object Geometry {
    /**
     * Cross-section formulas (all dimensions in mm):
     *  round      A = π/4·D²                 P = π·D
     *  square     A = a²                     P = 4a
     *  hex        A = (√3/2)·s²  (s = across flats)   P = 2√3·s
     *  flat       A = w·t                    P = 2(w+t)
     *  pipe       A = π/4·(D² − (D−2t)²)     P = π·D + π·(D−2t)
     *  rect tube  A = H·W − (H−2t)(W−2t)     P = 2(H+W) + 2(H+W−4t)   (sharp corners)
     *  angle      A = t·(a + b − t)          P = 2(a+b)
     *  channel/I  A = 2·B·tf + (H − 2tf)·tw  P = 2H + 4B − 2tw        (no root radii)
     *  tee        A = B·tf + (H − tf)·tw     P = 2(B+H)
     */
    fun section(shape: ShapeKind, v: Map<String, Double>): GeoResult {
        fun g(k: String): Double? = v[k]?.takeIf { it > 0 }
        fun ok(a: Double, p: Double?) = GeoResult.Ok(CrossSection(a, p))
        return when (shape) {
            ShapeKind.ROUND -> {
                val d = g("d") ?: return GeoResult.Missing
                ok(PI / 4 * d * d, PI * d)
            }
            ShapeKind.SQUARE -> {
                val a = g("a") ?: return GeoResult.Missing
                ok(a * a, 4 * a)
            }
            ShapeKind.HEX -> {
                val s = g("s") ?: return GeoResult.Missing
                ok(sqrt(3.0) / 2 * s * s, 2 * sqrt(3.0) * s)
            }
            ShapeKind.FLAT -> {
                val w = g("w") ?: return GeoResult.Missing
                val t = g("t") ?: return GeoResult.Missing
                ok(w * t, 2 * (w + t))
            }
            ShapeKind.PIPE -> {
                val d = g("od") ?: return GeoResult.Missing
                val t = g("wt") ?: return GeoResult.Missing
                if (2 * t > d) return GeoResult.Wall
                val di = d - 2 * t
                ok(PI / 4 * (d * d - di * di), PI * d + PI * di)
            }
            ShapeKind.RECT_TUBE -> {
                val h = g("h") ?: return GeoResult.Missing
                val w = g("w") ?: return GeoResult.Missing
                val t = g("wt") ?: return GeoResult.Missing
                if (2 * t > min(h, w)) return GeoResult.Wall
                ok(h * w - (h - 2 * t) * (w - 2 * t), 2 * (h + w) + 2 * (h + w - 4 * t))
            }
            ShapeKind.ANGLE -> {
                val a = g("a") ?: return GeoResult.Missing
                val b = g("b") ?: return GeoResult.Missing
                val t = g("t") ?: return GeoResult.Missing
                if (t >= min(a, b)) return GeoResult.Wall
                ok(t * (a + b - t), 2 * (a + b))
            }
            ShapeKind.CHANNEL, ShapeKind.IBEAM -> {
                val h = g("h") ?: return GeoResult.Missing
                val b = g("b") ?: return GeoResult.Missing
                val tw = g("tw") ?: return GeoResult.Missing
                val tf = g("tf") ?: return GeoResult.Missing
                if (2 * tf >= h || tw > b) return GeoResult.Wall
                ok(2 * b * tf + (h - 2 * tf) * tw, 2 * h + 4 * b - 2 * tw)
            }
            ShapeKind.TEE -> {
                val h = g("h") ?: return GeoResult.Missing
                val b = g("b") ?: return GeoResult.Missing
                val tw = g("tw") ?: return GeoResult.Missing
                val tf = g("tf") ?: return GeoResult.Missing
                if (tf >= h || tw > b) return GeoResult.Wall
                ok(b * tf + (h - tf) * tw, 2 * (b + h))
            }
            ShapeKind.PLATE, ShapeKind.REBAR, ShapeKind.SECTION -> GeoResult.Missing
        }
    }

    /** Plate volume in m³ from mm. */
    fun plateVolumeM3(t: Double, w: Double, l: Double): Double = t * w * l * 1e-9

    /** Mass per metre in kg/m from area in mm² and density in kg/m³. */
    fun kgPerMeter(areaMM2: Double, density: Double): Double = areaMM2 * 1e-6 * density
}
