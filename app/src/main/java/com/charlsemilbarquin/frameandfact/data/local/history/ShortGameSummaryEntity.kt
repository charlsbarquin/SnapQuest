package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "short_game_summaries")
data class ShortGameSummaryEntity(
    @PrimaryKey val sessionId: String,
    val sessionType: String,
    val completedAtEpochMillis: Long,
    val questionCount: Int,
    val categoryId: String?,
    val difficulty: String?,
    val participantsJson: String
)
