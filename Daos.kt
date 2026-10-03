package com.example.inknotes.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.inknotes.model.RecognitionLanguage
import kotlinx.coroutines.flow.Flow

@Dao
interface NotebookDao {
    @Query(
        """
        SELECT n.id, n.name, n.colorArgb, n.createdAt, n.updatedAt,
               (SELECT COUNT(*) FROM notes WHERE notebookId = n.id) AS noteCount
        FROM notebooks n
        ORDER BY n.updatedAt DESC
        """
    )
    fun observeAll(): Flow<List<NotebookWithCount>>

    @Query(
        """
        SELECT n.id, n.name, n.colorArgb, n.createdAt, n.updatedAt,
               (SELECT COUNT(*) FROM notes WHERE notebookId = n.id) AS noteCount
        FROM notebooks n WHERE n.id = :id
        """
    )
    fun observeById(id: Long): Flow<NotebookWithCount?>

    @Query("SELECT COUNT(*) FROM notebooks")
    suspend fun count(): Int

    @Insert
    suspend fun insert(notebook: NotebookEntity): Long

    @Query("UPDATE notebooks SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, name: String, now: Long)

    @Query("UPDATE notebooks SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE notebookId = :notebookId ORDER BY updatedAt DESC")
    fun observeByNotebook(notebookId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    /** [query] must already have LIKE wildcards escaped with a backslash. */
    @Query(
        """
        SELECT * FROM notes
        WHERE title LIKE '%' || :query || '%' ESCAPE '\'
           OR preview LIKE '%' || :query || '%' ESCAPE '\'
        ORDER BY updatedAt DESC
        LIMIT 100
        """
    )
    fun search(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("UPDATE notes SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: Long, title: String, now: Long)

    @Query("UPDATE notes SET language = :language WHERE id = :id")
    suspend fun setLanguage(id: Long, language: RecognitionLanguage)

    @Query("UPDATE notes SET preview = :preview, updatedAt = :now WHERE id = :id")
    suspend fun updatePreview(id: Long, preview: String, now: Long)

    @Query("UPDATE notes SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface StrokeDao {
    @Query("SELECT * FROM strokes WHERE noteId = :noteId ORDER BY orderIndex ASC")
    suspend fun getForNote(noteId: Long): List<StrokeEntity>

    @Insert
    suspend fun insertAll(strokes: List<StrokeEntity>)

    @Query("DELETE FROM strokes WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)
}

@Dao
interface NoteContentDao {
    @Query("SELECT * FROM note_content WHERE noteId = :noteId")
    suspend fun get(noteId: Long): NoteContentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(content: NoteContentEntity)
}
