package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ShieldMoon
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.services.OsGlobalAction
import com.example.services.ScrollDirection
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.MintSuccess
import com.example.viewmodel.InAppCommandEvent
import com.example.viewmodel.PmAiViewModel
import com.example.viewmodel.ToolUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneControlScreen(
    viewModel: PmAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val accessibilityStatus by viewModel.accessibilityStatus.collectAsStateWithLifecycle()
    val alwaysActiveStatus by viewModel.alwaysActiveStatus.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val toolState by viewModel.toolUiState.collectAsStateWithLifecycle()
    val prefilledRecipient by viewModel.prefilledRecipient.collectAsStateWithLifecycle()
    val prefilledMessage by viewModel.prefilledMessage.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    var scrollSpeedMs by remember { mutableFloatStateOf(1600f) }
    var recipientNumber by rememberSaveable { mutableStateOf("") }
    var messageDraft by rememberSaveable { mutableStateOf("") }
    var selectedTone by rememberSaveable { mutableStateOf("Hinglish") }

    LaunchedEffect(prefilledRecipient, prefilledMessage) {
        if (prefilledRecipient.isNotBlank()) recipientNumber = prefilledRecipient
        if (prefilledMessage.isNotBlank()) messageDraft = prefilledMessage
    }

    LaunchedEffect(Unit) {
        viewModel.inAppEvents.collect { event ->
            if (event is InAppCommandEvent.ScrollScreen) {
                val delta = if (event.direction == ScrollDirection.DOWN) 520f else -520f
                listState.animateScrollBy(delta)
            }
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start foreground guardian regardless; if permission granted, lock-screen controls are visible
        viewModel.toggleAlwaysActive(true)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Hero Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_phone_control_banner),
                    contentDescription = "PM AI Phone Control Engine",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xE6070B14),
                                    Color(0x99070B14),
                                    Color(0x44070B14)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        StatusPill(
                            label = if (accessibilityStatus.isConnected) "CONTROL ACTIVE" else "SETUP NEEDED",
                            active = accessibilityStatus.isConnected
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Phone Control Deck",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White
                        )
                        Text(
                            text = "Screen On & Off Guardian • Auto-Scroll • Messaging",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // 1. Always-Active Engine (Screen ON & OFF)
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (alwaysActiveStatus.isRunning) {
                    MintSuccess.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ShieldMoon,
                                contentDescription = null,
                                tint = if (alwaysActiveStatus.isRunning) MintSuccess else MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Always-Active Engine",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Keeps PM AI active whether screen is ON or OFF",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = alwaysActiveStatus.isRunning,
                            onCheckedChange = { enable ->
                                if (enable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        return@Switch
                                    }
                                }
                                viewModel.toggleAlwaysActive(enable)
                            },
                            modifier = Modifier.testTag("always_active_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusPill(
                            label = if (alwaysActiveStatus.isScreenOn) "SCREEN ON" else "SCREEN OFF",
                            active = alwaysActiveStatus.isRunning,
                            activeColor = CyanPulse
                        )
                        StatusPill(
                            label = if (alwaysActiveStatus.wakeLockHeld) "CPU WAKE READY" else "NORMAL POWER",
                            active = alwaysActiveStatus.wakeLockHeld
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = alwaysActiveStatus.lastEventText,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = { viewModel.openBatteryOptimizationSettings() },
                            modifier = Modifier.testTag("battery_optimization_button")
                        ) {
                            Icon(
                                Icons.Rounded.BatteryChargingFull,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Unrestricted")
                        }
                    }
                }
            }
        }

        // 2. Accessibility Service & Real Screen Scrolling
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AccessibilityNew,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "Screen Scroll & OS Gesture Engine",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = accessibilityStatus.lastActionFeedback,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = { viewModel.openAccessibilitySettings() },
                            modifier = Modifier.testTag("enable_accessibility_button")
                        ) {
                            Text(if (accessibilityStatus.isConnected) "Active" else "Enable")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Directional Pad
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ControlActionTile(
                            icon = Icons.Rounded.KeyboardArrowUp,
                            label = "Scroll Up",
                            onClick = { viewModel.triggerScroll(ScrollDirection.UP) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scroll_up_button")
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.KeyboardArrowDown,
                            label = "Scroll Down",
                            onClick = { viewModel.triggerScroll(ScrollDirection.DOWN) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scroll_down_button")
                        )
                        ControlActionTile(
                            icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            label = "Swipe Left",
                            onClick = { viewModel.triggerScroll(ScrollDirection.LEFT) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scroll_left_button")
                        )
                        ControlActionTile(
                            icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            label = "Swipe Right",
                            onClick = { viewModel.triggerScroll(ScrollDirection.RIGHT) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("scroll_right_button")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auto-Scroll Toggle + Speed Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Hands-Free Auto-Scroll",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Interval: ${(scrollSpeedMs / 1000f).let { String.format("%.1fs", it) }}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                viewModel.toggleAutoScroll(scrollSpeedMs.toLong(), ScrollDirection.DOWN)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (accessibilityStatus.isAutoScrolling) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ),
                            modifier = Modifier.testTag("auto_scroll_toggle_button")
                        ) {
                            Icon(
                                imageVector = if (accessibilityStatus.isAutoScrolling) Icons.Rounded.Stop else Icons.Rounded.SwapVert,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (accessibilityStatus.isAutoScrolling) "Stop Scroll" else "Auto-Scroll")
                        }
                    }

                    Slider(
                        value = scrollSpeedMs,
                        onValueChange = { scrollSpeedMs = it },
                        valueRange = 800f..3500f
                    )
                }
            }
        }

        // 3. Hardware & OS Navigation Controls
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Hardware & OS Navigation",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        maxItemsInEachRow = 3
                    ) {
                        ControlActionTile(
                            icon = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                            label = if (isTorchOn) "Torch ON" else "Torch OFF",
                            highlighted = isTorchOn,
                            onClick = { viewModel.toggleFlashlight() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("flashlight_button")
                        )
                        ControlActionTile(
                            icon = Icons.AutoMirrored.Rounded.VolumeUp,
                            label = "Vol +",
                            onClick = { viewModel.adjustVolume(true) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_up_button")
                        )
                        ControlActionTile(
                            icon = Icons.AutoMirrored.Rounded.VolumeDown,
                            label = "Vol -",
                            onClick = { viewModel.adjustVolume(false) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("volume_down_button")
                        )
                        ControlActionTile(
                            icon = Icons.AutoMirrored.Rounded.VolumeOff,
                            label = "Mute",
                            onClick = { viewModel.muteMedia() },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.PlayArrow,
                            label = "Play/Pause",
                            onClick = { viewModel.sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "Play/Pause") },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.SkipNext,
                            label = "Next Track",
                            onClick = { viewModel.sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, "Next Track") },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.Home,
                            label = "Home",
                            onClick = { viewModel.triggerOsAction(OsGlobalAction.HOME) },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.AutoMirrored.Rounded.ArrowBack,
                            label = "Back",
                            onClick = { viewModel.triggerOsAction(OsGlobalAction.BACK) },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.Apps,
                            label = "Recents",
                            onClick = { viewModel.triggerOsAction(OsGlobalAction.RECENTS) },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.Notifications,
                            label = "Notifs",
                            onClick = { viewModel.triggerOsAction(OsGlobalAction.NOTIFICATIONS) },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.Tune,
                            label = "Quick Panel",
                            onClick = { viewModel.triggerOsAction(OsGlobalAction.QUICK_SETTINGS) },
                            modifier = Modifier.weight(1f)
                        )
                        ControlActionTile(
                            icon = Icons.Rounded.Screenshot,
                            label = "Screenshot",
                            onClick = { viewModel.triggerOsAction(OsGlobalAction.SCREENSHOT) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Smart Messaging Studio (AI Draft + Direct SMS + WhatsApp)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Message,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "PM AI Smart Messaging",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = recipientNumber,
                        onValueChange = { recipientNumber = it },
                        label = { Text("Phone number (optional for WhatsApp share)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_recipient_input")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Hinglish", "English", "Hindi", "Formal").forEach { tone ->
                            FilterChip(
                                selected = selectedTone == tone,
                                onClick = { selectedTone = tone },
                                label = { Text(tone) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = messageDraft,
                        onValueChange = { messageDraft = it },
                        label = { Text("Message or topic (tap Write with PM AI)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("message_body_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val topic = messageDraft.ifBlank { "I am driving right now, will call you back in 20 minutes" }
                                viewModel.generateAiDraft(
                                    "Write a $selectedTone message for: $topic"
                                ) { drafted ->
                                    messageDraft = drafted
                                }
                            },
                            enabled = toolState !is ToolUiState.Loading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_draft_message_button")
                        ) {
                            if (toolState is ToolUiState.Loading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(6.dp))
                            Text("PM AI Draft")
                        }

                        Button(
                            onClick = { viewModel.sendDirectSms(recipientNumber, messageDraft) },
                            enabled = recipientNumber.isNotBlank() && messageDraft.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_direct_sms_button")
                        ) {
                            Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Send SMS")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.openSmsApp(recipientNumber, messageDraft) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Open SMS App")
                        }
                        OutlinedButton(
                            onClick = { viewModel.sendViaWhatsApp(recipientNumber, messageDraft) },
                            enabled = messageDraft.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("send_whatsapp_button")
                        ) {
                            Text("WhatsApp")
                        }
                    }
                }
            }
        }

        // 5. Quick App Launcher
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick App Launcher",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Camera", "YouTube", "WhatsApp", "Chrome", "Maps", "Settings").forEach { app ->
                            FilledTonalButton(
                                onClick = { viewModel.launchApp(app) },
                                modifier = Modifier.testTag("launch_${app.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = if (app == "Settings") Icons.Rounded.Settings else Icons.Rounded.Apps,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(app)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
        },
        border = BorderStroke(
            1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        }
    }
}
