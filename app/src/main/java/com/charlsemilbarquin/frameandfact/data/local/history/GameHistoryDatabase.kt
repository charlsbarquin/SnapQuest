package com.charlsemilbarquin.frameandfact.data.local.history

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        CompletedGameEntity::class,
        CompletedGameParticipantEntity::class,
        CompletedGameRoundEntity::class,
        QuestionReportEntity::class,
        PlayerProfileEntity::class,
        ProfileAchievementEntity::class,
        ShortGameSummaryEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class GameHistoryDatabase : RoomDatabase() {
    abstract fun completedGameDao(): CompletedGameDao

    companion object {
        const val DATABASE_NAME = "frame_and_fact_history.db"

        @Volatile
        private var instance: GameHistoryDatabase? = null

        fun getInstance(context: Context): GameHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                GameHistoryDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instance = it }
        }
    }
}
