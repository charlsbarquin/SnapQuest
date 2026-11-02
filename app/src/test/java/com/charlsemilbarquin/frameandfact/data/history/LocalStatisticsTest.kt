package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalStatisticsTest {
    @Test
    fun emptyHistoryHasNoFabricatedScoreOrRates() {
        val stats = LocalStatisticsCalculator.calculate(emptyList())
        assertEquals(0, stats.completedGames)
        assertEquals(0, stats.totalCompletedRounds)
        assertEquals(0, stats.totalPoints)
        assertNull(stats.averageGameScore)
        assertNull(stats.highestGameScore)
        assertNull(stats.lowestGameScore)
        assertNull(stats.overallSuccessRate)
        assertNull(stats.triviaAccuracy.percent)
        assertNull(stats.cameraAccuracy.percent)
    }

    @Test
    fun oneSoloGameCalculatesScoringRoundRatesAndKeepsLegacyCategoryDataOptional() {
        val game = soloRecord("solo", "profile-1", listOf(
            RoundOutcome.TRIVIA_CORRECT,
            RoundOutcome.TRIVIA_INCORRECT,
            RoundOutcome.TRIVIA_CORRECT,
            RoundOutcome.CAMERA_ACCEPTED,
            RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.TRIVIA_CORRECT,
            RoundOutcome.TRIVIA_INCORRECT,
            RoundOutcome.CAMERA_SKIPPED
        ), includeCategory = false)
        val stats = LocalStatisticsCalculator.calculate(listOf(game))

        assertEquals(1, stats.completedGames)
        assertEquals(8, stats.totalCompletedRounds)
        assertEquals(40, stats.totalPoints)
        assertEquals(40, stats.highestGameScore)
        assertEquals(40, stats.lowestGameScore)
        assertEquals(40.0, stats.averageGameScore!!, 0.001)
        assertEquals(1, stats.soloGames)
        assertEquals(0, stats.multiplayerGames)
        assertEquals(4, stats.successfulRounds)
        assertEquals(50.0, stats.overallSuccessRate!!, 0.001)
        assertEquals(3, stats.correctTriviaAnswers)
        assertEquals(2, stats.incorrectTriviaAnswers)
        assertEquals(60.0, stats.triviaAccuracy.percent!!, 0.001)
        assertEquals(1, stats.acceptedCameraRounds)
        assertEquals(2, stats.totalCameraRounds)
        assertEquals(50.0, stats.cameraAccuracy.percent!!, 0.001)
        assertTrue(stats.categoryAccuracy.isEmpty())
    }

    @Test
    fun zeroSuccessGameHasZeroRatesAndNoTriviaAccuracyWhenEveryQuestionWasSkipped() {
        val game = soloRecord("all-skipped", "profile-1", listOf(
            RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.CAMERA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.CAMERA_SKIPPED
        ))
        val stats = LocalStatisticsCalculator.calculate(listOf(game))
        assertEquals(0, stats.totalPoints)
        assertEquals(0, stats.successfulRounds)
        assertEquals(0.0, stats.overallSuccessRate!!, 0.001)
        assertNull(stats.triviaAccuracy.percent)
        assertEquals(0.0, stats.cameraAccuracy.percent!!, 0.001)
    }

    @Test
    fun multipleGamesDeduplicateByStableGameIdAndExcludeIncompleteRecords() {
        val first = soloRecord("first", "profile-1", listOf(
            RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.CAMERA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.CAMERA_SKIPPED
        ))
        val second = soloRecord("second", "profile-1", List(8) { index -> when (index) {
            0 -> RoundOutcome.TRIVIA_CORRECT
            3 -> RoundOutcome.CAMERA_ACCEPTED
            7 -> RoundOutcome.CAMERA_SKIPPED
            else -> RoundOutcome.TRIVIA_SKIPPED
        } })
        val incomplete = second.copy(gameId = "incomplete", rounds = second.rounds.dropLast(1))
        val stats = LocalStatisticsCalculator.calculate(listOf(first, second, second, incomplete))

        assertEquals(2, stats.completedGames)
        assertEquals(16, stats.totalCompletedRounds)
        assertEquals(30, stats.totalPoints)
        assertEquals(20, stats.highestGameScore)
        assertEquals(10, stats.lowestGameScore)
        assertEquals(15.0, stats.averageGameScore!!, 0.001)
    }

    @Test
    fun multiplayerWinsTiesAndLossesUseSavedParticipantRanks() {
        val tied = multiplayerRecord("tie", listOf(
            RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.CAMERA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.CAMERA_SKIPPED
        ))
        val decisive = multiplayerRecord("decisive", listOf(
            RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.CAMERA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.CAMERA_SKIPPED
        ))
        val stats = LocalStatisticsCalculator.calculate(listOf(tied, decisive))
        assertEquals(2, stats.multiplayerGames)
        assertEquals(1, stats.multiplayerWins)
        assertEquals(2, stats.multiplayerTies)
        assertEquals(1, stats.multiplayerLosses)
    }

    @Test
    fun categoryStatisticsUseOnlySavedCategoryAndAnsweredOutcomeData() {
        val game = soloRecord("category", "profile-1", listOf(
            RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_INCORRECT, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.CAMERA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.CAMERA_SKIPPED
        ))
        val science = game.rounds.filter { it.type == RoundType.TRIVIA }.mapIndexed { index, round ->
            if (index < 2) round.copy(categoryId = "science") else round.copy(categoryId = null)
        }
        val categorized = game.copy(rounds = game.rounds.filter { it.type == RoundType.FIND_AND_FRAME } + science)
            .copy(rounds = game.rounds.sortedBy { it.number })
        val accuracy = LocalStatisticsCalculator.calculate(listOf(categorized)).categoryAccuracy["science"]!!
        assertEquals(1, accuracy.correct)
        assertEquals(2, accuracy.answered)
        assertEquals(50.0, accuracy.percent!!, 0.001)
    }

    @Test
    fun personalBestsAreProfileLinkedModeAwareAndIgnoreLegacyMissingSnapshots() {
        val lower = soloRecord("lower", "profile-1", listOf(
            RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_INCORRECT, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.CAMERA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.TRIVIA_SKIPPED,
            RoundOutcome.TRIVIA_SKIPPED, RoundOutcome.CAMERA_SKIPPED
        ))
        val high = soloRecord("high", "profile-1", List(8) { index -> when (index) {
            0, 1, 2 -> RoundOutcome.TRIVIA_CORRECT
            3 -> RoundOutcome.CAMERA_ACCEPTED
            else -> if (index == 7) RoundOutcome.CAMERA_SKIPPED else RoundOutcome.TRIVIA_SKIPPED
        } })
        val multiplayer = multiplayerRecord("multi", List(8) { index -> if (index == 0) RoundOutcome.TRIVIA_CORRECT else when (index) {
            3, 7 -> RoundOutcome.CAMERA_SKIPPED
            else -> RoundOutcome.TRIVIA_SKIPPED
        } }).let { row ->
            row.copy(participants = row.participants.mapIndexed { index, participant ->
                if (index == 0) participant.copy(profileId = "profile-1") else participant
            })
        }
        val noSnapshots = high.copy(gameId = "no-snapshot", completedAtEpochMillis = 4,
            rounds = high.rounds.map { it.copy(categoryId = null) })
        val bests = LocalStatisticsCalculator.personalBests("profile-1", listOf(lower, high, multiplayer, noSnapshots))
        assertEquals(40, bests.highestScore)
        assertEquals(40, bests.highestSoloScore)
        assertEquals(10, bests.highestMultiplayerScore)
        assertEquals(4, bests.mostSuccessfulRounds)
        assertEquals(100.0, bests.bestTriviaAccuracy!!.percent!!, 0.001)
        assertTrue(bests.bestCategoryAccuracy.containsKey("science"))
        assertEquals(3, bests.bestCategoryAccuracy["science"]!!.answered)
        assertEquals(3, bests.bestCategoryAccuracy["science"]!!.correct)
        assertTrue(LocalStatisticsCalculator.personalBests("profile-1", listOf(noSnapshots)).bestCategoryAccuracy.isEmpty())
        assertNull(LocalStatisticsCalculator.personalBests("missing-profile", listOf(high)).highestScore)
    }

    @Test
    fun achievementProgressCountsDistinctLinkedGamesAndPreservesPersistedUnlocks() {
        val outcomes = List(8) { index -> when (index) {
            0, 1, 2, 4, 5, 6 -> RoundOutcome.TRIVIA_CORRECT
            3, 7 -> RoundOutcome.CAMERA_ACCEPTED
            else -> RoundOutcome.TRIVIA_SKIPPED
        } }
        val first = soloRecord("first", "profile-1", outcomes)
        val second = soloRecord("second", "profile-1", outcomes, completedAt = 2)
        val unlockedAt = 1234L
        val progress = LocalStatisticsCalculator.achievementProgress(
            "profile-1", listOf(first, first, second),
            listOf(UnlockedAchievement(FullGameAchievement.FIRST_FULL_GAME, unlockedAt))
        ).associateBy { it.achievement }

        assertEquals(2, progress[FullGameAchievement.FIVE_FULL_GAMES]!!.current)
        assertEquals(2, progress[FullGameAchievement.TEN_FULL_GAMES]!!.current)
        assertEquals(80, progress[FullGameAchievement.PERFECT_FULL_GAME]!!.current)
        assertEquals(6, progress[FullGameAchievement.SIX_CORRECT_TRIVIA]!!.current)
        assertEquals(2, progress[FullGameAchievement.TWO_ACCEPTED_CAMERA]!!.current)
        assertTrue(progress[FullGameAchievement.FIRST_FULL_GAME]!!.isUnlocked)
        assertEquals(unlockedAt, progress[FullGameAchievement.FIRST_FULL_GAME]!!.unlockedAtEpochMillis)
        assertEquals(0, LocalStatisticsCalculator.achievementProgress("missing", emptyList(), emptyList())
            .first { it.achievement == FullGameAchievement.FIRST_FULL_GAME }.current)
    }

    private fun soloRecord(
        id: String,
        profileId: String?,
        outcomes: List<RoundOutcome>,
        includeCategory: Boolean = true,
        completedAt: Long = 1
    ): CompletedGameHistoryRecord = record(id, listOf("p1"), outcomes, profileId, includeCategory, completedAt)

    private fun multiplayerRecord(id: String, outcomes: List<RoundOutcome>): CompletedGameHistoryRecord =
        record(id, listOf("p1", "p2"), outcomes, null, true, 1)

    private fun record(
        id: String,
        participantIds: List<String>,
        outcomes: List<RoundOutcome>,
        profileId: String?,
        includeCategory: Boolean,
        completedAt: Long
    ): CompletedGameHistoryRecord {
        require(outcomes.size == 8)
        val rounds = outcomes.mapIndexed { index, outcome ->
            val number = index + 1
            HistoryRoundResult(
                number = number,
                type = if (number in setOf(1, 2, 3, 5, 6, 7)) RoundType.TRIVIA else RoundType.FIND_AND_FRAME,
                participantId = participantIds[index % participantIds.size],
                judgeId = null,
                outcome = outcome,
                questionId = null,
                questionText = null,
                selectedAnswer = null,
                correctAnswer = null,
                explanation = null,
                categoryId = if (includeCategory && number in setOf(1, 2, 3, 5, 6, 7)) "science" else null,
                difficulty = null
            )
        }
        val participants = participantIds.map { participantId ->
            val participantRounds = rounds.filter { it.participantId == participantId }
            val score = participantRounds.sumOf { if (it.outcome == RoundOutcome.TRIVIA_CORRECT || it.outcome == RoundOutcome.CAMERA_ACCEPTED) 10 else 0 }
            HistoryParticipantResult(
                participantId, participantId, ParticipantKind.PLAYER, score,
                participantRounds.count { it.outcome == RoundOutcome.TRIVIA_CORRECT },
                participantRounds.count { it.outcome == RoundOutcome.CAMERA_ACCEPTED },
                rank = 1,
                profileId = profileId.takeIf { participantId == "p1" }
            )
        }.map { row ->
            val rank = 1 + participantsAbove(row.score, participantIds, rounds)
            row.copy(rank = rank)
        }
        return CompletedGameHistoryRecord(id, completedAt, 1, listOf("Science"), listOf("EASY"), participants, rounds)
    }

    private fun participantsAbove(score: Int, ids: List<String>, rounds: List<HistoryRoundResult>): Int = ids.count { id ->
        val participantScore = rounds.filter { it.participantId == id }
            .sumOf { if (it.outcome == RoundOutcome.TRIVIA_CORRECT || it.outcome == RoundOutcome.CAMERA_ACCEPTED) 10 else 0 }
        participantScore > score
    }
}
