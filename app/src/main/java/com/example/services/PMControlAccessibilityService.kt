package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "PMControlAccessibility"

class PMControlAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var autoScrollJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "PM AI Control AccessibilityService connected.")
        AccessibilityBridge.attach(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Lightweight passive observation; gestures & reads are triggered explicitly by user or PM AI.
    }

    override fun onInterrupt() {
        stopAutoScrollLoop()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        stopAutoScrollLoop()
        AccessibilityBridge.detach()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stopAutoScrollLoop()
        serviceScope.cancel()
        AccessibilityBridge.detach()
        super.onDestroy()
    }

    fun performSwipeScroll(direction: ScrollDirection): Boolean {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat().coerceAtLeast(100f)
        val height = metrics.heightPixels.toFloat().coerceAtLeast(100f)

        val path = Path()
        when (direction) {
            ScrollDirection.DOWN -> {
                // Swipe finger upward from 72% height to 28% height to scroll content down
                path.moveTo(width * 0.5f, height * 0.72f)
                path.lineTo(width * 0.5f, height * 0.28f)
            }
            ScrollDirection.UP -> {
                // Swipe finger downward from 28% height to 72% height to scroll content up
                path.moveTo(width * 0.5f, height * 0.28f)
                path.lineTo(width * 0.5f, height * 0.72f)
            }
            ScrollDirection.RIGHT -> {
                path.moveTo(width * 0.80f, height * 0.5f)
                path.lineTo(width * 0.20f, height * 0.5f)
            }
            ScrollDirection.LEFT -> {
                path.moveTo(width * 0.20f, height * 0.5f)
                path.lineTo(width * 0.80f, height * 0.5f)
            }
        }

        val stroke = GestureDescription.StrokeDescription(path, 0L, 320L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    AccessibilityBridge.updateFeedback("Scrolled ${direction.name.lowercase()}")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    AccessibilityBridge.updateFeedback("Scroll gesture interrupted")
                }
            },
            null
        )
        return dispatched
    }

    fun startAutoScrollLoop(direction: ScrollDirection, intervalMs: Long) {
        stopAutoScrollLoop()
        val safeInterval = intervalMs.coerceIn(700L, 5000L)
        AccessibilityBridge.updateAutoScrollState(true, direction, safeInterval)
        AccessibilityBridge.updateFeedback("Auto-scroll started (${direction.name.lowercase()})")
        autoScrollJob = serviceScope.launch {
            while (isActive) {
                performSwipeScroll(direction)
                delay(safeInterval)
            }
        }
    }

    fun stopAutoScrollLoop() {
        autoScrollJob?.cancel()
        autoScrollJob = null
        AccessibilityBridge.updateAutoScrollState(
            active = false,
            direction = AccessibilityBridge.status.value.autoScrollDirection,
            intervalMs = AccessibilityBridge.status.value.autoScrollIntervalMs
        )
    }

    fun performOsGlobalAction(action: OsGlobalAction): Boolean {
        val globalId = when (action) {
            OsGlobalAction.HOME -> GLOBAL_ACTION_HOME
            OsGlobalAction.BACK -> GLOBAL_ACTION_BACK
            OsGlobalAction.RECENTS -> GLOBAL_ACTION_RECENTS
            OsGlobalAction.NOTIFICATIONS -> GLOBAL_ACTION_NOTIFICATIONS
            OsGlobalAction.QUICK_SETTINGS -> GLOBAL_ACTION_QUICK_SETTINGS
            OsGlobalAction.SCREENSHOT -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    GLOBAL_ACTION_TAKE_SCREENSHOT
                } else {
                    return false
                }
            }
        }
        val ok = performGlobalAction(globalId)
        if (ok) {
            AccessibilityBridge.updateFeedback("Executed OS action: ${action.name}")
        }
        return ok
    }

    fun extractVisibleScreenText(): String? {
        val root = rootInActiveWindow ?: return null
        val collected = StringBuilder()
        collectNodeText(root, collected, maxChars = 1400)
        return collected.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun collectNodeText(node: AccessibilityNodeInfo?, sb: StringBuilder, maxChars: Int) {
        if (node == null || sb.length >= maxChars) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val snippet = when {
            !text.isNullOrEmpty() -> text
            !desc.isNullOrEmpty() -> desc
            else -> null
        }
        if (snippet != null) {
            if (sb.isNotEmpty()) sb.append(" | ")
            sb.append(snippet)
        }
        for (i in 0 until node.childCount) {
            if (sb.length >= maxChars) break
            collectNodeText(node.getChild(i), sb, maxChars)
        }
    }
}
