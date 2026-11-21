package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiBrainMode
import com.example.ai.ParsedPhoneCommand
import com.example.ai.PhoneActionType
import com.example.ai.PmAiBrainRepository
import com.example.ai.VoiceAssistantEngine
import com.example.data.AutomationLogEntity
import com.example.data.CallRuleEntity
import com.example.data.CallShieldConfig
import com.example.data.CallShieldMode
import com.example.data.ChatMessageEntity
import com.example.data.PmAiRepository
import com.example.data.ScreenedCallLogEntity
import com.example.services.AccessibilityBridge
import com.example.services.AccessibilityStatus
import com.example.services.AlwaysActiveBridge
import com.example.services.AlwaysActiveStatus
import com.example.services.ControlExecutionResult
import com.example.services.DeviceController
import com.example.services.OsGlobalAction
import com.example.services.PMAlwaysActiveService
import com.example.services.ScrollDirection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "PmAiViewModel"

sealed interface InAppCommandEvent {
    data class NavigateTo(val route: String) : InAppCommandEvent
    data class ScrollScreen(val direction: ScrollDirection) : InAppCommandEvent
    data class PrefillMessage(val recipient: String, val body: String) : InAppCommandEvent
}

sealed interface ChatStreamState {
    data object Idle : ChatStreamState
    data object Thinking : ChatStreamState
    data class Streaming(val partialText: String) : ChatStreamState
    data class Error(val message: String) : ChatStreamState
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val promptUsed: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

sealed interface ToolUiState {
    data object Idle : ToolUiState
    data object Loading : ToolUiState
    data class Success(val output: String) : ToolUiState
    data class Error(val message: String) : ToolUiState
}

data class PendingPermissionPrompt(
    val permission: String,
    val rationale: String
)

class PmAiViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PmAiRepository(application)
    private val aiBrain = PmAiBrainRepository()
    private val voiceEngine = VoiceAssistantEngine(application)

    val messages: StateFlow<List<ChatMessageEntity>> = repo.chatMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callRules: StateFlow<List<CallRuleEntity>> = repo.callRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val screenedCalls: StateFlow<List<ScreenedCallLogEntity>> = repo.screenedCalls
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automationLogs: StateFlow<List<AutomationLogEntity>> = repo.automationLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val callConfig: StateFlow<CallShieldConfig> = repo.callConfig
    val accessibilityStatus: StateFlow<AccessibilityStatus> = AccessibilityBridge.status
    val alwaysActiveStatus: StateFlow<AlwaysActiveStatus> = AlwaysActiveBridge.status
    val isListening: StateFlow<Boolean> = voiceEngine.isListening
    val isSpeaking: StateFlow<Boolean> = voiceEngine.isSpeaking
    val partialTranscript: StateFlow<String> = voiceEngine.partialTranscript

    private val _brainMode = MutableStateFlow(AiBrainMode.OMNI_BRAIN)
    val brainMode: StateFlow<AiBrainMode> = _brainMode.asStateFlow()

    private val _autoSpeakReplies = MutableStateFlow(false)
    val autoSpeakReplies: StateFlow<Boolean> = _autoSpeakReplies.asStateFlow()

    private val _chatStreamState = MutableStateFlow<ChatStreamState>(ChatStreamState.Idle)
    val chatStreamState: StateFlow<ChatStreamState> = _chatStreamState.asStateFlow()

    private val _attachedVisionBitmap = MutableStateFlow<Bitmap?>(null)
    val attachedVisionBitmap: StateFlow<Bitmap?> = _attachedVisionBitmap.asStateFlow()

    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    private val _studioReferenceBitmap = MutableStateFlow<Bitmap?>(null)
    val studioReferenceBitmap: StateFlow<Bitmap?> = _studioReferenceBitmap.asStateFlow()

    private val _toolUiState = MutableStateFlow<ToolUiState>(ToolUiState.Idle)
    val toolUiState: StateFlow<ToolUiState> = _toolUiState.asStateFlow()

