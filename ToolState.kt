package com.example.inknotes.model

enum class ActiveTool { PEN, HIGHLIGHTER, ERASER, LASSO }

data class ToolState(
    val tool: ActiveTool = ActiveTool.PEN,
    val penColor: Int = 0xFF1B2440.toInt(),
    val penWidth: Float = 3f,
    val highlighterColor: Int = 0xFFFFD84D.toInt(),
    val highlighterWidth: Float = 18f,
    val eraserWidth: Float = 14f,
) {
    val currentColor: Int
        get() = if (tool == ActiveTool.HIGHLIGHTER) highlighterColor else penColor
    val currentWidth: Float
        get() = when (tool) {
            ActiveTool.HIGHLIGHTER -> highlighterWidth
            ActiveTool.ERASER -> eraserWidth
            else -> penWidth
        }
}
