package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType

data class AccuracySummary(val correct: Int, val answered: Int) {
    val percent: Double? get() = (correct.toDouble() / answered).takeIf { answered > 0 && it.isFinite() }?.times(100.0)
}

data class OverallGameStatistics(
    val completedGames: Int,
    val totalCompletedRounds: Int,
    val totalPoints: Int,
    val averageGameScore: Double?,
    val highestGameScore: Int?,
    val lowestGameScore: Int?,
    val soloGames: Int,
    val multiplayerGames: Int,
    val multiplayerWins: Int,
    val multiplayerTies: Int,
    val multiplayerLosses: Int,
    val successfulRounds: Int,
    val overallSuccessRate: Double?,
    val correctTriviaAnswers: Int,
    val incorrectTriviaAnswers: Int,
    val triviaAccuracy: AccuracySummary,
    val acceptedCameraRounds: Int,
    val totalCameraRounds: Int,
    val cameraAccuracy: AccuracySummary,
    val categoryAccuracy: Map<String, AccuracySummary>
)

data class CategoryPersonalBest(val categoryId: String, val correct: Int, val answered: Int) {
    val accuracy: Double? get() = AccuracySummary(correct, answered).percent
}

data class ProfilePersonalBests(
    val highestScore: Int?,
    val highestSoloScore: Int?,
    val highestMultiplayerScore: Int?,
    val mostSuccessfulRounds: Int?,
    val bestTriviaAccuracy: AccuracySummary?,
    val bestCategoryAccuracy: Map<String, CategoryPersonalBest>
)

data class AchievementProgress(
    val achievement: FullGameAchievement,
    val current: Int,
    val unlockedAtEpochMillis: Long?
) {
    val isUnlocked: Boolean get() = unlockedAtEpochMillis != null
    val target: Int get() = achievement.target
    val displayProgress: Int get() = current.coerceIn(0, target)
}

/**
 * Statistics use only distinct, structurally complete eight-round History records. A History row
 * is the persistence boundary for a completed game; cancelled and unfinished sessions are never
 * inserted. Legacy nullable question snapshots still contribute to outcomes, but not category
 * statistics when their category ID is absent.
 */
object LocalStatisticsCalculator {
    fun completedGames(games: List<CompletedGameHistoryRecord>): List<CompletedGameHistoryRecord> =
        games.groupBy { it.gameId }
            .values
            .mapNotNull { copies -> copies.maxByOrNull { it.completedAtEpochMillis } }
            .filter(::isValidCompletedGame)

