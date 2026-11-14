package com.charlsemilbarquin.frameandfact.ui.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import com.charlsemilbarquin.frameandfact.data.auth.AuthOperation
import com.charlsemilbarquin.frameandfact.data.auth.EncryptedSessionStore
import com.charlsemilbarquin.frameandfact.data.auth.LocalAuthRepository
import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.data.settings.AppSettings
import com.charlsemilbarquin.frameandfact.data.settings.AppSettingsRepository
import com.charlsemilbarquin.frameandfact.data.settings.SettingsReadState
import com.charlsemilbarquin.frameandfact.data.settings.ThemePreference
import com.charlsemilbarquin.frameandfact.data.settings.ThemeWriteResult
import com.charlsemilbarquin.frameandfact.data.settings.TutorialWriteResult
import com.charlsemilbarquin.frameandfact.feature.auth.AuthViewModel
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File

@RunWith(AndroidJUnit4::class)
class AboutLogoutNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: GameHistoryDatabase
    private lateinit var sessionStore: EncryptedSessionStore
    private lateinit var repository: LocalAuthRepository
    private lateinit var authViewModel: AuthViewModel
    private lateinit var username: String
    private lateinit var testCameraCacheDirectory: File

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, GameHistoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val suffix = UUID.randomUUID().toString().replace("-", "")
        testCameraCacheDirectory = File(context.cacheDir, "about_logout_test_$suffix").apply { mkdirs() }
        sessionStore = EncryptedSessionStore(context, "snapquest_navigation_test", "snapquest_navigation_test_key")
        repository = LocalAuthRepository(database.authAccountDao(), sessionStore)
        username = "nav${suffix.take(12)}"
        assertTrue(repository.register("Navigation QA", username, "", "SnapQuest2026", "SnapQuest2026") is AuthOperation.Success)
        assertTrue(repository.login(username, "SnapQuest2026") is AuthOperation.Success)
        authViewModel = AuthViewModel(compose.activity.application, repository)
    }

    @After
    fun tearDown() {
        sessionStore.clear()
        database.close()
    }

    @Test
    fun settingsAboutBackAndLogoutUseTheRealAuthenticatedNavigation() = runBlocking {
        val navigationViewModel = AppNavigationViewModel(SavedStateHandle())
        compose.setContent {
            FrameFactTheme {
                AppNavigation(
                    settingsRepository = TestSettingsRepository,
                    settingsReadState = SettingsReadState.Ready(AppSettings(tutorialCompleted = true)),
                    navigationViewModel = navigationViewModel,
                    authViewModel = authViewModel,
                    cameraCacheDirectory = testCameraCacheDirectory
                )
            }
        }

        compose.waitUntil(20_000) { compose.onNodeWithText("Start Game").fetchSemanticsNodeOrNull() != null }
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Player profiles").performScrollTo().performClick()
        compose.onNodeWithTag("account-profile-username").assertIsDisplayed()
        compose.onNodeWithText(username).assertIsDisplayed()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("About SnapQuest").performScrollTo().performClick()
        compose.onNodeWithText("Developer", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Navigate back").performClick()
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNodeWithText("Confirm Logout").performClick()

        compose.waitUntil(20_000) { compose.onNodeWithText("Welcome back").fetchSemanticsNodeOrNull() != null }
        assertEquals(listOf(AppRoute.HOME), navigationViewModel.backStack.toList())
        assertNull(repository.restoreSession())
        assertEquals(username, database.authAccountDao().findByUsername(username)?.username)

        val restartedRepository = LocalAuthRepository(database.authAccountDao(), sessionStore)
        assertNull(restartedRepository.restoreSession())
        val relogin = restartedRepository.login(username, "SnapQuest2026") as AuthOperation.Success
        assertEquals(username, relogin.user.username)
        assertEquals(relogin.user, restartedRepository.restoreSession())
    }
}

private object TestSettingsRepository : AppSettingsRepository {
    override val settings: Flow<SettingsReadState> = flowOf(SettingsReadState.Ready(AppSettings(tutorialCompleted = true)))
    override suspend fun setTheme(theme: ThemePreference) = ThemeWriteResult.Saved
    override suspend fun markTutorialCompleted() = TutorialWriteResult.Saved
}

private fun androidx.compose.ui.test.SemanticsNodeInteraction.fetchSemanticsNodeOrNull() =
    runCatching { fetchSemanticsNode() }.getOrNull()
