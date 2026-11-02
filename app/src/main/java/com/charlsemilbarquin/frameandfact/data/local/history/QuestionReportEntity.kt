package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "question_reports")
data class QuestionReportEntity(
    @PrimaryKey val reportId: String,
    val questionId: String,
    val issueType: String,
    val note: String?,
    val createdAtEpochMillis: Long
)
