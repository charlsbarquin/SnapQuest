package com.charlsemilbarquin.frameandfact.domain.game

sealed interface GameEvent {
    val roundId: RoundId

    data class TriviaAnswer(override val roundId: RoundId, val choiceId: ChoiceId) : GameEvent
    data class SkipTrivia(override val roundId: RoundId) : GameEvent
    data class CameraAccepted(override val roundId: RoundId) : GameEvent
    data class CameraRejected(override val roundId: RoundId) : GameEvent
    data class SkipCameraRound(override val roundId: RoundId) : GameEvent
    data class CancelPhoto(override val roundId: RoundId) : GameEvent
}

sealed interface GameAction {
    data object CancelGameConfirmed : GameAction
    data class RoundEvent(val event: GameEvent) : GameAction
}

enum class GameChange { ROUND_RECORDED, PHOTO_CANCELLED, GAME_CANCELLED }

enum class RejectionReason {
    GAME_NOT_ACTIVE,
    NO_CURRENT_ROUND,
    ROUND_NOT_CURRENT,
    WRONG_EVENT_FOR_ROUND,
    UNKNOWN_QUESTION,
    INVALID_ANSWER_CHOICE
}

sealed interface GameActionResult {
    val session: GameSession

    data class Applied(override val session: GameSession, val change: GameChange) : GameActionResult
    data class Rejected(override val session: GameSession, val reason: RejectionReason) : GameActionResult
}

object GameEngine {
    fun apply(session: GameSession, action: GameAction): GameActionResult {
        if (session.status != GameStatus.ACTIVE) {
            return GameActionResult.Rejected(session, RejectionReason.GAME_NOT_ACTIVE)
        }
        if (action == GameAction.CancelGameConfirmed) {
            return GameActionResult.Applied(session.copy(status = GameStatus.CANCELLED), GameChange.GAME_CANCELLED)
        }

        val event = (action as GameAction.RoundEvent).event
        val currentRound = session.currentRound
            ?: return GameActionResult.Rejected(session, RejectionReason.NO_CURRENT_ROUND)
        if (event.roundId != currentRound.id) {
            return GameActionResult.Rejected(session, RejectionReason.ROUND_NOT_CURRENT)
        }

        if (event is GameEvent.CancelPhoto) {
            return if (currentRound.type == RoundType.FIND_AND_FRAME) {
                GameActionResult.Applied(session, GameChange.PHOTO_CANCELLED)
            } else {
                GameActionResult.Rejected(session, RejectionReason.WRONG_EVENT_FOR_ROUND)
            }
        }

        val outcome = when (event) {
            is GameEvent.TriviaAnswer -> {
                if (currentRound.type != RoundType.TRIVIA) {
                    return GameActionResult.Rejected(session, RejectionReason.WRONG_EVENT_FOR_ROUND)
                }
                val question = session.selectedQuestions.firstOrNull { it.id == currentRound.questionId }
                    ?: return GameActionResult.Rejected(session, RejectionReason.UNKNOWN_QUESTION)
                if (question.choices.none { it.id == event.choiceId }) {
                    return GameActionResult.Rejected(session, RejectionReason.INVALID_ANSWER_CHOICE)
                }
                if (event.choiceId == question.correctChoiceId) RoundOutcome.TRIVIA_CORRECT
                else RoundOutcome.TRIVIA_INCORRECT
            }
            is GameEvent.SkipTrivia -> if (currentRound.type == RoundType.TRIVIA) {
                RoundOutcome.TRIVIA_SKIPPED
            } else return GameActionResult.Rejected(session, RejectionReason.WRONG_EVENT_FOR_ROUND)
            is GameEvent.CameraAccepted -> if (currentRound.type == RoundType.FIND_AND_FRAME) {
                RoundOutcome.CAMERA_ACCEPTED
            } else return GameActionResult.Rejected(session, RejectionReason.WRONG_EVENT_FOR_ROUND)
            is GameEvent.CameraRejected -> if (currentRound.type == RoundType.FIND_AND_FRAME &&
                session.mode == GameMode.MULTIPLAYER
            ) {
                RoundOutcome.CAMERA_REJECTED
            } else return GameActionResult.Rejected(session, RejectionReason.WRONG_EVENT_FOR_ROUND)
            is GameEvent.SkipCameraRound -> if (currentRound.type == RoundType.FIND_AND_FRAME) {
                RoundOutcome.CAMERA_SKIPPED
            } else return GameActionResult.Rejected(session, RejectionReason.WRONG_EVENT_FOR_ROUND)
            is GameEvent.CancelPhoto -> error("Handled above")
        }

        val newOutcomes = session.outcomes + (currentRound.id to outcome)
        val newStatus = if (newOutcomes.size == session.rounds.size) GameStatus.COMPLETED else GameStatus.ACTIVE
        val newSubmittedChoices = if (event is GameEvent.TriviaAnswer) {
            session.submittedChoicesByRound + (currentRound.id to event.choiceId)
        } else session.submittedChoicesByRound
        return GameActionResult.Applied(
            session.copy(outcomes = newOutcomes, status = newStatus, submittedChoicesByRound = newSubmittedChoices),
            GameChange.ROUND_RECORDED
        )
    }
}

