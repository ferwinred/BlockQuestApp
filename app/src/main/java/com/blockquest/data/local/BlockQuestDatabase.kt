// =====================================================================
// BlockQuestDatabase.kt
// Block Quest — Room 3.0 database container
// =====================================================================

package com.blockquest.data.local

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [LevelCacheEntity::class, MissionProgressEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class BlockQuestDatabase : RoomDatabase() {
    abstract fun levelCacheDao(): LevelCacheDao
    abstract fun missionProgressDao(): MissionProgressDao

    companion object {
        fun build(context: Context): BlockQuestDatabase = Room
            .databaseBuilder(
                context.applicationContext,
                BlockQuestDatabase::class.java,
                "blockquest.db"
            )
            // Room 3.0 dropped SupportSQLite, so we don't need
            // to enable fallback DDL migrations.
            .fallbackToDestructiveMigration()
            .build()
    }
}
