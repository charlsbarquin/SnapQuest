package com.charlsemilbarquin.frameandfact.feature.history

import com.charlsemilbarquin.frameandfact.data.history.CompletedGameHistoryRecord
import com.charlsemilbarquin.frameandfact.data.history.HistoryParticipantResult
import com.charlsemilbarquin.frameandfact.data.history.HistoryRoundResult
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class IncorrectAnswerReviewTest {
    @Test
    fun includesOnlyCompleteIncorrectTriviaSnapshotsInRoundOrder() {
        val game = game(
            rounds = listOf(
                round(8, RoundType.FIND_AND_FRAME, RoundOutcome.CAMERA_REJECTED, questionText = "Camera prompt"),
                round(7, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, questionText = "Later", selectedAnswer = "Wrong", correctAnswer = "Right"),
                round(1, RoundType.TRIVIA, RoundOutcome.TRIVIA_CORRECT, questionText = "Correct", selectedAnswer = "Right", correctAnswer = "Right"),
                round(3, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, questionText = "Incomplete", selectedAnswer = null, correctAnswer = "Right"),
                round(2, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, questionText = "Earlier", selectedAnswer = "No", correctAnswer = "Yes", explanation = "Why yes.")
            )
        )

        val review = game.incorrectAnswerReviewData()

        assertEquals(listOf(2, 7), review.items.map { it.roundNumber })
        assertEquals("Earlier", review.items.first().questionText)
        assertEquals("No", review.items.first().selectedAnswer)
        assertEquals("Yes", review.items.first().correctAnswer)
        assertEquals("Why yes.", review.items.first().explanation)
        assertEquals(1, review.unavailableAnswerCount)
        assertEquals(3, review.incorrectAnswerCount)
    }

    @Test
    fun noIncorrectTriviaProducesEmptyReviewAndMissingExplanationStaysMissing() {
        val game = game(
            rounds = listOf(
                round(1, RoundType.TRIVIA, RoundOutcome.TRIVIA_CORRECT, "Question", "Answer", "Answer"),
                round(4, RoundType.FIND_AND_FRAME, RoundOutcome.CAMERA_REJECTED)
            )
        )
        val review = game.incorrectAnswerReviewData()
        assertEquals(0, review.incorrectAnswerCount)
        assertEquals(emptyList<IncorrectAnswerReviewItem>(), review.items)

        val missingExplanation = game(
            rounds = listOf(round(3, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, "Question", "Wrong", "Correct"))
        ).incorrectAnswerReviewData().items.single()
        assertNull(missingExplanation.explanation)
    }

    @Test
    fun multiplayerReviewNamesTheParticipantWhoseSingleRoundAnswerWasSaved() {
        val game = game(
            participants = listOf(
                HistoryParticipantResult("p1", "Blue Team", ParticipantKind.TEAM, 0, 0, 0, 2),
                HistoryParticipantResult("p2", "Gold Team", ParticipantKind.TEAM, 0, 0, 0, 1)
            ),
            rounds = listOf(round(2, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, "Shared team question", "Team's answer", "Correct answer", participantId = "p2"))
        )

        val item = game.incorrectAnswerReviewData().items.single()
        assertEquals("Gold Team", item.participantName)
        assertEquals("Team's answer", item.selectedAnswer)
    }

    @Test
    fun previousAndNextStopAtTheFirstAndLastItemsWithoutWrapping() {
        val data = game(
            rounds = listOf(
                round(1, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, "First", "A", "B"),
                round(3, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, "Second", "C", "D")
            )
        ).incorrectAnswerReviewData()
        val initial = IncorrectAnswerReviewState(data)

        assertEquals("1 of 2", initial.positionLabel)
        assertSame(initial, initial.previous())
        val second = initial.next()
        assertEquals("2 of 2", second.positionLabel)
        assertEquals("Second", second.currentItem?.questionText)
        assertSame(second, second.next())
        assertEquals("First", second.previous().currentItem?.questionText)
    }

    @Test
    fun reviewingSnapshotDataDoesNotMutateSavedRoundsOrScores() {
        val saved = game(
            rounds = listOf(round(2, RoundType.TRIVIA, RoundOutcome.TRIVIA_INCORRECT, "Question", "Wrong", "Right"))
        )
        val savedRounds = saved.rounds.toList()
        val savedParticipants = saved.participants.toList()

        saved.incorrectAnswerReviewData()
        IncorrectAnswerReviewState(saved.incorrectAnswerReviewData()).next()

        assertEquals(savedRounds, saved.rounds)
        assertEquals(savedParticipants, saved.participants)
    }

    private fun round(
        number: Int,
        type: RoundType,
        outcome: RoundOutcome,
        questionText: String? = null,
        selectedAnswer: String? = null,
        correctAnswer: String? = null,
        explanation: String? = null,
        participantId: String = "p1"
    ) = HistoryRoundResult(
        number = number,
        type = type,
        participantId = participantId,
        judgeId = null,
        outcome = outcome,
        questionId = if (type == RoundType.TRIVIA) "q-$number" else null,
        questionText = questionText,
        selectedAnswer = selectedAnswer,
        correctAnswer = correctAnswer,
        explanation = explanation,
        categoryId = "science",
        difficulty = "MEDIUM"
    )

    private fun game(
        rounds: List<HistoryRoundResult>,
        participants: List<HistoryParticipantResult> = listOf(
            HistoryParticipantResult("p1", "Solo Player", ParticipantKind.PLAYER, 0, 0, 0, 1)
        )
    ) = CompletedGameHistoryRecord(
        gameId = "review-game",
        completedAtEpochMillis = 100L,
        rulesVersion = 1,
        selectedCategories = listOf("Science"),
        difficultyLevels = listOf("MEDIUM"),
        participants = participants,
        rounds = rounds
    )
}
