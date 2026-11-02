package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.ApprovedCategories
import com.charlsemilbarquin.frameandfact.domain.game.CompletedGame
import com.charlsemilbarquin.frameandfact.domain.game.GameFactory
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.GameStatus
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType

/** Immutable snapshot used by both the local database and History UI. */
data class CompletedGameHistoryRecord(
    val gameId: String,
    val completedAtEpochMillis: Long,
    val rulesVersion: Int,
    val selectedCategories: List<String>,
    val difficultyLevels: List<String>,
    val participants: List<HistoryParticipantResult>,
    val rounds: List<HistoryRoundResult>
) {
    /** Existing participant rows distinguish Solo games without a schema migration. */
    val isSoloGame: Boolean get() = participants.size == 1
    val highestScore: Int get() = participants.maxOfOrNull { it.score } ?: 0
    val winners: List<HistoryParticipantResult>
        get() = participants.filter { it.score == highestScore }
    val isTie: Boolean get() = winners.size > 1
}

data class HistoryParticipantResult(
    val participantId: String,
    val displayName: String,
    val kind: ParticipantKind,
    val score: Int,
    val correctTriviaRounds: Int,
    val acceptedCameraRounds: Int,
    val rank: Int,
    val profileId: String? = null
)

data class HistoryRoundResult(
    val number: Int,
    val type: RoundType,
    val participantId: String,
    val judgeId: String?,
    val outcome: RoundOutcome,
    val questionId: String? = null,
    val questionText: String? = null,
    val selectedAnswer: String? = null,
    val correctAnswer: String? = null,
    val explanation: String? = null,
    val categoryId: String? = null,
    val difficulty: String? = null
)

object CompletedGameHistoryMapper {
    const val CURRENT_RULES_VERSION = 1

    /** Returns null for active, canceled, or structurally incomplete games. */
    fun fromSession(session: GameSession, completedAtEpochMillis: Long): CompletedGameHistoryRecord? {
        if (session.status != GameStatus.COMPLETED) return null
        val completed = session.completedGameOrNull() ?: return null
        val participantCountIsValid = when (session.mode) {
            com.charlsemilbarquin.frameandfact.domain.game.GameMode.SOLO -> session.participants.size == 1
            com.charlsemilbarquin.frameandfact.domain.game.GameMode.MULTIPLAYER -> session.participants.size in 2..6
        }
        if (!participantCountIsValid) return null
        if (completed.rounds.size != GameFactory.ROUND_COUNT ||
            completed.outcomes.keys != completed.rounds.map { it.id }.toSet()
        ) return null
        return fromCompletedGame(completed, session.selectedCategoryIds.map { it.value }, completedAtEpochMillis,
            session.selectedQuestions.map { it.difficulty.name }.distinct().sorted())
    }

    private fun fromCompletedGame(
        completed: CompletedGame,
        selectedCategoryIds: List<String>,
        completedAtEpochMillis: Long,
        difficultyLevels: List<String>
    ): CompletedGameHistoryRecord {
        val categoryNames = selectedCategoryIds.map { id ->
            ApprovedCategories.all.firstOrNull { it.id.value == id }?.name ?: id
        }
        val scoreByParticipant = completed.scores.associateBy { it.participantId }
        val orderedParticipants = completed.participants.mapIndexed { index, participant ->
            val score = checkNotNull(scoreByParticipant[participant.id])
            val rank = 1 + completed.scores.count { it.total > score.total }
            HistoryParticipantResult(
                participantId = participant.id.value,
                displayName = participant.displayName,
                kind = participant.kind,
                score = score.total,
                correctTriviaRounds = score.correctTriviaRounds,
                acceptedCameraRounds = score.acceptedCameraRounds,
                rank = rank,
                profileId = participant.profileId
            ) to index
        }.sortedBy { it.second }.map { it.first }

        val participantIds = completed.participants.map { it.id }.toSet()
        val rounds = completed.rounds.sortedBy { it.id.number }.map { round ->
            require(round.participantId in participantIds)
            val outcome = checkNotNull(completed.outcomes[round.id])
            if (round.type == RoundType.FIND_AND_FRAME) {
                if (completed.mode == com.charlsemilbarquin.frameandfact.domain.game.GameMode.SOLO) {
                    require(round.judgeId == null)
                } else {
                    require(round.judgeId != null && round.judgeId in participantIds)
                }
            }
            val question = round.questionId?.let { id -> completed.selectedQuestions.firstOrNull { it.id == id } }
            val selectedChoiceId = completed.submittedChoicesByRound[round.id]
            HistoryRoundResult(
                number = round.id.number,
                type = round.type,
                participantId = round.participantId.value,
                judgeId = round.judgeId?.value,
                outcome = outcome,
                questionId = question?.id?.value,
                questionText = question?.prompt,
                selectedAnswer = selectedChoiceId?.let { id -> question?.choices?.firstOrNull { it.id == id }?.text },
                correctAnswer = question?.choices?.firstOrNull { it.id == question.correctChoiceId }?.text,
                explanation = question?.explanation,
                categoryId = question?.categoryId?.value,
                difficulty = question?.difficulty?.name
            )
        }
        return CompletedGameHistoryRecord(
            gameId = completed.id.value,
            completedAtEpochMillis = completedAtEpochMillis,
            rulesVersion = CURRENT_RULES_VERSION,
            selectedCategories = categoryNames,
            difficultyLevels = difficultyLevels,
            participants = orderedParticipants,
            rounds = rounds
        )
    }
}
