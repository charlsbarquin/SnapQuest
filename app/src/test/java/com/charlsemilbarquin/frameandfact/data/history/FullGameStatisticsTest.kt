package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import org.junit.Assert.assertEquals
import org.junit.Test

class FullGameStatisticsTest {
    @Test
    fun countsOnlyExplicitlyLinkedFullGameSnapshotsAndAvailableAnswerDetails() {
        val linked = CompletedGameHistoryRecord(
            gameId = "linked", completedAtEpochMillis = 1, rulesVersion = 1,
            selectedCategories = listOf("Science"), difficultyLevels = listOf("EASY"),
            participants = listOf(HistoryParticipantResult("p1", "Alex", ParticipantKind.PLAYER, 20, 1, 1, 1, "profile-1")),
            rounds = listOf(
                HistoryRoundResult(1, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_CORRECT, categoryId = "science", difficulty = "EASY"),
                HistoryRoundResult(2, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_INCORRECT, categoryId = "science", difficulty = "EASY"),
                HistoryRoundResult(3, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_SKIPPED),
                HistoryRoundResult(4, RoundType.FIND_AND_FRAME, "p1", null, RoundOutcome.CAMERA_ACCEPTED),
                HistoryRoundResult(5, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_SKIPPED),
                HistoryRoundResult(6, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_SKIPPED),
                HistoryRoundResult(7, RoundType.TRIVIA, "p1", null, RoundOutcome.TRIVIA_SKIPPED),
                HistoryRoundResult(8, RoundType.FIND_AND_FRAME, "p1", null, RoundOutcome.CAMERA_SKIPPED)
            )
        )
        val unlinked = linked.copy(
            gameId = "unlinked",
            participants = listOf(HistoryParticipantResult("p2", "Alex", ParticipantKind.PLAYER, 20, 1, 1, 1)),
            rounds = linked.rounds.map { it.copy(participantId = "p2") }
        )

        val stats = FullGameStatisticsCalculator.forProfile("profile-1", listOf(linked, unlinked))
        assertEquals(1, stats.completedGames)
        assertEquals(1, stats.soloGames)
        assertEquals(20, stats.totalPoints)
        assertEquals(20, stats.highestScore)
        assertEquals(20.0, stats.averageScore, 0.001)
        assertEquals(1, stats.correctTriviaAnswers)
        assertEquals(1, stats.incorrectTriviaAnswers)
        assertEquals(1, stats.skippedCameraRounds)
        assertEquals(mapOf("science" to 1), stats.correctByCategory)
    }
}