    private val _callSummaryState = MutableStateFlow<ToolUiState>(ToolUiState.Idle)
    val callSummaryState: StateFlow<ToolUiState> = _callSummaryState.asStateFlow()

    private val _inAppEvents = MutableSharedFlow<InAppCommandEvent>(extraBufferCapacity = 8)
    val inAppEvents: SharedFlow<InAppCommandEvent> = _inAppEvents.asSharedFlow()

    private val _prefilledRecipient = MutableStateFlow("")
    val prefilledRecipient: StateFlow<String> = _prefilledRecipient.asStateFlow()

    private val _prefilledMessage = MutableStateFlow("")
    val prefilledMessage: StateFlow<String> = _prefilledMessage.asStateFlow()

    private val _pendingPermission = MutableStateFlow<PendingPermissionPrompt?>(null)
    val pendingPermission: StateFlow<PendingPermissionPrompt?> = _pendingPermission.asStateFlow()

    private val _showAccessibilityPrompt = MutableStateFlow(false)
    val showAccessibilityPrompt: StateFlow<Boolean> = _showAccessibilityPrompt.asStateFlow()

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _isCallRoleHeld = MutableStateFlow(DeviceController.isCallScreeningRoleHeld(application))
    val isCallRoleHeld: StateFlow<Boolean> = _isCallRoleHeld.asStateFlow()

    private val _isTorchOn = MutableStateFlow(DeviceController.isTorchOn())
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    fun refreshSystemStates() {
        val ctx = getApplication<Application>()
        AccessibilityBridge.refreshConnectionFlag(ctx)
        _isCallRoleHeld.value = DeviceController.isCallScreeningRoleHeld(ctx)
    }

    fun setBrainMode(mode: AiBrainMode) {
        _brainMode.value = mode
    }

