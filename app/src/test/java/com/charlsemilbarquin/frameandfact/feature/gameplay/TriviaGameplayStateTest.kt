package com.charlsemilbarquin.frameandfact.feature.gameplay

import com.charlsemilbarquin.frameandfact.data.content.BundledContentLoader
import com.charlsemilbarquin.frameandfact.data.content.ContentAssetMissingException
import com.charlsemilbarquin.frameandfact.data.content.ContentBank
import com.charlsemilbarquin.frameandfact.domain.game.GameAction
import com.charlsemilbarquin.frameandfact.domain.game.GameActionResult
import com.charlsemilbarquin.frameandfact.domain.game.GameEngine
import com.charlsemilbarquin.frameandfact.domain.game.GameEvent
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameMode
import com.charlsemilbarquin.frameandfact.domain.game.GameStatus
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.feature.setup.GameSetupDraft
import com.charlsemilbarquin.frameandfact.feature.setup.GameSetupSessionCreator
import com.charlsemilbarquin.frameandfact.feature.setup.SessionCreationResult
import com.charlsemilbarquin.frameandfact.feature.setup.SetupContentState
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TriviaGameplayStateTest {
    private val content: ContentBank by lazy {
        fun read(path: String): String {
            val file = Path.of(System.getProperty("user.dir"), "src", "main", "assets", path).toFile()
            if (!file.isFile) throw ContentAssetMissingException(path)
            return Files.readString(file.toPath())
        }
        BundledContentLoader(::read).load()
    }

    private fun newState(): TriviaGameplayState {
        var draft = GameSetupDraft.create()
        draft = draft.updateParticipantName(draft.participants[0].id, "Alex")
        draft = draft.updateParticipantName(draft.participants[1].id, "Sam")
        val result = GameSetupSessionCreator.create(
            draft,
            SetupContentState.Available(content),
            random = Random(17),
            gameId = GameId("trivia-gameplay-test")
        )
        return TriviaGameplayState((result as SessionCreationResult.Created).session)
    }

    private fun newSoloState(): TriviaGameplayState {
        val draft = GameSetupDraft.create(GameMode.SOLO)
        val result = GameSetupSessionCreator.create(
            draft, SetupContentState.Available(content), random = Random(27), gameId = GameId("solo-gameplay-test")
        ) as SessionCreationResult.Created
        return TriviaGameplayState(result.session)
    }

    private fun answer(state: TriviaGameplayState, correct: Boolean): TriviaGameplayState {
        val question = state.displayedQuestion ?: error("Expected trivia question")
        val choice = if (correct) question.correctChoiceId else question.choices.first { it.id != question.correctChoiceId }.id
        return TriviaGameplayReducer.submitAnswer(TriviaGameplayReducer.selectChoice(state, choice))
    }

    private fun recordTriviaThrough(state: TriviaGameplayState, roundNumber: Int): TriviaGameplayState {
        var current = state
        while (current.session.currentRound!!.id.number <= roundNumber) {
            val round = current.session.currentRound!!
            if (round.type == RoundType.FIND_AND_FRAME) {
                error("Camera round must be resolved by its own event before continuing this test path")
            }
            current = answer(current, correct = true)
            current = TriviaGameplayReducer.continueAfterFeedback(current)
        }
        return current
    }

    @Test fun correctTriviaAnswerAwardsTenPointsThroughTheEngine() {
        val state = newState()
        val participantId = state.session.currentRound!!.participantId

        val resolved = answer(state, correct = true)

        assertEquals(RoundOutcome.TRIVIA_CORRECT, resolved.displayedOutcome)
        assertEquals(10, resolved.session.scoreFor(participantId))
        assertEquals(10, resolved.scoreFor(resolved.displayedRound!!))
        assertEquals(1, resolved.session.outcomes.size)
    }

    @Test fun incorrectTriviaAnswerAwardsZeroPoints() {
        val state = newState()
        val participantId = state.session.currentRound!!.participantId

        val resolved = answer(state, correct = false)

        assertEquals(RoundOutcome.TRIVIA_INCORRECT, resolved.displayedOutcome)
        assertEquals(0, resolved.session.scoreFor(participantId))
        assertEquals(0, resolved.scoreFor(resolved.displayedRound!!))
    }

    @Test fun duplicateSubmissionsCannotRecordAnotherOutcomeAndEngineRejectsOldRound() {
        val state = newState()
        val round = state.session.currentRound!!
        val resolved = answer(state, correct = true)
        val duplicate = TriviaGameplayReducer.submitAnswer(resolved)
        val oldRoundEvent = GameEngine.apply(
            resolved.session,
            GameAction.RoundEvent(GameEvent.TriviaAnswer(round.id, state.displayedQuestion!!.correctChoiceId))
        )

        assertSame(resolved, duplicate)
        assertEquals(1, duplicate.session.outcomes.size)
        assertTrue(oldRoundEvent is GameActionResult.Rejected)
    }

    @Test fun displayedQuestionAndScoreComeFromTheCurrentEngineSession() {
        val state = newState()
        val round = state.session.currentRound!!

        assertEquals(round.questionId, state.displayedQuestion?.id)
        assertEquals(state.session.scoreFor(round.participantId), state.scoreFor(round))
        assertEquals(0, state.scoreFor(round))
    }

    @Test fun nextTriviaParticipantFollowsTheFactoryConfiguredOrder() {
        val state = newState()
        val firstParticipant = state.session.currentRound!!.participantId
        val expectedNext = state.session.rounds[1].participantId
        val afterFeedback = TriviaGameplayReducer.continueAfterFeedback(answer(state, correct = true))

        assertEquals(firstParticipant, state.session.rounds[0].participantId)
        assertEquals(expectedNext, afterFeedback.session.currentRound?.participantId)
        assertEquals(RoundId(2), afterFeedback.session.currentRound?.id)
    }

    @Test fun triviaRoundThreeStopsAtCameraRoundFourWithoutSkippingOrScoringIt() {
        val state = recordTriviaThrough(newState(), roundNumber = 3)
        val cameraRound = state.session.currentRound!!
        val attemptedChoice = state.session.selectedQuestions.first().correctChoiceId
        val attemptedAnswer = TriviaGameplayReducer.submitAnswer(
            TriviaGameplayReducer.selectChoice(state, attemptedChoice)
        )
        val attemptedSkip = TriviaGameplayReducer.skipRound(state)

        assertEquals(RoundId(4), cameraRound.id)
        assertEquals(RoundType.FIND_AND_FRAME, cameraRound.type)
        assertEquals(4, state.displayedRound?.id?.number)
        assertEquals(3, state.session.outcomes.size)
        assertSame(state, attemptedAnswer)
        assertSame(state, attemptedSkip)
        assertEquals(3, attemptedAnswer.session.outcomes.size)
    }

    @Test fun triviaRoundSevenLeavesGameActiveAtCameraRoundEight() {
        var state = recordTriviaThrough(newState(), roundNumber = 3)
        val cameraFour = state.session.currentRound!!
        val cameraFourResult = GameEngine.apply(
            state.session,
            GameAction.RoundEvent(GameEvent.CameraAccepted(cameraFour.id))
        ) as GameActionResult.Applied
        state = state.copy(session = cameraFourResult.session)
        state = recordTriviaThrough(state, roundNumber = 7)

        val cameraEight = state.session.currentRound!!
        assertEquals(RoundId(8), cameraEight.id)
        assertEquals(RoundType.FIND_AND_FRAME, cameraEight.type)
        assertEquals(7, state.session.outcomes.size)
        assertEquals(GameStatus.ACTIVE, state.session.status)
        assertEquals(8, state.displayedRound?.id?.number)
    }

    @Test fun skippingTriviaRecordsZeroAndStillUsesEngineForProgression() {
        val state = newState()
        val participant = state.session.currentRound!!.participantId
        val skipped = TriviaGameplayReducer.skipRound(state)

        assertEquals(RoundOutcome.TRIVIA_SKIPPED, skipped.displayedOutcome)
        assertEquals(0, skipped.session.scoreFor(participant))
        assertEquals(1, skipped.session.outcomes.size)
    }

    @Test fun soloCameraAcceptanceUsesTheEngineOnceAndAwardsTenPoints() {
        var state = newSoloState()
        repeat(3) {
            state = TriviaGameplayReducer.skipRound(state)
            state = TriviaGameplayReducer.continueAfterFeedback(state)
        }
        val camera = state.session.currentRound!!
        assertEquals(RoundId(4), camera.id)
        assertEquals(GameMode.SOLO, state.session.mode)
        assertEquals(null, camera.judgeId)

        val accepted = TriviaGameplayReducer.resolveCameraRound(state, accepted = true)
        val duplicate = TriviaGameplayReducer.resolveCameraRound(accepted, accepted = true)
        assertEquals(RoundOutcome.CAMERA_ACCEPTED, accepted.displayedOutcome)
        assertEquals(10, accepted.session.scoreFor(camera.participantId))
        assertSame(accepted, duplicate)
        assertEquals(4, accepted.session.outcomes.size)
        assertEquals(RoundId(5), TriviaGameplayReducer.continueAfterFeedback(accepted).session.currentRound?.id)
    }
}
