package com.example.inknotes.ink

import com.example.inknotes.model.Stroke

/**
 * Stroke eraser geometry. A stroke is hit when the eraser's swept path (a segment from the
 * previous sample to the current one, so fast swipes cannot skip strokes) comes within
 * `radius + strokeWidth / 2` of any part of the stroke's polyline, i.e. the eraser circle
 * overlaps the ink you can actually see. Untouched neighbours are never affected.
 * All values are in page units.
 */
object EraserHitTester {

    fun hits(
        strokes: List<Stroke>,
        ax: Float, ay: Float, bx: Float, by: Float,
        radius: Float,
    ): List<Stroke> {
        var result: ArrayList<Stroke>? = null
        val minX = minOf(ax, bx) - radius
        val maxX = maxOf(ax, bx) + radius
        val minY = minOf(ay, by) - radius
        val maxY = maxOf(ay, by) + radius

        for (stroke in strokes) {
            val b = stroke.bounds
            if (b.right < minX || b.left > maxX || b.bottom < minY || b.top > maxY) continue
            if (touches(stroke, ax, ay, bx, by, radius + stroke.width / 2f)) {
                if (result == null) result = ArrayList()
                result.add(stroke)
            }
        }
        return result ?: emptyList()
    }

    private fun touches(s: Stroke, ax: Float, ay: Float, bx: Float, by: Float, reach: Float): Boolean {
        val pts = s.points
        val reach2 = reach * reach
        if (pts.size == 1) {
            return pointSegmentDist2(pts[0].x, pts[0].y, ax, ay, bx, by) <= reach2
        }
        for (i in 0 until pts.size - 1) {
            val p = pts[i]
            val q = pts[i + 1]
            if (segmentDist2(p.x, p.y, q.x, q.y, ax, ay, bx, by) <= reach2) return true
        }
        return false
    }

    internal fun pointSegmentDist2(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return (px - cx) * (px - cx) + (py - cy) * (py - cy)
    }

    internal fun segmentDist2(
        p1x: Float, p1y: Float, p2x: Float, p2y: Float,
        q1x: Float, q1y: Float, q2x: Float, q2y: Float,
    ): Float {
        if (properlyIntersect(p1x, p1y, p2x, p2y, q1x, q1y, q2x, q2y)) return 0f
        return minOf(
            minOf(pointSegmentDist2(p1x, p1y, q1x, q1y, q2x, q2y), pointSegmentDist2(p2x, p2y, q1x, q1y, q2x, q2y)),
            minOf(pointSegmentDist2(q1x, q1y, p1x, p1y, p2x, p2y), pointSegmentDist2(q2x, q2y, p1x, p1y, p2x, p2y)),
        )
    }

    private fun cross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float): Float =
        (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

    private fun properlyIntersect(
        p1x: Float, p1y: Float, p2x: Float, p2y: Float,
        q1x: Float, q1y: Float, q2x: Float, q2y: Float,
    ): Boolean {
        val d1 = cross(q1x, q1y, q2x, q2y, p1x, p1y)
        val d2 = cross(q1x, q1y, q2x, q2y, p2x, p2y)
        val d3 = cross(p1x, p1y, p2x, p2y, q1x, q1y)
        val d4 = cross(p1x, p1y, p2x, p2y, q2x, q2y)
        return ((d1 > 0f && d2 < 0f) || (d1 < 0f && d2 > 0f)) &&
            ((d3 > 0f && d4 < 0f) || (d3 < 0f && d4 > 0f))
    }
}
