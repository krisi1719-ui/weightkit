package com.weightkit.app

import com.weightkit.app.model.GeoResult
import com.weightkit.app.model.Geometry
import com.weightkit.app.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class GeometryTest {
    private val steel = 7850.0

    private fun kgM(shape: ShapeKind, vararg v: Pair<String, Double>): Double {
        val r = Geometry.section(shape, mapOf(*v))
        assertTrue("expected Ok for $shape", r is GeoResult.Ok)
        return Geometry.kgPerMeter((r as GeoResult.Ok).cs.areaMM2, steel)
    }

    @Test fun plate1000x1000x10_is_78_5kg() {
        assertEquals(78.5, Geometry.plateVolumeM3(10.0, 1000.0, 1000.0) * steel, 1e-9)
    }

    @Test fun roundBar20mm() {
        // π/4·20²·7850e-6 = 2.466 kg/m
        assertEquals(2.466, kgM(ShapeKind.ROUND, "d" to 20.0), 0.001)
    }

    @Test fun squareBar10mm() = assertEquals(0.785, kgM(ShapeKind.SQUARE, "a" to 10.0), 1e-9)

    @Test fun hexBar24AF() {
        // √3/2·24²·7850e-6 = 3.916 kg/m
        assertEquals(3.916, kgM(ShapeKind.HEX, "s" to 24.0), 0.001)
    }

    @Test fun flatBar50x5() = assertEquals(1.9625, kgM(ShapeKind.FLAT, "w" to 50.0, "t" to 5.0), 1e-9)

    @Test fun pipe60_3x3_6() {
        // EN 10220: 60.3 × 3.6 = 5.03 kg/m
        assertEquals(5.03, kgM(ShapeKind.PIPE, "od" to 60.3, "wt" to 3.6), 0.01)
    }

    @Test fun pipeWallTooThick() {
        assertTrue(Geometry.section(ShapeKind.PIPE, mapOf("od" to 10.0, "wt" to 6.0)) is GeoResult.Wall)
    }

    @Test fun rectTube40x40x2_sharpCorners() {
        // (40·40 − 36·36)·7850e-6 = 2.386 kg/m (rounded-corner tables: ~2.31)
        assertEquals(2.386, kgM(ShapeKind.RECT_TUBE, "h" to 40.0, "w" to 40.0, "wt" to 2.0), 0.001)
    }

    @Test fun angle50x50x5() {
        // t(a+b−t) = 475 mm² → 3.73 kg/m; EN table 3.77 (incl. root radius) → within 2%
        val v = kgM(ShapeKind.ANGLE, "a" to 50.0, "b" to 50.0, "t" to 5.0)
        assertEquals(3.77, v, 3.77 * 0.02)
    }

    @Test fun ibeamWithoutRadiusCloseToIPE200() {
        // IPE 200 without root radii: 2·100·8.5 + 183·5.6 = 2724.8 mm² → 21.39 kg/m (table 22.4 incl. radii)
        val v = kgM(ShapeKind.IBEAM, "h" to 200.0, "b" to 100.0, "tw" to 5.6, "tf" to 8.5)
        assertEquals(2724.8 * 1e-6 * steel, v, 1e-9)
        val withRadius = (2724.8 + (4 - PI) * 12 * 12) * 1e-6 * steel
        assertEquals(22.4, withRadius, 22.4 * 0.01)
    }

    @Test fun missingInputs() {
        assertTrue(Geometry.section(ShapeKind.ROUND, emptyMap()) is GeoResult.Missing)
        assertTrue(Geometry.section(ShapeKind.FLAT, mapOf("w" to 0.0, "t" to 5.0)) is GeoResult.Missing)
    }
}
