package com.charlsemilbarquin.frameandfact.data.history

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.charlsemilbarquin.frameandfact.data.local.history.GameHistoryDatabase
import com.charlsemilbarquin.frameandfact.data.local.history.MIGRATION_1_2
import com.charlsemilbarquin.frameandfact.data.local.history.MIGRATION_2_3
import com.charlsemilbarquin.frameandfact.data.local.history.MIGRATION_3_4
import com.charlsemilbarquin.frameandfact.data.local.history.MIGRATION_4_5
import com.charlsemilbarquin.frameandfact.data.local.history.MIGRATION_5_6
import com.charlsemilbarquin.frameandfact.data.local.history.MIGRATION_6_7
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameHistoryMigrationTest {
    @Test
    fun v1HistoryRemainsReadableAndNewSnapshotsStartNull() = kotlinx.coroutines.runBlocking {
        val databaseName = "history-v1-to-v2"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { db ->
            db.execSQL("CREATE TABLE completed_games (gameId TEXT NOT NULL, completedAtEpochMillis INTEGER NOT NULL, rulesVersion INTEGER NOT NULL, selectedCategories TEXT NOT NULL, difficultyLevels TEXT NOT NULL, PRIMARY KEY(gameId))")
            db.execSQL("CREATE TABLE completed_game_participants (gameId TEXT NOT NULL, participantId TEXT NOT NULL, participantOrder INTEGER NOT NULL, displayName TEXT NOT NULL, participantKind TEXT NOT NULL, score INTEGER NOT NULL, correctTriviaRounds INTEGER NOT NULL, acceptedCameraRounds INTEGER NOT NULL, rank INTEGER NOT NULL, PRIMARY KEY(gameId, participantId), FOREIGN KEY(gameId) REFERENCES completed_games(gameId) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE UNIQUE INDEX index_completed_game_participants_gameId_participantOrder ON completed_game_participants(gameId, participantOrder)")
            db.execSQL("CREATE TABLE completed_game_rounds (gameId TEXT NOT NULL, roundNumber INTEGER NOT NULL, roundType TEXT NOT NULL, participantId TEXT NOT NULL, judgeId TEXT, outcome TEXT NOT NULL, PRIMARY KEY(gameId, roundNumber), FOREIGN KEY(gameId) REFERENCES completed_games(gameId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(gameId, participantId) REFERENCES completed_game_participants(gameId, participantId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(gameId, judgeId) REFERENCES completed_game_participants(gameId, participantId) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_completed_game_rounds_gameId_participantId ON completed_game_rounds(gameId, participantId)")
            db.execSQL("CREATE INDEX index_completed_game_rounds_gameId_judgeId ON completed_game_rounds(gameId, judgeId)")
            db.execSQL("INSERT INTO completed_games VALUES ('old-game', 123, 1, 'Science', 'EASY')")
            db.execSQL("INSERT INTO completed_game_participants VALUES ('old-game', 'p1', 0, 'Player', 'PLAYER', 10, 1, 0, 1)")
            db.execSQL("INSERT INTO completed_game_rounds VALUES ('old-game', 1, 'TRIVIA', 'p1', NULL, 'TRIVIA_CORRECT')")
            db.version = 1
        }

        val migrated = Room.databaseBuilder(context, GameHistoryDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()
        try {
            val saved = RoomGameHistoryRepository(migrated).findCompletedGame("old-game")
            assertEquals("old-game", saved?.gameId)
            assertEquals("Player", saved?.participants?.single()?.displayName)
            assertEquals(RoundOutcome.TRIVIA_CORRECT, saved?.rounds?.single()?.outcome)
            assertTrue(saved?.rounds?.single()?.questionText == null)
            assertTrue(saved?.rounds?.single()?.selectedAnswer == null)
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun v6DataIsAssignedToOldestAccountWithoutDroppingRecords() = kotlinx.coroutines.runBlocking {
        val databaseName = "history-v6-to-v7-owner"
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(databaseName)
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null).use { db ->
            createV1Tables(db)
            db.execSQL("INSERT INTO completed_games VALUES ('legacy-game', 123, 1, 'Science', 'EASY')")
            db.execSQL("INSERT INTO completed_game_participants VALUES ('legacy-game', 'p1', 0, 'Player', 'PLAYER', 10, 1, 0, 1)")
            db.execSQL("INSERT INTO completed_game_rounds VALUES ('legacy-game', 1, 'TRIVIA', 'p1', NULL, 'TRIVIA_CORRECT')")
            db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN questionId TEXT")
            db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN questionText TEXT")
            db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN selectedAnswer TEXT")
            db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN correctAnswer TEXT")
            db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN explanation TEXT")
            db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN categoryId TEXT")
            db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN difficulty TEXT")
            db.execSQL("CREATE TABLE question_reports (reportId TEXT NOT NULL, questionId TEXT NOT NULL, issueType TEXT NOT NULL, note TEXT, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(reportId))")
            db.execSQL("CREATE TABLE player_profiles (profileId TEXT NOT NULL, displayName TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(profileId))")
            db.execSQL("CREATE TABLE profile_achievements (profileId TEXT NOT NULL, achievementId TEXT NOT NULL, unlockedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(profileId, achievementId), FOREIGN KEY(profileId) REFERENCES player_profiles(profileId) ON UPDATE NO ACTION ON DELETE CASCADE)")
            db.execSQL("CREATE INDEX index_profile_achievements_profileId ON profile_achievements(profileId)")
            db.execSQL("ALTER TABLE completed_game_participants ADD COLUMN profileId TEXT REFERENCES player_profiles(profileId) ON UPDATE NO ACTION ON DELETE SET NULL")
            db.execSQL("CREATE INDEX index_completed_game_participants_profileId ON completed_game_participants(profileId)")
            db.execSQL("CREATE TABLE short_game_summaries (sessionId TEXT NOT NULL, sessionType TEXT NOT NULL, completedAtEpochMillis INTEGER NOT NULL, questionCount INTEGER NOT NULL, categoryId TEXT, difficulty TEXT, participantsJson TEXT NOT NULL, PRIMARY KEY(sessionId))")
            db.execSQL("CREATE TABLE auth_accounts (accountId TEXT NOT NULL, fullName TEXT NOT NULL, username TEXT NOT NULL, normalizedUsername TEXT NOT NULL, normalizedEmail TEXT, passwordSaltBase64 TEXT NOT NULL, passwordHashBase64 TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(accountId))")
            db.execSQL("CREATE UNIQUE INDEX index_auth_accounts_normalizedUsername ON auth_accounts(normalizedUsername)")
            db.execSQL("CREATE UNIQUE INDEX index_auth_accounts_normalizedEmail ON auth_accounts(normalizedEmail)")
            db.execSQL("INSERT INTO auth_accounts VALUES ('older', 'Older', 'older', 'older', NULL, 'salt', 'hash', 10)")
            db.execSQL("INSERT INTO auth_accounts VALUES ('newer', 'Newer', 'newer', 'newer', NULL, 'salt', 'hash', 20)")
            db.execSQL("INSERT INTO player_profiles VALUES ('legacy-profile', 'Legacy Player', 10)")
            db.execSQL("INSERT INTO question_reports VALUES ('legacy-report', 'science-1', 'OTHER', NULL, 10)")
            db.execSQL("INSERT INTO short_game_summaries VALUES ('legacy-short', 'QUICK_PLAY', 10, 5, NULL, NULL, '[]')")
            db.version = 6
        }

        val migrated = Room.databaseBuilder(context, GameHistoryDatabase::class.java, databaseName)
            .addMigrations(MIGRATION_6_7)
            .build()
        try {
            assertEquals("legacy-game", RoomGameHistoryRepository(migrated, "older").findCompletedGame("legacy-game")?.gameId)
            assertTrue(RoomGameHistoryRepository(migrated, "newer").observeCompletedGames().first().isEmpty())
            assertEquals("Legacy Player", RoomLocalPlayerProfileRepository(migrated, "older").observeProfiles().first().single().displayName)
            assertEquals("legacy-report", RoomQuestionReportRepository(migrated, "older").observeReports().first().single().reportId)
            assertEquals("legacy-short", RoomShortGameSummaryRepository(migrated, "older").observeSummaries().first().single().sessionId)
            assertEquals(0, migrated.openHelper.readableDatabase.query("SELECT COUNT(*) FROM completed_games WHERE ownerAccountId IS NULL").use { cursor -> cursor.moveToFirst(); cursor.getInt(0) })
        } finally {
            migrated.close()
            context.deleteDatabase(databaseName)
        }
    }

    private fun createV1Tables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE completed_games (gameId TEXT NOT NULL, completedAtEpochMillis INTEGER NOT NULL, rulesVersion INTEGER NOT NULL, selectedCategories TEXT NOT NULL, difficultyLevels TEXT NOT NULL, PRIMARY KEY(gameId))")
        db.execSQL("CREATE TABLE completed_game_participants (gameId TEXT NOT NULL, participantId TEXT NOT NULL, participantOrder INTEGER NOT NULL, displayName TEXT NOT NULL, participantKind TEXT NOT NULL, score INTEGER NOT NULL, correctTriviaRounds INTEGER NOT NULL, acceptedCameraRounds INTEGER NOT NULL, rank INTEGER NOT NULL, PRIMARY KEY(gameId, participantId), FOREIGN KEY(gameId) REFERENCES completed_games(gameId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE UNIQUE INDEX index_completed_game_participants_gameId_participantOrder ON completed_game_participants(gameId, participantOrder)")
        db.execSQL("CREATE TABLE completed_game_rounds (gameId TEXT NOT NULL, roundNumber INTEGER NOT NULL, roundType TEXT NOT NULL, participantId TEXT NOT NULL, judgeId TEXT, outcome TEXT NOT NULL, PRIMARY KEY(gameId, roundNumber), FOREIGN KEY(gameId) REFERENCES completed_games(gameId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(gameId, participantId) REFERENCES completed_game_participants(gameId, participantId) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(gameId, judgeId) REFERENCES completed_game_participants(gameId, participantId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX index_completed_game_rounds_gameId_participantId ON completed_game_rounds(gameId, participantId)")
        db.execSQL("CREATE INDEX index_completed_game_rounds_gameId_judgeId ON completed_game_rounds(gameId, judgeId)")
    }
}
