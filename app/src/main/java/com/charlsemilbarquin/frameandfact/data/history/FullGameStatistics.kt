package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType

data class FullGameStatistics(
    val completedGames: Int,
    val soloGames: Int,
    val multiplayerGames: Int,
    val totalPoints: Int,
    val highestScore: Int,
    val averageScore: Double,
    val correctTriviaAnswers: Int,
    val incorrectTriviaAnswers: Int,
    val acceptedCameraRounds: Int,
    val skippedCameraRounds: Int,
    val correctByCategory: Map<String, Int>,
    val correctByDifficulty: Map<String, Int>
)

object FullGameStatisticsCalculator {
    fun forProfile(profileId: String, games: List<CompletedGameHistoryRecord>): FullGameStatistics {
        val linkedGames = LocalStatisticsCalculator.completedGames(games).mapNotNull { game ->
            val participant = game.participants.firstOrNull { it.profileId == profileId } ?: return@mapNotNull null
            game to participant
        }
        val rounds = linkedGames.flatMap { (game, participant) ->
            game.rounds.filter { it.participantId == participant.participantId }.map { game to it }
        }
        val categoryCorrect = rounds.filter { (_, round) -> round.type == RoundType.TRIVIA && round.outcome == RoundOutcome.TRIVIA_CORRECT }
            .mapNotNull { (_, round) -> round.categoryId }.groupingBy { it }.eachCount()
        val difficultyCorrect = rounds.filter { (_, round) -> round.type == RoundType.TRIVIA && round.outcome == RoundOutcome.TRIVIA_CORRECT }
            .mapNotNull { (_, round) -> round.difficulty }.groupingBy { it }.eachCount()
        val scores = linkedGames.map { it.second.score }
        return FullGameStatistics(
            completedGames = linkedGames.size,
            soloGames = linkedGames.count { it.first.isSoloGame },
            multiplayerGames = linkedGames.count { !it.first.isSoloGame },
            totalPoints = scores.sum(),
            highestScore = scores.maxOrNull() ?: 0,
            averageScore = scores.average().takeIf(Double::isFinite) ?: 0.0,
            correctTriviaAnswers = rounds.count { (_, r) -> r.type == RoundType.TRIVIA && r.outcome == RoundOutcome.TRIVIA_CORRECT },
            incorrectTriviaAnswers = rounds.count { (_, r) -> r.type == RoundType.TRIVIA && r.outcome == RoundOutcome.TRIVIA_INCORRECT },
            acceptedCameraRounds = rounds.count { (_, r) -> r.type == RoundType.FIND_AND_FRAME && r.outcome == RoundOutcome.CAMERA_ACCEPTED },
            skippedCameraRounds = rounds.count { (_, r) -> r.type == RoundType.FIND_AND_FRAME && r.outcome == RoundOutcome.CAMERA_SKIPPED },
            correctByCategory = categoryCorrect,
            correctByDifficulty = difficultyCorrect
        )
    }
}
