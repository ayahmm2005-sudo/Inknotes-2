package com.example.inknotes.data.repository

import com.example.inknotes.data.local.NotebookDao
import com.example.inknotes.data.local.NotebookEntity
import com.example.inknotes.data.local.toModel
import com.example.inknotes.model.Notebook
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotebookRepository @Inject constructor(
    private val dao: NotebookDao,
) {
    fun observeNotebooks(): Flow<List<Notebook>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeNotebook(id: Long): Flow<Notebook?> =
        dao.observeById(id).map { it?.toModel() }

    suspend fun createNotebook(name: String, colorArgb: Int): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            NotebookEntity(name = name.trim(), colorArgb = colorArgb, createdAt = now, updatedAt = now)
        )
    }

    suspend fun rename(id: Long, name: String) =
        dao.rename(id, name.trim(), System.currentTimeMillis())

    /** Deleting a notebook cascades to its notes, strokes and text. */
    suspend fun delete(id: Long) = dao.delete(id)

    /** Guarantees the user always has somewhere to put a note. Returns the notebook id created, or null. */
    suspend fun ensureDefaultNotebook(name: String, colorArgb: Int): Long? =
        if (dao.count() == 0) createNotebook(name, colorArgb) else null
}
