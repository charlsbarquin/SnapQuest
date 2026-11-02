package com.charlsemilbarquin.frameandfact.data.local.history

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Add nullable review snapshots without changing or discarding previously saved History rows. */
val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN questionId TEXT")
        db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN questionText TEXT")
        db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN selectedAnswer TEXT")
        db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN correctAnswer TEXT")
        db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN explanation TEXT")
        db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN categoryId TEXT")
        db.execSQL("ALTER TABLE completed_game_rounds ADD COLUMN difficulty TEXT")
    }
}

/** Adds a private local inbox for question quality reports. */
val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS question_reports (reportId TEXT NOT NULL, questionId TEXT NOT NULL, " +
                "issueType TEXT NOT NULL, note TEXT, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(reportId))"
        )
    }
}

/** Adds explicit local profiles and achievement storage, then links new History rows safely. */
val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS player_profiles (profileId TEXT NOT NULL, displayName TEXT NOT NULL, createdAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(profileId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS profile_achievements (profileId TEXT NOT NULL, achievementId TEXT NOT NULL, unlockedAtEpochMillis INTEGER NOT NULL, PRIMARY KEY(profileId, achievementId), FOREIGN KEY(profileId) REFERENCES player_profiles(profileId) ON UPDATE NO ACTION ON DELETE CASCADE)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_profile_achievements_profileId ON profile_achievements(profileId)")
        db.execSQL("ALTER TABLE completed_game_participants ADD COLUMN profileId TEXT REFERENCES player_profiles(profileId) ON UPDATE NO ACTION ON DELETE SET NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_completed_game_participants_profileId ON completed_game_participants(profileId)")
    }
}

/** Stores short trivia-mode summaries independently from Full Game History and achievements. */
val MIGRATION_4_5: Migration = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS short_game_summaries (sessionId TEXT NOT NULL, sessionType TEXT NOT NULL, completedAtEpochMillis INTEGER NOT NULL, questionCount INTEGER NOT NULL, categoryId TEXT, difficulty TEXT, participantsJson TEXT NOT NULL, PRIMARY KEY(sessionId))")
    }
}
