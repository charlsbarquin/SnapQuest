package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.ShortSessionType
import com.charlsemilbarquin.frameandfact.domain.game.ShortTriviaSession

data class ShortGameParticipantResult(
    val participantId: String,
    val displayName: String,
    val score: Int,
    val maximumScore: Int,
    val correctAnswers: Int,
    val incorrectOrSkippedAnswers: Int
)

data class ShortGameSummary(
    val sessionId: String,
    val sessionType: ShortSessionType,
    val completedAtEpochMillis: Long,
    val questionCount: Int,
    val categoryId: String?,
    val difficulty: String?,
    val participants: List<ShortGameParticipantResult>
)

fun ShortTriviaSession.toCompletedSummary(completedAtEpochMillis: Long): ShortGameSummary? {
    if (!isComplete || answers.size != questions.size) return null
    val chosenCategory = questions.map { it.categoryId.value }.distinct().singleOrNull()
    val chosenDifficulty = questions.map { it.difficulty.name }.distinct().singleOrNull()
    return ShortGameSummary(
        sessionId = id.value,
        sessionType = type,
        completedAtEpochMillis = completedAtEpochMillis,
        questionCount = questions.size,
        categoryId = if (type == ShortSessionType.CATEGORY_FOCUSED) chosenCategory else null,
        difficulty = if (type == ShortSessionType.CATEGORY_FOCUSED) chosenDifficulty else null,
        participants = participants.mapIndexed { participantIndex, participant ->
            val assigned = questions.indices.count { it % participants.size == participantIndex }
            val participantAnswers = answers.filter { it.participantId == participant.id }
            ShortGameParticipantResult(
                participantId = participant.id.value,
                displayName = participant.displayName,
                score = participantAnswers.count { it.correct } * 10,
                maximumScore = assigned * 10,
                correctAnswers = participantAnswers.count { it.correct },
                incorrectOrSkippedAnswers = participantAnswers.count { !it.correct }
            )
        }
    )
}
