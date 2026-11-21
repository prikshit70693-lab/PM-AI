package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC, id ASC")
    fun observeMessages(): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages")
    suspend fun clearAll()
}

@Dao
interface CallRuleDao {
    @Query("SELECT * FROM call_rules ORDER BY createdAt DESC")
    fun observeRules(): Flow<List<CallRuleEntity>>

    @Query("SELECT * FROM call_rules WHERE isEnabled = 1")
    suspend fun getActiveRules(): List<CallRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: CallRuleEntity): Long

    @Update
    suspend fun updateRule(rule: CallRuleEntity)

    @Query("DELETE FROM call_rules WHERE id = :id")
    suspend fun deleteRule(id: Long)
}

@Dao
interface ScreenedCallLogDao {
    @Query("SELECT * FROM screened_calls ORDER BY timestamp DESC LIMIT 50")
    fun observeLogs(): Flow<List<ScreenedCallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ScreenedCallLogEntity): Long

    @Query("DELETE FROM screened_calls")
    suspend fun clearAll()
}

@Dao
interface AutomationLogDao {
    @Query("SELECT * FROM automation_logs ORDER BY timestamp DESC LIMIT 50")
    fun observeLogs(): Flow<List<AutomationLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AutomationLogEntity): Long

    @Query("DELETE FROM automation_logs")
    suspend fun clearAll()
}
