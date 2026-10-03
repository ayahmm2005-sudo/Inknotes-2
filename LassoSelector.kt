package com.example.inknotes.ink

import com.example.inknotes.model.Stroke

/**
 * Point-in-polygon selection for the Lasso tool.
 *
 * The lasso loop is captured in the same page-space coordinates as strokes (via
 * [Viewport.pageX]/[Viewport.pageY]), so selection is correct regardless of the current
 * zoom/pan — exactly like [EraserHitTester].
 *
 * A stroke is selected when any of its sampled points lies inside the closed loop (even-odd
 * ray casting), or when any of its points lies exactly on the loop boundary. This is a cheap,
 * good-enough approximation of "intersects or is inside the loop": it is exact for strokes
 * entirely inside or outside the loop, and for strokes that cross the loop boundary it selects
 * them as soon as any sampled point falls inside, which in practice is how freehand lasso
 * selection is expected to behave.
 */
object LassoSelector {

    fun select(strokes: List<Stroke>, loopX: FloatArray, loopY: FloatArray, loopSize: Int): List<Stroke> {
        if (loopSize < 3 || strokes.isEmpty()) return emptyList()

        var loopLeft = Float.MAX_VALUE
        var loopTop = Float.MAX_VALUE
        var loopRight = -Float.MAX_VALUE
        var loopBottom = -Float.MAX_VALUE
        for (i in 0 until loopSize) {
            val x = loopX[i]
            val y = loopY[i]
            if (x < loopLeft) loopLeft = x
            if (x > loopRight) loopRight = x
            if (y < loopTop) loopTop = y
            if (y > loopBottom) loopBottom = y
        }

        val result = ArrayList<Stroke>()
        for (stroke in strokes) {
            val b = stroke.bounds
            // Quick reject: stroke bounding box doesn't overlap the loop's bounding box at all.
            if (b.right < loopLeft || b.left > loopRight || b.bottom < loopTop || b.top > loopBottom) continue

            var inside = false
            for (p in stroke.points) {
                if (contains(loopX, loopY, loopSize, p.x, p.y)) {
                    inside = true
                    break
                }
            }
            if (inside) result.add(stroke)
        }
        return result
    }

    /** Even-odd ray-casting point-in-polygon test. */
    private fun contains(px: FloatArray, py: FloatArray, n: Int, x: Float, y: Float): Boolean {
        var inside = false
        var j = n - 1
        for (i in 0 until n) {
            val xi = px[i]
            val yi = py[i]
            val xj = px[j]
            val yj = py[j]
            if ((yi > y) != (yj > y)) {
                val xIntersect = xi + (y - yi) / (yj - yi) * (xj - xi)
                if (x < xIntersect) inside = !inside
            }
            j = i
        }
        return inside
    }
}
