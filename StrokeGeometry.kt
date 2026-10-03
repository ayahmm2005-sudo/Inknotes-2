package com.example.inknotes.ink

import android.graphics.Path
import com.example.inknotes.model.StrokeType

/**
 * Turns raw samples into drawable paths. This is display-only: smoothing and pressure width
 * are applied here, while the stored points stay exactly as captured.
 *
 * Pen: one filled outline whose half-width follows (smoothed) pressure.
 * Highlighter: a constant-width stroked centerline (drawn as a single path so it never
 * doubles up on itself).
 *
 * Owns scratch arrays, so use from one thread (the UI thread) only.
 */
class StrokeGeometry {
    private var lx = FloatArray(0)
    private var ly = FloatArray(0)
    private var rx = FloatArray(0)
    private var ry = FloatArray(0)
    private var radius = FloatArray(0)

    fun build(path: Path, buf: PointBuffer, type: StrokeType, width: Float) {
        if (buf.size == 0) return
        when (type) {
            StrokeType.HIGHLIGHTER -> centerline(path, buf)
            StrokeType.PEN -> outline(path, buf, width)
        }
    }

    private fun centerline(path: Path, b: PointBuffer) {
        val n = b.size
        val x = b.x
        val y = b.y
        path.moveTo(x[0], y[0])
        if (n == 1) {
            path.lineTo(x[0] + 0.01f, y[0]) // zero-length line + round cap = a dot
            return
        }
        for (i in 1 until n - 1) {
            path.quadTo(x[i], y[i], (x[i] + x[i + 1]) / 2f, (y[i] + y[i + 1]) / 2f)
        }
        path.lineTo(x[n - 1], y[n - 1])
    }

    private fun outline(path: Path, b: PointBuffer, baseWidth: Float) {
        val n = b.size
        val x = b.x
        val y = b.y
        ensureCapacity(n)

        var smoothed = b.pressure[0]
        for (i in 0 until n) {
            smoothed += PRESSURE_SMOOTHING * (b.pressure[i] - smoothed)
            radius[i] = 0.5f * baseWidth * (MIN_FACTOR + PRESSURE_GAIN * smoothed)
        }

        if (n == 1) {
            path.addCircle(x[0], y[0], radius[0], Path.Direction.CCW)
            return
        }

        var dxPrev = 1f
        var dyPrev = 0f
        for (i in 0 until n) {
            val a = if (i > 0) i - 1 else 0
            val c = if (i < n - 1) i + 1 else n - 1
            var dx = x[c] - x[a]
            var dy = y[c] - y[a]
            val len = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            if (len < 1e-6f) {
                dx = dxPrev
                dy = dyPrev
            } else {
                dx /= len
                dy /= len
            }
            dxPrev = dx
            dyPrev = dy
            val nx = -dy * radius[i]
            val ny = dx * radius[i]
            lx[i] = x[i] + nx
            ly[i] = y[i] + ny
            rx[i] = x[i] - nx
            ry[i] = y[i] - ny
        }

        path.moveTo(lx[0], ly[0])
        for (i in 1 until n - 1) {
            path.quadTo(lx[i], ly[i], (lx[i] + lx[i + 1]) / 2f, (ly[i] + ly[i + 1]) / 2f)
        }
        path.lineTo(lx[n - 1], ly[n - 1])
        path.lineTo(rx[n - 1], ry[n - 1])
        for (i in n - 2 downTo 1) {
            path.quadTo(rx[i], ry[i], (rx[i] + rx[i - 1]) / 2f, (ry[i] + ry[i - 1]) / 2f)
        }
        path.lineTo(rx[0], ry[0])
        path.close()
        // Round caps. Same winding direction as the outline so the non-zero fill never cancels.
        path.addCircle(x[0], y[0], radius[0], Path.Direction.CCW)
        path.addCircle(x[n - 1], y[n - 1], radius[n - 1], Path.Direction.CCW)
    }

    private fun ensureCapacity(n: Int) {
        if (lx.size >= n) return
        val cap = maxOf(n, lx.size * 2, 256)
        lx = FloatArray(cap); ly = FloatArray(cap)
        rx = FloatArray(cap); ry = FloatArray(cap)
        radius = FloatArray(cap)
    }

    private companion object {
        /** Width factor = MIN_FACTOR + PRESSURE_GAIN * pressure; 0.5 (finger/mouse) is ~nominal. */
        const val MIN_FACTOR = 0.6f
        const val PRESSURE_GAIN = 0.7f
        const val PRESSURE_SMOOTHING = 0.4f
    }
}