    fun toggleAutoSpeak() {
        val next = !_autoSpeakReplies.value
        _autoSpeakReplies.value = next
        if (!next) voiceEngine.stopSpeaking()
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun clearPendingPermission() {
        _pendingPermission.value = null
    }

    fun dismissAccessibilityPrompt() {
        _showAccessibilityPrompt.value = false
    }

    fun attachVisionUri(uri: Uri) {
        viewModelScope.launch {
            val bmp = decodeScaledBitmap(uri, maxDim = 1024)
            if (bmp != null) {
                _attachedVisionBitmap.value = bmp
            } else {
                _statusBanner.value = "Could not load selected image."
            }
        }
    }

    fun clearVisionBitmap() {
        _attachedVisionBitmap.value = null
    }

    fun attachStudioReferenceUri(uri: Uri) {
        viewModelScope.launch {
            val bmp = decodeScaledBitmap(uri, maxDim = 1024)
            if (bmp != null) {
                _studioReferenceBitmap.value = bmp
            } else {
                _statusBanner.value = "Could not load reference image."
            }
        }
    }

    fun clearStudioReference() {
        _studioReferenceBitmap.value = null
    }

    fun startVoiceCommand() {
        voiceEngine.startListening(
            languageTag = if (_brainMode.value == AiBrainMode.HINGLISH_BUDDY) "hi-IN" else "en-IN",
            onResult = { spoken ->
                sendUserPrompt(spoken, fromVoice = true)
            },
            onError = { err ->
                _statusBanner.value = err
            }
        )
    }

    fun stopVoiceListening() {
        voiceEngine.stopListening()
    }

    fun speakText(text: String) {
        voiceEngine.speak(text)
    }

    fun stopSpeaking() {
        voiceEngine.stopSpeaking()
    }

    fun sendUserPrompt(rawPrompt: String, fromVoice: Boolean = false) {
        val prompt = rawPrompt.trim()
        val imageBitmap = _attachedVisionBitmap.value
        if (prompt.isEmpty() && imageBitmap == null) return

        val effectivePrompt = prompt.ifEmpty { "Analyze this image and explain what you see." }
        _attachedVisionBitmap.value = null

        viewModelScope.launch {
            val currentMode = _brainMode.value
            repo.saveMessage(
                role = "user",
                content = effectivePrompt,
                brainMode = currentMode.label,
                hasImage = imageBitmap != null
            )
            _chatStreamState.value = ChatStreamState.Thinking

            try {
                // Step 1: If text-only, check if user is issuing a Phone Control command via Gemini Intent Router
                if (imageBitmap == null) {
                    val parsedCommand = aiBrain.classifyPhoneIntent(effectivePrompt)
                    if (parsedCommand.actionType != PhoneActionType.NONE) {
                        val actionHandled = handleParsedPhoneCommand(parsedCommand, effectivePrompt, currentMode, fromVoice)
                        if (actionHandled) return@launch
                    }
                }

                // Step 2: Stream Gemini 3.8 Flash conversational / problem-solving response
                val history = messages.value.takeLast(10).map { it.role to it.content }
                var finalReply = ""
                aiBrain.streamChatResponse(
                    userMessage = effectivePrompt,
                    historyPairs = history,
                    mode = currentMode,
                    attachedBitmap = imageBitmap
                ).collect { partial ->
                    finalReply = partial
                    _chatStreamState.value = ChatStreamState.Streaming(partial)
                }

                repo.saveMessage(
                    role = "model",
                    content = finalReply,
                    brainMode = currentMode.label
                )
                _chatStreamState.value = ChatStreamState.Idle
                if (fromVoice || _autoSpeakReplies.value) {
                    voiceEngine.speak(finalReply)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "PM AI response timed out", e)
                _chatStreamState.value = ChatStreamState.Error("PM AI timed out after 60 seconds. Please try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PM AI generation failed", e)
                _chatStreamState.value = ChatStreamState.Error(
                    e.localizedMessage ?: "PM AI encountered an error."
                )
            }
        }
    }

    private suspend fun handleParsedPhoneCommand(
        cmd: ParsedPhoneCommand,
        rawPrompt: String,
        mode: AiBrainMode,
        fromVoice: Boolean
    ): Boolean {
        val ctx = getApplication<Application>()

        if (cmd.actionType == PhoneActionType.READ_SCREEN) {
            val screenText = AccessibilityBridge.readScreenText()
            if (screenText == null) {
                _showAccessibilityPrompt.value = true
                val msg = "Enable PM AI Phone Control in Accessibility Settings so I can read and summarize your active screen."
                repo.saveMessage("model", msg, mode.label, actionBadge = "ACCESSIBILITY_REQUIRED")
                repo.logAutomation(rawPrompt, "OS_NAV", "NEEDS_ACCESSIBILITY", msg)
                _chatStreamState.value = ChatStreamState.Idle
                return true
            }
            var reply = ""
            aiBrain.streamChatResponse(
                userMessage = rawPrompt,
                historyPairs = emptyList(),
                mode = mode,
                extraScreenContext = screenText
            ).collect { partial ->
                reply = partial
                _chatStreamState.value = ChatStreamState.Streaming(partial)
            }
            repo.saveMessage("model", reply, mode.label, actionBadge = "READ_SCREEN")
            repo.logAutomation(rawPrompt, "OS_NAV", "EXECUTED", "Read & analyzed screen content")
            _chatStreamState.value = ChatStreamState.Idle
            if (fromVoice || _autoSpeakReplies.value) voiceEngine.speak(reply)
            return true
        }

        val execResult: ControlExecutionResult = when (cmd.actionType) {
            PhoneActionType.SCROLL_DOWN -> {
                _inAppEvents.tryEmit(InAppCommandEvent.ScrollScreen(ScrollDirection.DOWN))
                executeScroll(ScrollDirection.DOWN)
            }
            PhoneActionType.SCROLL_UP -> {
                _inAppEvents.tryEmit(InAppCommandEvent.ScrollScreen(ScrollDirection.UP))
                executeScroll(ScrollDirection.UP)
            }
            PhoneActionType.SCROLL_LEFT -> executeScroll(ScrollDirection.LEFT)
            PhoneActionType.SCROLL_RIGHT -> executeScroll(ScrollDirection.RIGHT)
            PhoneActionType.AUTO_SCROLL_START -> executeAutoScroll(true, ScrollDirection.DOWN, 1600L)
            PhoneActionType.AUTO_SCROLL_STOP -> executeAutoScroll(false, ScrollDirection.DOWN, 1600L)
            PhoneActionType.NAV_HOME -> executeOsAction(OsGlobalAction.HOME)
            PhoneActionType.NAV_BACK -> executeOsAction(OsGlobalAction.BACK)
            PhoneActionType.NAV_RECENTS -> executeOsAction(OsGlobalAction.RECENTS)
            PhoneActionType.OPEN_NOTIFICATIONS -> executeOsAction(OsGlobalAction.NOTIFICATIONS)
            PhoneActionType.OPEN_QUICK_SETTINGS -> executeOsAction(OsGlobalAction.QUICK_SETTINGS)
            PhoneActionType.TAKE_SCREENSHOT -> executeOsAction(OsGlobalAction.SCREENSHOT)
            PhoneActionType.FLASHLIGHT_ON -> {
                val res = DeviceController.setFlashlight(ctx, true)
                _isTorchOn.value = DeviceController.isTorchOn()
                res
            }
            PhoneActionType.FLASHLIGHT_OFF -> {
                val res = DeviceController.setFlashlight(ctx, false)
                _isTorchOn.value = DeviceController.isTorchOn()
                res
            }
            PhoneActionType.VOLUME_UP -> DeviceController.adjustVolume(ctx, true)
            PhoneActionType.VOLUME_DOWN -> DeviceController.adjustVolume(ctx, false)
            PhoneActionType.MUTE_MEDIA -> DeviceController.muteMedia(ctx)
            PhoneActionType.MEDIA_PLAY_PAUSE -> DeviceController.dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause")
            PhoneActionType.MEDIA_NEXT -> DeviceController.dispatchMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT, "Next Track")
            PhoneActionType.CALL_NUMBER -> DeviceController.placeCall(ctx, cmd.phoneNumber, directCall = true)
            PhoneActionType.SEND_SMS -> {
                _prefilledRecipient.value = cmd.phoneNumber
                _prefilledMessage.value = cmd.messageBody
                _inAppEvents.tryEmit(InAppCommandEvent.PrefillMessage(cmd.phoneNumber, cmd.messageBody))
                DeviceController.openSmsComposer(ctx, cmd.phoneNumber, cmd.messageBody)
            }
            PhoneActionType.SEND_WHATSAPP -> {
                _prefilledRecipient.value = cmd.phoneNumber
                _prefilledMessage.value = cmd.messageBody
                _inAppEvents.tryEmit(InAppCommandEvent.PrefillMessage(cmd.phoneNumber, cmd.messageBody))
                DeviceController.openWhatsAppMessage(ctx, cmd.phoneNumber, cmd.messageBody)
            }
            PhoneActionType.OPEN_APP -> DeviceController.openAppByName(ctx, cmd.appName)
            PhoneActionType.SET_ALARM -> DeviceController.setAlarm(ctx, cmd.alarmHour, cmd.alarmMinute)
            PhoneActionType.OPEN_WIFI_SETTINGS -> DeviceController.openWifiPanel(ctx)
            PhoneActionType.OPEN_BLUETOOTH_SETTINGS -> DeviceController.openSystemSettings(
                ctx,
                android.provider.Settings.ACTION_BLUETOOTH_SETTINGS,
                "Bluetooth Settings"
            )
            PhoneActionType.SET_CALL_MODE_QUIET -> {
                repo.updateCallMode(CallShieldMode.MEETING_QUIET)
                ControlExecutionResult.Success("PM AI Call Shield switched to Meeting Quiet Mode.", "CALL")
            }
            PhoneActionType.SET_CALL_MODE_SPAM_GUARD -> {
                repo.updateCallMode(CallShieldMode.SPAM_GUARD)
                ControlExecutionResult.Success("PM AI Call Shield switched to Spam & Robocall Guard.", "CALL")
            }
            PhoneActionType.OPEN_CONTROL_TAB -> {
                _inAppEvents.tryEmit(InAppCommandEvent.NavigateTo("control"))
                ControlExecutionResult.Success("Opened Phone Control Deck.", "OS_NAV")
            }
            PhoneActionType.OPEN_CALL_SHIELD_TAB -> {
                _inAppEvents.tryEmit(InAppCommandEvent.NavigateTo("calls"))
                ControlExecutionResult.Success("Opened PM AI Call Shield.", "CALL")
            }
            PhoneActionType.OPEN_AI_STUDIO_TAB -> {
                _inAppEvents.tryEmit(InAppCommandEvent.NavigateTo("studio"))
                ControlExecutionResult.Success("Opened PM AI Creative Studio.", "APP")
            }
            PhoneActionType.GENERATE_IMAGE -> {
                val imgPrompt = cmd.messageBody.ifBlank { rawPrompt }
                _inAppEvents.tryEmit(InAppCommandEvent.NavigateTo("studio"))
                generateStudioImage(imgPrompt)
                ControlExecutionResult.Success("Started generating image in AI Studio: \"$imgPrompt\"", "APP")
            }
            PhoneActionType.ENABLE_ALWAYS_ACTIVE -> {
                PMAlwaysActiveService.start(ctx)
                ControlExecutionResult.Success("PM AI Always-Active Engine enabled for Screen On & Off.", "HARDWARE")
            }
            PhoneActionType.DISABLE_ALWAYS_ACTIVE -> {
                PMAlwaysActiveService.stop(ctx)
                ControlExecutionResult.Success("PM AI Always-Active Engine stopped.", "HARDWARE")
            }
            PhoneActionType.ADD_CALL_VIP_RULE -> {
                val label = cmd.messageBody.ifBlank { "VIP Contact" }
                val num = cmd.phoneNumber
                if (num.isNotBlank()) {
                    repo.addCallRule(label, num, "ALLOW_VIP", "")
                    ControlExecutionResult.Success("Added VIP call rule for $label ($num).", "CALL")
                } else {
                    ControlExecutionResult.Error("Please include a phone number to add a VIP rule.", "CALL")
                }
            }
            PhoneActionType.ADD_CALL_BLOCK_RULE -> {
                val label = cmd.messageBody.ifBlank { "Blocked Caller" }
                val num = cmd.phoneNumber
                if (num.isNotBlank()) {
                    repo.addCallRule(label, num, "REJECT_WITH_SMS", callConfig.value.autoSmsTemplate)
                    ControlExecutionResult.Success("Added block rule for $num.", "CALL")
                } else {
                    ControlExecutionResult.Error("Please include a phone number or prefix to block.", "CALL")
                }
            }
            PhoneActionType.NONE, PhoneActionType.READ_SCREEN -> return false
        }

        val replyText = when (execResult) {
            is ControlExecutionResult.Success -> {
                repo.logAutomation(rawPrompt, execResult.category, "EXECUTED", execResult.message)
                if (cmd.spokenConfirmation.isNotBlank()) "${cmd.spokenConfirmation}\n(${execResult.message})" else execResult.message
            }
            is ControlExecutionResult.NeedsPermission -> {
                _pendingPermission.value = PendingPermissionPrompt(execResult.permission, execResult.rationale)
                repo.logAutomation(rawPrompt, execResult.category, "NEEDS_PERMISSION", execResult.rationale)
                execResult.rationale
            }
            is ControlExecutionResult.NeedsAccessibility -> {
                _showAccessibilityPrompt.value = true
                repo.logAutomation(rawPrompt, "SCROLL", "NEEDS_ACCESSIBILITY", execResult.rationale)
                execResult.rationale
            }
            is ControlExecutionResult.Error -> {
                repo.logAutomation(rawPrompt, execResult.category, "FAILED", execResult.message)
                execResult.message
            }
        }

        repo.saveMessage(
            role = "model",
            content = replyText,
            brainMode = mode.label,
            actionBadge = cmd.actionType.name
        )
        _chatStreamState.value = ChatStreamState.Idle
        if (fromVoice || _autoSpeakReplies.value) {
            voiceEngine.speak(replyText)
        }
        return true
    }