    fun calculate(games: List<CompletedGameHistoryRecord>): OverallGameStatistics {
        val valid = completedGames(games)
        val rounds = valid.flatMap { it.rounds }
        val successful = rounds.count { it.outcome == RoundOutcome.TRIVIA_CORRECT || it.outcome == RoundOutcome.CAMERA_ACCEPTED }
        val answeredTrivia = rounds.filter {
            it.type == RoundType.TRIVIA && it.outcome in setOf(RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_INCORRECT)
        }
        val cameraRounds = rounds.filter { it.type == RoundType.FIND_AND_FRAME }
        val categoryAccuracy = rounds.asSequence()
            .filter { it.type == RoundType.TRIVIA && !it.categoryId.isNullOrBlank() &&
                it.outcome in setOf(RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_INCORRECT) }
            .groupBy { requireNotNull(it.categoryId) }
            .mapValues { (_, rows) -> AccuracySummary(rows.count { it.outcome == RoundOutcome.TRIVIA_CORRECT }, rows.size) }
        val gameScores = valid.map { game -> game.participants.sumOf { it.score } }
        val multiplayerResults = valid.filterNot { it.isSoloGame }
        val multiplayerTies = multiplayerResults.sumOf { game ->
            val topRank = game.participants.filter { it.rank == 1 }.size
            if (topRank > 1) topRank else 0
        }
        val multiplayerWins = multiplayerResults.sumOf { game ->
            if (game.participants.count { it.rank == 1 } == 1) 1 else 0
        }
        val multiplayerLosses = multiplayerResults.sumOf { game -> game.participants.count { it.rank > 1 } }
        return OverallGameStatistics(
            completedGames = valid.size,
            totalCompletedRounds = rounds.size,
            totalPoints = gameScores.sum(),
            averageGameScore = gameScores.average().takeIf { it.isFinite() },
            highestGameScore = gameScores.maxOrNull(),
            lowestGameScore = gameScores.minOrNull(),
            soloGames = valid.count { it.isSoloGame },
            multiplayerGames = multiplayerResults.size,
            multiplayerWins = multiplayerWins,
            multiplayerTies = multiplayerTies,
            multiplayerLosses = multiplayerLosses,
            successfulRounds = successful,
            overallSuccessRate = (successful.toDouble() / rounds.size).takeIf { rounds.isNotEmpty() && it.isFinite() }?.times(100.0),
            correctTriviaAnswers = answeredTrivia.count { it.outcome == RoundOutcome.TRIVIA_CORRECT },
            incorrectTriviaAnswers = answeredTrivia.count { it.outcome == RoundOutcome.TRIVIA_INCORRECT },
            triviaAccuracy = AccuracySummary(
                correct = answeredTrivia.count { it.outcome == RoundOutcome.TRIVIA_CORRECT },
                answered = answeredTrivia.size
            ),
            acceptedCameraRounds = cameraRounds.count { it.outcome == RoundOutcome.CAMERA_ACCEPTED },
            totalCameraRounds = cameraRounds.size,
            cameraAccuracy = AccuracySummary(
                correct = cameraRounds.count { it.outcome == RoundOutcome.CAMERA_ACCEPTED },
                answered = cameraRounds.size
            ),
            categoryAccuracy = categoryAccuracy
        )
    }

    fun personalBests(profileId: String, games: List<CompletedGameHistoryRecord>): ProfilePersonalBests {
        val linked = completedGames(games).mapNotNull { game ->
            game.participants.firstOrNull { it.profileId == profileId }?.let { game to it }
        }
        val roundGroups = linked.map { (game, participant) ->
            game to participant to game.rounds.filter { it.participantId == participant.participantId }
        }
        val perGameAccuracy = roundGroups.mapNotNull { (gameAndParticipant, rounds) ->
            val (game, participant) = gameAndParticipant
            val answers = rounds.filter { it.type == RoundType.TRIVIA &&
                it.outcome in setOf(RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_INCORRECT) }
            if (answers.isEmpty()) null else game to AccuracySummary(
                answers.count { it.outcome == RoundOutcome.TRIVIA_CORRECT }, answers.size
            )
        }
        val categoryCandidates = roundGroups.flatMap { (_, rounds) ->
            rounds.filter { it.type == RoundType.TRIVIA && !it.categoryId.isNullOrBlank() &&
                it.outcome in setOf(RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_INCORRECT) }
                .groupBy { requireNotNull(it.categoryId) }
                .map { (categoryId, rows) -> categoryId to CategoryPersonalBest(
                    categoryId, rows.count { it.outcome == RoundOutcome.TRIVIA_CORRECT }, rows.size
                ) }
        }
        val bestCategories = categoryCandidates.groupBy { it.first }.mapValues { (_, candidates) ->
            candidates.map { it.second }.maxWithOrNull(
                compareBy<CategoryPersonalBest> { it.accuracy ?: -1.0 }.thenBy { it.answered }
            )!!
        }
        return ProfilePersonalBests(
            highestScore = linked.maxOfOrNull { it.second.score },
            highestSoloScore = linked.filter { it.first.isSoloGame }.maxOfOrNull { it.second.score },
            highestMultiplayerScore = linked.filterNot { it.first.isSoloGame }.maxOfOrNull { it.second.score },
            mostSuccessfulRounds = roundGroups.maxOfOrNull { (_, rounds) -> rounds.count {
                it.outcome == RoundOutcome.TRIVIA_CORRECT || it.outcome == RoundOutcome.CAMERA_ACCEPTED
            } },
            bestTriviaAccuracy = perGameAccuracy.maxWithOrNull(
                compareBy<Pair<CompletedGameHistoryRecord, AccuracySummary>> { it.second.percent ?: -1.0 }
                    .thenBy { it.second.answered }
            )?.second,
            bestCategoryAccuracy = bestCategories
        )
    }

