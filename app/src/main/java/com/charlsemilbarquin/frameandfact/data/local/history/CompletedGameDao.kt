package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CompletedGameDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGame(game: CompletedGameEntity): Long

    @Insert
    suspend fun insertParticipants(participants: List<CompletedGameParticipantEntity>)

    @Insert
    suspend fun insertRounds(rounds: List<CompletedGameRoundEntity>)

    @Transaction
    suspend fun insertCompletedGame(
        game: CompletedGameEntity,
        participants: List<CompletedGameParticipantEntity>,
        rounds: List<CompletedGameRoundEntity>
    ): Boolean {
        if (insertGame(game) == -1L) return false
        insertParticipants(participants)
        insertRounds(rounds)
        return true
    }

    @Transaction
    @Query("SELECT * FROM completed_games ORDER BY completedAtEpochMillis DESC, gameId ASC")
    fun observeAll(): Flow<List<CompletedGameWithDetails>>

    @Transaction
    @Query("SELECT * FROM completed_games WHERE gameId = :gameId")
    suspend fun getById(gameId: String): CompletedGameWithDetails?

    /** Deleting the parent rows cascades to participant and round rows in the same SQL operation. */
    @Transaction
    @Query("DELETE FROM completed_games")
    suspend fun deleteAllCompletedGames(): Int

    @Insert
    suspend fun insertQuestionReport(report: QuestionReportEntity)

    @Query("SELECT * FROM question_reports ORDER BY createdAtEpochMillis DESC, reportId ASC")
    fun observeQuestionReports(): Flow<List<QuestionReportEntity>>

    @Query("DELETE FROM question_reports WHERE reportId = :reportId")
    suspend fun deleteQuestionReport(reportId: String): Int

    @Query("""
        SELECT rounds.questionId FROM completed_game_rounds AS rounds
        INNER JOIN completed_games AS games ON games.gameId = rounds.gameId
        WHERE rounds.questionId IS NOT NULL
        ORDER BY games.completedAtEpochMillis DESC, rounds.roundNumber DESC
        LIMIT :limit
    """)
    suspend fun recentlyEncounteredQuestionIds(limit: Int): List<String>

    @Insert
    suspend fun insertPlayerProfile(profile: PlayerProfileEntity)

    @Query("UPDATE player_profiles SET displayName = :displayName WHERE profileId = :profileId")
    suspend fun renamePlayerProfile(profileId: String, displayName: String): Int

    @Query("SELECT * FROM player_profiles ORDER BY displayName COLLATE NOCASE, createdAtEpochMillis")
    fun observePlayerProfiles(): Flow<List<PlayerProfileEntity>>

    @Query("SELECT * FROM player_profiles WHERE profileId = :profileId")
    suspend fun getPlayerProfile(profileId: String): PlayerProfileEntity?

    @Query("DELETE FROM player_profiles WHERE profileId = :profileId")
    suspend fun deletePlayerProfile(profileId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun awardProfileAchievement(achievement: ProfileAchievementEntity): Long

    @Query("SELECT * FROM profile_achievements WHERE profileId = :profileId ORDER BY unlockedAtEpochMillis, achievementId")
    suspend fun getProfileAchievements(profileId: String): List<ProfileAchievementEntity>

    @Query("SELECT * FROM profile_achievements WHERE profileId = :profileId ORDER BY unlockedAtEpochMillis, achievementId")
    fun observeProfileAchievements(profileId: String): Flow<List<ProfileAchievementEntity>>

    @Query("SELECT COUNT(*) FROM completed_game_participants WHERE profileId = :profileId")
    suspend fun completedFullGameCountForProfile(profileId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShortGameSummary(summary: ShortGameSummaryEntity): Long

    @Query("SELECT * FROM short_game_summaries ORDER BY completedAtEpochMillis DESC, sessionId ASC")
    fun observeShortGameSummaries(): Flow<List<ShortGameSummaryEntity>>

    @Query("DELETE FROM short_game_summaries WHERE sessionId = :sessionId")
    suspend fun deleteShortGameSummary(sessionId: String): Int

    @Query("DELETE FROM short_game_summaries")
    suspend fun deleteAllShortGameSummaries(): Int
}
