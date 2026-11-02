package com.charlsemilbarquin.frameandfact.ui.navigation

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.MainActivity
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayReducer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityRotationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun activeGameRouteAndRoundSurviveActivityRecreation() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("home-ready").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip").performClick()
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("home-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-ready").assertIsDisplayed()
        compose.onNodeWithText("Start Game").performClick()
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Round 1 of 8").fetchSemanticsNodes().isNotEmpty()
        }

        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Round 1 of 8").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Player · Player").assertIsDisplayed()
        compose.onNodeWithText("Score 0 / 80").assertIsDisplayed()
    }

    @Test
    fun activeCameraRoundAndParticipantSurviveActivityRecreation() {
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("home-ready").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Skip").performClick()
        }
        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithTag("home-ready").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("home-ready").assertIsDisplayed()
        compose.onNodeWithText("Start Game").performClick()

        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Round 1 of 8").fetchSemanticsNodes().isNotEmpty()
        }
        val navigationState = ViewModelProvider(compose.activity)[AppNavigationViewModel::class.java]
        compose.runOnIdle {
            var gameplay = requireNotNull(navigationState.gameplayState.value)
            repeat(3) {
                gameplay = TriviaGameplayReducer.continueAfterFeedback(
                    TriviaGameplayReducer.skipRound(gameplay)
                )
            }
            navigationState.gameplayState.value = gameplay
        }

        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Round 4 of 8 — Find & Frame").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Player: Player").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Current score: 0 points").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Camera").performScrollTo().assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.waitUntil(timeoutMillis = 20_000) {
            compose.onAllNodesWithText("Round 4 of 8 — Find & Frame").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Player: Player").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Current score: 0 points").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Start Camera").performScrollTo().assertIsDisplayed()
    }
}
