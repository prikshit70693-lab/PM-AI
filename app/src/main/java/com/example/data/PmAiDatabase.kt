package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ChatMessageEntity::class,
        CallRuleEntity::class,
        ScreenedCallLogEntity::class,
        AutomationLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class PmAiDatabase : RoomDatabase() {
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun callRuleDao(): CallRuleDao
    abstract fun screenedCallLogDao(): ScreenedCallLogDao
    abstract fun automationLogDao(): AutomationLogDao

    companion object {
        @Volatile
        private var INSTANCE: PmAiDatabase? = null

        fun getInstance(context: Context): PmAiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    PmAiDatabase::class.java,
                    "pm_ai_database"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
