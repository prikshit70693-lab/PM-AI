package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String, // "user" or "model"
    val content: String,
    val brainMode: String = "PM Brain",
    val actionBadge: String? = null,
    val hasImage: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_rules")
data class CallRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val phonePattern: String,
    val ruleAction: String, // "ALLOW_VIP", "BLOCK_SILENT", "REJECT_WITH_SMS"
    val customReply: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "screened_calls")
data class ScreenedCallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerNumber: String,
    val actionTaken: String, // "ALLOWED", "SILENCED", "REJECTED"
    val reason: String,
    val autoReplyPrepared: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "automation_logs")
data class AutomationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val commandText: String,
    val category: String, // "SCROLL", "OS_NAV", "HARDWARE", "CALL", "MESSAGE", "APP"
    val status: String, // "EXECUTED", "NEEDS_PERMISSION", "NEEDS_ACCESSIBILITY", "FAILED"
    val detail: String,
    val timestamp: Long = System.currentTimeMillis()
)
