package com.example.inknotes.data.repository

import androidx.room.withTransaction
import com.example.inknotes.data.local.AppDatabase
import com.example.inknotes.data.local.NoteContentEntity
import com.example.inknotes.data.local.NoteEntity
import com.example.inknotes.data.local.toEntity
import com.example.inknotes.data.local.toModel
import com.example.inknotes.model.Note
import com.example.inknotes.model.RecognitionLanguage
import com.example.inknotes.model.Stroke
import com.example.inknotes.model.TextBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepository @Inject constructor(
    private val db: AppDatabase,
) {
    private val noteDao = db.noteDao()
    private val notebookDao = db.notebookDao()
    private val strokeDao = db.strokeDao()
    private val contentDao = db.noteContentDao()

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val blocksSerializer = ListSerializer(TextBlock.serializer())

    // ---- Observing -------------------------------------------------------

    fun observeNotes(notebookId: Long): Flow<List<Note>> =
        noteDao.observeByNotebook(notebookId).map { l -> l.map { it.toModel() } }

    fun observeNote(id: Long): Flow<Note?> =
        noteDao.observeById(id).map { it?.toModel() }

    fun observeRecent(limit: Int = 12): Flow<List<Note>> =
        noteDao.observeRecent(limit).map { l -> l.map { it.toModel() } }

    fun search(query: String): Flow<List<Note>> {
        val escaped = query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return noteDao.search(escaped).map { l -> l.map { it.toModel() } }
    }

    // ---- Note management -------------------------------------------------

    suspend fun createNote(notebookId: Long, title: String = ""): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        val id = noteDao.insert(
            NoteEntity(
                notebookId = notebookId, title = title.trim(),
                language = RecognitionLanguage.AUTO, preview = "",
                createdAt = now, updatedAt = now,
            )
        )
        notebookDao.touch(notebookId, now)
        id
    }

    suspend fun rename(noteId: Long, title: String) =
        noteDao.rename(noteId, title.trim(), System.currentTimeMillis())

    suspend fun setLanguage(noteId: Long, language: RecognitionLanguage) =
        noteDao.setLanguage(noteId, language)

    suspend fun delete(noteId: Long) = noteDao.delete(noteId)

    /** Copies metadata, all strokes and typed text into a new note. Returns the new id. */
    suspend fun duplicate(noteId: Long, titleSuffix: String = " (copy)"): Long? = db.withTransaction {
        val src = noteDao.getById(noteId) ?: return@withTransaction null
        val now = System.currentTimeMillis()
        val newId = noteDao.insert(
            src.copy(
                id = 0,
                title = if (src.title.isBlank()) src.title else src.title + titleSuffix,
                createdAt = now,
                updatedAt = now,
            )
        )
        val strokes = strokeDao.getForNote(noteId).map { it.copy(id = 0, noteId = newId) }
        if (strokes.isNotEmpty()) strokeDao.insertAll(strokes)
        contentDao.get(noteId)?.let { contentDao.upsert(it.copy(noteId = newId)) }
        notebookDao.touch(src.notebookId, now)
        newId
    }

    // ---- Handwriting content --------------------------------------------

    suspend fun loadStrokes(noteId: Long): List<Stroke> = withContext(Dispatchers.Default) {
        strokeDao.getForNote(noteId).map { it.toModel() }
    }

    /** Replaces the note's strokes atomically. Called by the debounced autosave. */
    suspend fun saveStrokes(noteId: Long, strokes: List<Stroke>) {
        val entities = withContext(Dispatchers.Default) {
            strokes.mapIndexed { i, s -> s.toEntity(noteId, i) }
        }
        db.withTransaction {
            strokeDao.deleteForNote(noteId)
            if (entities.isNotEmpty()) strokeDao.insertAll(entities)
            noteDao.touch(noteId, System.currentTimeMillis())
        }
    }

    // ---- Typed text content ---------------------------------------------

    suspend fun loadBlocks(noteId: Long): List<TextBlock> = withContext(Dispatchers.Default) {
        val raw = contentDao.get(noteId)?.blocksJson ?: return@withContext emptyList()
        runCatching { json.decodeFromString(blocksSerializer, raw) }.getOrDefault(emptyList())
    }

    suspend fun saveBlocks(noteId: Long, blocks: List<TextBlock>) {
        val (encoded, preview) = withContext(Dispatchers.Default) {
            json.encodeToString(blocksSerializer, blocks) to
                blocks.joinToString(" ") { it.text }.replace(Regex("\\s+"), " ").trim().take(200)
        }
        db.withTransaction {
            contentDao.upsert(NoteContentEntity(noteId, encoded))
            noteDao.updatePreview(noteId, preview, System.currentTimeMillis())
        }
    }
}
