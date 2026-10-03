package com.example.inknotes.ink

import android.graphics.RectF

/**
 * The transform between page coordinates (dp, see [com.example.inknotes.model.InkPoint]) and this
 * view's screen pixels. This is the *only* thing that changes when the user pans or zooms — stored
 * stroke geometry is never touched, so zoom/pan can never corrupt a note.
 *
 * A mutable class rather than an immutable value: it is updated on every pan/zoom touch-move, and
 * this avoids allocating a new instance per frame while a gesture is in progress.
 *
 * [tx]/[ty] are in screen pixels. [scale] converts page dp directly to screen pixels (it already
 * includes device density), while [zoom] is the user-facing, device-independent 100%-relative
 * value shown in the UI and persisted in a [ViewportSnapshot].
 */
class Viewport(private val density: Float) {
    var zoom: Float = 1f
        private set
    var scale: Float = density
        private set
    var tx: Float = 0f
        private set
    var ty: Float = 0f
        private set

    fun pageX(screenX: Float): Float = (screenX - tx) / scale
    fun pageY(screenY: Float): Float = (screenY - ty) / scale

    fun visibleRect(viewWidthPx: Int, viewHeightPx: Int, out: RectF) {
        out.left = (0f - tx) / scale
        out.top = (0f - ty) / scale
        out.right = (viewWidthPx - tx) / scale
        out.bottom = (viewHeightPx - ty) / scale
    }

    /**
     * Applies one incremental step of a pan/pinch gesture: zooms by [factor] around the gesture's
     * previous centroid ([oldCx],[oldCy]) and pans so that centroid now sits at ([newCx],[newCy]).
     * A pure pan is just [factor] == 1 with the centroid having moved.
     */
    fun applyGesture(oldCx: Float, oldCy: Float, newCx: Float, newCy: Float, factor: Float) {
        val pageX = (oldCx - tx) / scale
        val pageY = (oldCy - ty) / scale
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = zoom * density
        tx = newCx - pageX * scale
        ty = newCy - pageY * scale
    }

    fun restore(newZoom: Float, newTx: Float, newTy: Float) {
        zoom = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        scale = zoom * density
        tx = newTx
        ty = newTy
    }

    fun reset() {
        zoom = 1f
        scale = density
        tx = 0f
        ty = 0f
    }

    companion object {
        const val MIN_ZOOM = 0.25f
        const val MAX_ZOOM = 8f
    }
}

/** What [EditorViewModel][com.example.inknotes.ui.editor.EditorViewModel] keeps in memory so returning to a note keeps its pan/zoom. Not persisted to Room. */
data class ViewportSnapshot(val zoom: Float, val tx: Float, val ty: Float)
