package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CallShieldMode(
    val title: String,
    val subtitle: String
) {
    OFF("Standard Ring", "All calls ring normally; PM AI only logs rules."),
    BLOCK_UNKNOWN("Block Unknown", "Silences & rejects numbers not in VIP rules or unknown/private callers."),
    SPAM_GUARD("Spam & Robocall Guard", "Rejects calls flagged as unverified/spam or matching block rules."),
    VIP_ONLY("VIP Focus Mode", "Allows only VIP rules; blocks others with smart AI auto-reply."),
    MEETING_QUIET("Meeting Quiet Mode", "Auto-declines incoming calls and prepares an AI busy reply.")
}

data class CallShieldConfig(
    val mode: CallShieldMode = CallShieldMode.SPAM_GUARD,
    val autoSmsReplyEnabled: Boolean = false,
    val autoSmsTemplate: String = "Hi, PM AI is screening calls right now as I'm busy. I'll call you back shortly!"
)

class PmAiRepository(context: Context) {
    private val db = PmAiDatabase.getInstance(context)
    private val prefs = context.getSharedPreferences("pm_ai_prefs", Context.MODE_PRIVATE)

    val chatMessages: Flow<List<ChatMessageEntity>> = db.chatMessageDao().observeMessages()
    val callRules: Flow<List<CallRuleEntity>> = db.callRuleDao().observeRules()
    val screenedCalls: Flow<List<ScreenedCallLogEntity>> = db.screenedCallLogDao().observeLogs()
    val automationLogs: Flow<List<AutomationLogEntity>> = db.automationLogDao().observeLogs()

    private val _callConfig = MutableStateFlow(loadCallConfig())
    val callConfig: StateFlow<CallShieldConfig> = _callConfig.asStateFlow()

    private fun loadCallConfig(): CallShieldConfig {
        val modeName = prefs.getString(KEY_CALL_MODE, CallShieldMode.SPAM_GUARD.name)
            ?: CallShieldMode.SPAM_GUARD.name
        val mode = runCatching { CallShieldMode.valueOf(modeName) }.getOrDefault(CallShieldMode.SPAM_GUARD)
        val smsEnabled = prefs.getBoolean(KEY_AUTO_SMS, false)
        val template = prefs.getString(
            KEY_SMS_TEMPLATE,
            "Hi, PM AI is handling calls while I'm unavailable. I'll get back to you soon!"
        ) ?: "Hi, PM AI is handling calls while I'm unavailable. I'll get back to you soon!"
        return CallShieldConfig(mode, smsEnabled, template)
    }

    fun getCallConfigSnapshot(): CallShieldConfig = loadCallConfig()

    fun updateCallMode(mode: CallShieldMode) {
        prefs.edit().putString(KEY_CALL_MODE, mode.name).apply()
        _callConfig.value = _callConfig.value.copy(mode = mode)
    }

    fun updateAutoSmsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_SMS, enabled).apply()
        _callConfig.value = _callConfig.value.copy(autoSmsReplyEnabled = enabled)
    }

    fun updateAutoSmsTemplate(template: String) {
        prefs.edit().putString(KEY_SMS_TEMPLATE, template).apply()
        _callConfig.value = _callConfig.value.copy(autoSmsTemplate = template)
    }

    suspend fun saveMessage(
        role: String,
        content: String,
        brainMode: String = "PM Brain",
        actionBadge: String? = null,
        hasImage: Boolean = false
    ) {
        db.chatMessageDao().insertMessage(
            ChatMessageEntity(
                role = role,
                content = content,
                brainMode = brainMode,
                actionBadge = actionBadge,
                hasImage = hasImage
            )
        )
    }

    suspend fun clearChat() = db.chatMessageDao().clearAll()

    suspend fun addCallRule(
        label: String,
        phonePattern: String,
        ruleAction: String,
        customReply: String = ""
    ) {
        db.callRuleDao().insertRule(
            CallRuleEntity(
                label = label.trim(),
                phonePattern = phonePattern.trim(),
                ruleAction = ruleAction,
                customReply = customReply.trim()
            )
        )
    }

    suspend fun toggleCallRule(rule: CallRuleEntity) {
        db.callRuleDao().updateRule(rule.copy(isEnabled = !rule.isEnabled))
    }

    suspend fun deleteCallRule(id: Long) = db.callRuleDao().deleteRule(id)

    suspend fun getActiveCallRules(): List<CallRuleEntity> = db.callRuleDao().getActiveRules()

    suspend fun logScreenedCall(
        callerNumber: String,
        actionTaken: String,
        reason: String,
        autoReplyPrepared: String? = null
    ) {
        db.screenedCallLogDao().insertLog(
            ScreenedCallLogEntity(
                callerNumber = callerNumber,
                actionTaken = actionTaken,
                reason = reason,
                autoReplyPrepared = autoReplyPrepared
            )
        )
    }

    suspend fun clearScreenedCalls() = db.screenedCallLogDao().clearAll()

    suspend fun logAutomation(
        commandText: String,
        category: String,
        status: String,
        detail: String
    ) {
        db.automationLogDao().insertLog(
            AutomationLogEntity(
                commandText = commandText,
                category = category,
                status = status,
                detail = detail
            )
        )
    }

    suspend fun clearAutomationLogs() = db.automationLogDao().clearAll()

    companion object {
        private const val KEY_CALL_MODE = "call_shield_mode"
        private const val KEY_AUTO_SMS = "auto_sms_enabled"
        private const val KEY_SMS_TEMPLATE = "auto_sms_template"
    }
}
