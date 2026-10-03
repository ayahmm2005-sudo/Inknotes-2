package com.example.inknotes.ink

import com.example.inknotes.model.Stroke

/**
 * The in-memory vector page: an ordered list of strokes plus stroke-level undo/redo.
 *
 * - Every history entry is a whole-stroke operation (add, remove, clear); nothing is ever
 *   rasterized or partially erased, so recognition later sees the exact original points.
 * - Main-thread only. Persistence works on [snapshot] copies (strokes are immutable).
 */
class InkDocument {

    interface Listener {
        /** A stroke was appended at the end: renderers can draw just that stroke. */
        fun onStrokeAppended(stroke: Stroke) {}

        /** Strokes changed in another way: renderers must redraw everything. */
        fun onContentReplaced() {}

        /**
         * One or more existing strokes were replaced in place (same [Stroke.uid], new geometry —
         * e.g. a Lasso move). Renderers should evict any per-stroke cache for these uids; the
         * strokes themselves are still found via [strokes] / [onContentReplaced].
         */
        fun onStrokesModified(uids: Collection<String>) {}

        /** A committed change (or history change) happened: autosave and undo/redo state. */
        fun onChanged() {}
    }

    private class Removed(val index: Int, val stroke: Stroke)

    private sealed interface Action {
        class Add(val stroke: Stroke) : Action
        class Remove(val removed: List<Removed>) : Action
        class Clear(val strokes: List<Stroke>) : Action
        class Replace(val pairs: List<Pair<Stroke, Stroke>>) : Action
    }

    private val items = ArrayList<Stroke>()
    private val undoStack = ArrayDeque<Action>()
    private val redoStack = ArrayDeque<Action>()
    private val pendingErase = ArrayList<Removed>()
    private val listeners = ArrayList<Listener>()

    /** Increments on every committed change. Autosave compares it with the last saved value. */
    var revision: Int = 0
        private set

    val strokes: List<Stroke> get() = items
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun addListener(listener: Listener) {
        if (listener !in listeners) listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    fun snapshot(): List<Stroke> = items.toList()

    /** Replaces the page with saved content. Clears history and does not count as a change. */
    fun load(saved: List<Stroke>) {
        items.clear()
        items.addAll(saved)
        undoStack.clear()
        redoStack.clear()
        pendingErase.clear()
        revision = 0
        notifyReplaced()
        notifyChanged()
    }

    fun add(stroke: Stroke) {
        items.add(stroke)
        pushUndo(Action.Add(stroke))
        redoStack.clear()
        revision++
        listeners.toList().forEach { it.onStrokeAppended(stroke) }
        notifyChanged()
    }

    // ---- Eraser: strokes disappear live during the drag, and the whole drag is ONE undo step ----

    fun eraseStrokes(hit: List<Stroke>) {
        var removedAny = false
        for (stroke in hit) {
            val index = items.indexOfFirst { it.uid == stroke.uid }
            if (index < 0) continue
            items.removeAt(index)
            pendingErase.add(Removed(index, stroke))
            removedAny = true
        }
        if (removedAny) notifyReplaced()
    }

    fun commitErase() {
        if (pendingErase.isEmpty()) return
        pushUndo(Action.Remove(pendingErase.toList()))
        pendingErase.clear()
        redoStack.clear()
        revision++
        notifyChanged()
    }

    /** Puts back everything removed during an interrupted erase gesture. */
    fun cancelErase() {
        if (pendingErase.isEmpty()) return
        for (r in pendingErase.asReversed()) items.add(r.index, r.stroke)
        pendingErase.clear()
        notifyReplaced()
    }

    // ---- Lasso: move a selection of strokes, committed as ONE undo step ----

    /**
     * Replaces each existing stroke whose [Stroke.uid] matches one in [updated] with the given
     * (already-translated) copy. Used by Lasso drag: the strokes keep their identity, only their
     * points move. Strokes not currently in the document are ignored. Returns false if nothing
     * matched (so callers can skip pushing an undo step for a no-op drag).
     */
    fun replaceStrokes(updated: List<Stroke>): Boolean {
        if (updated.isEmpty()) return false
        val pairs = ArrayList<Pair<Stroke, Stroke>>(updated.size)
        for (newStroke in updated) {
            val index = items.indexOfFirst { it.uid == newStroke.uid }
            if (index < 0) continue
            pairs.add(items[index] to newStroke)
            items[index] = newStroke
        }
        if (pairs.isEmpty()) return false
        pushUndo(Action.Replace(pairs))
        redoStack.clear()
        revision++
        val uids = pairs.map { it.second.uid }
        listeners.toList().forEach { it.onStrokesModified(uids) }
        notifyChanged()
        return true
    }

    fun clear() {
        if (items.isEmpty()) return
        pushUndo(Action.Clear(items.toList()))
        items.clear()
        redoStack.clear()
        revision++
        notifyReplaced()
        notifyChanged()
    }

    // ---- History ----

    fun undo(): Boolean {
        val action = undoStack.removeLastOrNull() ?: return false
        when (action) {
            is Action.Add -> {
                val i = items.indexOfLast { it.uid == action.stroke.uid }
                if (i >= 0) items.removeAt(i)
            }
            is Action.Remove -> for (r in action.removed.asReversed()) items.add(r.index.coerceAtMost(items.size), r.stroke)
            is Action.Clear -> items.addAll(action.strokes)
            is Action.Replace -> for ((old, _) in action.pairs) {
                val i = items.indexOfFirst { it.uid == old.uid }
                if (i >= 0) items[i] = old
            }
        }
        redoStack.addLast(action)
        revision++
        notifyReplaced()
        if (action is Action.Replace) {
            val uids = action.pairs.map { it.first.uid }
            listeners.toList().forEach { it.onStrokesModified(uids) }
        }
        notifyChanged()
        return true
    }

    fun redo(): Boolean {
        val action = redoStack.removeLastOrNull() ?: return false
        when (action) {
            is Action.Add -> {
                items.add(action.stroke)
                undoStack.addLast(action)
                revision++
                listeners.toList().forEach { it.onStrokeAppended(action.stroke) }
                notifyChanged()
                return true
            }
            is Action.Remove -> for (r in action.removed) if (r.index < items.size) items.removeAt(r.index)
            is Action.Clear -> items.clear()
            is Action.Replace -> for ((_, new) in action.pairs) {
                val i = items.indexOfFirst { it.uid == new.uid }
                if (i >= 0) items[i] = new
            }
        }
        undoStack.addLast(action)
        revision++
        notifyReplaced()
        if (action is Action.Replace) {
            val uids = action.pairs.map { it.second.uid }
            listeners.toList().forEach { it.onStrokesModified(uids) }
        }
        notifyChanged()
        return true
    }

    private fun pushUndo(action: Action) {
        undoStack.addLast(action)
        if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
    }

    private fun notifyReplaced() = listeners.toList().forEach { it.onContentReplaced() }
    private fun notifyChanged() = listeners.toList().forEach { it.onChanged() }

    private companion object {
        const val MAX_HISTORY = 300
    }
}
