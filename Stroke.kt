package com.example.inknotes.model

import java.util.UUID

/**
 * A single sampled input point, in page coordinates.
 *
 * Page coordinates are density-independent (1 unit = 1dp at 100% zoom): [com.example.inknotes.model.ToolState]
 * widths are used directly as page units with no conversion, and device density is folded only
 * into `ink.Viewport.scale`, which is the *only* thing that changes when the user pans or zooms.
 * A stored point is therefore never rewritten by zoom/pan, and looks identical on any device.
 *
 * [t] is milliseconds since the first point of the *stroke* (see [Stroke.startTimeMs] for the
 * absolute anchor). [pressure] is 0..1; 1 when the input device has no pressure sensor.
 */
data class InkPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
    val t: Int = 0,
)

enum class StrokeType { PEN, HIGHLIGHTER }

data class StrokeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class Stroke(
    /** In-memory identity only (used by undo/redo and selection). Not persisted. */
    val uid: String = UUID.randomUUID().toString(),
    val type: StrokeType,
    val colorArgb: Int,
    val width: Float,
    val points: List<InkPoint>,
    /** Wall-clock time (epoch ms) of the first point. Absolute point time = startTimeMs + point.t. */
    val startTimeMs: Long = 0L,
) {
    val bounds: StrokeBounds by lazy(LazyThreadSafetyMode.NONE) {
        if (points.isEmpty()) {
            StrokeBounds(0f, 0f, 0f, 0f)
        } else {
            var l = Float.MAX_VALUE
            var t = Float.MAX_VALUE
            var r = -Float.MAX_VALUE
            var b = -Float.MAX_VALUE
            for (p in points) {
                if (p.x < l) l = p.x
                if (p.x > r) r = p.x
                if (p.y < t) t = p.y
                if (p.y > b) b = p.y
            }
            val pad = width / 2f
            StrokeBounds(l - pad, t - pad, r + pad, b + pad)
        }
    }
}
