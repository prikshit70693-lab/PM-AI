package com.example.services

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.telephony.SmsManager
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat

private const val TAG = "DeviceController"

sealed interface ControlExecutionResult {
    data class Success(val message: String, val category: String) : ControlExecutionResult
    data class NeedsPermission(val permission: String, val rationale: String, val category: String) : ControlExecutionResult
    data class NeedsAccessibility(val rationale: String) : ControlExecutionResult
    data class Error(val message: String, val category: String) : ControlExecutionResult
}

object DeviceController {

    private var torchEnabled = false

    fun isTorchOn(): Boolean = torchEnabled

    fun setFlashlight(context: Context, enable: Boolean): ControlExecutionResult {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (cameraId == null) {
                ControlExecutionResult.Error("No camera flash hardware found on this device.", "HARDWARE")
            } else {
                cameraManager.setTorchMode(cameraId, enable)
                torchEnabled = enable
                ControlExecutionResult.Success(
                    message = if (enable) "Flashlight turned ON." else "Flashlight turned OFF.",
                    category = "HARDWARE"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Flashlight error", e)
            ControlExecutionResult.Error("Could not toggle flashlight: ${e.localizedMessage}", "HARDWARE")
        }
    }

    fun adjustVolume(context: Context, raise: Boolean): ControlExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                direction,
                AudioManager.FLAG_SHOW_UI
            )
            ControlExecutionResult.Success(
                message = if (raise) "Media volume increased." else "Media volume decreased.",
                category = "HARDWARE"
            )
        } catch (e: Exception) {
            ControlExecutionResult.Error("Volume adjustment failed: ${e.localizedMessage}", "HARDWARE")
        }
    }

    fun muteMedia(context: Context): ControlExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                AudioManager.ADJUST_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            ControlExecutionResult.Success("Media volume muted.", "HARDWARE")
        } catch (e: Exception) {
            ControlExecutionResult.Error("Could not mute volume: ${e.localizedMessage}", "HARDWARE")
        }
    }

    fun dispatchMediaKey(context: Context, keyCode: Int, label: String): ControlExecutionResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            ControlExecutionResult.Success("Media command executed: $label", "HARDWARE")
        } catch (e: Exception) {
            ControlExecutionResult.Error("Media control failed: ${e.localizedMessage}", "HARDWARE")
        }
    }

    fun placeCall(context: Context, phoneNumber: String, directCall: Boolean = false): ControlExecutionResult {
        val clean = phoneNumber.trim()
        if (clean.isEmpty()) {
            return ControlExecutionResult.Error("Please provide a phone number to call.", "CALL")
        }
        return try {
            val hasCallPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED

            if (directCall && !hasCallPermission) {
                return ControlExecutionResult.NeedsPermission(
                    permission = Manifest.permission.CALL_PHONE,
                    rationale = "Grant Phone Call permission to place calls directly, or use Dialer.",
                    category = "CALL"
                )
            }

            val action = if (directCall && hasCallPermission) Intent.ACTION_CALL else Intent.ACTION_DIAL
            val intent = Intent(action, Uri.parse("tel:${Uri.encode(clean)}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ControlExecutionResult.Success(
                message = if (action == Intent.ACTION_CALL) "Calling $clean now..." else "Opened dialer for $clean.",
                category = "CALL"
            )
        } catch (e: Exception) {
            ControlExecutionResult.Error("Could not start call: ${e.localizedMessage}", "CALL")
        }
    }

    fun sendSmsDirect(context: Context, phoneNumber: String, body: String): ControlExecutionResult {
        val clean = phoneNumber.trim()
        val text = body.trim()
        if (clean.isEmpty() || text.isEmpty()) {
            return ControlExecutionResult.Error("Recipient number and message text are required.", "MESSAGE")
        }
        val hasSmsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasSmsPermission) {
            return ControlExecutionResult.NeedsPermission(
                permission = Manifest.permission.SEND_SMS,
                rationale = "Grant SEND_SMS permission so PM AI can send SMS directly, or tap 'Open SMS App'.",
                category = "MESSAGE"
            )
        }

        return try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            if (smsManager == null) {
                ControlExecutionResult.Error("SMS service unavailable on this device.", "MESSAGE")
            } else {
                val parts = smsManager.divideMessage(text)
                if (parts.size > 1) {
                    smsManager.sendMultipartTextMessage(clean, null, parts, null, null)
                } else {
                    smsManager.sendTextMessage(clean, null, text, null, null)
                }
                ControlExecutionResult.Success("SMS sent directly to $clean.", "MESSAGE")
            }
        } catch (e: Exception) {
            ControlExecutionResult.Error("SMS send failed: ${e.localizedMessage}", "MESSAGE")
        }
    }

    fun openSmsComposer(context: Context, phoneNumber: String, body: String): ControlExecutionResult {
        return try {
            val uri = Uri.parse("smsto:${Uri.encode(phoneNumber.trim())}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ControlExecutionResult.Success("Opened Messaging app with your PM AI draft.", "MESSAGE")
        } catch (e: Exception) {
            ControlExecutionResult.Error("No SMS app available: ${e.localizedMessage}", "MESSAGE")
        }
    }

    fun openWhatsAppMessage(context: Context, phoneNumber: String, body: String): ControlExecutionResult {
        return try {
            val digits = phoneNumber.filter { it.isDigit() }
            if (digits.isNotEmpty()) {
                val url = "https://wa.me/$digits?text=${Uri.encode(body)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ControlExecutionResult.Success("Opened WhatsApp chat for $phoneNumber.", "MESSAGE")
            } else {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage("com.whatsapp")
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(shareIntent)
                ControlExecutionResult.Success("Opened WhatsApp message picker.", "MESSAGE")
            }
        } catch (e: Exception) {
            // Fallback to system share sheet if WhatsApp is not installed
            try {
                val fallback = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, body)
                }
                val chooser = Intent.createChooser(fallback, "Send via").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
                ControlExecutionResult.Success("WhatsApp not found; opened system share sheet.", "MESSAGE")
            } catch (ex: Exception) {
                ControlExecutionResult.Error("Could not share message: ${ex.localizedMessage}", "MESSAGE")
            }
        }
    }

    fun openAppByName(context: Context, appQuery: String): ControlExecutionResult {
        val query = appQuery.trim().lowercase()
        val pm = context.packageManager

        val knownPackages = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "chrome" to "com.android.chrome",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "settings" to "com.android.settings",
            "camera" to "com.android.camera2",
            "calculator" to "com.google.android.calculator"
        )

        if (query.contains("setting")) {
            return openSystemSettings(context, Settings.ACTION_SETTINGS, "Settings")
        }
        if (query.contains("wifi") || query.contains("wi-fi")) {
            return openWifiPanel(context)
        }
        if (query.contains("bluetooth")) {
            return openSystemSettings(context, Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth Settings")
        }
        if (query.contains("camera")) {
            return try {
                val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                ControlExecutionResult.Success("Opened Camera.", "APP")
            } catch (e: Exception) {
                ControlExecutionResult.Error("Camera app unavailable.", "APP")
            }
        }

        val mappedPkg = knownPackages.entries.firstOrNull { query.contains(it.key) }?.value
        if (mappedPkg != null) {
            val launchIntent = pm.getLaunchIntentForPackage(mappedPkg)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return ControlExecutionResult.Success("Launched ${appQuery.trim()}.", "APP")
            }
        }

        // Dynamic search across launcher activities declared in <queries>
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(mainIntent, 0)
        val matched = apps.firstOrNull { info ->
            val label = info.loadLabel(pm).toString().lowercase()
            label.contains(query) || info.activityInfo.packageName.lowercase().contains(query)
        }

        if (matched != null) {
            val launch = pm.getLaunchIntentForPackage(matched.activityInfo.packageName)
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launch)
                return ControlExecutionResult.Success("Launched ${matched.loadLabel(pm)}.", "APP")
            }
        }

        return ControlExecutionResult.Error("App '$appQuery' is not installed on this device.", "APP")
    }

    fun setAlarm(context: Context, hour: Int, minute: Int, label: String = "PM AI Alarm"): ControlExecutionResult {
        return try {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour.coerceIn(0, 23))
                putExtra(AlarmClock.EXTRA_MINUTES, minute.coerceIn(0, 59))
                putExtra(AlarmClock.EXTRA_MESSAGE, label)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            ControlExecutionResult.Success(
                String.format("Alarm intent opened for %02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59)),
                "APP"
            )
        } catch (e: Exception) {
            ControlExecutionResult.Error("Could not set alarm: ${e.localizedMessage}", "APP")
        }
    }

    fun openWifiPanel(context: Context): ControlExecutionResult {
        return try {
            val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Settings.Panel.ACTION_WIFI
            } else {
                Settings.ACTION_WIFI_SETTINGS
            }
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ControlExecutionResult.Success("Opened Wi-Fi control panel.", "HARDWARE")
        } catch (e: Exception) {
            openSystemSettings(context, Settings.ACTION_WIFI_SETTINGS, "Wi-Fi Settings")
        }
    }

    fun openSystemSettings(context: Context, action: String, label: String): ControlExecutionResult {
        return try {
            val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ControlExecutionResult.Success("Opened $label.", "HARDWARE")
        } catch (e: Exception) {
            ControlExecutionResult.Error("Unable to open $label.", "HARDWARE")
        }
    }

    fun openAccessibilitySettings(context: Context): ControlExecutionResult {
        return openSystemSettings(context, Settings.ACTION_ACCESSIBILITY_SETTINGS, "Accessibility Settings")
    }

    fun isCallScreeningRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return false
        return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    fun createCallScreeningRoleIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val roleManager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
        return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
    }

    fun openBatteryOptimizationSettings(context: Context): ControlExecutionResult {
        return openSystemSettings(
            context,
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            "Battery Optimization Settings"
        )
    }
}