    // Direct Control Hub Actions
    fun triggerScroll(direction: ScrollDirection) {
        viewModelScope.launch {
            val res = executeScroll(direction)
            handleDirectControlResult("Scroll ${direction.name}", res)
        }
    }

    fun toggleAutoScroll(intervalMs: Long, direction: ScrollDirection = ScrollDirection.DOWN) {
        viewModelScope.launch {
            val currentlyActive = accessibilityStatus.value.isAutoScrolling
            val res = executeAutoScroll(!currentlyActive, direction, intervalMs)
            handleDirectControlResult(if (!currentlyActive) "Start Auto-Scroll" else "Stop Auto-Scroll", res)
        }
    }

    fun triggerOsAction(action: OsGlobalAction) {
        viewModelScope.launch {
            val res = executeOsAction(action)
            handleDirectControlResult("OS ${action.name}", res)
        }
    }

    fun toggleFlashlight() {
        val ctx = getApplication<Application>()
        val target = !_isTorchOn.value
        val res = DeviceController.setFlashlight(ctx, target)
        _isTorchOn.value = DeviceController.isTorchOn()
        viewModelScope.launch { handleDirectControlResult("Flashlight ${if (target) "ON" else "OFF"}", res) }
    }

    fun adjustVolume(raise: Boolean) {
        val res = DeviceController.adjustVolume(getApplication(), raise)
        viewModelScope.launch { handleDirectControlResult(if (raise) "Volume Up" else "Volume Down", res) }
    }

