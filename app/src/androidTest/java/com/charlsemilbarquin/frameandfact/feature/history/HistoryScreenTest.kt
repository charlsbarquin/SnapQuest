package com.charlsemilbarquin.frameandfact.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.HistoryParticipantResult
import com.charlsemilbarquin.frameandfact.data.history.HistoryRoundResult
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.feature.history.HistoryGameDetailScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun displaysTheSavedRecordProvidedToTheHistoryScreen() {
        val savedRecord = CompletedGameHistoryRecord(
            gameId = "saved-game",
            completedAtEpochMillis = 1_777_777_000_000L,
            rulesVersion = 1,
            selectedCategories = listOf("Science"),
            difficultyLevels = listOf("EASY"),
            participants = listOf(HistoryParticipantResult("p1", "Repository Player", ParticipantKind.PLAYER, 10, 1, 0, 1)),
            rounds = listOf(HistoryRoundResult(1, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_CORRECT))
        )
        compose.setContent {
            HistoryScreen(
                state = HistoryUiState.Populated(listOf(savedRecord)),
                onOpenGame = {},
                onRetry = {}
            )
        }

        compose.onNodeWithText("Repository Player: 10 points").assertIsDisplayed()
        compose.onNodeWithText("Winner: Repository Player").assertIsDisplayed()
    }

    @Test
    fun gameDetailsShowSavedAnswerSnapshot() {
        val round = HistoryRoundResult(
            1, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT,
            questionId = "science-q1",
            questionText = "Which answer is correct?",
            selectedAnswer = "The selected answer",
            correctAnswer = "The correct answer",
            explanation = "This explains why the correct answer is right.",
            categoryId = "science",
            difficulty = "MEDIUM"
        )
        val savedGame = gameWithRounds(listOf(round))
        compose.setContent { HistoryGameDetailScreen(savedGame, onRetry = {}, isLoading = false) }

        compose.onNodeWithText("Which answer is correct?").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your answer: The selected answer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Correct answer: The correct answer").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("This explains why the correct answer is right.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Science · Medium").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun gameDetailsRemainReadableWhenLegacySnapshotFieldsAreMissing() {
        val legacyRound = HistoryRoundResult(1, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_SKIPPED)
        compose.setContent {
            HistoryGameDetailScreen(gameWithRounds(listOf(legacyRound)), onRetry = {}, isLoading = false)
        }

        compose.onNodeWithText("Question details were not saved for this older game.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun incorrectAnswerReviewShowsSavedAnswersAndNavigatesWithoutWrapping() {
        val game = gameWithRounds(
            listOf(
                HistoryRoundResult(
                    1, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT,
                    questionId = "q1", questionText = "First missed question", selectedAnswer = "First wrong",
                    correctAnswer = "First correct", explanation = "First explanation", categoryId = "science", difficulty = "MEDIUM"
                ),
                HistoryRoundResult(
                    4, RoundType.FIND_AND_FRAME, "p1", null, RoundOutcome.CAMERA_REJECTED,
                    questionText = "Camera challenge that must not be reviewed"
                ),
                HistoryRoundResult(
                    5, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT,
                    questionId = "q5", questionText = "Second missed question", selectedAnswer = "Second wrong",
                    correctAnswer = "Second correct", explanation = "Second explanation", categoryId = "history", difficulty = "HARD"
                )
            )
        )
        val originalRounds = game.rounds.toList()
        var backCalls = 0
        compose.setContent {
            IncorrectAnswerReviewScreen(game, isLoading = false, onBack = { backCalls++ }, onRetry = {})
        }

        compose.onNodeWithText("1 of 2 incorrect answers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("First missed question").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Your answer (incorrect): First wrong").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Correct answer: First correct").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("First explanation").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Science · Medium").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Answer choices were not saved with this game.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Camera challenge that must not be reviewed").assertDoesNotExist()
        compose.onNodeWithText("Previous answer").assertIsNotEnabled()
        compose.onNodeWithText("Next answer").performScrollTo().assertIsEnabled().performClick()

        compose.onNodeWithText("2 of 2 incorrect answers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Second missed question").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Next answer").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Previous answer").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("1 of 2 incorrect answers").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Back to game details").performClick()

        org.junit.Assert.assertEquals(1, backCalls)
        org.junit.Assert.assertEquals(originalRounds, game.rounds)
    }

    @Test
    fun reviewShowsMissingExplanationWithoutInventingIt() {
        val game = gameWithRounds(
            listOf(
                HistoryRoundResult(
                    2, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT,
                    questionText = "Saved question", selectedAnswer = "Saved wrong answer", correctAnswer = "Saved correct answer"
                )
            )
        )
        compose.setContent { IncorrectAnswerReviewScreen(game, false, onBack = {}, onRetry = {}) }

        compose.onNodeWithText("No explanation was saved for this question.").assertIsDisplayed()
        compose.onNodeWithText("Your answer (incorrect): Saved wrong answer").assertIsDisplayed()
        compose.onNodeWithText("Correct answer: Saved correct answer").assertIsDisplayed()
    }

    @Test
    fun legacyIncorrectRecordWithoutSnapshotExplainsReviewIsUnavailable() {
        val game = gameWithRounds(
            listOf(HistoryRoundResult(2, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT))
        )
        compose.setContent { IncorrectAnswerReviewScreen(game, false, onBack = {}, onRetry = {}) }

        compose.onNodeWithText(
            "Review details are unavailable for this older game because its question and answer snapshot is incomplete."
        ).assertIsDisplayed()
    }

    @Test
    fun gameDetailsExposeReviewOnlyForReviewableIncorrectTrivia() {
        val game = gameWithRounds(
            listOf(
                HistoryRoundResult(
                    2, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT,
                    questionText = "Reviewable", selectedAnswer = "Wrong", correctAnswer = "Right"
                ),
                HistoryRoundResult(4, RoundType.FIND_AND_FRAME, "p1", null, RoundOutcome.CAMERA_REJECTED)
            )
        )
        var reviewCalls = 0
        compose.setContent {
            HistoryGameDetailScreen(game, onRetry = {}, isLoading = false, onReviewIncorrectAnswers = { reviewCalls++ })
        }

        compose.onNodeWithText("Review incorrect answers (1)").performScrollTo().assertIsDisplayed().performClick()
        org.junit.Assert.assertEquals(1, reviewCalls)
    }

    @Test
    fun cameraFailureAndCorrectTriviaDoNotCreateReviewEntryPoint() {
        val game = gameWithRounds(
            listOf(
                HistoryRoundResult(1, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_CORRECT,
                    questionText = "Correct", selectedAnswer = "A", correctAnswer = "A"),
                HistoryRoundResult(4, RoundType.FIND_AND_FRAME, "p1", null, RoundOutcome.CAMERA_REJECTED)
            )
        )
        compose.setContent { HistoryGameDetailScreen(game, onRetry = {}, isLoading = false) }

        compose.onNodeWithText("Review incorrect answers (1)").assertDoesNotExist()
        compose.onNodeWithText("Review incorrect answers").assertDoesNotExist()
    }

    @Test
    fun legacyIncorrectRoundWithoutEnoughSnapshotDataHasNoReviewButtonAndExplainsWhy() {
        val game = gameWithRounds(
            listOf(HistoryRoundResult(2, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT))
        )
        compose.setContent { HistoryGameDetailScreen(game, onRetry = {}, isLoading = false) }

        compose.onNodeWithText("Review incorrect answers").assertDoesNotExist()
        compose.onNodeWithText(
            "Review details are unavailable for this older game because its question and answer snapshot is incomplete."
        ).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun multiplayerReviewLabelsTheParticipantWhoseAnswerWasRecorded() {
        val game = gameWithRounds(
            listOf(
                HistoryRoundResult(
                    3, RoundType.TRIVIA, "team-2", null, RoundOutcome.TRIVIA_INCORRECT,
                    questionText = "Which fact?", selectedAnswer = "Team selection", correctAnswer = "Saved answer"
                )
            )
        ).copy(
            participants = listOf(
                HistoryParticipantResult("team-1", "Blue", ParticipantKind.TEAM, 10, 1, 0, 1),
                HistoryParticipantResult("team-2", "Gold", ParticipantKind.TEAM, 0, 0, 0, 2)
            )
        )
        compose.setContent { IncorrectAnswerReviewScreen(game, false, onBack = {}, onRetry = {}) }

        compose.onNodeWithText("Gold's answer (incorrect): Team selection").assertIsDisplayed()
        compose.onNodeWithText("Correct answer: Saved answer").assertIsDisplayed()
    }

    @Test
    fun emptyReviewScreenExplainsThatThereAreNoIncorrectTriviaAnswers() {
        val game = gameWithRounds(
            listOf(
                HistoryRoundResult(1, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_CORRECT),
                HistoryRoundResult(4, RoundType.FIND_AND_FRAME, "p1", null, RoundOutcome.CAMERA_REJECTED)
            )
        )
        compose.setContent { IncorrectAnswerReviewScreen(game, false, onBack = {}, onRetry = {}) }

        compose.onNodeWithText("This game has no incorrect trivia answers to review.").assertIsDisplayed()
    }

    private fun gameWithRounds(rounds: List<HistoryRoundResult>) = CompletedGameHistoryRecord(
        gameId = "snapshot-game",
        completedAtEpochMillis = 1_777_777_000_000L,
        rulesVersion = 1,
        selectedCategories = listOf("Science"),
        difficultyLevels = listOf("MEDIUM"),
        participants = listOf(HistoryParticipantResult("p1", "Repository Player", ParticipantKind.PLAYER, 10, 1, 0, 1)),
        rounds = rounds
    )
}
