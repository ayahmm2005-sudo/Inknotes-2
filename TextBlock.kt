package com.example.inknotes.model

import kotlinx.serialization.Serializable

@Serializable
enum class BlockType { PARAGRAPH, HEADING1, HEADING2, HEADING3, BULLET, NUMBERED }

@Serializable
enum class BlockAlign { START, CENTER, END, JUSTIFY }

/** Inline style applied to characters [start, end) of a block's text. */
@Serializable
data class TextSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val fontFamily: String? = null,
    val fontSizeSp: Float? = null,
    val colorArgb: Int? = null,
)

@Serializable
data class TextBlock(
    val id: String,
    val type: BlockType = BlockType.PARAGRAPH,
    val text: String = "",
    val align: BlockAlign = BlockAlign.START,
    val spans: List<TextSpan> = emptyList(),
)
