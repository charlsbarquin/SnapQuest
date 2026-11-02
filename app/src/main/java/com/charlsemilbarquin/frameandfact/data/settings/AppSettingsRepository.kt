package com.charlsemilbarquin.frameandfact.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

const val SETTINGS_DATASTORE_NAME = "frame_and_fact_settings"
const val THEME_PREFERENCE_KEY = "theme_mode"

private val THEME_MODE = stringPreferencesKey(THEME_PREFERENCE_KEY)
private val TUTORIAL_COMPLETED = booleanPreferencesKey("tutorial_completed")
private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

fun createAppSettingsRepository(context: Context): AppSettingsRepository =
    PreferencesAppSettingsRepository(context.applicationContext.settingsDataStore)

interface AppSettingsRepository {
    val settings: Flow<SettingsReadState>
    suspend fun setTheme(theme: ThemePreference): ThemeWriteResult
    suspend fun markTutorialCompleted(): TutorialWriteResult
}

class PreferencesAppSettingsRepository(
    private val dataStore: DataStore<Preferences>
) : AppSettingsRepository {
    override val settings: Flow<SettingsReadState> = flow {
        while (true) {
            try {
                dataStore.data.map { preferences ->
                    val rawValue = preferences[THEME_MODE]
                    val theme = rawValue?.let { value ->
                        ThemePreference.entries.firstOrNull { it.name == value }
                    } ?: ThemePreference.SYSTEM_DEFAULT
                    SettingsReadState.Ready(AppSettings(theme, preferences[TUTORIAL_COMPLETED] ?: false))
                }.collect { emit(it) }
                break
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emit(SettingsReadState.Error("Settings could not be loaded. System default theme is in use."))
                delay(1_000)
            }
        }
    }

    override suspend fun setTheme(theme: ThemePreference): ThemeWriteResult = try {
        dataStore.edit { preferences -> preferences[THEME_MODE] = theme.name }
        ThemeWriteResult.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ThemeWriteResult.Failed("Theme setting could not be saved. The previous setting remains in effect.")
    }

    override suspend fun markTutorialCompleted(): TutorialWriteResult = try {
        dataStore.edit { preferences -> preferences[TUTORIAL_COMPLETED] = true }
        TutorialWriteResult.Saved
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        TutorialWriteResult.Failed("Tutorial completion could not be saved. Please try again.")
    }
}
