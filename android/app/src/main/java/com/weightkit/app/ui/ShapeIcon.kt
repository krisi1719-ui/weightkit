package com.weightkit.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.weightkit.app.model.ShapeKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Filled cross-section pictogram for each shape (drawn in a unit square). */
@Composable
fun ShapeIcon(shape: ShapeKind, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawShape(shape, color) }
}

private fun DrawScope.drawShape(shape: ShapeKind, color: Color) {
    val s = min(size.width, size.height)
    val ox = (size.width - s) / 2
    val oy = (size.height - s) / 2
    fun p(x: Double, y: Double) = Offset(ox + (x * s).toFloat(), oy + (y * s).toFloat())
    fun poly(vararg pts: Pair<Double, Double>): Path = Path().apply {
        val f = p(pts[0].first, pts[0].second)
        moveTo(f.x, f.y)
        for (q in pts.drop(1)) { val o = p(q.first, q.second); lineTo(o.x, o.y) }
        close()
    }
    fun circle(cx: Double, cy: Double, r: Double) = drawCircle(color, (r * s).toFloat(), p(cx, cy))

    when (shape) {
        ShapeKind.PLATE -> {
            drawPath(poly(0.08 to 0.62, 0.62 to 0.62, 0.92 to 0.38, 0.38 to 0.38), color)
            drawPath(poly(0.08 to 0.62, 0.62 to 0.62, 0.62 to 0.70, 0.08 to 0.70), color.copy(alpha = 0.6f))
            drawPath(poly(0.62 to 0.62, 0.92 to 0.38, 0.92 to 0.46, 0.62 to 0.70), color.copy(alpha = 0.4f))
        }
        ShapeKind.ROUND -> circle(0.5, 0.5, 0.38)
        ShapeKind.SQUARE -> drawPath(poly(0.14 to 0.14, 0.86 to 0.14, 0.86 to 0.86, 0.14 to 0.86), color)
        ShapeKind.HEX -> {
            val pts = (0 until 6).map { i -> val a = i * PI / 3; (0.5 + 0.4 * cos(a)) to (0.5 + 0.4 * sin(a)) }
            drawPath(poly(*pts.toTypedArray()), color)
        }
        ShapeKind.FLAT -> drawPath(poly(0.06 to 0.38, 0.94 to 0.38, 0.94 to 0.62, 0.06 to 0.62), color)
        ShapeKind.PIPE -> {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addOval(androidx.compose.ui.geometry.Rect(p(0.5, 0.5), (0.4 * s).toFloat()))
                addOval(androidx.compose.ui.geometry.Rect(p(0.5, 0.5), (0.28 * s).toFloat()))
            }
            drawPath(path, color)
        }
        ShapeKind.RECT_TUBE -> {
            val path = poly(0.1 to 0.18, 0.9 to 0.18, 0.9 to 0.82, 0.1 to 0.82)
            path.addPath(poly(0.22 to 0.30, 0.78 to 0.30, 0.78 to 0.70, 0.22 to 0.70))
            path.fillType = PathFillType.EvenOdd
            drawPath(path, color)
        }
        ShapeKind.ANGLE -> drawPath(poly(0.15 to 0.1, 0.3 to 0.1, 0.3 to 0.75, 0.88 to 0.75, 0.88 to 0.9, 0.15 to 0.9), color)
        ShapeKind.CHANNEL -> drawPath(
            poly(0.2 to 0.1, 0.8 to 0.1, 0.8 to 0.24, 0.36 to 0.24, 0.36 to 0.76, 0.8 to 0.76, 0.8 to 0.9, 0.2 to 0.9), color
        )
        ShapeKind.IBEAM, ShapeKind.SECTION -> drawPath(
            poly(
                0.12 to 0.1, 0.88 to 0.1, 0.88 to 0.23, 0.57 to 0.23, 0.57 to 0.77, 0.88 to 0.77,
                0.88 to 0.9, 0.12 to 0.9, 0.12 to 0.77, 0.43 to 0.77, 0.43 to 0.23, 0.12 to 0.23
            ), color
        )
        ShapeKind.TEE -> drawPath(
            poly(0.1 to 0.12, 0.9 to 0.12, 0.9 to 0.27, 0.57 to 0.27, 0.57 to 0.9, 0.43 to 0.9, 0.43 to 0.27, 0.1 to 0.27), color
        )
        ShapeKind.REBAR -> {
            circle(0.5, 0.5, 0.3)
            for (i in 0 until 8) {
                val a = i * PI / 4
                circle(0.5 + 0.32 * cos(a), 0.5 + 0.32 * sin(a), 0.06)
            }
        }
    }
}
