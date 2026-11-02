package com.charlsemilbarquin.frameandfact.feature.gameplay

import com.charlsemilbarquin.frameandfact.domain.game.ChoiceId
import com.charlsemilbarquin.frameandfact.domain.game.GameAction
import com.charlsemilbarquin.frameandfact.domain.game.GameActionResult
import com.charlsemilbarquin.frameandfact.domain.game.GameChange
import com.charlsemilbarquin.frameandfact.domain.game.GameEngine
import com.charlsemilbarquin.frameandfact.domain.game.GameEvent
import com.charlsemilbarquin.frameandfact.domain.game.GameRound
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.RoundId
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.domain.game.TriviaQuestion

/** In-memory presentation state around the authoritative domain session. */
data class TriviaGameplayState(
    val session: GameSession,
    val selectedChoiceId: ChoiceId? = null,
    val resolvedRoundId: RoundId? = null
) {
    /** Keeps the just-resolved round visible until the player acknowledges its feedback. */
    val displayedRound: GameRound?
        get() = resolvedRoundId?.let { id -> session.rounds.firstOrNull { it.id == id } }
            ?: session.currentRound

    val displayedQuestion: TriviaQuestion?
        get() = displayedRound?.questionId?.let { id -> session.selectedQuestions.firstOrNull { it.id == id } }

    val displayedOutcome: RoundOutcome?
        get() = resolvedRoundId?.let(session.outcomes::get)

    fun scoreFor(round: GameRound): Int = session.scoreFor(round.participantId)
}

/** Routes player actions through GameEngine; this class contains no scoring or rotation rules. */
object TriviaGameplayReducer {
    fun selectChoice(state: TriviaGameplayState, choiceId: ChoiceId): TriviaGameplayState {
        if (state.resolvedRoundId != null) return state
        val round = state.session.currentRound ?: return state
        if (round.type != RoundType.TRIVIA) return state
        val question = state.session.selectedQuestions.firstOrNull { it.id == round.questionId } ?: return state
        if (question.choices.none { it.id == choiceId }) return state
        return state.copy(selectedChoiceId = choiceId)
    }

    fun submitAnswer(state: TriviaGameplayState): TriviaGameplayState {
        if (state.resolvedRoundId != null) return state
        val round = state.session.currentRound ?: return state
        if (round.type != RoundType.TRIVIA) return state
        val choiceId = state.selectedChoiceId ?: return state
        val result = GameEngine.apply(
            state.session,
            GameAction.RoundEvent(GameEvent.TriviaAnswer(round.id, choiceId))
        )
        return when (result) {
            is GameActionResult.Applied -> if (result.change == GameChange.ROUND_RECORDED) {
                state.copy(session = result.session, resolvedRoundId = round.id)
            } else state
            is GameActionResult.Rejected -> state
        }
    }

    fun skipRound(state: TriviaGameplayState): TriviaGameplayState {
        if (state.resolvedRoundId != null) return state
        val round = state.session.currentRound ?: return state
        if (round.type != RoundType.TRIVIA) return state
        val result = GameEngine.apply(
            state.session,
            GameAction.RoundEvent(GameEvent.SkipTrivia(round.id))
        )
        return when (result) {
            is GameActionResult.Applied -> if (result.change == GameChange.ROUND_RECORDED) {
                state.copy(session = result.session, selectedChoiceId = null, resolvedRoundId = round.id)
            } else state
            is GameActionResult.Rejected -> state
        }
    }

    fun resolveCameraRound(state: TriviaGameplayState, accepted: Boolean): TriviaGameplayState {
        if (state.resolvedRoundId != null) return state
        val round = state.session.currentRound ?: return state
        if (round.type != RoundType.FIND_AND_FRAME) return state
        val event = if (accepted) GameEvent.CameraAccepted(round.id) else GameEvent.CameraRejected(round.id)
        return applyCameraEvent(state, event)
    }

    fun skipCameraRound(state: TriviaGameplayState): TriviaGameplayState {
        val round = state.session.currentRound ?: return state
        if (round.type != RoundType.FIND_AND_FRAME) return state
        return applyCameraEvent(state, GameEvent.SkipCameraRound(round.id))
    }

    /** Cancel Photo validates the current camera boundary but never records an outcome. */
    fun cancelPhoto(state: TriviaGameplayState): TriviaGameplayState {
        if (state.resolvedRoundId != null) return state
        val round = state.session.currentRound ?: return state
        if (round.type != RoundType.FIND_AND_FRAME) return state
        return when (val result = GameEngine.apply(
            state.session,
            GameAction.RoundEvent(GameEvent.CancelPhoto(round.id))
        )) {
            is GameActionResult.Applied -> if (result.change == GameChange.PHOTO_CANCELLED) {
                state.copy(session = result.session)
            } else state
            is GameActionResult.Rejected -> state
        }
    }

    private fun applyCameraEvent(state: TriviaGameplayState, event: GameEvent): TriviaGameplayState {
        val round = state.session.currentRound ?: return state
        if (round.type != RoundType.FIND_AND_FRAME || event.roundId != round.id) return state
        return when (val result = GameEngine.apply(state.session, GameAction.RoundEvent(event))) {
            is GameActionResult.Applied -> if (result.change == GameChange.ROUND_RECORDED) {
                state.copy(session = result.session, selectedChoiceId = null, resolvedRoundId = round.id)
            } else state
            is GameActionResult.Rejected -> state
        }
    }

    fun continueAfterFeedback(state: TriviaGameplayState): TriviaGameplayState =
        if (state.resolvedRoundId == null) state
        else state.copy(selectedChoiceId = null, resolvedRoundId = null)
}
