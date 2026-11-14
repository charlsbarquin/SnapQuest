package com.charlsemilbarquin.frameandfact.feature.profile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.data.history.LocalPlayerProfile
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlayerProfilesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun accountUsernameUpdatesWithoutRenamingIndependentLocalProfiles() {
        val accountUsername = mutableStateOf("charls123")
        val multiplayerProfile = LocalPlayerProfile("profile-one", "Tournament Player", 1L)
        compose.setContent {
            FrameFactTheme {
                PlayerProfilesScreen(
                    profiles = listOf(multiplayerProfile),
                    accountUsername = accountUsername.value,
                    onCreate = {},
                    onRename = { _, _ -> error("The account display must not rename saved profiles") },
                    onDelete = { error("Displaying an account username must not delete profiles") },
                    onBack = {}
                )
            }
        }

        compose.onNodeWithTag("account-profile-username").assertIsDisplayed().assertTextEquals("charls123")
        compose.onNodeWithText("Tournament Player · Full Game progress").assertIsDisplayed()

        compose.runOnIdle { accountUsername.value = "another.account" }
        compose.onNodeWithTag("account-profile-username").assertTextEquals("another.account")
        compose.onNodeWithText("Tournament Player · Full Game progress").assertIsDisplayed()
    }

    @Test
    fun unavailableAccountUsernameHasSafeFallback() {
        compose.setContent {
            FrameFactTheme {
                PlayerProfilesScreen(
                    profiles = emptyList(),
                    accountUsername = null,
                    onCreate = {},
                    onRename = { _, _ -> },
                    onDelete = {},
                    onBack = {}
                )
            }
        }

        compose.onNodeWithText("Account username unavailable").assertIsDisplayed()
    }
}
