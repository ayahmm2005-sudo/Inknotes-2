package com.example.inknotes.recognition

import com.example.inknotes.model.RecognitionLanguage
import kotlinx.coroutines.flow.Flow

/** On-device download state of a language's Digital Ink recognition model. */
enum class ModelStatus { NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED, FAILED }

/** Checks, downloads, and removes the on-device Digital Ink models (Arabic, English, …). */
interface ModelManager {
    /** Current on-device status for [language]. Never starts a download by itself. */
    suspend fun status(language: RecognitionLanguage): ModelStatus

    /**
     * Downloads the model for [language], emitting [ModelStatus.DOWNLOADING] immediately and a
     * single terminal status ([ModelStatus.DOWNLOADED] or [ModelStatus.FAILED]) when the
     * download finishes. Safe to call again if a previous download failed.
     */
    fun download(language: RecognitionLanguage): Flow<ModelStatus>

    /** Deletes the on-device model for [language], freeing its storage. True on success. */
    suspend fun delete(language: RecognitionLanguage): Boolean
}
