package com.charlsemilbarquin.frameandfact.feature.history

import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.HistoryRoundResult
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType

/** A review item is built only from facts captured in the completed-game snapshot. */
data class IncorrectAnswerReviewItem(
    val roundNumber: Int,
    val participantName: String,
    val questionText: String,
    val selectedAnswer: String,
    val correctAnswer: String,
    val explanation: String?,
    val category: String?,
    val difficulty: String?
)

data class IncorrectAnswerReviewData(
    val items: List<IncorrectAnswerReviewItem>,
    val unavailableAnswerCount: Int
) {
    val incorrectAnswerCount: Int get() = items.size + unavailableAnswerCount
}

fun CompletedGameHistoryRecord.incorrectAnswerReviewData(): IncorrectAnswerReviewData {
    val incorrectTriviaRounds = rounds.filter {
        it.type == RoundType.TRIVIA && it.outcome == RoundOutcome.TRIVIA_INCORRECT
    }.sortedBy { it.number }
    val participantNames = participants.associate { it.participantId to it.displayName }
    val reviewable = incorrectTriviaRounds.mapNotNull { round ->
        round.toReviewItem(participantNames[round.participantId])
    }
    return IncorrectAnswerReviewData(
        items = reviewable,
        unavailableAnswerCount = incorrectTriviaRounds.size - reviewable.size
    )
}

private fun HistoryRoundResult.toReviewItem(participantName: String?): IncorrectAnswerReviewItem? {
    val prompt = questionText?.takeIf(String::isNotBlank) ?: return null
    val selected = selectedAnswer?.takeIf(String::isNotBlank) ?: return null
    val correct = correctAnswer?.takeIf(String::isNotBlank) ?: return null
    return IncorrectAnswerReviewItem(
        roundNumber = number,
        participantName = participantName?.takeIf(String::isNotBlank) ?: "Participant",
        questionText = prompt,
        selectedAnswer = selected,
        correctAnswer = correct,
        explanation = explanation?.takeIf(String::isNotBlank),
        category = categoryId?.humanizeSnapshotValue(),
        difficulty = difficulty?.humanizeSnapshotValue()
    )
}

private fun String.humanizeSnapshotValue(): String =
    lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

data class IncorrectAnswerReviewState(
    val data: IncorrectAnswerReviewData,
    val currentIndex: Int = 0
) {
    init {
        require(data.items.isEmpty() || currentIndex in data.items.indices)
        require(data.items.isNotEmpty() || currentIndex == 0)
    }

    val currentItem: IncorrectAnswerReviewItem? get() = data.items.getOrNull(currentIndex)
    val positionLabel: String
        get() = if (data.items.isEmpty()) "0 of 0" else "${currentIndex + 1} of ${data.items.size}"

    fun previous(): IncorrectAnswerReviewState =
        if (currentIndex == 0) this else copy(currentIndex = currentIndex - 1)

    fun next(): IncorrectAnswerReviewState =
        if (currentIndex >= data.items.lastIndex) this else copy(currentIndex = currentIndex + 1)
}
