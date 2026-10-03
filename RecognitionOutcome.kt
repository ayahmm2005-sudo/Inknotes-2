package com.example.inknotes.recognition

/** Result of a single [HandwritingRecognizer.recognize] call. */
sealed interface RecognitionOutcome {
    /** [candidates] is ranked best-first and is never empty. */
    data class Success(val candidates: List<String>) : RecognitionOutcome
    data class Failure(val reason: RecognitionFailureReason) : RecognitionOutcome
}

/** Why a [RecognitionOutcome.Failure] happened, so the caller can react appropriately. */
enum class RecognitionFailureReason {
    /** [HandwritingRecognizer.recognize] was called with no strokes. */
    NO_STROKES,

    /** [com.example.inknotes.model.RecognitionLanguage] has no fixed ML Kit model (e.g. AUTO). */
    UNSUPPORTED_LANGUAGE,

    /** The on-device model for the requested language hasn't been downloaded yet. */
    MODEL_NOT_DOWNLOADED,

    /** The recognizer itself failed (unexpected ML Kit error). */
    RECOGNIZER_ERROR,
}
