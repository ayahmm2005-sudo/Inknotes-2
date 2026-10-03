package com.example.inknotes.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.inknotes.model.AppSettings
import com.example.inknotes.model.RecognitionLanguage
import com.example.inknotes.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val LANGUAGE = stringPreferencesKey("recognition_language")
        val FINGER = booleanPreferencesKey("finger_drawing")
    }

    val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                themeMode = p[Keys.THEME].toEnum(ThemeMode.SYSTEM),
                recognitionLanguage = p[Keys.LANGUAGE].toEnum(RecognitionLanguage.AUTO),
                fingerDrawing = p[Keys.FINGER] ?: true,
            )
        }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.THEME] = mode.name }
    suspend fun setRecognitionLanguage(lang: RecognitionLanguage) = store.edit { it[Keys.LANGUAGE] = lang.name }
    suspend fun setFingerDrawing(enabled: Boolean) = store.edit { it[Keys.FINGER] = enabled }

    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() } ?: default
}
