package com.example.inknotes.data.local

import com.example.inknotes.model.Note
import com.example.inknotes.model.Notebook
import com.example.inknotes.model.Stroke

fun NotebookWithCount.toModel() = Notebook(
    id = id, name = name, colorArgb = colorArgb, noteCount = noteCount,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun NoteEntity.toModel() = Note(
    id = id, notebookId = notebookId, title = title, language = language,
    preview = preview, createdAt = createdAt, updatedAt = updatedAt,
)

fun Stroke.toEntity(noteId: Long, orderIndex: Int): StrokeEntity {
    val b = bounds
    return StrokeEntity(
        noteId = noteId,
        orderIndex = orderIndex,
        type = type,
        colorArgb = colorArgb,
        width = width,
        pointCount = points.size,
        startTimeMs = startTimeMs,
        boundsLeft = b.left, boundsTop = b.top, boundsRight = b.right, boundsBottom = b.bottom,
        points = StrokeCodec.encode(points),
    )
}

fun StrokeEntity.toModel() = Stroke(
    type = type,
    colorArgb = colorArgb,
    width = width,
    points = StrokeCodec.decode(points),
    startTimeMs = startTimeMs,
)
