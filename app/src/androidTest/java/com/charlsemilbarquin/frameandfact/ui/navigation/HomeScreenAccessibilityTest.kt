package com.charlsemilbarquin.frameandfact.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class HomeScreenAccessibilityTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun homeActionsRemainReadableAndClickableAtLargeFontScale() {
        var clickedAction: String? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.6f)) {
                FrameFactTheme(darkTheme = true) {
                    HomeScreen(
                        modifier = Modifier,
                        onMultiplayer = { clickedAction = "multiplayer" },
                        onSoloPlayer = { clickedAction = "solo" },
                        onPractice = { clickedAction = "practice" },
                        onQuickPlay = { clickedAction = "start" },
                        onQuickTrivia = { clickedAction = "quick-trivia" },
                        onCategoryPractice = { clickedAction = "category-practice" },
                        onHistory = { clickedAction = "history" },
                        onStatistics = { clickedAction = "statistics" },
                        onShortHistory = { clickedAction = "short-history" },
                        onHelp = { clickedAction = "help" },
                        onSettings = { clickedAction = "settings" },
                        onTutorial = { clickedAction = "tutorial" }
                    )
                }
            }
        }

        compose.onNodeWithText("Solo Player").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals("solo", clickedAction)
        compose.onNodeWithText("Category Practice").performScrollTo().assertIsDisplayed().assertHasClickAction().performClick()
        assertEquals("category-practice", clickedAction)
        compose.onNodeWithText("Rules & Help").performScrollTo().assertIsDisplayed().assertHasClickAction()
    }
}
