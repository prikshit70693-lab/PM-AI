package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PhoneCallback
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.CallRuleEntity
import com.example.data.CallShieldMode
import com.example.services.DeviceController
import com.example.ui.components.GlassCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.MintSuccess
import com.example.viewmodel.PmAiViewModel
import com.example.viewmodel.ToolUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CallShieldScreen(
    viewModel: PmAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRoleHeld by viewModel.isCallRoleHeld.collectAsStateWithLifecycle()
    val callConfig by viewModel.callConfig.collectAsStateWithLifecycle()
    val callRules by viewModel.callRules.collectAsStateWithLifecycle()
    val screenedCalls by viewModel.screenedCalls.collectAsStateWithLifecycle()
    val automationLogs by viewModel.automationLogs.collectAsStateWithLifecycle()
    val callSummaryState by viewModel.callSummaryState.collectAsStateWithLifecycle()

    var quickDialNumber by rememberSaveable { mutableStateOf("") }
    var ruleLabel by rememberSaveable { mutableStateOf("") }
    var rulePattern by rememberSaveable { mutableStateOf("") }
    var ruleAction by rememberSaveable { mutableStateOf("ALLOW_VIP") }
    var busyContext by rememberSaveable { mutableStateOf("") }

    val roleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshSystemStates()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Android 16 Call Screening Role Status Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                borderColor = if (isRoleHeld) MintSuccess.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            ) {
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
                                Icons.Rounded.Security,
                                contentDescription = null,
                                tint = if (isRoleHeld) MintSuccess else MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "PM AI Automatic Call Shield",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Screens spam, unknown, and VIP calls even when screen is off",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        StatusPill(
                            label = if (isRoleHeld) "ACTIVE" else "INACTIVE",
                            active = isRoleHeld
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val roleIntent = DeviceController.createCallScreeningRoleIntent(context)
                            if (roleIntent != null) {
                                roleLauncher.launch(roleIntent)
                            }
                        },
                        enabled = !isRoleHeld,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activate_call_shield_button")
                    ) {
                        Text(
                            if (isRoleHeld) "PM AI is Default Call Screener"
                            else "Set PM AI as Call Screening App"
                        )
                    }
                }
            }
        }

        // 2. Quick Dial & Direct Call
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Call & Dialer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = quickDialNumber,
                        onValueChange = { quickDialNumber = it },
                        label = { Text("Enter phone number") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_number_input")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.dialOrCall(quickDialNumber, directCall = false) },
                            enabled = quickDialNumber.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.PhoneCallback, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Open Dialer")
                        }
                        Button(
                            onClick = { viewModel.dialOrCall(quickDialNumber, directCall = true) },
                            enabled = quickDialNumber.isNotBlank(),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("direct_call_button")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Direct Call")
                        }
                    }
                }
            }
        }

        // 3. Call Handling Mode Selector
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Automatic Call Handling Mode",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    CallShieldMode.entries.forEach { mode ->
                        val selected = callConfig.mode == mode
                        Surface(
                            onClick = { viewModel.setCallShieldMode(mode) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("call_mode_${mode.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { viewModel.setCallShieldMode(mode) }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = mode.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. AI Smart Auto-Reply SMS
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "AI Busy Auto-Reply SMS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Auto-send SMS when PM AI rejects a call in Quiet/VIP mode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = callConfig.autoSmsReplyEnabled,
                            onCheckedChange = { viewModel.setAutoSmsEnabled(it) }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = busyContext,
                        onValueChange = { busyContext = it },
                        label = { Text("Situation (e.g., In meeting till 5 PM)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FilledTonalButton(
                        onClick = {
                            val prompt = busyContext.ifBlank { "I am in an important meeting, call back in 30 mins" }
                            viewModel.generateAiDraft(
                                "Write a short, polite bilingual Hinglish/English auto-reply SMS on behalf of PM AI for: $prompt"
                            ) { generated ->
                                viewModel.setAutoSmsTemplate(generated)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Generate Auto-Reply with PM AI")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = callConfig.autoSmsTemplate,
                        onValueChange = { viewModel.setAutoSmsTemplate(it) },
                        label = { Text("Active Auto-Reply SMS Template") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 5. Add VIP / Block Rule
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "VIP & Call Block Rules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ruleLabel,
                        onValueChange = { ruleLabel = it },
                        label = { Text("Rule Name (e.g., Mom, Telemarketer)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_rule_name_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rulePattern,
                        onValueChange = { rulePattern = it },
                        label = { Text("Phone number or prefix (e.g., 140, +9198...)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("call_rule_pattern_input")
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "ALLOW_VIP" to "Allow VIP",
                            "BLOCK_SILENT" to "Silence",
                            "REJECT_WITH_SMS" to "Reject + SMS"
                        ).forEach { (key, title) ->
                            FilterChip(
                                selected = ruleAction == key,
                                onClick = { ruleAction = key },
                                label = { Text(title) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.addCallRule(ruleLabel, rulePattern, ruleAction, callConfig.autoSmsTemplate)
                            ruleLabel = ""
                            rulePattern = ""
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_call_rule_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Call Rule")
                    }

                    if (callRules.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        callRules.forEach { rule ->
                            CallRuleRow(
                                rule = rule,
                                onToggle = { viewModel.toggleCallRule(rule) },
                                onDelete = { viewModel.deleteCallRule(rule.id) }
                            )
                        }
                    }
                }
            }
        }

        // 6. AI Missed & Screened Call Summary
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.Summarize,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    text = "AI Missed & Screened Call Summary",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Digest blocked spam, missed callers & suggested callbacks",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = { viewModel.summarizeScreenedCalls() },
                        enabled = callSummaryState !is ToolUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("summarize_calls_button")
                    ) {
                        if (callSummaryState is ToolUiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Summarizing Calls...")
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Summarize Screened & Missed Calls")
                        }
                    }

                    when (val st = callSummaryState) {
                        is ToolUiState.Success -> {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = st.output,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        is ToolUiState.Error -> {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = st.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }

        // 7. Screened Calls & Action Logs
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Screened Calls & Action History",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (screenedCalls.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearScreenedCalls() }) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear call logs")
                    }
                }
            }
        }

        if (screenedCalls.isEmpty() && automationLogs.isEmpty()) {
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "No screened calls or phone control actions logged yet. Enable PM AI Call Shield or run a phone command to see live logs here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(screenedCalls, key = { "call_${it.id}" }) { callLog ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(callLog.callerNumber, fontWeight = FontWeight.Bold)
                            Text(
                                text = callLog.actionTaken,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Text(callLog.reason, style = MaterialTheme.typography.bodyMedium)
                        callLog.autoReplyPrepared?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(automationLogs.take(15), key = { "auto_${it.id}" }) { log ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = log.commandText,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "${log.category} • ${log.detail}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = formatShortTime(log.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CallRuleRow(
    rule: CallRuleEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(rule.label, style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${rule.phonePattern} • ${rule.ruleAction}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = rule.isEnabled, onCheckedChange = { onToggle() })
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete call rule")
            }
        }
    }
}

private fun formatShortTime(ts: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
}
