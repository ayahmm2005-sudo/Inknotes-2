package com.example.inknotes.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recognitionLanguage: RecognitionLanguage = RecognitionLanguage.AUTO,
    /** When false, only a stylus draws; fingers pan/zoom. */
    val fingerDrawing: Boolean = true,
)
