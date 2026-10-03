package com.example.inknotes.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        NotebookEntity::class,
        NoteEntity::class,
        StrokeEntity::class,
        NoteContentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun notebookDao(): NotebookDao
    abstract fun noteDao(): NoteDao
    abstract fun strokeDao(): StrokeDao
    abstract fun noteContentDao(): NoteContentDao

    companion object {
        const val NAME = "inknotes.db"
    }
}