    fun muteMedia() {
        val res = DeviceController.muteMedia(getApplication())
        viewModelScope.launch { handleDirectControlResult("Mute Media", res) }
    }

    fun sendMediaKey(keyCode: Int, label: String) {
        val res = DeviceController.dispatchMediaKey(getApplication(), keyCode, label)
        viewModelScope.launch { handleDirectControlResult(label, res) }
    }

    fun launchApp(appName: String) {
        val res = DeviceController.openAppByName(getApplication(), appName)
        viewModelScope.launch { handleDirectControlResult("Open $appName", res) }
    }

    fun openAccessibilitySettings() {
        DeviceController.openAccessibilitySettings(getApplication())
    }

    fun toggleAlwaysActive(enable: Boolean) {
        val ctx = getApplication<Application>()
        if (enable) {
            PMAlwaysActiveService.start(ctx)
            _statusBanner.value = "PM AI Always-Active Engine started (Screen On & Off)."
        } else {
            PMAlwaysActiveService.stop(ctx)
            _statusBanner.value = "PM AI Always-Active Engine stopped."
        }
    }

    fun openBatteryOptimizationSettings() {
        val res = DeviceController.openBatteryOptimizationSettings(getApplication())
        if (res is ControlExecutionResult.Error) {
            _statusBanner.value = res.message
        }
    }

