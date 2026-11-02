package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.data.local.history.toEntities
import com.charlsemilbarquin.frameandfact.data.local.history.toRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import com.charlsemilbarquin.frameandfact.domain.game.QuestionId
import com.charlsemilbarquin.frameandfact.data.local.history.ProfileAchievementEntity

sealed interface SaveHistoryResult {
    data object Inserted : SaveHistoryResult
    data object AlreadyPresent : SaveHistoryResult
}

interface GameHistoryRepository {
    fun observeCompletedGames(): Flow<List<CompletedGameHistoryRecord>>
    suspend fun findCompletedGame(gameId: String): CompletedGameHistoryRecord?
    suspend fun saveCompletedGame(record: CompletedGameHistoryRecord): SaveHistoryResult
    suspend fun clearHistory(): Int
    suspend fun recentlyEncounteredQuestionIds(limit: Int): Set<QuestionId> = emptySet()
}

class RoomGameHistoryRepository(database: GameHistoryDatabase) : GameHistoryRepository {
    private val dao = database.completedGameDao()

    override fun observeCompletedGames(): Flow<List<CompletedGameHistoryRecord>> =
        dao.observeAll().map { games -> games.map { it.toRecord() } }

    override suspend fun findCompletedGame(gameId: String): CompletedGameHistoryRecord? =
        dao.getById(gameId)?.toRecord()

    override suspend fun saveCompletedGame(record: CompletedGameHistoryRecord): SaveHistoryResult {
        val (game, participants, rounds) = record.toEntities()
        val inserted = dao.insertCompletedGame(game, participants, rounds)
        val savedRecord = if (inserted) {
            record
        } else {
            // The stable game ID is the idempotency key; a repeated completion is already saved.
            checkNotNull(dao.getById(record.gameId)).toRecord()
        }
        awardFullGameAchievements(savedRecord)
        return if (inserted) SaveHistoryResult.Inserted else SaveHistoryResult.AlreadyPresent
    }

    override suspend fun clearHistory(): Int = dao.deleteAllCompletedGames()

    override suspend fun recentlyEncounteredQuestionIds(limit: Int): Set<QuestionId> =
        dao.recentlyEncounteredQuestionIds(limit.coerceAtLeast(0)).map(::QuestionId).toSet()

    private suspend fun awardFullGameAchievements(record: CompletedGameHistoryRecord) {
        val validRecord = LocalStatisticsCalculator.completedGames(listOf(record)).singleOrNull() ?: return
        val savedGames = LocalStatisticsCalculator.completedGames(dao.observeAll().first().map { it.toRecord() })
        validRecord.participants.forEach { participant ->
            val profileId = participant.profileId ?: return@forEach
            val completedCount = savedGames.count { game -> game.participants.any { it.profileId == profileId } }
            val eligible = buildSet {
                if (completedCount >= 1) add(FullGameAchievement.FIRST_FULL_GAME)
                if (completedCount >= 5) add(FullGameAchievement.FIVE_FULL_GAMES)
                if (completedCount >= 10) add(FullGameAchievement.TEN_FULL_GAMES)
                if (participant.score == 80) add(FullGameAchievement.PERFECT_FULL_GAME)
                if (participant.correctTriviaRounds == 6) add(FullGameAchievement.SIX_CORRECT_TRIVIA)
                if (participant.acceptedCameraRounds == 2) add(FullGameAchievement.TWO_ACCEPTED_CAMERA)
            }
            eligible.forEach { achievement ->
                dao.awardProfileAchievement(
                    ProfileAchievementEntity(profileId, achievement.id, record.completedAtEpochMillis)
                )
            }
        }
    }
}
