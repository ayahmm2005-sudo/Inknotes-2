package com.example.inknotes.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.inknotes.model.RecognitionLanguage
import com.example.inknotes.model.StrokeType

@Entity(tableName = "notebooks")
data class NotebookEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

/** Query result: a notebook plus how many notes it contains. */
data class NotebookWithCount(
    val id: Long,
    val name: String,
    val colorArgb: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val noteCount: Int,
)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = NotebookEntity::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("notebookId"), Index("updatedAt")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val notebookId: Long,
    val title: String,
    val language: RecognitionLanguage,
    val preview: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "strokes",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("noteId")],
)
data class StrokeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val orderIndex: Int,
    val type: StrokeType,
    val colorArgb: Int,
    val width: Float,
    val pointCount: Int,
    @ColumnInfo(defaultValue = "0") val startTimeMs: Long,
    val boundsLeft: Float,
    val boundsTop: Float,
    val boundsRight: Float,
    val boundsBottom: Float,
    /** Compact binary encoding, see [StrokeCodec]. */
    val points: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is StrokeEntity && id == other.id &&
        noteId == other.noteId && orderIndex == other.orderIndex && points.contentEquals(other.points)

    override fun hashCode(): Int = 31 * id.hashCode() + points.contentHashCode()
}

/** Typed text for a note, stored as JSON-encoded [com.example.inknotes.model.TextBlock] list. */
@Entity(
    tableName = "note_content",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class NoteContentEntity(
    @PrimaryKey val noteId: Long,
    val blocksJson: String,
)