    fun dialOrCall(number: String, directCall: Boolean) {
        val res = DeviceController.placeCall(getApplication(), number, directCall)
        viewModelScope.launch { handleDirectControlResult("Call $number", res) }
    }

    fun sendDirectSms(number: String, message: String) {
        val res = DeviceController.sendSmsDirect(getApplication(), number, message)
        viewModelScope.launch { handleDirectControlResult("Direct SMS to $number", res) }
    }

    fun openSmsApp(number: String, message: String) {
        val res = DeviceController.openSmsComposer(getApplication(), number, message)
        viewModelScope.launch { handleDirectControlResult("SMS Composer", res) }
    }

    fun sendViaWhatsApp(number: String, message: String) {
        val res = DeviceController.openWhatsAppMessage(getApplication(), number, message)
        viewModelScope.launch { handleDirectControlResult("WhatsApp to $number", res) }
    }

    private suspend fun handleDirectControlResult(label: String, res: ControlExecutionResult) {
        when (res) {
            is ControlExecutionResult.Success -> {
                _statusBanner.value = res.message
                repo.logAutomation(label, res.category, "EXECUTED", res.message)
            }
            is ControlExecutionResult.NeedsPermission -> {
                _pendingPermission.value = PendingPermissionPrompt(res.permission, res.rationale)
                repo.logAutomation(label, res.category, "NEEDS_PERMISSION", res.rationale)
            }
            is ControlExecutionResult.NeedsAccessibility -> {
                _showAccessibilityPrompt.value = true
                repo.logAutomation(label, "SCROLL", "NEEDS_ACCESSIBILITY", res.rationale)
            }
            is ControlExecutionResult.Error -> {
                _statusBanner.value = res.message
                repo.logAutomation(label, res.category, "FAILED", res.message)
            }
        }
    }

