package com.charlsemilbarquin.frameandfact.feature.statistics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.FullGameAchievement
import com.charlsemilbarquin.frameandfact.data.history.HistoryParticipantResult
import com.charlsemilbarquin.frameandfact.data.history.HistoryRoundResult
import com.charlsemilbarquin.frameandfact.data.history.LocalPlayerProfile
import com.charlsemilbarquin.frameandfact.data.history.UnlockedAchievement
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.feature.history.HistoryUiState
import com.charlsemilbarquin.frameandfact.ui.theme.FrameFactTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun emptyStateExplainsWhenStatisticsWillAppear() {
        compose.setContent {
            FrameFactTheme {
                StatisticsScreen(HistoryUiState.Empty, emptyList(), emptyMap(), {}, {})
            }
        }
        compose.onNodeWithText("No Scores Yet").assertIsDisplayed()
        compose.onNodeWithText("Play a game to see your high scores here.").assertIsDisplayed()
        compose.onNodeWithText("Start Playing").assertIsDisplayed()
    }

    @Test
    fun displaysOverallStatsPersonalBestsAndLockedAndUnlockedAchievementProgress() {
        val record = perfectSoloRecord()
        val profile = LocalPlayerProfile("profile-1", "Alex", 1L)
        compose.setContent {
            FrameFactTheme {
                StatisticsScreen(
                    historyState = HistoryUiState.Populated(listOf(record)),
                    profiles = listOf(profile),
                    achievements = mapOf(profile.profileId to listOf(
                        UnlockedAchievement(FullGameAchievement.FIRST_FULL_GAME, 123L)
                    )),
                    onRetry = {},
                    onManageProfiles = {}
                )
            }
        }

        compose.onNodeWithText("Completed games: 1 · rounds: 8").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Total points: 80 · average game: 80.0 / 80").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Highest: 80 / 80 · lowest: 80 / 80").performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("Alex").assertCountEquals(2)
        compose.onNodeWithText("Best overall: 80 / 80").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("First Full Game · Unlocked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Five Full Games · Locked").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("1 / 5").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Standard versus Category-Focused totals are not shown because History does not save an explicit category-mode field.")
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun weeklyLeaderboardUsesOnlyCurrentWeekSavedResults() {
        val currentGame = perfectSoloRecord().copy(completedAtEpochMillis = System.currentTimeMillis())
        compose.setContent {
            FrameFactTheme {
                StatisticsScreen(
                    historyState = HistoryUiState.Populated(listOf(currentGame)),
                    profiles = listOf(LocalPlayerProfile("profile-1", "Alex", 1L)),
                    achievements = emptyMap(),
                    onRetry = {},
                    onManageProfiles = {}
                )
            }
        }

        compose.onNodeWithText("This Week").performClick()
        compose.onAllNodesWithText("Alex").assertCountEquals(2)
        compose.onNodeWithText("80", useUnmergedTree = true).assertIsDisplayed()
    }

    private fun perfectSoloRecord() = CompletedGameHistoryRecord(
        gameId = "stats-perfect-game",
        completedAtEpochMillis = 1L,
        rulesVersion = 1,
        selectedCategories = listOf("Science"),
        difficultyLevels = listOf("EASY"),
        participants = listOf(HistoryParticipantResult(
            participantId = "p1", displayName = "Alex", kind = ParticipantKind.PLAYER,
            score = 80, correctTriviaRounds = 6, acceptedCameraRounds = 2, rank = 1, profileId = "profile-1"
        )),
        rounds = (1..8).map { number ->
            val type = if (number in setOf(1, 2, 3, 5, 6, 7)) RoundType.TRIVIA else RoundType.FIND_AND_FRAME
            HistoryRoundResult(
                number = number, type = type, participantId = "p1", judgeId = null,
                outcome = if (type == RoundType.TRIVIA) RoundOutcome.TRIVIA_CORRECT else RoundOutcome.CAMERA_ACCEPTED,
                categoryId = if (type == RoundType.TRIVIA) "science" else null,
                difficulty = if (type == RoundType.TRIVIA) "EASY" else null
            )
        }
    )
}
