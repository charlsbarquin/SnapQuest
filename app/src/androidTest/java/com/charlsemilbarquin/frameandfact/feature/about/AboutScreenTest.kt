package com.charlsemilbarquin.frameandfact.feature.about

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aboutContentAndDeveloperDisclosureRenderInLightTheme() = assertAboutScreen(darkTheme = false)

    @Test
    fun aboutContentAndDeveloperDisclosureRenderInDarkTheme() = assertAboutScreen(darkTheme = true)

    @Test
    fun aboutContentAndDeveloperDisclosureRenderWithSystemTheme() {
        compose.setContent {
            FrameFactTheme(darkTheme = isSystemInDarkTheme()) { AboutScreen() }
        }
        assertCoreAboutContent()
    }

    private fun assertAboutScreen(darkTheme: Boolean) {
        compose.setContent { FrameFactTheme(darkTheme = darkTheme) { AboutScreen() } }
        assertCoreAboutContent()
    }

    private fun assertCoreAboutContent() {
        compose.onNodeWithText("SnapQuest").assertIsDisplayed()
        compose.onNodeWithText("Full Game rules").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Account-specific player profiles", substring = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Developer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SnapQuest is an offline trivia and camera-challenge game developed as an Android application project.", substring = true).performScrollTo().assertIsDisplayed()
    }
}
