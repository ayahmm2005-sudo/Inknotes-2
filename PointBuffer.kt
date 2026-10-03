package com.example.inknotes.ink

import com.example.inknotes.model.InkPoint

/**
 * Growable primitive buffer of ink samples. The active stroke lives in one of these while the
 * pen is down, so writing allocates nothing per point (no GC pauses mid-stroke).
 * Main-thread only.
 */
class PointBuffer(initialCapacity: Int = 256) {
    var x = FloatArray(initialCapacity); private set
    var y = FloatArray(initialCapacity); private set
    var pressure = FloatArray(initialCapacity); private set
    var t = IntArray(initialCapacity); private set
    var size = 0; private set

    fun clear() {
        size = 0
    }

    fun add(px: Float, py: Float, p: Float, time: Int) {
        if (size == x.size) grow(size * 2)
        x[size] = px
        y[size] = py
        pressure[size] = p
        t[size] = time
        size++
    }

    fun setFrom(points: List<InkPoint>) {
        size = 0
        if (points.size > x.size) grow(points.size)
        for (p in points) add(p.x, p.y, p.pressure, p.t)
    }

    fun toPoints(): List<InkPoint> {
        val out = ArrayList<InkPoint>(size)
        for (i in 0 until size) out.add(InkPoint(x[i], y[i], pressure[i], t[i]))
        return out
    }

    private fun grow(capacity: Int) {
        val n = maxOf(capacity, 16)
        x = x.copyOf(n)
        y = y.copyOf(n)
        pressure = pressure.copyOf(n)
        t = t.copyOf(n)
    }
}
