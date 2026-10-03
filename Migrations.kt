package com.example.inknotes.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v2: strokes remember their absolute start time so recognition can use real inter-stroke timing. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE strokes ADD COLUMN startTimeMs INTEGER NOT NULL DEFAULT 0")
    }
}
