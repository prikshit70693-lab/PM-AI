package com.example.services

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

enum class ScrollDirection {
    DOWN, UP, LEFT, RIGHT
}

enum class OsGlobalAction {
    HOME, BACK, RECENTS, NOTIFICATIONS, QUICK_SETTINGS, SCREENSHOT
}

data class AccessibilityStatus(
    val isConnected: Boolean = false,
    val isAutoScrolling: Boolean = false,
    val autoScrollDirection: ScrollDirection = ScrollDirection.DOWN,
    val autoScrollIntervalMs: Long = 1600L,
    val lastActionFeedback: String = "Ready"
)

object AccessibilityBridge {
    private var serviceRef: WeakReference<PMControlAccessibilityService>? = null

    private val _status = MutableStateFlow(AccessibilityStatus())
    val status: StateFlow<AccessibilityStatus> = _status.asStateFlow()

    fun attach(service: PMControlAccessibilityService) {
        serviceRef = WeakReference(service)
        _status.value = _status.value.copy(
            isConnected = true,
            lastActionFeedback = "PM AI Control Service connected"
        )
    }

    fun detach() {
        serviceRef = null
        _status.value = _status.value.copy(
            isConnected = false,
            isAutoScrolling = false,
            lastActionFeedback = "Service disconnected"
        )
    }

    fun updateAutoScrollState(active: Boolean, direction: ScrollDirection, intervalMs: Long) {
        _status.value = _status.value.copy(
            isAutoScrolling = active,
            autoScrollDirection = direction,
            autoScrollIntervalMs = intervalMs
        )
    }

    fun updateFeedback(feedback: String) {
        _status.value = _status.value.copy(lastActionFeedback = feedback)
    }

    fun refreshConnectionFlag(context: Context): Boolean {
        val enabledInSettings = isAccessibilityServiceEnabled(context)
        val actuallyConnected = serviceRef?.get() != null
        val isActive = enabledInSettings || actuallyConnected
        _status.value = _status.value.copy(isConnected = isActive)
        return isActive
    }

    fun performScroll(direction: ScrollDirection): Boolean {
        val service = serviceRef?.get() ?: return false
        return service.performSwipeScroll(direction)
    }

    fun startAutoScroll(direction: ScrollDirection = ScrollDirection.DOWN, intervalMs: Long = 1600L): Boolean {
        val service = serviceRef?.get() ?: return false
        service.startAutoScrollLoop(direction, intervalMs)
        return true
    }

    fun stopAutoScroll(): Boolean {
        val service = serviceRef?.get()
        service?.stopAutoScrollLoop()
        _status.value = _status.value.copy(
            isAutoScrolling = false,
            lastActionFeedback = "Auto-scroll stopped"
        )
        return service != null
    }

    fun performOsAction(action: OsGlobalAction): Boolean {
        val service = serviceRef?.get() ?: return false
        return service.performOsGlobalAction(action)
    }

    fun readScreenText(): String? {
        return serviceRef?.get()?.extractVisibleScreenText()
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val expectedComponent = ComponentName(context, PMControlAccessibilityService::class.java)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val expectedShort = expectedComponent.flattenToShortString()
        val expectedFull = expectedComponent.flattenToString()
        return enabledServices.split(':').any {
            it.equals(expectedShort, ignoreCase = true) ||
                it.equals(expectedFull, ignoreCase = true)
        }
    }
}
