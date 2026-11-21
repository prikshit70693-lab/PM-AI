package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PMAlwaysActive"
private const val CHANNEL_ID = "pm_ai_always_active_channel"
private const val NOTIFICATION_ID = 1601

const val ACTION_START_ALWAYS_ACTIVE = "com.example.action.START_ALWAYS_ACTIVE"
const val ACTION_STOP_ALWAYS_ACTIVE = "com.example.action.STOP_ALWAYS_ACTIVE"
const val ACTION_NOTIFICATION_SCROLL_DOWN = "com.example.action.NOTIF_SCROLL_DOWN"

data class AlwaysActiveStatus(
    val isRunning: Boolean = false,
    val isScreenOn: Boolean = true,
    val wakeLockHeld: Boolean = false,
    val lastEventText: String = "Standby"
)

object AlwaysActiveBridge {
    private val _status = MutableStateFlow(AlwaysActiveStatus())
    val status: StateFlow<AlwaysActiveStatus> = _status.asStateFlow()

    fun update(
        isRunning: Boolean = _status.value.isRunning,
        isScreenOn: Boolean = _status.value.isScreenOn,
        wakeLockHeld: Boolean = _status.value.wakeLockHeld,
        lastEventText: String = _status.value.lastEventText
    ) {
        _status.value = AlwaysActiveStatus(
            isRunning = isRunning,
            isScreenOn = isScreenOn,
            wakeLockHeld = wakeLockHeld,
            lastEventText = lastEventText
        )
    }
}

class PMAlwaysActiveService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var screenReceiver: BroadcastReceiver? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenStateReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALWAYS_ACTIVE -> {
                stopGuardian()
                return START_NOT_STICKY
            }
            ACTION_NOTIFICATION_SCROLL_DOWN -> {
                AccessibilityBridge.performScroll(ScrollDirection.DOWN)
                return START_STICKY
            }
        }

        startForegroundGuardian()
        return START_STICKY
    }

    private fun startForegroundGuardian() {
        val notification = buildStatusNotification(
            subtitle = "PM AI Brain & Call Shield active (Screen On & Off)"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        acquirePartialWakeLock()
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val screenInteractive = pm.isInteractive
        AlwaysActiveBridge.update(
            isRunning = true,
            isScreenOn = screenInteractive,
            wakeLockHeld = wakeLock?.isHeld == true,
            lastEventText = "Always-Active Engine running"
        )
        Log.d(TAG, "PM AI Always-Active Service started.")
    }

    private fun acquirePartialWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PMAI::AlwaysActiveGuardianLock"
                ).apply {
                    setReferenceCounted(false)
                }
            }
            if (wakeLock?.isHeld == false) {
                // Acquire with 12-hour safety ceiling and re-acquire on service lifecycle
                wakeLock?.acquire(12 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire partial wake lock", e)
        }
    }

    private fun releasePartialWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
    }

    private fun registerScreenStateReceiver() {
        if (screenReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        AlwaysActiveBridge.update(
                            isScreenOn = false,
                            wakeLockHeld = wakeLock?.isHeld == true,
                            lastEventText = "Screen OFF • PM AI Guardian active"
                        )
                        refreshNotification("Screen OFF • PM AI & Call Shield listening")
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        AlwaysActiveBridge.update(
                            isScreenOn = true,
                            wakeLockHeld = wakeLock?.isHeld == true,
                            lastEventText = "Screen ON • PM AI Ready"
                        )
                        refreshNotification("Screen ON • PM AI Brain & Phone Control active")
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        AlwaysActiveBridge.update(
                            isScreenOn = true,
                            wakeLockHeld = wakeLock?.isHeld == true,
                            lastEventText = "Device Unlocked • PM AI Ready"
                        )
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun refreshNotification(subtitle: String) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildStatusNotification(subtitle))
    }

    private fun buildStatusNotification(subtitle: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            10,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val scrollIntent = Intent(this, PMAlwaysActiveService::class.java).apply {
            action = ACTION_NOTIFICATION_SCROLL_DOWN
        }
        val scrollPending = PendingIntent.getService(
            this,
            11,
            scrollIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PMAlwaysActiveService::class.java).apply {
            action = ACTION_STOP_ALWAYS_ACTIVE
        }
        val stopPending = PendingIntent.getService(
            this,
            12,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("PM AI Always Active")
            .setContentText(subtitle)
            .setOngoing(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openPending)
            .addAction(0, "Scroll", scrollPending)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PM AI Always-Active Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps PM AI Brain, Call Shield, and Screen On/Off controls active."
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun stopGuardian() {
        releasePartialWakeLock()
        AlwaysActiveBridge.update(
            isRunning = false,
            wakeLockHeld = false,
            lastEventText = "Stopped by user"
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        runCatching {
            screenReceiver?.let { unregisterReceiver(it) }
        }
        screenReceiver = null
        releasePartialWakeLock()
        AlwaysActiveBridge.update(
            isRunning = false,
            wakeLockHeld = false,
            lastEventText = "Service stopped"
        )
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, PMAlwaysActiveService::class.java).apply {
                action = ACTION_START_ALWAYS_ACTIVE
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PMAlwaysActiveService::class.java).apply {
                action = ACTION_STOP_ALWAYS_ACTIVE
            }
            context.startService(intent)
        }
    }
}
