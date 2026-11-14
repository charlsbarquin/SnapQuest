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
    @Query("SELECT * FROM completed_games WHERE ownerAccountId IS :ownerAccountId ORDER BY completedAtEpochMillis DESC, gameId ASC")
    fun observeAll(ownerAccountId: String?): Flow<List<CompletedGameWithDetails>>

    @Transaction
    @Query("SELECT * FROM completed_games WHERE gameId = :gameId AND ownerAccountId IS :ownerAccountId")
    suspend fun getById(gameId: String, ownerAccountId: String?): CompletedGameWithDetails?

    /** Deleting the parent rows cascades to participant and round rows in the same SQL operation. */
    @Transaction
    @Query("DELETE FROM completed_games WHERE ownerAccountId IS :ownerAccountId")
    suspend fun deleteAllCompletedGames(ownerAccountId: String?): Int

    @Insert
    suspend fun insertQuestionReport(report: QuestionReportEntity)

    @Query("SELECT * FROM question_reports WHERE ownerAccountId IS :ownerAccountId ORDER BY createdAtEpochMillis DESC, reportId ASC")
    fun observeQuestionReports(ownerAccountId: String?): Flow<List<QuestionReportEntity>>

    @Query("DELETE FROM question_reports WHERE reportId = :reportId AND ownerAccountId IS :ownerAccountId")
    suspend fun deleteQuestionReport(reportId: String, ownerAccountId: String?): Int

    @Query("""
        SELECT rounds.questionId FROM completed_game_rounds AS rounds
        INNER JOIN completed_games AS games ON games.gameId = rounds.gameId
        WHERE rounds.questionId IS NOT NULL AND games.ownerAccountId IS :ownerAccountId
        ORDER BY games.completedAtEpochMillis DESC, rounds.roundNumber DESC
        LIMIT :limit
    """)
    suspend fun recentlyEncounteredQuestionIds(ownerAccountId: String?, limit: Int): List<String>

    @Insert
    suspend fun insertPlayerProfile(profile: PlayerProfileEntity)

    @Query("UPDATE player_profiles SET displayName = :displayName WHERE profileId = :profileId AND ownerAccountId IS :ownerAccountId")
    suspend fun renamePlayerProfile(profileId: String, displayName: String, ownerAccountId: String?): Int

    @Query("SELECT * FROM player_profiles WHERE ownerAccountId IS :ownerAccountId ORDER BY displayName COLLATE NOCASE, createdAtEpochMillis")
    fun observePlayerProfiles(ownerAccountId: String?): Flow<List<PlayerProfileEntity>>

    @Query("SELECT * FROM player_profiles WHERE profileId = :profileId AND ownerAccountId IS :ownerAccountId")
    suspend fun getPlayerProfile(profileId: String, ownerAccountId: String?): PlayerProfileEntity?

    @Query("DELETE FROM player_profiles WHERE profileId = :profileId AND ownerAccountId IS :ownerAccountId")
    suspend fun deletePlayerProfile(profileId: String, ownerAccountId: String?): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun awardProfileAchievement(achievement: ProfileAchievementEntity): Long

    @Query("SELECT achievements.* FROM profile_achievements AS achievements INNER JOIN player_profiles AS profiles ON profiles.profileId = achievements.profileId WHERE achievements.profileId = :profileId AND profiles.ownerAccountId IS :ownerAccountId ORDER BY achievements.unlockedAtEpochMillis, achievements.achievementId")
    suspend fun getProfileAchievements(profileId: String, ownerAccountId: String?): List<ProfileAchievementEntity>

    @Query("SELECT achievements.* FROM profile_achievements AS achievements INNER JOIN player_profiles AS profiles ON profiles.profileId = achievements.profileId WHERE achievements.profileId = :profileId AND profiles.ownerAccountId IS :ownerAccountId ORDER BY achievements.unlockedAtEpochMillis, achievements.achievementId")
    fun observeProfileAchievements(profileId: String, ownerAccountId: String?): Flow<List<ProfileAchievementEntity>>

    @Query("SELECT COUNT(*) FROM completed_game_participants AS participants INNER JOIN completed_games AS games ON games.gameId = participants.gameId WHERE participants.profileId = :profileId AND games.ownerAccountId IS :ownerAccountId")
    suspend fun completedFullGameCountForProfile(profileId: String, ownerAccountId: String?): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertShortGameSummary(summary: ShortGameSummaryEntity): Long

    @Query("SELECT * FROM short_game_summaries WHERE ownerAccountId IS :ownerAccountId ORDER BY completedAtEpochMillis DESC, sessionId ASC")
    fun observeShortGameSummaries(ownerAccountId: String?): Flow<List<ShortGameSummaryEntity>>

    @Query("DELETE FROM short_game_summaries WHERE sessionId = :sessionId AND ownerAccountId IS :ownerAccountId")
    suspend fun deleteShortGameSummary(sessionId: String, ownerAccountId: String?): Int

    @Query("DELETE FROM short_game_summaries WHERE ownerAccountId IS :ownerAccountId")
    suspend fun deleteAllShortGameSummaries(ownerAccountId: String?): Int

    /** The first real account claims pre-account rows once; no legacy row is discarded. */
    @Transaction
    suspend fun claimUnownedRecords(ownerAccountId: String): Int {
        val games = claimUnownedCompletedGames(ownerAccountId)
        claimUnownedProfiles(ownerAccountId)
        claimUnownedShortSummaries(ownerAccountId)
        claimUnownedQuestionReports(ownerAccountId)
        return games
    }

    @Query("UPDATE completed_games SET ownerAccountId = :ownerAccountId WHERE ownerAccountId IS NULL")
    suspend fun claimUnownedCompletedGames(ownerAccountId: String): Int

    @Query("UPDATE player_profiles SET ownerAccountId = :ownerAccountId WHERE ownerAccountId IS NULL")
    suspend fun claimUnownedProfiles(ownerAccountId: String): Int

    @Query("UPDATE short_game_summaries SET ownerAccountId = :ownerAccountId WHERE ownerAccountId IS NULL")
    suspend fun claimUnownedShortSummaries(ownerAccountId: String): Int

    @Query("UPDATE question_reports SET ownerAccountId = :ownerAccountId WHERE ownerAccountId IS NULL")
    suspend fun claimUnownedQuestionReports(ownerAccountId: String): Int
}
