package com.example.ui.screens

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Summarize
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.GlassCard
import com.example.ui.components.ShimmerBox
import com.example.viewmodel.ImageGenUiState
import com.example.viewmodel.PmAiViewModel
import com.example.viewmodel.ToolUiState

private enum class QuickToolType(
    val title: String,
    val icon: ImageVector,
    val hint: String,
    val instruction: String
) {
    SOLVE(
        "Problem Solver",
        Icons.Rounded.Psychology,
        "Enter any math, science, or life problem...",
        "You are PM AI Problem Solver. Solve the user's problem clearly step by step."
    ),
    TRANSLATE(
        "Hindi / Hinglish",
        Icons.Rounded.Translate,
        "Paste text to translate between Hindi, Hinglish & English...",
        "Translate the input naturally into both Hindi (Devanagari) and English/Hinglish."
    ),
    SUMMARIZE(
        "Summarizer",
        Icons.Rounded.Summarize,
        "Paste article, notes, or message thread to summarize...",
        "Summarize the text into 4 crisp bullet points and a 1-line key takeaway."
    ),
    CODE(
        "Code Fixer",
        Icons.Rounded.Code,
        "Paste Kotlin/Python/JS code or describe what to build...",
        "Analyze, fix bugs, or write clean, well-commented code for the request."
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStudioScreen(
    viewModel: PmAiViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageGenState by viewModel.imageGenState.collectAsStateWithLifecycle()
    val refBitmap by viewModel.studioReferenceBitmap.collectAsStateWithLifecycle()
    val toolUiState by viewModel.toolUiState.collectAsStateWithLifecycle()

    var imagePrompt by rememberSaveable { mutableStateOf("") }
    var selectedTool by rememberSaveable { mutableStateOf(QuickToolType.SOLVE) }
    var toolInput by rememberSaveable { mutableStateOf("") }

    val studioPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.attachStudioReferenceUri(it) }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        item {
            Column {
                Text(
                    text = "PM AI Creative & Tools Studio",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "AI Image Generation, Photo Remix, Problem Solver, Translator & Code Lab",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 1. PM AI Image Studio (Gemini Nano Banana 2.1)
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "PM AI Image Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Generate original artwork or remix a photo using Gemini Nano Banana 2.1.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = imagePrompt,
                        onValueChange = { imagePrompt = it },
                        label = { Text("Describe the image to generate or edit...") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("studio_image_prompt_input")
                    )

                    refBitmap?.let { bmp ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Reference photo for remix",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Reference photo ready for AI remix",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.clearStudioReference() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Remove reference photo")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                studioPhotoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_pick_photo_button")
                        ) {
                            Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Remix Photo")
                        }

                        Button(
                            onClick = { viewModel.generateStudioImage(imagePrompt) },
                            enabled = imagePrompt.isNotBlank() && imageGenState !is ImageGenUiState.Loading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("studio_generate_image_button")
                        ) {
                            Text(if (refBitmap != null) "Remix Image" else "Generate")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (val state = imageGenState) {
                        is ImageGenUiState.Idle -> {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        Icons.Rounded.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Try: \"Cyberpunk neon Delhi street in rain, 3D render\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        is ImageGenUiState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "PM AI is synthesizing image (may take 30-60s)...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                        is ImageGenUiState.Success -> {
                            Column {
                                Image(
                                    bitmap = state.bitmap.asImageBitmap(),
                                    contentDescription = "PM AI generated image for ${state.promptUsed}",
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                FilledTonalButton(
                                    onClick = {
                                        val saved = saveBitmapToGallery(context, state.bitmap)
                                        Toast.makeText(
                                            context,
                                            if (saved) "Saved to Pictures/PM AI!" else "Could not save image.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save to Gallery")
                                }
                            }
                        }
                        is ImageGenUiState.Error -> {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = state.message,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. All-in-One AI Tools Workbench
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "PM AI All-Purpose Tools",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QuickToolType.entries.forEach { tool ->
                            FilterChip(
                                selected = selectedTool == tool,
                                onClick = { selectedTool = tool },
                                label = { Text(tool.title) },
                                leadingIcon = {
                                    Icon(tool.icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = toolInput,
                        onValueChange = { toolInput = it },
                        label = { Text(selectedTool.hint) },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_tool_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = { viewModel.runAiTool(selectedTool.instruction, toolInput) },
                        enabled = toolInput.isNotBlank() && toolUiState !is ToolUiState.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_ai_tool_button")
                    ) {
                        if (toolUiState is ToolUiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Solving...")
                        } else {
                            Text("Run ${selectedTool.title}")
                        }
                    }

                    when (val st = toolUiState) {
                        is ToolUiState.Success -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = st.output,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }
                        is ToolUiState.Error -> {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = st.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> Unit
                    }
                }
            }
        }
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "PM_AI_${System.currentTimeMillis()}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PM AI")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        true
    } catch (_: Exception) {
        false
    }
}