    private fun executeScroll(direction: ScrollDirection): ControlExecutionResult {
        val ok = AccessibilityBridge.performScroll(direction)
        return if (ok) {
            ControlExecutionResult.Success("Scrolled screen ${direction.name.lowercase()}.", "SCROLL")
        } else {
            ControlExecutionResult.NeedsAccessibility(
                "Enable 'PM AI Phone Control' in Accessibility Settings to scroll any screen automatically."
            )
        }
    }

    private fun executeAutoScroll(start: Boolean, direction: ScrollDirection, intervalMs: Long): ControlExecutionResult {
        if (!start) {
            AccessibilityBridge.stopAutoScroll()
            return ControlExecutionResult.Success("Auto-scroll stopped.", "SCROLL")
        }
        val ok = AccessibilityBridge.startAutoScroll(direction, intervalMs)
        return if (ok) {
            ControlExecutionResult.Success("Auto-scroll running (${direction.name.lowercase()}).", "SCROLL")
        } else {
            ControlExecutionResult.NeedsAccessibility(
                "Enable 'PM AI Phone Control' in Accessibility Settings to run hands-free auto-scroll."
            )
        }
    }

    private fun executeOsAction(action: OsGlobalAction): ControlExecutionResult {
        val ok = AccessibilityBridge.performOsAction(action)
        return if (ok) {
            ControlExecutionResult.Success("Executed ${action.name.lowercase().replace('_', ' ')}.", "OS_NAV")
        } else {
            ControlExecutionResult.NeedsAccessibility(
                "Enable 'PM AI Phone Control' in Accessibility Settings for OS navigation & gestures."
            )
        }
    }

    // Call Shield Actions
    fun setCallShieldMode(mode: CallShieldMode) {
        repo.updateCallMode(mode)
        _statusBanner.value = "Call Shield set to ${mode.title}."
    }

    fun setAutoSmsEnabled(enabled: Boolean) {
        repo.updateAutoSmsEnabled(enabled)
    }

    fun setAutoSmsTemplate(template: String) {
        repo.updateAutoSmsTemplate(template)
    }

    fun addCallRule(label: String, phonePattern: String, action: String, customReply: String) {
        if (label.isBlank() || phonePattern.isBlank()) {
            _statusBanner.value = "Enter a rule name and phone number/pattern."
            return
        }
        viewModelScope.launch {
            repo.addCallRule(label, phonePattern, action, customReply)
            _statusBanner.value = "Call rule '$label' saved."
        }
    }

    fun toggleCallRule(rule: CallRuleEntity) {
        viewModelScope.launch { repo.toggleCallRule(rule) }
    }

    fun deleteCallRule(id: Long) {
        viewModelScope.launch { repo.deleteCallRule(id) }
    }

    fun clearScreenedCalls() {
        viewModelScope.launch { repo.clearScreenedCalls() }
    }

    fun clearChatHistory() {
        viewModelScope.launch { repo.clearChat() }
    }

