package com.weightkit.app

import com.weightkit.app.model.Reverse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReverseTest {
    @Test fun neededLength() {
        // round Ø20 = 2.466 kg/m; 100 kg in 4 pieces → 10.14 m each
        assertEquals(100 / (2.4661 * 4), Reverse.neededLengthM(100.0, 2.4661, 4)!!, 1e-9)
        assertNull(Reverse.neededLengthM(0.0, 2.4, 1))
        assertNull(Reverse.neededLengthM(10.0, 0.0, 1))
    }

    @Test fun neededPlateThickness() {
        // 1 × 2 m × 10 mm = 157 kg → 300 kg needs 19.108 mm
        assertEquals(10 * 300 / 157.0, Reverse.neededThickness(300.0, 10.0, 157.0, 1)!!, 1e-9)
        assertEquals(10 * 300 / (157.0 * 2), Reverse.neededThickness(300.0, 10.0, 157.0, 2)!!, 1e-9)
        assertNull(Reverse.neededThickness(300.0, 0.0, 157.0, 1))
    }

    @Test fun wholePieces() {
        assertEquals(3, Reverse.wholePieces(235.5, 78.5))   // exact multiple must not become 2
        assertEquals(3, Reverse.wholePieces(0.3, 0.1))      // 0.3/0.1 = 2.9999999999999996 in floating point
        assertEquals(2, Reverse.wholePieces(235.4, 78.5))
        assertEquals(0, Reverse.wholePieces(50.0, 78.5))
        assertEquals(0, Reverse.wholePieces(50.0, 0.0))
    }
}
