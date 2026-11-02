package com.charlsemilbarquin.frameandfact.data.history

import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.data.local.history.QuestionReportEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class QuestionIssueType {
    INCORRECT_ANSWER_KEY, AMBIGUOUS_QUESTION, TYPOGRAPHICAL_ERROR, DUPLICATE_QUESTION,
    INCORRECT_DIFFICULTY, OTHER
}

data class QuestionReport(
    val reportId: String,
    val questionId: String,
    val issueType: QuestionIssueType,
    val note: String?,
    val createdAtEpochMillis: Long
)

interface QuestionReportRepository {
    fun observeReports(): Flow<List<QuestionReport>>
    suspend fun addReport(questionId: String, issueType: QuestionIssueType, note: String?)
    suspend fun deleteReport(reportId: String)
}

class RoomQuestionReportRepository(database: GameHistoryDatabase) : QuestionReportRepository {
    private val dao = database.completedGameDao()

    override fun observeReports(): Flow<List<QuestionReport>> = dao.observeQuestionReports().map { rows ->
        rows.map { it.toReport() }
    }

    override suspend fun addReport(questionId: String, issueType: QuestionIssueType, note: String?) {
        require(questionId.isNotBlank())
        dao.insertQuestionReport(
            QuestionReportEntity(
                reportId = "report-${java.util.UUID.randomUUID()}",
                questionId = questionId,
                issueType = issueType.name,
                note = note?.trim()?.takeIf(String::isNotEmpty),
                createdAtEpochMillis = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteReport(reportId: String) {
        dao.deleteQuestionReport(reportId)
    }

    private fun QuestionReportEntity.toReport() = QuestionReport(
        reportId, questionId, QuestionIssueType.valueOf(issueType), note, createdAtEpochMillis
    )
}
