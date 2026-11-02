package com.charlsemilbarquin.frameandfact.data.history

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.data.local.history.ShortGameSummaryEntity
import com.charlsemilbarquin.frameandfact.domain.game.ShortSessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SaveShortGameResult { INSERTED, ALREADY_PRESENT }

interface ShortGameSummaryRepository {
    fun observeSummaries(): Flow<List<ShortGameSummary>>
    suspend fun save(summary: ShortGameSummary): SaveShortGameResult
    suspend fun clear(): Int
}

class RoomShortGameSummaryRepository(database: GameHistoryDatabase) : ShortGameSummaryRepository {
    private val dao = database.completedGameDao()
    private val gson = Gson()
    private val participantListType = object : TypeToken<List<ShortGameParticipantResult>>() {}.type

    override fun observeSummaries(): Flow<List<ShortGameSummary>> = dao.observeShortGameSummaries().map { rows ->
        rows.map { row ->
            ShortGameSummary(
                sessionId = row.sessionId,
                sessionType = ShortSessionType.valueOf(row.sessionType),
                completedAtEpochMillis = row.completedAtEpochMillis,
                questionCount = row.questionCount,
                categoryId = row.categoryId,
                difficulty = row.difficulty,
                participants = gson.fromJson(row.participantsJson, participantListType)
            )
        }
    }

    override suspend fun save(summary: ShortGameSummary): SaveShortGameResult {
        require(summary.sessionId.isNotBlank() && summary.questionCount > 0 && summary.participants.isNotEmpty())
        val inserted = dao.insertShortGameSummary(
            ShortGameSummaryEntity(
                sessionId = summary.sessionId,
                sessionType = summary.sessionType.name,
                completedAtEpochMillis = summary.completedAtEpochMillis,
                questionCount = summary.questionCount,
                categoryId = summary.categoryId,
                difficulty = summary.difficulty,
                participantsJson = gson.toJson(summary.participants)
            )
        ) != -1L
        return if (inserted) SaveShortGameResult.INSERTED else SaveShortGameResult.ALREADY_PRESENT
    }

    override suspend fun clear(): Int = dao.deleteAllShortGameSummaries()
}
