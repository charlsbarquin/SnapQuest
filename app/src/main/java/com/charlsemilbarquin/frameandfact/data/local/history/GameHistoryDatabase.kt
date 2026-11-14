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
        ShortGameSummaryEntity::class,
        AuthAccountEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class GameHistoryDatabase : RoomDatabase() {
    abstract fun completedGameDao(): CompletedGameDao
    abstract fun authAccountDao(): AuthAccountDao

    companion object {
        const val DATABASE_NAME = "frame_and_fact_history.db"

        @Volatile
        private var instance: GameHistoryDatabase? = null

        fun getInstance(context: Context): GameHistoryDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                GameHistoryDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).build().also { instance = it }
        }
    }
}
