package com.example.inknotes.model

/** Which handwriting model(s) to use when converting a note's ink to text. */
enum class RecognitionLanguage(val tag: String?) {
    AUTO(null),
    ARABIC("ar"),
    ENGLISH("en-US"),
}

data class Note(
    val id: Long,
    val notebookId: Long,
    val title: String,
    val language: RecognitionLanguage,
    /** First ~200 characters of typed text; used for search and list previews. */
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
)
