package com.charlsemilbarquin.frameandfact.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class AppSettingsRepositoryTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test
    fun missingThemePreferenceUsesSystemDefault() = runBlocking {
        val scope = testScope()
        try {
            val repository = PreferencesAppSettingsRepository(createStore(temporaryFolder.newFilePath("default"), scope))
            assertEquals(SettingsReadState.Ready(AppSettings.DEFAULT), repository.settings.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun everyThemeSelectionCanBeSavedAndReadBack() = runBlocking {
        ThemePreference.entries.forEachIndexed { index, preference ->
            val scope = testScope()
            try {
                val repository = PreferencesAppSettingsRepository(
                    createStore(temporaryFolder.newFilePath("theme-$index"), scope)
                )
                assertEquals(ThemeWriteResult.Saved, repository.setTheme(preference))
                assertEquals(SettingsReadState.Ready(AppSettings(preference)), repository.settings.first())
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun storedThemeSurvivesDataStoreReopen() = runBlocking {
        val file = temporaryFolder.newFilePath("reopen")
        val firstScope = testScope()
        val firstRepository = PreferencesAppSettingsRepository(createStore(file, firstScope))
        assertEquals(ThemeWriteResult.Saved, firstRepository.setTheme(ThemePreference.DARK))
        firstScope.cancel()

        val secondScope = testScope()
        try {
            val reopenedRepository = PreferencesAppSettingsRepository(createStore(file, secondScope))
            assertEquals(
                SettingsReadState.Ready(AppSettings(ThemePreference.DARK)),
                reopenedRepository.settings.first()
            )
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun tutorialCompletionIsPersistedLocallyAndDefaultsToNotCompleted() = runBlocking {
        val file = temporaryFolder.newFilePath("tutorial")
        val firstScope = testScope()
        try {
            val repository = PreferencesAppSettingsRepository(createStore(file, firstScope))
            assertEquals(SettingsReadState.Ready(AppSettings.DEFAULT), repository.settings.first())
            assertEquals(TutorialWriteResult.Saved, repository.markTutorialCompleted())
        } finally {
            firstScope.cancel()
        }
        val secondScope = testScope()
        try {
            val reopened = PreferencesAppSettingsRepository(createStore(file, secondScope))
            assertEquals(SettingsReadState.Ready(AppSettings(tutorialCompleted = true)), reopened.settings.first())
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun readAndWriteErrorsAreReturnedWithoutCrashingOrClaimingSuccess() = runBlocking {
        val repository = PreferencesAppSettingsRepository(FailingDataStore())
        assertTrue(repository.settings.first() is SettingsReadState.Error)
        val write = repository.setTheme(ThemePreference.LIGHT)
        assertTrue(write is ThemeWriteResult.Failed)
        assertFalse(write == ThemeWriteResult.Saved)
        assertTrue(repository.markTutorialCompleted() is TutorialWriteResult.Failed)
    }

    @Test
    fun themePreferenceResolvesSystemLightDarkAndExplicitChoices() {
        assertFalse(ThemePreference.SYSTEM_DEFAULT.resolveDarkTheme(systemIsDark = false))
        assertTrue(ThemePreference.SYSTEM_DEFAULT.resolveDarkTheme(systemIsDark = true))
        assertFalse(ThemePreference.LIGHT.resolveDarkTheme(systemIsDark = true))
        assertTrue(ThemePreference.DARK.resolveDarkTheme(systemIsDark = false))
    }

    private fun TemporaryFolder.newFilePath(name: String): File = File(root, "$name.preferences_pb")

    private fun createStore(file: File, scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun testScope() = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class FailingDataStore : DataStore<Preferences> {
        override val data: Flow<Preferences> = flow { throw IOException("read failed") }

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            throw IOException("write failed")
        }
    }
}
