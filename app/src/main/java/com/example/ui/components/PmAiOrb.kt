package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.MintSuccess

@Composable
fun PmAiVoiceOrbCard(
    isListening: Boolean,
    isThinking: Boolean,
    isSpeaking: Boolean,
    isAlwaysActive: Boolean,
    partialTranscript: String,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_pulse")
    val pulseSpeed = when {
        isListening -> 850
        isThinking -> 1100
        isSpeaking -> 950
        else -> 2400
    }
    val pulseProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse_ring"
    )

    val activeColor = when {
        isListening -> CyanPulse
        isThinking -> ElectricViolet
        isSpeaking -> MintSuccess
        else -> MaterialTheme.colorScheme.primary
    }

    val statusTitle = when {
        isListening -> "PM AI is Listening..."
        isThinking -> "PM AI Brain Thinking..."
        isSpeaking -> "PM AI Speaking..."
        else -> "Tap Orb to Speak to PM AI"
    }

    val statusSubtitle = when {
        isListening && partialTranscript.isNotBlank() -> partialTranscript
        isListening -> "Speak in Hindi, Hinglish, or English"
        isThinking -> "Analyzing intent & solving with Gemini Brain"
        isSpeaking -> "Tap orb anytime to interrupt"
        isAlwaysActive -> "Always-Active Engine ON • Screen On & Off Ready"
        else -> "Ask anything or control calls, scroll & messages"
    }

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        borderColor = activeColor.copy(alpha = if (isListening || isThinking) 0.7f else 0.25f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .testTag("pm_ai_voice_orb")
                    .semantics { contentDescription = statusTitle }
                    .clip(CircleShape)
                    .clickable(onClick = onOrbClick),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(80.dp)) {
                    val maxRadius = size.minDimension / 2f
                    val ringRadius = maxRadius * (0.68f + 0.30f * pulseProgress)
                    val ringAlpha = (1f - pulseProgress) * if (isListening || isThinking || isSpeaking) 0.85f else 0.4f
                    drawCircle(
                        color = activeColor.copy(alpha = ringAlpha),
                        radius = ringRadius,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(activeColor.copy(alpha = 0.28f), Color.Transparent),
                            center = center,
                            radius = maxRadius
                        ),
                        radius = maxRadius
                    )
                }

                Image(
                    painter = painterResource(id = R.drawable.img_ai_orb_hero),
                    contentDescription = "PM AI Neural Core",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                )

                Surface(
                    shape = CircleShape,
                    color = activeColor.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                isListening || isSpeaking -> Icons.Rounded.Stop
                                isThinking -> Icons.Rounded.GraphicEq
                                else -> Icons.Rounded.Mic
                            },
                            contentDescription = null,
                            tint = Color(0xFF06101B),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = statusTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 2.dp
    ) {
        Column(content = content)
    }
}

@Composable
fun StatusPill(
    label: String,
    active: Boolean,
    activeColor: Color = MintSuccess,
    modifier: Modifier = Modifier
) {
    val bgColor = if (active) activeColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor = if (active) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor,
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Canvas(modifier = Modifier.size(7.dp)) {
                drawCircle(color = contentColor)
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_x"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(translate - 300f, 0f),
                    end = Offset(translate, 300f)
                )
            )
    )
}
