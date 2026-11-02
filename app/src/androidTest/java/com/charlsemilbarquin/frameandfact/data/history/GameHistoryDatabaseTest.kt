package com.charlsemilbarquin.frameandfact.data.history

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantKind
import com.charlsemilbarquin.frameandfact.domain.game.GameId
import com.charlsemilbarquin.frameandfact.domain.game.GameSession
import com.charlsemilbarquin.frameandfact.domain.game.Participant
import com.charlsemilbarquin.frameandfact.domain.game.ParticipantId
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import com.charlsemilbarquin.frameandfact.domain.game.RoundType
import com.charlsemilbarquin.frameandfact.feature.history.incorrectAnswerReviewData
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class GameHistoryDatabaseTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyDatabaseEmitsEmptyHistory() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomGameHistoryRepository(database)
            assertTrue(repository.observeCompletedGames().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun savesNamesScoresTieAndRoundsAndRejectsDuplicateGameIds() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomGameHistoryRepository(database)
            val record = record("tie-game", 100L)
            assertEquals(SaveHistoryResult.Inserted, repository.saveCompletedGame(record))
            assertEquals(SaveHistoryResult.AlreadyPresent, repository.saveCompletedGame(record))

            val saved = repository.observeCompletedGames().first().single()
            assertEquals(1, database.completedGameDao().observeAll().first().size)
            assertEquals(listOf("Ada", "Grace"), saved.participants.map { it.displayName })
            assertEquals(listOf(40, 40), saved.participants.map { it.score })
            assertTrue(saved.isTie)
            assertEquals(2, saved.rounds.size)
            assertEquals("Which?", saved.rounds.first().questionText)
            assertEquals("Alpha", saved.rounds.first().selectedAnswer)
            assertEquals("Alpha", saved.rounds.first().correctAnswer)
            assertEquals("The sample is correct.", saved.rounds.first().explanation)
            assertEquals("science", saved.rounds.first().categoryId)
            assertEquals("EASY", saved.rounds.first().difficulty)
            assertEquals("tie-game", repository.findCompletedGame("tie-game")?.gameId)
        } finally {
            database.close()
        }
    }

    @Test
    fun savesSoloHistoryWithCameraRoundsWithoutJudgeAndKeepsModeDistinguishable() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomGameHistoryRepository(database)
            val solo = CompletedGameHistoryRecord(
                gameId = "solo-history",
                completedAtEpochMillis = 150L,
                rulesVersion = 1,
                selectedCategories = listOf("Science"),
                difficultyLevels = listOf("EASY", "MEDIUM", "HARD"),
                participants = listOf(HistoryParticipantResult("solo", "Solo", ParticipantKind.PLAYER, 80, 6, 2, 1)),
                rounds = listOf(HistoryRoundResult(4, RoundType.FIND_AND_FRAME, "solo", null, RoundOutcome.CAMERA_ACCEPTED))
            )
            assertEquals(SaveHistoryResult.Inserted, repository.saveCompletedGame(solo))

            val saved = repository.findCompletedGame("solo-history")!!
            assertTrue(saved.isSoloGame)
            assertEquals(1, saved.participants.size)
            assertEquals(null, saved.rounds.single().judgeId)
        } finally {
            database.close()
        }
    }

    @Test
    fun historyIsOrderedNewestFirstAndReadingDoesNotMutateSavedData() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomGameHistoryRepository(database)
            val old = record("old", 100L)
            val recent = record("recent", 300L)
            repository.saveCompletedGame(old)
            repository.saveCompletedGame(recent)

            val ordered = repository.observeCompletedGames().first()
            assertEquals(listOf("recent", "old"), ordered.map { it.gameId })
            assertEquals(old, repository.findCompletedGame("old"))
        } finally {
            database.close()
        }
    }

    @Test
    fun incorrectAnswerReviewSurvivesDatabaseReopenWithoutChangingHistory() = runBlocking {
        val name = "history-test-${UUID.randomUUID()}.db"
        val initial = Room.databaseBuilder(context, GameHistoryDatabase::class.java, name).build()
        val base = record("reopen-game", 200L)
        val record = base.copy(
            participants = base.participants.map { participant ->
                if (participant.participantId == "ada") participant.copy(score = 30, correctTriviaRounds = 2)
                else participant
            },
            rounds = listOf(
                base.rounds.first().copy(
                    outcome = RoundOutcome.TRIVIA_INCORRECT,
                    selectedAnswer = "Beta"
                ),
                base.rounds.last()
            )
        )
        try {
            assertEquals(SaveHistoryResult.Inserted, RoomGameHistoryRepository(initial).saveCompletedGame(record))
        } finally {
            initial.close()
        }

        val reopened = Room.databaseBuilder(context, GameHistoryDatabase::class.java, name).build()
        try {
            val loaded = RoomGameHistoryRepository(reopened).observeCompletedGames().first().single()
            assertEquals(record, loaded)
            val review = loaded.incorrectAnswerReviewData()
            assertEquals(1, review.items.size)
            assertEquals("Which?", review.items.single().questionText)
            assertEquals("Beta", review.items.single().selectedAnswer)
            assertEquals("Alpha", review.items.single().correctAnswer)
            assertEquals(30, loaded.participants.first().score)
            assertEquals(2, loaded.participants.first().correctTriviaRounds)
            assertEquals(RoundOutcome.TRIVIA_INCORRECT, loaded.rounds.first().outcome)
            assertEquals(1, RoomGameHistoryRepository(reopened).observeCompletedGames().first().size)
        } finally {
            reopened.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun clearHistoryDeletesOnlyHistoryAndCascadesParticipantAndRoundRows() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomGameHistoryRepository(database)
            repository.saveCompletedGame(record("delete-game", 100L))
            val activeSession = GameSession(
                id = GameId("active-session"),
                participants = listOf(Participant(ParticipantId("active-player"), "Active", ParticipantKind.PLAYER)),
                selectedCategoryIds = emptySet(),
                startingParticipantId = ParticipantId("active-player"),
                rounds = emptyList(),
                selectedQuestions = emptyList()
            )
            val activeSnapshot = activeSession.copy()

            assertEquals(1, repository.clearHistory())
            assertEquals(emptyList<CompletedGameHistoryRecord>(), repository.observeCompletedGames().first())
            assertEquals(0, rowCount(database, "completed_game_participants"))
            assertEquals(0, rowCount(database, "completed_game_rounds"))
            assertEquals(activeSnapshot, activeSession)
        } finally {
            database.close()
        }
    }

    @Test
    fun questionReportsPersistLocallyAndCanBeRemovedIndependently() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val reports = RoomQuestionReportRepository(database)
            reports.addReport("science-1", QuestionIssueType.AMBIGUOUS_QUESTION, "  Two choices seem possible.  ")
            val saved = reports.observeReports().first().single()
            assertEquals("science-1", saved.questionId)
            assertEquals(QuestionIssueType.AMBIGUOUS_QUESTION, saved.issueType)
            assertEquals("Two choices seem possible.", saved.note)
            assertTrue(saved.createdAtEpochMillis > 0)

            reports.deleteReport(saved.reportId)
            assertTrue(reports.observeReports().first().isEmpty())
            assertTrue(RoomGameHistoryRepository(database).observeCompletedGames().first().isEmpty())
        } finally {
            database.close()
        }
    }

    @Test
    fun recentQuestionLookupReadsNewestSavedTriviaSnapshots() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomGameHistoryRepository(database)
            val old = record("old-questions", 100L).copy(
                rounds = record("old-questions", 100L).rounds.map {
                    if (it.type == RoundType.TRIVIA) it.copy(questionId = "older-${it.number}") else it
                }
            )
            val newest = record("new-questions", 200L).copy(
                rounds = record("new-questions", 200L).rounds.map {
                    if (it.type == RoundType.TRIVIA) it.copy(questionId = "newest-${it.number}") else it
                }
            )
            repository.saveCompletedGame(old)
            repository.saveCompletedGame(newest)

            assertEquals(setOf("newest-1"), repository.recentlyEncounteredQuestionIds(1).map { it.value }.toSet())
            assertEquals(setOf("newest-1", "older-1"), repository.recentlyEncounteredQuestionIds(2).map { it.value }.toSet())
        } finally {
            database.close()
        }
    }

    @Test
    fun profileAchievementsAreFullGameOnlyIdempotentAndDeletionPreservesHistorySnapshots() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val profiles = RoomLocalPlayerProfileRepository(database)
            val profile = profiles.createProfile("Alex")
            val history = RoomGameHistoryRepository(database)
            val incomplete = record("incomplete-profile-game", 0L).copy(
                participants = record("incomplete-profile-game", 0L).participants.mapIndexed { index, row ->
                    if (index == 0) row.copy(profileId = profile.profileId) else row
                }
            )
            assertEquals(SaveHistoryResult.Inserted, history.saveCompletedGame(incomplete))
            assertTrue(profiles.observeAchievements(profile.profileId).first().isEmpty())
            val records = (1..5).map { index -> validAchievementRecord("profile-game-$index", index.toLong(), profile.profileId) }
            records.forEach { assertEquals(SaveHistoryResult.Inserted, history.saveCompletedGame(it)) }
            assertEquals(SaveHistoryResult.AlreadyPresent, history.saveCompletedGame(records.last()))
            assertEquals(
                setOf(FullGameAchievement.FIRST_FULL_GAME, FullGameAchievement.FIVE_FULL_GAMES),
                profiles.observeAchievements(profile.profileId).first().map { it.achievement }.toSet()
            )
            assertEquals(5, FullGameStatisticsCalculator.forProfile(profile.profileId, history.observeCompletedGames().first()).completedGames)

            assertTrue(profiles.deleteProfile(profile.profileId))
            val preserved = history.findCompletedGame(records.first().gameId)!!
            assertEquals("Ada", preserved.participants.first().displayName)
            assertEquals(null, preserved.participants.first().profileId)
            assertTrue(profiles.observeAchievements(profile.profileId).first().isEmpty())
            assertEquals(6, history.observeCompletedGames().first().size)
        } finally {
            database.close()
        }
    }

    @Test
    fun tenGameAchievementUnlocksOnceAndIgnoresRepeatedSaveProcessing() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val profiles = RoomLocalPlayerProfileRepository(database)
            val profile = profiles.createProfile("Ten Game Player")
            val history = RoomGameHistoryRepository(database)
            val records = (1..10).map { index -> validAchievementRecord("ten-game-$index", index.toLong(), profile.profileId) }
            records.forEach { assertEquals(SaveHistoryResult.Inserted, history.saveCompletedGame(it)) }
            assertEquals(SaveHistoryResult.AlreadyPresent, history.saveCompletedGame(records.last()))
            val achievements = profiles.observeAchievements(profile.profileId).first()
            assertEquals(1, achievements.count { it.achievement == FullGameAchievement.TEN_FULL_GAMES })
            assertTrue(achievements.any { it.achievement == FullGameAchievement.FIVE_FULL_GAMES })
        } finally {
            database.close()
        }
    }

    @Test
    fun quickPlaySummariesAreSavedSeparatelyFromFullGameHistory() = runBlocking {
        val database = inMemoryDatabase()
        try {
            val repository = RoomShortGameSummaryRepository(database)
            val summary = ShortGameSummary(
                sessionId = "quick-1",
                sessionType = com.charlsemilbarquin.frameandfact.domain.game.ShortSessionType.QUICK_PLAY,
                completedAtEpochMillis = 500L,
                questionCount = 5,
                categoryId = null,
                difficulty = null,
                participants = listOf(ShortGameParticipantResult("p1", "Player", 30, 30, 3, 2))
            )
            assertEquals(SaveShortGameResult.INSERTED, repository.save(summary))
            assertEquals(SaveShortGameResult.ALREADY_PRESENT, repository.save(summary))
            assertEquals(summary, repository.observeSummaries().first().single())
            assertTrue(RoomGameHistoryRepository(database).observeCompletedGames().first().isEmpty())
        } finally {
            database.close()
        }
    }

    private fun rowCount(database: GameHistoryDatabase, table: String): Int =
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private fun inMemoryDatabase(): GameHistoryDatabase = Room.inMemoryDatabaseBuilder(
        context,
        GameHistoryDatabase::class.java
    ).allowMainThreadQueries().build()

    private fun record(id: String, completedAt: Long) = CompletedGameHistoryRecord(
        gameId = id,
        completedAtEpochMillis = completedAt,
        rulesVersion = 1,
        selectedCategories = listOf("Science"),
        difficultyLevels = listOf("EASY", "HARD"),
        participants = listOf(
            HistoryParticipantResult("ada", "Ada", ParticipantKind.PLAYER, 40, 3, 1, 1),
            HistoryParticipantResult("grace", "Grace", ParticipantKind.TEAM, 40, 3, 1, 1)
        ),
        rounds = listOf(
            HistoryRoundResult(
                1, RoundType.TRIVIA, "ada", null, RoundOutcome.TRIVIA_CORRECT,
                questionId = "science-1", questionText = "Which?", selectedAnswer = "Alpha",
                correctAnswer = "Alpha", explanation = "The sample is correct.",
                categoryId = "science", difficulty = "EASY"
            ),
            HistoryRoundResult(4, RoundType.FIND_AND_FRAME, "ada", "grace", RoundOutcome.CAMERA_ACCEPTED)
        )
    )

    private fun validAchievementRecord(id: String, completedAt: Long, profileId: String) =
        CompletedGameHistoryRecord(
            gameId = id,
            completedAtEpochMillis = completedAt,
            rulesVersion = 1,
            selectedCategories = listOf("Science"),
            difficultyLevels = listOf("EASY"),
            participants = listOf(
                HistoryParticipantResult("ada", "Ada", ParticipantKind.PLAYER, 0, 0, 0, 1, profileId),
                HistoryParticipantResult("grace", "Grace", ParticipantKind.PLAYER, 0, 0, 0, 1)
            ),
            rounds = (1..8).map { number ->
                val trivia = number in setOf(1, 2, 3, 5, 6, 7)
                val participantId = if (number % 2 == 1) "ada" else "grace"
                HistoryRoundResult(
                    number = number,
                    type = if (trivia) RoundType.TRIVIA else RoundType.FIND_AND_FRAME,
                    participantId = participantId,
                    judgeId = if (trivia) null else if (participantId == "ada") "grace" else "ada",
                    outcome = if (trivia) RoundOutcome.TRIVIA_SKIPPED else RoundOutcome.CAMERA_SKIPPED,
                    categoryId = if (trivia) "science" else null,
                    difficulty = if (trivia) "EASY" else null
                )
            }
        )
}
