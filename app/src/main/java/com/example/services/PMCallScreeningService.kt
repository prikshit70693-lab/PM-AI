package com.example.services

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.CallShieldMode
import com.example.data.PmAiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

private const val TAG = "PMCallScreening"

class PMCallScreeningService : CallScreeningService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        val rawNumber = callDetails.handle?.schemeSpecificPart?.trim().orEmpty()
        val displayNumber = rawNumber.ifEmpty { "Unknown / Private" }

        serviceScope.launch {
            try {
                val repo = PmAiRepository(applicationContext)
                val config = repo.getCallConfigSnapshot()
                val activeRules = repo.getActiveCallRules()

                val normalizedIncoming = normalizeDigits(rawNumber)
                val matchedRule = activeRules.firstOrNull { rule ->
                    val patternDigits = normalizeDigits(rule.phonePattern)
                    patternDigits.isNotEmpty() && normalizedIncoming.contains(patternDigits)
                }

                val isVerificationFailed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    callDetails.callerNumberVerificationStatus ==
                        Connection.VERIFICATION_STATUS_FAILED
                } else {
                    false
                }

                val decision: CallDecision = when {
                    matchedRule?.ruleAction == "ALLOW_VIP" -> {
                        CallDecision("ALLOWED", "Matched VIP rule: ${matchedRule.label}", allow = true)
                    }
                    matchedRule?.ruleAction == "BLOCK_SILENT" -> {
                        CallDecision("SILENCED", "Matched block rule: ${matchedRule.label}", allow = false, reject = false, silence = true)
                    }
                    matchedRule?.ruleAction == "REJECT_WITH_SMS" -> {
                        val reply = matchedRule.customReply.ifBlank { config.autoSmsTemplate }
                        CallDecision("REJECTED", "Matched auto-reject rule: ${matchedRule.label}", allow = false, reject = true, silence = true, autoReply = reply)
                    }
                    config.mode == CallShieldMode.OFF -> {
                        CallDecision("ALLOWED", "Standard Ring mode active", allow = true)
                    }
                    config.mode == CallShieldMode.VIP_ONLY -> {
                        val reply = if (config.autoSmsReplyEnabled) config.autoSmsTemplate else null
                        CallDecision("REJECTED", "VIP Focus Mode blocked non-VIP caller", allow = false, reject = true, silence = true, autoReply = reply)
                    }
                    config.mode == CallShieldMode.MEETING_QUIET -> {
                        val reply = if (config.autoSmsReplyEnabled) config.autoSmsTemplate else null
                        CallDecision("REJECTED", "Meeting Quiet Mode active", allow = false, reject = true, silence = true, autoReply = reply)
                    }
                    config.mode == CallShieldMode.BLOCK_UNKNOWN && rawNumber.isBlank() -> {
                        CallDecision("REJECTED", "Unknown/Private number blocked", allow = false, reject = true, silence = true)
                    }
                    config.mode == CallShieldMode.SPAM_GUARD && isVerificationFailed -> {
                        CallDecision("REJECTED", "Caller verification failed (potential spam)", allow = false, reject = true, silence = true)
                    }
                    else -> {
                        CallDecision("ALLOWED", "Passed ${config.mode.title}", allow = true)
                    }
                }

                val responseBuilder = CallResponse.Builder()
                    .setDisallowCall(!decision.allow)
                    .setRejectCall(decision.reject)
                    .setSilenceCall(decision.silence)
                    .setSkipCallLog(false)
                    .setSkipNotification(!decision.allow)

                respondToCall(callDetails, responseBuilder.build())

                var smsStatusNote: String? = decision.autoReply
                if (!decision.allow && !decision.autoReply.isNullOrBlank() && rawNumber.isNotBlank()) {
                    val sent = trySendAutoReplySms(rawNumber, decision.autoReply)
                    smsStatusNote = if (sent) "Auto-SMS sent: ${decision.autoReply}" else "Auto-SMS prepared (grant SEND_SMS to auto-send): ${decision.autoReply}"
                }

                repo.logScreenedCall(
                    callerNumber = displayNumber,
                    actionTaken = decision.actionTaken,
                    reason = decision.reason,
                    autoReplyPrepared = smsStatusNote
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error screening call", e)
                respondToCall(callDetails, CallResponse.Builder().build())
            }
        }
    }

    private fun trySendAutoReplySms(phoneNumber: String, message: String): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return false

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                applicationContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            smsManager?.sendTextMessage(phoneNumber, null, message, null, null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send auto-reply SMS", e)
            false
        }
    }

    private fun normalizeDigits(input: String): String {
        return input.filter { it.isDigit() }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class CallDecision(
        val actionTaken: String,
        val reason: String,
        val allow: Boolean,
        val reject: Boolean = false,
        val silence: Boolean = false,
        val autoReply: String? = null
    )
}
