package com.example.inknotes.recognition

import com.example.inknotes.model.RecognitionLanguage
import com.example.inknotes.model.Stroke

/**
 * Converts handwritten ink (the same vector [Stroke] list the editor already stores and
 * autosaves) into recognized text. This is the recognition foundation only — nothing in the app
 * yet calls this to replace handwriting with text; that arrives with the Convert-to-Text UI.
 *
 * Implementations must run recognition off the caller's thread and must never touch
 * [com.example.inknotes.ink.InkCanvasView] or the handwriting input path, so a slow or
 * not-yet-downloaded model can never stall writing.
 */
interface HandwritingRecognizer {
    /**
     * Recognizes [strokes] (page-space vector strokes, in writing order) as handwriting in
     * [language]. Returns [RecognitionOutcome.Failure] with
     * [RecognitionFailureReason.MODEL_NOT_DOWNLOADED] rather than triggering a download —
     * callers decide when to download via [ModelManager].
     */
    suspend fun recognize(strokes: List<Stroke>, language: RecognitionLanguage): RecognitionOutcome

    /** Releases any recognizer/model resources held for [language]. Safe to call at any time. */
    fun close(language: RecognitionLanguage)
}
