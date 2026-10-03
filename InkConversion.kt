package com.example.inknotes.recognition

import com.example.inknotes.model.Stroke
import com.google.mlkit.vision.digitalink.Ink

/**
 * Converts vector [Stroke]s into ML Kit's [Ink] representation. Nothing is rasterized: every
 * sampled point is passed through as-is.
 *
 * Timing: each point's absolute time is [Stroke.startTimeMs] + the point's own `t` (ms since the
 * stroke began, see [com.example.inknotes.model.InkPoint.t]). Using the same wall-clock timeline
 * for every stroke — rather than restarting at 0 per stroke — preserves the real gaps between
 * strokes, which ML Kit uses for word/letter segmentation.
 */
internal fun List<Stroke>.toInk(): Ink {
    val builder = Ink.builder()
    for (stroke in this) builder.addStroke(stroke.toMlKitStroke())
    return builder.build()
}

private fun Stroke.toMlKitStroke(): Ink.Stroke {
    val builder = Ink.Stroke.builder()
    for (point in points) {
        builder.addPoint(Ink.Point.create(point.x, point.y, startTimeMs + point.t))
    }
    return builder.build()
}
