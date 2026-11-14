package com.charlsemilbarquin.frameandfact.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.data.settings.AppSettings
import com.charlsemilbarquin.frameandfact.data.settings.SettingsReadState
import com.charlsemilbarquin.frameandfact.data.settings.ThemePreference
import com.charlsemilbarquin.frameandfact.feature.auth.AuthFeedback
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun selectedThemeReflectsTheStoredPreference() {
        compose.setContent { SettingsScreenReady(theme = ThemePreference.DARK) }

        compose.onNodeWithText("App Theme", substring = true).assertIsDisplayed().performClick()
        compose.onNodeWithTag("theme-option-DARK").assertIsDisplayed().assertIsSelected()
        compose.onNodeWithText("Light").assertIsDisplayed()
        compose.onNodeWithText("System default").assertIsDisplayed()
    }

    @Test
    fun aboutEntryCallsNavigationHandlerAndAccountCopyExplainsSharedRecords() {
        var opened = false
        compose.setContent {
            SettingsScreen(
                settingsState = SettingsReadState.Ready(AppSettings.DEFAULT),
                themeWriteResult = null,
                isThemeSaving = false,
                clearHistoryState = ClearHistoryUiState.Idle,
                onThemeSelected = {},
                onConfirmClearHistory = {},
                onAbout = { opened = true },
                accountFullName = "QA Player",
                accountUsername = "qa.player"
            )
        }

        compose.onNodeWithText("belong to this account", substring = true)
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("About SnapQuest").performScrollTo().performClick()
        assertEquals(true, opened)
        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNodeWithText("stay visible to other local accounts", substring = true).assertIsDisplayed()
    }

    @Test
    fun logoutConfirmationOnlyCallsLogoutAfterConfirmation() {
        val logoutCalls = AtomicInteger()
        compose.setContent {
            SettingsScreen(
                settingsState = SettingsReadState.Ready(AppSettings.DEFAULT),
                themeWriteResult = null,
                isThemeSaving = false,
                clearHistoryState = ClearHistoryUiState.Idle,
                onThemeSelected = {},
                onConfirmClearHistory = {},
                onLogout = { logoutCalls.incrementAndGet() }
            )
        }

        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNodeWithText("stay visible to other local accounts", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Stay signed in").performClick()
        assertEquals(0, logoutCalls.get())

        compose.onNodeWithText("Log out").performScrollTo().performClick()
        compose.onNodeWithText("Confirm Logout").assertIsDisplayed().performClick()
        assertEquals(1, logoutCalls.get())
    }

    @Test
    fun logoutFailureIsShownInSettings() {
        compose.setContent {
            SettingsScreen(
                settingsState = SettingsReadState.Ready(AppSettings.DEFAULT),
                themeWriteResult = null,
                isThemeSaving = false,
                clearHistoryState = ClearHistoryUiState.Idle,
                onThemeSelected = {},
                onConfirmClearHistory = {},
                authFeedback = AuthFeedback("Could not securely end the session. Please try again.", isError = true)
            )
        }

        compose.onNodeWithText("Could not securely end the session. Please try again.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun cancelingHistoryConfirmationDoesNotInvokeDeletion() {
        val deleteCalls = AtomicInteger()
        compose.setContent {
            SettingsScreen(
                settingsState = SettingsReadState.Ready(AppSettings.DEFAULT),
                themeWriteResult = null,
                isThemeSaving = false,
                clearHistoryState = ClearHistoryUiState.Idle,
                onThemeSelected = {},
                onConfirmClearHistory = { deleteCalls.incrementAndGet() }
            )
        }

        compose.onNodeWithText("Clear History").performScrollTo().performClick()
        compose.onNodeWithText("This permanently removes all completed-game records stored in local history. This action cannot be undone.")
            .assertIsDisplayed()
        compose.onNodeWithText("Keep History").performClick()
        assertEquals(0, deleteCalls.get())
    }

    @Test
    fun confirmedHistoryActionInvokesDeletionOnce() {
        val deleteCalls = AtomicInteger()
        compose.setContent {
            SettingsScreen(
                settingsState = SettingsReadState.Ready(AppSettings.DEFAULT),
                themeWriteResult = null,
                isThemeSaving = false,
                clearHistoryState = ClearHistoryUiState.Idle,
                onThemeSelected = {},
                onConfirmClearHistory = { deleteCalls.incrementAndGet() }
            )
        }

        compose.onNodeWithText("Clear History").performScrollTo().performClick()
        compose.onNodeWithText("Delete History").performClick()
        assertEquals(1, deleteCalls.get())
    }

    @androidx.compose.runtime.Composable
    private fun SettingsScreenReady(theme: ThemePreference) {
        SettingsScreen(
            settingsState = SettingsReadState.Ready(AppSettings(theme)),
            themeWriteResult = null,
            isThemeSaving = false,
            clearHistoryState = ClearHistoryUiState.Idle,
            onThemeSelected = {},
            onConfirmClearHistory = {}
        )
    }
}
