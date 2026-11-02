package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.data.local.history.PlayerProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

data class LocalPlayerProfile(val profileId: String, val displayName: String, val createdAtEpochMillis: Long)

enum class FullGameAchievement(val id: String, val title: String, val description: String, val target: Int) {
    FIRST_FULL_GAME("first_full_game", "First Full Game", "Complete one saved standard game with this profile linked.", 1),
    FIVE_FULL_GAMES("five_full_games", "Five Full Games", "Complete five saved standard games with this profile linked.", 5),
    TEN_FULL_GAMES("ten_full_games", "Dedicated Player", "Complete ten saved standard games with this profile linked.", 10),
    PERFECT_FULL_GAME("perfect_full_game", "Perfect 80", "Earn all 80 points in one completed game.", 80),
    SIX_CORRECT_TRIVIA("six_correct_trivia", "Trivia Perfect", "Answer all six trivia questions correctly in one game.", 6),
    TWO_ACCEPTED_CAMERA("two_accepted_camera", "Frame Perfect", "Have both camera challenges accepted in one game.", 2)
}

data class UnlockedAchievement(val achievement: FullGameAchievement, val unlockedAtEpochMillis: Long)

interface LocalPlayerProfileRepository {
    fun observeProfiles(): Flow<List<LocalPlayerProfile>>
    fun observeAchievements(profileId: String): Flow<List<UnlockedAchievement>>
    suspend fun createProfile(displayName: String): LocalPlayerProfile
    suspend fun renameProfile(profileId: String, displayName: String): Boolean
    suspend fun deleteProfile(profileId: String): Boolean
}

class RoomLocalPlayerProfileRepository(private val database: GameHistoryDatabase) : LocalPlayerProfileRepository {
    private val dao = database.completedGameDao()

    override fun observeProfiles(): Flow<List<LocalPlayerProfile>> = dao.observePlayerProfiles().map { profiles ->
        profiles.map { LocalPlayerProfile(it.profileId, it.displayName, it.createdAtEpochMillis) }
    }

    override fun observeAchievements(profileId: String): Flow<List<UnlockedAchievement>> =
        dao.observeProfileAchievements(profileId).map { rows -> rows.mapNotNull { row ->
            FullGameAchievement.entries.firstOrNull { it.id == row.achievementId }
                ?.let { UnlockedAchievement(it, row.unlockedAtEpochMillis) }
        } }

    override suspend fun createProfile(displayName: String): LocalPlayerProfile {
        val cleanName = displayName.trim()
        require(cleanName.isNotEmpty())
        val profile = LocalPlayerProfile("profile-${UUID.randomUUID()}", cleanName, System.currentTimeMillis())
        dao.insertPlayerProfile(PlayerProfileEntity(profile.profileId, profile.displayName, profile.createdAtEpochMillis))
        return profile
    }

    override suspend fun renameProfile(profileId: String, displayName: String): Boolean {
        val cleanName = displayName.trim()
        require(cleanName.isNotEmpty())
        return dao.renamePlayerProfile(profileId, cleanName) > 0
    }

    override suspend fun deleteProfile(profileId: String): Boolean = dao.deletePlayerProfile(profileId) > 0
}
