package com.example.inknotes.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.example.inknotes.data.local.AppDatabase
import com.example.inknotes.data.local.MIGRATION_1_2
import com.example.inknotes.data.local.NoteContentDao
import com.example.inknotes.data.local.NoteDao
import com.example.inknotes.data.local.NotebookDao
import com.example.inknotes.data.local.StrokeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideNotebookDao(db: AppDatabase): NotebookDao = db.notebookDao()
    @Provides fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()
    @Provides fun provideStrokeDao(db: AppDatabase): StrokeDao = db.strokeDao()
    @Provides fun provideNoteContentDao(db: AppDatabase): NoteContentDao = db.noteContentDao()

    /** Outlives every screen; used to flush unsaved ink when a ViewModel is cleared. */
    @Provides @Singleton @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore
}
