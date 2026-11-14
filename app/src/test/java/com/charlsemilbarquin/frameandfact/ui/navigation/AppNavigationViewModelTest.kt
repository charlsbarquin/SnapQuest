package com.charlsemilbarquin.frameandfact.ui.navigation

import androidx.lifecycle.SavedStateHandle
import com.charlsemilbarquin.frameandfact.domain.game.CategoryId
import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.Difficulty
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameRound
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantId
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.domain.game.TriviaChoice
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestionType
import com.charlsemilbarquin.frameandfact.feature.gameplay.TriviaGameplayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationViewModelTest {
    @Test
    fun startupPhotoCleanupRunsOnlyOnceForAnActivityViewModel() {
        val state = AppNavigationViewModel(SavedStateHandle())

        assertTrue(state.claimStartupPhotoCleanup())
        assertFalse(state.claimStartupPhotoCleanup())
    }

    @Test
    fun activeGameRestoresForTheAccountThatStartedIt() {
        val originalHandle = SavedStateHandle()
        val original = AppNavigationViewModel(originalHandle)
        val expected = sampleGameplayState()
        original.persistGameplayState("account-a", expected)
        val recreated = AppNavigationViewModel(
            SavedStateHandle(
                mapOf(
                    AppNavigationViewModel.SAVED_GAME_JSON_KEY to originalHandle.get<String>(AppNavigationViewModel.SAVED_GAME_JSON_KEY),
                    AppNavigationViewModel.SAVED_GAME_ACCOUNT_KEY to "account-a"
                )
            )
        )

        recreated.restoreGameplayState("account-a")

        assertEquals(expected, recreated.gameplayState.value)
        assertEquals(expected.session, recreated.createdSession.value)
        assertEquals(AppRoute.GAMEPLAY, recreated.backStack.last())
    }

    @Test
    fun activeGameIsNotRestoredIntoAnotherAccount() {
        val originalHandle = SavedStateHandle()
        val original = AppNavigationViewModel(originalHandle)
        original.persistGameplayState("account-a", sampleGameplayState())
        val recreated = AppNavigationViewModel(
            SavedStateHandle(
                mapOf(
                    AppNavigationViewModel.SAVED_GAME_JSON_KEY to originalHandle.get<String>(AppNavigationViewModel.SAVED_GAME_JSON_KEY),
                    AppNavigationViewModel.SAVED_GAME_ACCOUNT_KEY to "account-a"
                )
            )
        )

        recreated.restoreGameplayState("account-b")

        assertNull(recreated.gameplayState.value)
        assertEquals(AppRoute.HOME, recreated.backStack.last())
    }

    private fun sampleGameplayState(): TriviaGameplayState {
        val participant = Participant(ParticipantId("p1"), "Player", ParticipantKind.PLAYER)
        val question = TriviaQuestion(
            id = QuestionId("q1"), categoryId = CategoryId("science"), difficulty = Difficulty.EASY,
            prompt = "Question?", choices = listOf(TriviaChoice(ChoiceId("a"), "Answer")),
            correctChoiceId = ChoiceId("a"), type = TriviaQuestionType.MULTIPLE_CHOICE
        )
        val firstRound = GameRound(RoundId(1), RoundType.TRIVIA, participant.id, questionId = question.id)
        val secondRound = GameRound(RoundId(2), RoundType.TRIVIA, participant.id, questionId = question.id)
        val session = GameSession(
            id = GameId("game1"), participants = listOf(participant), selectedCategoryIds = setOf(CategoryId("science")),
            startingParticipantId = participant.id, rounds = listOf(firstRound, secondRound),
            selectedQuestions = listOf(question), outcomes = mapOf(RoundId(1) to RoundOutcome.TRIVIA_CORRECT),
            mode = GameMode.SOLO, submittedChoicesByRound = mapOf(RoundId(1) to ChoiceId("a"))
        )
        return TriviaGameplayState(session, resolvedRoundId = RoundId(1))
    }
}
