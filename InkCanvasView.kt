package com.example.inknotes.ink

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.inknotes.model.ActiveTool
import com.example.inknotes.model.Stroke
import com.example.inknotes.model.StrokeType
import com.example.inknotes.model.ToolState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * The handwriting surface.
 *
 * Latency design
 * - Committed strokes are painted into a screen-sized bitmap once; a frame is then one bitmap
 *   blit + the active stroke, no matter how much is on the page.
 * - The active stroke lives in primitive arrays ([PointBuffer]); nothing is allocated per point.
 * - Events are consumed unbuffered (requestUnbufferedDispatch) and every MotionEvent history
 *   sample is used, so fast pen movement keeps its full sampling rate.
 * - Nothing here touches Compose state while writing, so there is no recomposition mid-stroke.
 *
 * Coordinates: strokes are stored in page space (dp at 100%). Pan/zoom only changes [viewport].
 * Palm rejection: pen wins over touch, touch is ignored while a pen is hovering/recently used,
 * very large contacts and system-flagged palms are ignored, and a second finger landing
 * shortly after the first turns a would-be stroke into a pinch instead.
 */
@SuppressLint("ClickableViewAccessibility")
class InkCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    // ---- Configuration (set by the host; cheap to change) ----
    var toolState: ToolState = ToolState()
    var fingerDrawing: Boolean = true
    var inputEnabled: Boolean = false
    var onViewportChanged: ((zoom: Float, tx: Float, ty: Float) -> Unit)? = null
    /** Fired when a stroke/erase gesture begins (lets the host close open panels). */
    var onInteractionStart: (() -> Unit)? = null
    /** Fired whenever the Lasso selection changes, with the number of currently selected strokes. */
    var onSelectionChanged: ((Int) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val viewport = Viewport(density)
    private val renderer = StrokeRenderer()
    private var document = InkDocument()

    // ---- Committed-stroke cache ----
    private var cache: Bitmap? = null
    private var cacheCanvas: Canvas? = null
    private var cacheDirty = true
    private var cachedScale = 0f
    private var cachedTx = 0f
    private var cachedTy = 0f

    // ---- Interaction state ----
    private enum class Mode { NONE, DRAW, ERASE, GESTURE, IGNORE, LASSO_DRAW, LASSO_DRAG }
    private var mode = Mode.NONE
    private var interactionStart = 0L
    private var activePointerId = -1
    private var activeIsPen = false
    private var activeIsFinger = false
    private var activeUsesPressure = false

    // Stroke being written
    private val active = PointBuffer()
    private var activeType = StrokeType.PEN
    private var activeColor = 0
    private var activeWidth = 0f
    private var strokeStartTime = 0L
    /** Wall-clock (epoch) anchor for [Stroke.startTimeMs] — separate from [strokeStartTime], which is
     *  uptimeMillis-based and only used for relative in-stroke timing (see [addSample]). */
    private var strokeStartWallMs = 0L

    // Eraser
    private var eraserPageX = 0f
    private var eraserPageY = 0f
    private var eraserRadiusDp = 0f
    private var eraserScreenX = 0f
    private var eraserScreenY = 0f
    private var eraserVisible = false

    // Lasso selection — strokes stay as-is in [document] until a drag is committed; while
    // drawing the loop or dragging, nothing is written to the document, so Cancel/abandon is free.
    private val lassoLoop = PointBuffer(64)
    private var selectedStrokes: List<Stroke> = emptyList()
    private var selectionUids: HashSet<String> = HashSet()
    private var selectionBoundsPage: RectF? = null
    private var dragStartPageX = 0f
    private var dragStartPageY = 0f
    private var dragDxPage = 0f
    private var dragDyPage = 0f
    private var dragSnapshot: List<Stroke> = emptyList()

    // Pan / pinch
    private var gestureActive = false
    private var gCx = 0f
    private var gCy = 0f
    private var gSpan = 0f
    private var gStartScale = 0f
    private var gStartTx = 0f
    private var gStartTy = 0f
    private var mCx = 0f
    private var mCy = 0f
    private var mSpan = 0f
    private var mCount = 0

    // Stylus / palm tracking
    private var stylusHovering = false
    private var stylusLastActive = 0L
    private var lastHoverEvent = 0L
    private val palmContactPx = resources.displayMetrics.xdpi * PALM_CONTACT_INCH

    // Reused scratch objects (no allocation in onDraw)
    private val visibleRect = RectF()
    private var lineBuffer = FloatArray(0)
    private val linePaint = Paint().apply {
        color = RULE_COLOR
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1f, density * 0.6f)
    }
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
        color = 0xAA243B8F.toInt()
    }
    private val lassoPath = Path()
    private val lassoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
        color = SELECTION_COLOR
        pathEffect = DashPathEffect(floatArrayOf(density * 7f, density * 5f), 0f)
    }
    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = density * 1.5f
        color = SELECTION_COLOR
        pathEffect = DashPathEffect(floatArrayOf(density * 7f, density * 5f), 0f)
    }
    private val selectionFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, Color.red(SELECTION_COLOR), Color.green(SELECTION_COLOR), Color.blue(SELECTION_COLOR))
    }

    private val documentListener = object : InkDocument.Listener {
        override fun onStrokeAppended(stroke: Stroke) {
            val c = cacheCanvas
            if (c != null && cacheIsCurrent()) {
                c.save()
                c.translate(viewport.tx, viewport.ty)
                c.scale(viewport.scale, viewport.scale)
                renderer.drawStroke(c, stroke)
                c.restore()
            } else {
                cacheDirty = true
            }
            invalidate()
        }

        override fun onContentReplaced() {
            cacheDirty = true
            // Strokes may have been removed/reordered/undone; a stale selection could reference
            // strokes that no longer exist, so drop it rather than risk a corrupt overlay or drag.
            clearSelectionState()
            invalidate()
        }

        override fun onStrokesModified(uids: Collection<String>) {
            renderer.invalidate(uids)
            cacheDirty = true
            invalidate()
        }
    }

    // ------------------------------------------------------------------ public API

    fun bind(doc: InkDocument) {
        if (doc === document) return
        if (isAttachedToWindow) document.removeListener(documentListener)
        document = doc
        if (isAttachedToWindow) doc.addListener(documentListener)
        cacheDirty = true
        invalidate()
    }

    fun restoreViewport(zoom: Float, tx: Float, ty: Float) {
        viewport.restore(zoom, tx, ty)
        cacheDirty = true
        invalidate()
    }

    fun resetViewport() {
        viewport.reset()
        cacheDirty = true
        invalidate()
        notifyViewport()
    }

    /** Deselects the current Lasso selection, if any. Does not change the page. */
    fun clearSelection() {
        if (selectedStrokes.isEmpty()) return
        clearSelectionState()
        invalidate()
    }

    /** Deletes the strokes currently selected via Lasso, as one undoable action. */
    fun deleteSelectedStrokes() {
        if (selectedStrokes.isEmpty()) return
        document.eraseStrokes(selectedStrokes)
        document.commitErase()
        clearSelectionState()
        invalidate()
    }

    private fun clearSelectionState() {
        if (selectedStrokes.isEmpty() && selectionBoundsPage == null) return
        selectedStrokes = emptyList()
        selectionUids = HashSet()
        selectionBoundsPage = null
        onSelectionChanged?.invoke(0)
    }

    // ------------------------------------------------------------------ lifecycle

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        document.addListener(documentListener)
        cacheDirty = true
    }

    override fun onDetachedFromWindow() {
        document.removeListener(documentListener)
        abandonInteraction()
        cache?.recycle()
        cache = null
        cacheCanvas = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cacheDirty = true
    }

    // ------------------------------------------------------------------ drawing

    override fun onDraw(canvas: Canvas) {
        drawPaper(canvas)
        val strokes = document.strokes

        if (mode == Mode.LASSO_DRAG) {
            drawWithDragOffset(canvas, strokes)
        } else if (gestureActive) {
            if (strokes.size <= DIRECT_DRAW_LIMIT || !drawCachePreview(canvas)) drawDirect(canvas, strokes)
        } else {
            val bmp = ensureCache()
            if (bmp != null) canvas.drawBitmap(bmp, 0f, 0f, null)
        }

        if (active.size > 0) {
            canvas.save()
            canvas.translate(viewport.tx, viewport.ty)
            canvas.scale(viewport.scale, viewport.scale)
            renderer.drawActive(canvas, active, activeType, activeColor, activeWidth)
            canvas.restore()
        }
        if (eraserVisible) {
            canvas.drawCircle(eraserScreenX, eraserScreenY, eraserRadiusDp * density, cursorPaint)
        }
        if (mode == Mode.LASSO_DRAW && lassoLoop.size > 1) {
            drawLassoLoop(canvas)
        }
        if (selectedStrokes.isNotEmpty() && mode != Mode.LASSO_DRAW) {
            drawSelectionOverlay(canvas)
        }
    }

    /** Lasso drag preview: unselected strokes drawn normally, selected ones translated live. */
    private fun drawWithDragOffset(canvas: Canvas, strokes: List<Stroke>) {
        viewport.visibleRect(width, height, visibleRect)
        canvas.save()
        canvas.translate(viewport.tx, viewport.ty)
        canvas.scale(viewport.scale, viewport.scale)
        for (s in strokes) {
            if (selectionUids.contains(s.uid)) continue
            val b = s.bounds
            if (b.right < visibleRect.left || b.left > visibleRect.right ||
                b.bottom < visibleRect.top || b.top > visibleRect.bottom
            ) {
                continue
            }
            renderer.drawStroke(canvas, s)
        }
        canvas.save()
        canvas.translate(dragDxPage, dragDyPage)
        for (s in dragSnapshot) renderer.drawStroke(canvas, s)
        canvas.restore()
        canvas.restore()
    }

    private fun drawLassoLoop(canvas: Canvas) {
        lassoPath.rewind()
        lassoPath.moveTo(toScreenX(lassoLoop.x[0]), toScreenY(lassoLoop.y[0]))
        for (i in 1 until lassoLoop.size) lassoPath.lineTo(toScreenX(lassoLoop.x[i]), toScreenY(lassoLoop.y[i]))
        canvas.drawPath(lassoPath, lassoPaint)
    }

    private fun drawSelectionOverlay(canvas: Canvas) {
        val b = selectionBoundsPage ?: return
        val dx = if (mode == Mode.LASSO_DRAG) dragDxPage else 0f
        val dy = if (mode == Mode.LASSO_DRAG) dragDyPage else 0f
        val left = toScreenX(b.left + dx)
        val top = toScreenY(b.top + dy)
        val right = toScreenX(b.right + dx)
        val bottom = toScreenY(b.bottom + dy)
        canvas.drawRect(left, top, right, bottom, selectionFillPaint)
        canvas.drawRect(left, top, right, bottom, selectionPaint)
    }

    private fun toScreenX(pageX: Float) = pageX * viewport.scale + viewport.tx
    private fun toScreenY(pageY: Float) = pageY * viewport.scale + viewport.ty

    private fun drawPaper(canvas: Canvas) {
        canvas.drawColor(PAPER_COLOR)
        val spacing = RULE_SPACING * viewport.scale
        if (spacing < 10f) return
        val first = ceil(-viewport.ty / spacing).toInt()
        val last = floor((height - viewport.ty) / spacing).toInt()
        val count = last - first + 1
        if (count <= 0) return
        if (lineBuffer.size < count * 4) lineBuffer = FloatArray(count * 4)
        var o = 0
        for (k in first..last) {
            val y = viewport.ty + k * spacing
            lineBuffer[o++] = 0f
            lineBuffer[o++] = y
            lineBuffer[o++] = width.toFloat()
            lineBuffer[o++] = y
        }
        canvas.drawLines(lineBuffer, 0, o, linePaint)
    }

    /** Exact vector drawing of what is on screen; used while panning/zooming small pages. */
    private fun drawDirect(canvas: Canvas, strokes: List<Stroke>) {
        viewport.visibleRect(width, height, visibleRect)
        canvas.save()
        canvas.translate(viewport.tx, viewport.ty)
        canvas.scale(viewport.scale, viewport.scale)
        renderer.drawStrokes(canvas, strokes, visibleRect)
        canvas.restore()
    }

    /** For very large pages: move the existing bitmap instead of re-drawing every stroke. */
    private fun drawCachePreview(canvas: Canvas): Boolean {
        val bmp = cache ?: return false
        if (cacheDirty || cachedScale <= 0f) return false
        val k = viewport.scale / cachedScale
        canvas.save()
        canvas.translate(viewport.tx - k * cachedTx, viewport.ty - k * cachedTy)
        canvas.scale(k, k)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        canvas.restore()
        return true
    }

    private fun cacheIsCurrent(): Boolean =
        cache != null && !cacheDirty && !gestureActive &&
            cachedScale == viewport.scale && cachedTx == viewport.tx && cachedTy == viewport.ty

    private fun ensureCache(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        var bmp = cache
        if (bmp == null || bmp.width != width || bmp.height != height) {
            bmp?.recycle()
            bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            cache = bmp
            cacheCanvas = Canvas(bmp)
            cacheDirty = true
        }
        if (cacheDirty || cachedScale != viewport.scale || cachedTx != viewport.tx || cachedTy != viewport.ty) {
            renderCache(bmp)
        }
        return bmp
    }

    private fun renderCache(bmp: Bitmap) {
        val c = cacheCanvas ?: return
        bmp.eraseColor(Color.TRANSPARENT)
        viewport.visibleRect(width, height, visibleRect)
        c.save()
        c.translate(viewport.tx, viewport.ty)
        c.scale(viewport.scale, viewport.scale)
        renderer.drawStrokes(c, document.strokes, visibleRect)
        c.restore()
        renderer.retainOnly(document.strokes)
        cachedScale = viewport.scale
        cachedTx = viewport.tx
        cachedTy = viewport.ty
        cacheDirty = false
    }

    // ------------------------------------------------------------------ hover (pen presence)

    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (isPen(event.getToolType(0))) {
            val now = SystemClock.uptimeMillis()
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    stylusHovering = true
                    lastHoverEvent = now
                    stylusLastActive = now
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    stylusHovering = false
                    stylusLastActive = now
                }
            }
            return true
        }
        return super.onHoverEvent(event)
    }

    private fun stylusActive(now: Long): Boolean =
        (stylusHovering && now - lastHoverEvent < HOVER_TIMEOUT_MS) ||
            now - stylusLastActive < PEN_GUARD_MS

    // ------------------------------------------------------------------ touch

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputEnabled) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> onDown(event)
            MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(event)
            MotionEvent.ACTION_MOVE -> onMove(event)
            MotionEvent.ACTION_POINTER_UP -> onPointerUp(event)
            MotionEvent.ACTION_UP -> onUp(event)
            MotionEvent.ACTION_CANCEL -> {
                abandonInteraction()
            }
        }
        return true
    }

    private fun onDown(event: MotionEvent) {
        abandonInteraction()
        parent?.requestDisallowInterceptTouchEvent(true)
        requestUnbufferedDispatch(event)
        interactionStart = event.eventTime
        startPointer(event, 0)
    }

    /** Decides what the pointer at [index] does: draw, erase, pan, or nothing. */
    private fun startPointer(event: MotionEvent, index: Int) {
        val toolType = event.getToolType(index)
        val now = SystemClock.uptimeMillis()
        val pen = isPen(toolType)

        if (pen) {
            stylusLastActive = now
        } else if (toolType == TOOL_TYPE_PALM ||
            (toolType == MotionEvent.TOOL_TYPE_FINGER &&
                (stylusActive(now) || event.getTouchMajor(index) > palmContactPx))
        ) {
            mode = Mode.IGNORE
            return
        }

        val precise = pen || toolType == MotionEvent.TOOL_TYPE_MOUSE
        val tool = if (toolType == MotionEvent.TOOL_TYPE_ERASER) ActiveTool.ERASER else toolState.tool
        activePointerId = event.getPointerId(index)
        activeIsPen = pen
        activeIsFinger = toolType == MotionEvent.TOOL_TYPE_FINGER
        activeUsesPressure = toolType == MotionEvent.TOOL_TYPE_STYLUS

        when {
            !precise && !fingerDrawing -> beginGesture(event)
            tool == ActiveTool.ERASER -> beginErase(event, index)
            tool == ActiveTool.LASSO -> beginLasso(event, index)
            else -> beginStroke(event, index, tool)
        }
    }

    private fun onPointerDown(event: MotionEvent) {
        val index = event.actionIndex
        val toolType = event.getToolType(index)
        val now = SystemClock.uptimeMillis()

        if (isPen(toolType)) {
            // The pen always wins: drop whatever a finger or palm was doing and write.
            stylusLastActive = now
            abandonInteraction()
            interactionStart = event.eventTime
            startPointer(event, index)
            return
        }
        if (toolType == TOOL_TYPE_PALM || stylusActive(now) || event.getTouchMajor(index) > palmContactPx) return

        when (mode) {
            Mode.DRAW, Mode.ERASE, Mode.LASSO_DRAW, Mode.LASSO_DRAG ->
                // Two fingers landing together is a pinch, not a stroke; a late second finger is ignored.
                if (activeIsFinger && event.eventTime - interactionStart < PINCH_GRACE_MS) {
                    abandonInteraction()
                    beginGesture(event)
                }
            Mode.GESTURE -> {
                // Finger count changed: re-baseline so the view doesn't jump.
                measure(event, -1)
                gCx = mCx
                gCy = mCy
                gSpan = mSpan
            }
            else -> Unit
        }
    }

    private fun onMove(event: MotionEvent) {
        when (mode) {
            Mode.DRAW -> {
                val i = event.findPointerIndex(activePointerId)
                if (i < 0) return
                if (activeIsPen) stylusLastActive = SystemClock.uptimeMillis()
                for (h in 0 until event.historySize) {
                    addSample(
                        event.getHistoricalX(i, h), event.getHistoricalY(i, h),
                        if (activeUsesPressure) event.getHistoricalPressure(i, h) else DEFAULT_PRESSURE,
                        event.getHistoricalEventTime(h),
                    )
                }
                addSample(
                    event.getX(i), event.getY(i),
                    if (activeUsesPressure) event.getPressure(i) else DEFAULT_PRESSURE,
                    event.eventTime,
                )
                invalidate()
            }
            Mode.ERASE -> {
                val i = event.findPointerIndex(activePointerId)
                if (i < 0) return
                if (activeIsPen) stylusLastActive = SystemClock.uptimeMillis()
                for (h in 0 until event.historySize) {
                    eraseTo(event.getHistoricalX(i, h), event.getHistoricalY(i, h))
                }
                eraseTo(event.getX(i), event.getY(i))
                eraserScreenX = event.getX(i)
                eraserScreenY = event.getY(i)
                invalidate()
            }
            Mode.GESTURE -> {
                measure(event, -1)
                val factor = if (mCount >= 2 && gSpan > MIN_SPAN_PX && mSpan > 0f) mSpan / gSpan else 1f
                viewport.applyGesture(gCx, gCy, mCx, mCy, factor)
                gCx = mCx
                gCy = mCy
                gSpan = mSpan
                invalidate()
            }
            Mode.LASSO_DRAW -> {
                val i = event.findPointerIndex(activePointerId)
                if (i < 0) return
                for (h in 0 until event.historySize) {
                    addLassoSample(event.getHistoricalX(i, h), event.getHistoricalY(i, h))
                }
                addLassoSample(event.getX(i), event.getY(i))
                invalidate()
            }
            Mode.LASSO_DRAG -> {
                val i = event.findPointerIndex(activePointerId)
                if (i < 0) return
                val px = viewport.pageX(event.getX(i))
                val py = viewport.pageY(event.getY(i))
                dragDxPage = px - dragStartPageX
                dragDyPage = py - dragStartPageY
                invalidate()
            }
            else -> Unit
        }
    }

    private fun onPointerUp(event: MotionEvent) {
        val index = event.actionIndex
        val canceled = (event.flags and MotionEvent.FLAG_CANCELED) != 0
        when (mode) {
            Mode.DRAW -> if (event.getPointerId(index) == activePointerId) {
                if (canceled) discardStroke() else {
                    addSample(event.getX(index), event.getY(index), lastPressure(event, index), event.eventTime)
                    commitStroke()
                }
                mode = Mode.NONE
            }
            Mode.ERASE -> if (event.getPointerId(index) == activePointerId) {
                if (canceled) document.cancelErase() else document.commitErase()
                eraserVisible = false
                mode = Mode.NONE
                invalidate()
            }
            Mode.GESTURE -> {
                measure(event, index)
                gCx = mCx
                gCy = mCy
                gSpan = mSpan
            }
            Mode.LASSO_DRAW -> if (event.getPointerId(index) == activePointerId) {
                if (canceled) lassoLoop.clear() else finishLassoDraw()
                mode = Mode.NONE
                invalidate()
            }
            Mode.LASSO_DRAG -> if (event.getPointerId(index) == activePointerId) {
                if (canceled) cancelLassoDrag() else commitLassoDrag()
                mode = Mode.NONE
                invalidate()
            }
            else -> Unit
        }
    }

    private fun onUp(event: MotionEvent) {
        val canceled = (event.flags and MotionEvent.FLAG_CANCELED) != 0
        when (mode) {
            Mode.DRAW -> {
                val i = event.findPointerIndex(activePointerId)
                if (i >= 0 && !canceled) {
                    addSample(event.getX(i), event.getY(i), lastPressure(event, i), event.eventTime)
                    commitStroke()
                } else {
                    discardStroke()
                }
            }
            Mode.ERASE -> {
                if (canceled) document.cancelErase() else document.commitErase()
                eraserVisible = false
            }
            Mode.GESTURE -> endGesture()
            Mode.LASSO_DRAW -> if (canceled) lassoLoop.clear() else finishLassoDraw()
            Mode.LASSO_DRAG -> if (canceled) cancelLassoDrag() else commitLassoDrag()
            else -> Unit
        }
        mode = Mode.NONE
        invalidate()
    }

    /** Cancels whatever is in progress without keeping its result. */
    private fun abandonInteraction() {
        when (mode) {
            Mode.DRAW -> discardStroke()
            Mode.ERASE -> {
                document.cancelErase()
                eraserVisible = false
            }
            Mode.GESTURE -> endGesture()
            Mode.LASSO_DRAW -> lassoLoop.clear()
            Mode.LASSO_DRAG -> cancelLassoDrag()
            else -> Unit
        }
        mode = Mode.NONE
        invalidate()
    }

    // ------------------------------------------------------------------ stroke capture

    private fun beginStroke(event: MotionEvent, index: Int, tool: ActiveTool) {
        mode = Mode.DRAW
        val highlighter = tool == ActiveTool.HIGHLIGHTER
        activeType = if (highlighter) StrokeType.HIGHLIGHTER else StrokeType.PEN
        activeColor = if (highlighter) toolState.highlighterColor else toolState.penColor
        activeWidth = if (highlighter) toolState.highlighterWidth else toolState.penWidth
        strokeStartTime = event.eventTime
        strokeStartWallMs = System.currentTimeMillis()
        active.clear()
        addSample(
            event.getX(index), event.getY(index),
            if (activeUsesPressure) event.getPressure(index) else DEFAULT_PRESSURE,
            event.eventTime,
        )
        onInteractionStart?.invoke()
        invalidate()
    }

    /**
     * Converts a screen sample to page space and appends it. Only sub-half-pixel repeats are
     * dropped; the first and last samples and all real movement are kept exactly, so tiny
     * strokes such as Arabic dots survive and recognition sees the true path and timing.
     */
    private fun addSample(sx: Float, sy: Float, pressure: Float, time: Long) {
        val px = viewport.pageX(sx)
        val py = viewport.pageY(sy)
        val n = active.size
        var t = (time - strokeStartTime).toInt().coerceAtLeast(0)
        if (n > 0) {
            val dx = px - active.x[n - 1]
            val dy = py - active.y[n - 1]
            val minDist = MIN_SAMPLE_DISTANCE_PX / viewport.scale
            if (dx * dx + dy * dy < minDist * minDist) return
            if (t < active.t[n - 1]) t = active.t[n - 1]
        }
        active.add(px, py, pressure.coerceIn(MIN_PRESSURE, 1f), t)
    }

    private fun lastPressure(event: MotionEvent, index: Int): Float =
        if (activeUsesPressure) event.getPressure(index) else DEFAULT_PRESSURE

    private fun commitStroke() {
        if (active.size == 0) return
        val stroke = Stroke(
            type = activeType,
            colorArgb = activeColor,
            width = activeWidth,
            points = active.toPoints(),
            startTimeMs = strokeStartWallMs,
        )
        active.clear()
        document.add(stroke)
    }

    private fun discardStroke() {
        active.clear()
    }

    // ------------------------------------------------------------------ eraser

    private fun beginErase(event: MotionEvent, index: Int) {
        mode = Mode.ERASE
        eraserRadiusDp = toolState.eraserWidth
        eraserPageX = viewport.pageX(event.getX(index))
        eraserPageY = viewport.pageY(event.getY(index))
        eraserScreenX = event.getX(index)
        eraserScreenY = event.getY(index)
        eraserVisible = true
        eraseSegment(eraserPageX, eraserPageY, eraserPageX, eraserPageY)
        onInteractionStart?.invoke()
        invalidate()
    }

    private fun eraseTo(sx: Float, sy: Float) {
        val px = viewport.pageX(sx)
        val py = viewport.pageY(sy)
        eraseSegment(eraserPageX, eraserPageY, px, py)
        eraserPageX = px
        eraserPageY = py
    }

    private fun eraseSegment(ax: Float, ay: Float, bx: Float, by: Float) {
        // The eraser has a fixed on-screen size, so its page-space radius shrinks as you zoom in.
        val radiusPage = eraserRadiusDp * density / viewport.scale
        val hit = EraserHitTester.hits(document.strokes, ax, ay, bx, by, radiusPage)
        if (hit.isNotEmpty()) document.eraseStrokes(hit)
    }

    // ------------------------------------------------------------------ lasso selection

    /** Starts either a new selection loop, or a drag if the touch lands inside the current selection. */
    private fun beginLasso(event: MotionEvent, index: Int) {
        val px = viewport.pageX(event.getX(index))
        val py = viewport.pageY(event.getY(index))
        val bounds = selectionBoundsPage
        if (bounds != null && selectedStrokes.isNotEmpty() &&
            RectF(bounds).apply { inset(-SELECTION_GRAB_PADDING_DP, -SELECTION_GRAB_PADDING_DP) }.contains(px, py)
        ) {
            beginLassoDrag(px, py)
        } else {
            clearSelectionState()
            beginLassoDraw(px, py)
        }
    }

    private fun beginLassoDraw(px: Float, py: Float) {
        mode = Mode.LASSO_DRAW
        lassoLoop.clear()
        lassoLoop.add(px, py, 1f, 0)
        onInteractionStart?.invoke()
        invalidate()
    }

    private fun addLassoSample(sx: Float, sy: Float) {
        val px = viewport.pageX(sx)
        val py = viewport.pageY(sy)
        val n = lassoLoop.size
        if (n > 0) {
            val dx = px - lassoLoop.x[n - 1]
            val dy = py - lassoLoop.y[n - 1]
            val minDist = MIN_SAMPLE_DISTANCE_PX / viewport.scale
            if (dx * dx + dy * dy < minDist * minDist) return
        }
        lassoLoop.add(px, py, 1f, 0)
    }

    private fun finishLassoDraw() {
        val n = lassoLoop.size
        val picked = if (n >= 3) LassoSelector.select(document.strokes, lassoLoop.x, lassoLoop.y, n) else emptyList()
        lassoLoop.clear()
        applySelection(picked)
    }

    private fun applySelection(strokes: List<Stroke>) {
        selectedStrokes = strokes
        selectionUids = HashSet(strokes.map { it.uid })
        selectionBoundsPage = computeBounds(strokes)
        onSelectionChanged?.invoke(strokes.size)
        invalidate()
    }

    private fun beginLassoDrag(px: Float, py: Float) {
        mode = Mode.LASSO_DRAG
        dragStartPageX = px
        dragStartPageY = py
        dragDxPage = 0f
        dragDyPage = 0f
        dragSnapshot = selectedStrokes
        onInteractionStart?.invoke()
        invalidate()
    }

    /** Commits the drag as ONE replace action; strokes keep their [Stroke.uid] identity. */
    private fun commitLassoDrag() {
        if (dragDxPage != 0f || dragDyPage != 0f) {
            val translated = dragSnapshot.map { s ->
                s.copy(points = s.points.map { it.copy(x = it.x + dragDxPage, y = it.y + dragDyPage) })
            }
            if (document.replaceStrokes(translated)) {
                selectedStrokes = translated
                selectionUids = HashSet(translated.map { it.uid })
                selectionBoundsPage = computeBounds(translated)
                onSelectionChanged?.invoke(translated.size)
            }
        }
        dragDxPage = 0f
        dragDyPage = 0f
        dragSnapshot = emptyList()
    }

    /** Reverts an interrupted drag; nothing was ever written to the document, so this is free. */
    private fun cancelLassoDrag() {
        dragDxPage = 0f
        dragDyPage = 0f
        dragSnapshot = emptyList()
    }

    private fun computeBounds(strokes: List<Stroke>): RectF? {
        if (strokes.isEmpty()) return null
        var l = Float.MAX_VALUE
        var t = Float.MAX_VALUE
        var r = -Float.MAX_VALUE
        var b = -Float.MAX_VALUE
        for (s in strokes) {
            val sb = s.bounds
            if (sb.left < l) l = sb.left
            if (sb.top < t) t = sb.top
            if (sb.right > r) r = sb.right
            if (sb.bottom > b) b = sb.bottom
        }
        return RectF(l, t, r, b)
    }

    // ------------------------------------------------------------------ pan / zoom

    private fun beginGesture(event: MotionEvent) {
        mode = Mode.GESTURE
        gestureActive = true
        gStartScale = viewport.scale
        gStartTx = viewport.tx
        gStartTy = viewport.ty
        measure(event, -1)
        gCx = mCx
        gCy = mCy
        gSpan = mSpan
    }

    private fun endGesture() {
        if (!gestureActive) return
        gestureActive = false
        if (viewport.scale != gStartScale || viewport.tx != gStartTx || viewport.ty != gStartTy) {
            cacheDirty = true // re-render committed strokes crisply at the final scale
            notifyViewport()
        }
        invalidate()
    }

    /** Centroid, mean spread and count of the pointers (optionally skipping one). */
    private fun measure(event: MotionEvent, skipIndex: Int) {
        var cx = 0f
        var cy = 0f
        var n = 0
        for (i in 0 until event.pointerCount) {
            if (i == skipIndex) continue
            cx += event.getX(i)
            cy += event.getY(i)
            n++
        }
        mCount = n
        if (n == 0) return
        cx /= n
        cy /= n
        var span = 0f
        if (n >= 2) {
            for (i in 0 until event.pointerCount) {
                if (i == skipIndex) continue
                span += hypot(event.getX(i) - cx, event.getY(i) - cy)
            }
            span /= n
        }
        mCx = cx
        mCy = cy
        mSpan = span
    }

    private fun notifyViewport() {
        onViewportChanged?.invoke(viewport.zoom, viewport.tx, viewport.ty)
    }

    private fun isPen(toolType: Int) =
        toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER

    private companion object {
        const val PAPER_COLOR = 0xFFFAFBFD.toInt()
        const val RULE_COLOR = 0xFFDDE2EA.toInt()
        const val RULE_SPACING = 36f            // page units (dp) between ruled lines

        const val DEFAULT_PRESSURE = 0.5f        // finger / mouse: uniform line width
        const val MIN_PRESSURE = 0.02f
        const val MIN_SAMPLE_DISTANCE_PX = 0.5f

        const val TOOL_TYPE_PALM = 5             // MotionEvent.TOOL_TYPE_PALM (API 34)
        const val PALM_CONTACT_INCH = 0.6f       // touch contacts wider than this are palms
        const val PEN_GUARD_MS = 500L            // ignore touch this long after the pen was active
        const val HOVER_TIMEOUT_MS = 5000L       // safety net if a hover-exit is never delivered
        const val PINCH_GRACE_MS = 250L
        const val MIN_SPAN_PX = 8f
        const val DIRECT_DRAW_LIMIT = 500        // above this, pans move the cached bitmap instead

        const val SELECTION_COLOR = 0xFF1E88E5.toInt()  // Lasso loop / selection outline
        const val SELECTION_GRAB_PADDING_DP = 12f       // extra margin (page units) for grabbing a selection
    }
}
