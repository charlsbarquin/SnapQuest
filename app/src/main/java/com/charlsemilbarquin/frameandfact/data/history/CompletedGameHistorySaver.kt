package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface CompletedGameSaveAttempt {
    data object NotCompleted : CompletedGameSaveAttempt
    data class Saved(val record: CompletedGameHistoryRecord) : CompletedGameSaveAttempt
    data class Failed(val record: CompletedGameHistoryRecord) : CompletedGameSaveAttempt
}

/** Keeps one completion snapshot per game for retries and coalesces repeated completion callbacks. */
class CompletedGameHistorySaver(
    private val repository: GameHistoryRepository,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis
) {
    private val mutex = Mutex()
    private val attemptsByGameId = mutableMapOf<String, CompletedGameSaveAttempt>()

    suspend fun saveIfCompleted(session: GameSession): CompletedGameSaveAttempt = mutex.withLock {
        val gameId = session.completedGameOrNull()?.id?.value ?: return CompletedGameSaveAttempt.NotCompleted
        attemptsByGameId[gameId]?.let { return it }
        val record = CompletedGameHistoryMapper.fromSession(session, nowEpochMillis())
            ?: return CompletedGameSaveAttempt.NotCompleted
        saveLocked(record)
    }

    suspend fun retry(record: CompletedGameHistoryRecord): CompletedGameSaveAttempt = mutex.withLock {
        val prior = attemptsByGameId[record.gameId]
        if (prior is CompletedGameSaveAttempt.Saved) return prior
        saveLocked(record)
    }

    private suspend fun saveLocked(record: CompletedGameHistoryRecord): CompletedGameSaveAttempt = try {
        repository.saveCompletedGame(record)
        CompletedGameSaveAttempt.Saved(record).also { attemptsByGameId[record.gameId] = it }
    } catch (error: Exception) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        CompletedGameSaveAttempt.Failed(record).also { attemptsByGameId[record.gameId] = it }
    }
}
