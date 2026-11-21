package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PmAiDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = Color(0xFF002129),
    primaryContainer = Color(0xFF003B4A),
    onPrimaryContainer = Color(0xFFB3F5FF),
    secondary = ElectricViolet,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2E1E59),
    onSecondaryContainer = VioletGlow,
    tertiary = MintSuccess,
    onTertiary = Color(0xFF002619),
    tertiaryContainer = Color(0xFF0B3B2B),
    onTertiaryContainer = Color(0xFFA7F3D0),
    background = ObsidianBg,
    onBackground = TextPrimaryDark,
    surface = DeepSpaceSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedGlassSurface,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerHigh = HighGlassSurface,
    outline = OutlineDark,
    error = CoralAlert,
    onError = Color.White
)

private val PmAiLightColorScheme = lightColorScheme(
    primary = DeepCyanLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F0FF),
    onPrimaryContainer = Color(0xFF002B3D),
    secondary = DeepVioletLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE9FE),
    onSecondaryContainer = Color(0xFF2E1065),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = CloudBg,
    onBackground = TextPrimaryLight,
    surface = CloudSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = CloudSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = OutlineLight,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val PmAiShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun PmAiTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PmAiDarkColorScheme else PmAiLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PmAiTypography,
        shapes = PmAiShapes,
        content = content
    )
}
