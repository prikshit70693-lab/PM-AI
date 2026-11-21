package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ai.AiBrainMode
import com.example.data.ChatMessageEntity
import com.example.services.ScrollDirection
import com.example.ui.components.GlassCard
import com.example.ui.components.PmAiVoiceOrbCard
import com.example.ui.components.StatusPill
import com.example.viewmodel.ChatStreamState
import com.example.viewmodel.InAppCommandEvent
import com.example.viewmodel.PmAiViewModel

private val QUICK_PROMPTS = listOf(
    "Scroll down" to "Scroll down karo",
    "Auto scroll" to "Auto scroll chalu karo",
    "Torch ON" to "Flashlight on karo",
    "Meeting Calls" to "Meeting quiet call mode on karo",
    "Solve Math" to "Solve step by step: 3x^2 - 12x + 9 = 0",
    "Hinglish Draft" to "Write a polite Hinglish message for my boss saying I will reach 15 mins late"
)

@Composable
fun AssistantScreen(
    viewModel: PmAiViewModel,
    onNavigateToControl: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val brainMode by viewModel.brainMode.collectAsStateWithLifecycle()
    val streamState by viewModel.chatStreamState.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val autoSpeak by viewModel.autoSpeakReplies.collectAsStateWithLifecycle()
    val attachedBitmap by viewModel.attachedVisionBitmap.collectAsStateWithLifecycle()
    val alwaysActive by viewModel.alwaysActiveStatus.collectAsStateWithLifecycle()

    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceCommand()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.attachVisionUri(it) }
    }

    LaunchedEffect(messages.size, streamState) {
        val total = messages.size + if (streamState is ChatStreamState.Streaming) 1 else 0
        if (total > 0) {
            listState.animateScrollToItem(total - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.inAppEvents.collect { event ->
            if (event is InAppCommandEvent.ScrollScreen) {
                val delta = if (event.direction == ScrollDirection.DOWN) 520f else -520f
                listState.animateScrollBy(delta)
            }
        }
    }

    val handleVoiceClick = {
        when {
            isListening -> viewModel.stopVoiceListening()
            isSpeaking -> viewModel.stopSpeaking()
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> viewModel.startVoiceCommand()
            else -> audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 16.dp)
    ) {
        // Top Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "PM AI Brain",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    StatusPill(
                        label = if (alwaysActive.isRunning) "ALWAYS ACTIVE" else "READY",
                        active = alwaysActive.isRunning
                    )
                }
                Text(
                    text = brainMode.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.toggleAutoSpeak() },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_auto_speak_button")
                ) {
                    Icon(
                        imageVector = if (autoSpeak) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                        contentDescription = if (autoSpeak) "Disable auto-speak" else "Enable auto-speak",
                        tint = if (autoSpeak) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (messages.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearChatHistory() },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_chat_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = "Clear chat history",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Brain Mode Selector Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AiBrainMode.entries) { mode ->
                val selected = mode == brainMode
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setBrainMode(mode) },
                    label = { Text(mode.label, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = if (selected) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("brain_mode_${mode.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactive PM AI Neural Voice Orb
        PmAiVoiceOrbCard(
            isListening = isListening,
            isThinking = streamState is ChatStreamState.Thinking,
            isSpeaking = isSpeaking,
            isAlwaysActive = alwaysActive.isRunning,
            partialTranscript = partialTranscript,
            onOrbClick = handleVoiceClick
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Quick Suggestion Commands
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(QUICK_PROMPTS) { (chipTitle, fullCommand) ->
                AssistChip(
                    onClick = { viewModel.sendUserPrompt(fullCommand) },
                    label = { Text(chipTitle, style = MaterialTheme.typography.labelLarge) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Chat Messages List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (messages.isEmpty() && streamState is ChatStreamState.Idle) {
                item {
                    WelcomeAssistantCard(onOpenControls = onNavigateToControl)
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    onSpeakClick = { viewModel.speakText(msg.content) }
                )
            }

            when (val st = streamState) {
                is ChatStreamState.Thinking -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 48.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "PM AI Brain is thinking...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                is ChatStreamState.Streaming -> {
                    item {
                        ChatBubble(
                            message = ChatMessageEntity(
                                role = "model",
                                content = st.partialText,
                                brainMode = brainMode.label
                            ),
                            onSpeakClick = {}
                        )
                    }
                }
                is ChatStreamState.Error -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "PM AI Error: ${st.message}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
                ChatStreamState.Idle -> Unit
            }
        }

        // Attached Vision Bitmap Preview
        attachedBitmap?.let { bmp ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Attached image for PM AI Vision",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Image attached for PM AI Vision analysis",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearVisionBitmap() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Remove attached image")
                    }
                }
            }
        }

        // Bottom Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("attach_image_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AddPhotoAlternate,
                    contentDescription = "Pick photo for PM AI Vision"
                )
            }

            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "Ask PM AI or command phone...",
                        maxLines = 1
                    )
                },
                singleLine = false,
                maxLines = 3,
                shape = RoundedCornerShape(24.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (inputText.isNotBlank() || attachedBitmap != null) {
                            viewModel.sendUserPrompt(inputText)
                            inputText = ""
                        }
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("assistant_prompt_input")
            )

            IconButton(
                onClick = handleVoiceClick,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("voice_mic_button")
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = if (isListening) "Stop voice input" else "Start voice input"
                )
            }

            IconButton(
                onClick = {
                    if (inputText.isNotBlank() || attachedBitmap != null) {
                        viewModel.sendUserPrompt(inputText)
                        inputText = ""
                    }
                },
                enabled = (inputText.isNotBlank() || attachedBitmap != null) &&
                    streamState !is ChatStreamState.Thinking &&
                    streamState !is ChatStreamState.Streaming,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("send_prompt_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "Send to PM AI"
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessageEntity,
    onSpeakClick: () -> Unit
) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isUser) 20.dp else 6.dp,
                bottomEnd = if (isUser) 6.dp else 20.dp
            ),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            },
            border = BorderStroke(
                1.dp,
                if (isUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            ),
            modifier = Modifier.widthIn(max = 330.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "PM AI • ${message.brainMode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            message.actionBadge?.let { badge ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        IconButton(
                            onClick = onSpeakClick,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "Read PM AI response aloud",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                } else if (message.hasImage) {
                    Text(
                        text = "[Photo attached for Vision Q&A]",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

@Composable
private fun WelcomeAssistantCard(onOpenControls: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Namaste! Main hoon PM AI.",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Main Gemini Brain se powered hoon aur aapka poora Android 16 phone control kar sakta hoon—screen scrolling, automatic call handling, messaging, voice talk, vision Q&A, aur AI image generation!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Try saying or typing:\n• \"Niche scroll karo\" / \"Auto scroll start karo\"\n• \"Flashlight on karo\" / \"Open YouTube\"\n• \"Send WhatsApp to 9876543210 I'm on the way\"\n• \"Solve this math or coding problem...\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            AssistChip(
                onClick = onOpenControls,
                label = { Text("Setup Always-Active & Phone Control") },
                modifier = Modifier.testTag("setup_phone_control_chip")
            )
        }
    }
}
