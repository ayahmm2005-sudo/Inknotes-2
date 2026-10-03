package com.example.inknotes.ink

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.inknotes.model.Stroke
import com.example.inknotes.model.StrokeType

/**
 * Turns strokes into drawn pixels, via [StrokeGeometry] for the actual path shape.
 *
 * Committed strokes are display-only: each stroke's [Path] is built once and cached by [Stroke.uid]
 * (a stroke is immutable, so its geometry never needs rebuilding), which is what keeps redrawing
 * a full page on undo/redo/erase cheap. [retainOnly] evicts strokes that are no longer on the page
 * so the cache can't grow without bound over a long editing session.
 *
 * Not thread-safe: call only from the view/UI thread, same as the rest of the ink package.
 */
class StrokeRenderer {
    private val pathCache = HashMap<String, Path>()
    private val geometry = StrokeGeometry()
    private val activePath = Path()

    private val penPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val highlighterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun drawStroke(canvas: Canvas, stroke: Stroke) {
        canvas.drawPath(pathFor(stroke), paintFor(stroke))
    }

    /** Draws only strokes whose bounds intersect [visibleRect] (page units), for large pages. */
    fun drawStrokes(canvas: Canvas, strokes: List<Stroke>, visibleRect: RectF) {
        for (s in strokes) {
            val b = s.bounds
            if (b.right < visibleRect.left || b.left > visibleRect.right ||
                b.bottom < visibleRect.top || b.top > visibleRect.bottom
            ) {
                continue
            }
            drawStroke(canvas, s)
        }
    }

    /** Draws the in-progress stroke straight from its live [PointBuffer] — nothing here is cached. */
    fun drawActive(canvas: Canvas, active: PointBuffer, type: StrokeType, color: Int, width: Float) {
        if (active.size == 0) return
        activePath.rewind()
        geometry.build(activePath, active, type, width)
        canvas.drawPath(activePath, configuredPaint(type, color, width))
    }

    /** Drops cached geometry for any stroke no longer present (removed by erase, undo, or clear). */
    fun retainOnly(strokes: List<Stroke>) {
        if (pathCache.isEmpty()) return
        val keep = HashSet<String>(strokes.size)
        for (s in strokes) keep.add(s.uid)
        pathCache.keys.retainAll(keep)
    }

    /**
     * Drops cached geometry for strokes whose *points* changed while keeping the same
     * [Stroke.uid] (a Lasso move). The next [drawStroke]/[drawStrokes] call rebuilds the path
     * from the stroke's new points.
     */
    fun invalidate(uids: Collection<String>) {
        if (uids.isEmpty() || pathCache.isEmpty()) return
        pathCache.keys.removeAll(uids)
    }

    private fun pathFor(stroke: Stroke): Path = pathCache.getOrPut(stroke.uid) {
        val buffer = PointBuffer(stroke.points.size).also { it.setFrom(stroke.points) }
        Path().also { geometry.build(it, buffer, stroke.type, stroke.width) }
    }

    private fun paintFor(stroke: Stroke): Paint = configuredPaint(stroke.type, stroke.colorArgb, stroke.width)

    private fun configuredPaint(type: StrokeType, color: Int, width: Float): Paint = when (type) {
        StrokeType.PEN -> penPaint.apply { this.color = color }
        StrokeType.HIGHLIGHTER -> highlighterPaint.apply {
            this.color = withAlpha(color, HIGHLIGHTER_ALPHA)
            this.strokeWidth = width
        }
    }

    private fun withAlpha(argb: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(argb), Color.green(argb), Color.blue(argb))

    companion object {
        const val HIGHLIGHTER_ALPHA = 97 // ~38% of 255
    }
}
