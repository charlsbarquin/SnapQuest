package com.charlsemilbarquin.frameandfact.feature.auth

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthenticationScreensTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun loginScreenShowsSharedLocalDataNoticeAndSubmitsEnteredCredentials() {
        var submitted: Triple<String, String, Boolean>? = null
        var registrationOpened = false
        compose.setContent {
            FrameFactTheme {
                LoginScreen(AuthFeedback(), { identifier, password, remember -> submitted = Triple(identifier, password, remember) }, { registrationOpened = true })
            }
        }

        compose.onNodeWithText("share this installation's game history", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Remember Me").assertIsDisplayed()
        compose.onNodeWithTag("remember-me-checkbox").assertIsOn()
        compose.onNodeWithTag("login-identifier").performTextInput("player.one")
        compose.onNodeWithTag("login-password").performTextInput("SnapQuest2026")
        compose.onNodeWithTag("login-submit").performClick()
        assertEquals(Triple("player.one", "SnapQuest2026", true), submitted)

        compose.onNodeWithText("Create an account").performClick()
        assertEquals(true, registrationOpened)
    }

    @Test
    fun uncheckedRememberMeIsPassedToLoginHandler() {
        var rememberSubmitted: Boolean? = null
        compose.setContent {
            FrameFactTheme {
                LoginScreen(AuthFeedback(), { _, _, remember -> rememberSubmitted = remember }, {})
            }
        }

        compose.onNodeWithTag("remember-me-checkbox").assertIsOn().performClick().assertIsOff()
        compose.onNodeWithTag("login-submit").performClick()
        assertEquals(false, rememberSubmitted)
    }

    @Test
    fun loginScreenRendersAuthenticationErrors() {
        compose.setContent {
            FrameFactTheme {
                LoginScreen(AuthFeedback("Username/email or password is incorrect.", isError = true), { _, _, _ -> }, {})
            }
        }
        compose.onNodeWithText("Username/email or password is incorrect.").assertIsDisplayed()
    }

    @Test
    fun registrationScreenSubmitsFieldsAndTogglesPasswordVisibility() {
        var submitted: List<String>? = null
        var loginOpened = false
        compose.setContent {
            FrameFactTheme {
                RegistrationScreen(
                    AuthFeedback(),
                    { name, username, email, password, confirmation -> submitted = listOf(name, username, email, password, confirmation) },
                    { loginOpened = true }
                )
            }
        }

        compose.onNodeWithText("belong to the signed-in account", substring = true).assertIsDisplayed()
        compose.onNodeWithTag("register-full-name").performScrollTo().performTextInput("QA Player")
        compose.onNodeWithTag("register-username").performScrollTo().performTextInput("qa.player")
        compose.onNodeWithTag("register-email").performScrollTo().performTextInput("qa@example.invalid")
        compose.onNodeWithTag("register-password").performScrollTo().performTextInput("SnapQuest2026")
        compose.onNodeWithTag("register-confirm-password").performScrollTo().performTextInput("SnapQuest2026")
        compose.onNodeWithText("Show passwords").performScrollTo().performClick()
        compose.onNodeWithText("Hide passwords").assertIsDisplayed()
        compose.onNodeWithTag("register-submit").performScrollTo().performClick()
        assertEquals(listOf("QA Player", "qa.player", "qa@example.invalid", "SnapQuest2026", "SnapQuest2026"), submitted)

        compose.onNodeWithText("Already have an account? Log in").performScrollTo().performClick()
        assertEquals(true, loginOpened)
    }
}