    fun achievementProgress(
        profileId: String,
        games: List<CompletedGameHistoryRecord>,
        unlocked: List<UnlockedAchievement>
    ): List<AchievementProgress> {
        val linked = completedGames(games).mapNotNull { game ->
            game.participants.firstOrNull { it.profileId == profileId }?.let { game to it }
        }
        val linkedRounds = linked.map { (game, participant) ->
            Triple(game, participant, game.rounds.filter { it.participantId == participant.participantId })
        }
        val unlockDates = unlocked.associate { it.achievement to it.unlockedAtEpochMillis }
        return FullGameAchievement.entries.map { achievement ->
            val current = when (achievement) {
                FullGameAchievement.FIRST_FULL_GAME,
                FullGameAchievement.FIVE_FULL_GAMES,
                FullGameAchievement.TEN_FULL_GAMES -> linked.size
                FullGameAchievement.PERFECT_FULL_GAME -> linked.maxOfOrNull { it.second.score } ?: 0
                FullGameAchievement.SIX_CORRECT_TRIVIA -> linkedRounds.maxOfOrNull { (_, _, rounds) ->
                    rounds.count { it.type == RoundType.TRIVIA && it.outcome == RoundOutcome.TRIVIA_CORRECT }
                } ?: 0
                FullGameAchievement.TWO_ACCEPTED_CAMERA -> linkedRounds.maxOfOrNull { (_, _, rounds) ->
                    rounds.count { it.type == RoundType.FIND_AND_FRAME && it.outcome == RoundOutcome.CAMERA_ACCEPTED }
                } ?: 0
            }
            AchievementProgress(achievement, current, unlockDates[achievement])
        }
    }

    private fun isValidCompletedGame(game: CompletedGameHistoryRecord): Boolean {
        val participants = game.participants
        if (game.gameId.isBlank() || participants.isEmpty() || participants.size !in 1..6) return false
        if (participants.map { it.participantId }.distinct().size != participants.size ||
            participants.any { it.participantId.isBlank() || it.score !in 0..80 || it.rank !in 1..participants.size }
        ) return false
        if (participants.size == 1 && participants.single().kind.name == "TEAM") return false
        if (participants.size in 2..6 && participants.map { it.kind }.distinct().size != 1) return false
        if (game.rounds.size != 8 || game.rounds.map { it.number }.toSet() != (1..8).toSet()) return false
        val participantIds = participants.map { it.participantId }.toSet()
        if (game.rounds.any { round ->
                val expectedType = if (round.number in setOf(1, 2, 3, 5, 6, 7)) RoundType.TRIVIA else RoundType.FIND_AND_FRAME
                round.type != expectedType || round.participantId !in participantIds ||
                    (round.type == RoundType.TRIVIA && round.outcome !in setOf(
                        RoundOutcome.TRIVIA_CORRECT, RoundOutcome.TRIVIA_INCORRECT, RoundOutcome.TRIVIA_SKIPPED
                    )) || (round.type == RoundType.FIND_AND_FRAME && round.outcome !in setOf(
                        RoundOutcome.CAMERA_ACCEPTED, RoundOutcome.CAMERA_REJECTED, RoundOutcome.CAMERA_SKIPPED
                    ))
            }) return false
        if (participants.any { participant ->
                val points = game.rounds.filter { it.participantId == participant.participantId }
                    .sumOf { if (it.outcome == RoundOutcome.TRIVIA_CORRECT || it.outcome == RoundOutcome.CAMERA_ACCEPTED) 10 else 0 }
                participant.score != points
            }) return false
        return participants.all { participant ->
            participant.rank == 1 + participants.count { it.score > participant.score }
        }
    }
}
