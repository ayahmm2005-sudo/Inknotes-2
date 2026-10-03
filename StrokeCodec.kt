package com.example.inknotes.data.local

import com.example.inknotes.model.InkPoint
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * Compact binary format, 11 bytes per point:
 *   float x, float y, uint8 pressure (0..255), int16 dt (ms since previous point).
 * A 500-point stroke is ~5.5 KB instead of tens of KB as JSON.
 */
object StrokeCodec {
    private const val BYTES_PER_POINT = 11

    fun encode(points: List<InkPoint>): ByteArray {
        val buf = ByteBuffer.allocate(points.size * BYTES_PER_POINT)
        var prevT = 0
        points.forEachIndexed { i, p ->
            buf.putFloat(p.x)
            buf.putFloat(p.y)
            buf.put((p.pressure.coerceIn(0f, 1f) * 255f).roundToInt().toByte())
            val dt = if (i == 0) 0 else (p.t - prevT).coerceIn(0, Short.MAX_VALUE.toInt())
            buf.putShort(dt.toShort())
            prevT += dt
        }
        return buf.array()
    }

    fun decode(bytes: ByteArray): List<InkPoint> {
        val count = bytes.size / BYTES_PER_POINT
        val buf = ByteBuffer.wrap(bytes)
        val out = ArrayList<InkPoint>(count)
        var t = 0
        repeat(count) {
            val x = buf.getFloat()
            val y = buf.getFloat()
            val pressure = (buf.get().toInt() and 0xFF) / 255f
            t += buf.getShort().toInt()
            out.add(InkPoint(x, y, pressure, t))
        }
        return out
    }
}
