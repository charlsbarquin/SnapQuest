package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "short_game_summaries",
    foreignKeys = [ForeignKey(
        entity = AuthAccountEntity::class,
        parentColumns = ["accountId"],
        childColumns = ["ownerAccountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("ownerAccountId")]
)
data class ShortGameSummaryEntity(
    @PrimaryKey val sessionId: String,
    val sessionType: String,
    val completedAtEpochMillis: Long,
    val questionCount: Int,
    val categoryId: String?,
    val difficulty: String?,
    val participantsJson: String,
    val ownerAccountId: String? = null
)
