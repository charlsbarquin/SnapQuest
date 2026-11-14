package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "question_reports",
    foreignKeys = [ForeignKey(
        entity = AuthAccountEntity::class,
        parentColumns = ["accountId"],
        childColumns = ["ownerAccountId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("ownerAccountId")]
)
data class QuestionReportEntity(
    @PrimaryKey val reportId: String,
    val questionId: String,
    val issueType: String,
    val note: String?,
    val createdAtEpochMillis: Long,
    val ownerAccountId: String? = null
)