    fun summarizeScreenedCalls() {
        val logs = screenedCalls.value
        if (logs.isEmpty()) {
            _callSummaryState.value = ToolUiState.Error(
                "No screened or missed calls recorded yet. Once PM AI screens incoming calls, tap here for an AI summary."
            )
            return
        }
        viewModelScope.launch {
            _callSummaryState.value = ToolUiState.Loading
            try {
                val formatter = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
                val digestInput = logs.take(25).joinToString("\n") { call ->
                    "- Caller: ${call.callerNumber} | Action: ${call.actionTaken} | Time: ${formatter.format(Date(call.timestamp))} | Reason: ${call.reason} | Auto-Reply: ${call.autoReplyPrepared ?: "None"}"
                }
                val summary = aiBrain.generateQuickToolOutput(
                    toolInstruction = "You are PM AI Call Assistant. Summarize these screened and missed calls clearly: " +
                        "(1) Total calls screened & blocked vs allowed, (2) Important callers to consider calling back, " +
                        "(3) Auto-replies dispatched, and (4) Recommended call-routing adjustment.",
                    inputText = digestInput
                )
                _callSummaryState.value = ToolUiState.Success(summary)
            } catch (e: TimeoutCancellationException) {
                _callSummaryState.value = ToolUiState.Error("Call summary timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Call summary failed", e)
                _callSummaryState.value = ToolUiState.Error(e.localizedMessage ?: "Failed to summarize calls.")
            }
        }
    }

    // AI Studio & Smart Tools
    fun generateAiDraft(instruction: String, onDraftReady: (String) -> Unit) {
        if (instruction.isBlank()) return
        viewModelScope.launch {
            _toolUiState.value = ToolUiState.Loading
            try {
                val result = aiBrain.generateQuickToolOutput(
                    toolInstruction = "You are PM AI Smart Composer. Write ONLY the final ready-to-send message/reply without quotes or explanations.",
                    inputText = instruction
                )
                _toolUiState.value = ToolUiState.Success(result)
                onDraftReady(result)
            } catch (e: TimeoutCancellationException) {
                _toolUiState.value = ToolUiState.Error("Request timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Draft generation failed", e)
                _toolUiState.value = ToolUiState.Error(e.localizedMessage ?: "Draft generation failed.")
            }
        }
    }

    fun runAiTool(toolInstruction: String, inputText: String) {
        if (inputText.isBlank()) {
            _toolUiState.value = ToolUiState.Error("Please enter text or a problem to solve.")
            return
        }
        viewModelScope.launch {
            _toolUiState.value = ToolUiState.Loading
            try {
                val output = aiBrain.generateQuickToolOutput(toolInstruction, inputText)
                _toolUiState.value = ToolUiState.Success(output)
            } catch (e: TimeoutCancellationException) {
                _toolUiState.value = ToolUiState.Error("AI tool timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI tool failed", e)
                _toolUiState.value = ToolUiState.Error(e.localizedMessage ?: "AI tool execution failed.")
            }
        }
    }

    fun generateStudioImage(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty()) {
            _imageGenState.value = ImageGenUiState.Error("Enter an image prompt first.")
            return
        }
        viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading
            try {
                val bitmap = aiBrain.generateStudioImage(clean, _studioReferenceBitmap.value)
                _imageGenState.value = ImageGenUiState.Success(bitmap, clean)
            } catch (e: TimeoutCancellationException) {
                _imageGenState.value = ImageGenUiState.Error("Image generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Studio image generation failed", e)
                _imageGenState.value = ImageGenUiState.Error(
                    e.localizedMessage ?: "Image generation failed."
                )
            }
        }
    }

    private suspend fun decodeScaledBitmap(uri: Uri, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val resolver = getApplication<Application>().contentResolver
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(resolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(resolver, uri)
            }
            val w = raw.width
            val h = raw.height
            if (w <= maxDim && h <= maxDim) {
                raw
            } else {
                val scale = maxDim.toFloat() / maxOf(w, h)
                Bitmap.createScaledBitmap(raw, (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from URI", e)
            null
        }
    }

    override fun onCleared() {
        voiceEngine.release()
        super.onCleared()
    }
}
