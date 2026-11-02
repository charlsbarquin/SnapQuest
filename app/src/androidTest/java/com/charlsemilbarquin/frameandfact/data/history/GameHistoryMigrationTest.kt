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
import com.charlsemilbarquin.frameandfact.domain.game.RoundOutcome
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
}
