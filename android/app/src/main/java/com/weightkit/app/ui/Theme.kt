package com.weightkit.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Accent = Color(0xFFD96610)
val AccentDark = Color(0xFFFF9D41)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2CC),
    onPrimaryContainer = Color(0xFF3A1A00),
    secondary = Color(0xFF3B4A5A),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF12171D),
    surface = Color.White,
    onSurface = Color(0xFF12171D),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF4A5563),
    surfaceContainer = Color(0xFFF0F2F5),
    surfaceContainerHigh = Color(0xFFE9ECF0),
)

private val Dark = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF2A1300),
    primaryContainer = Color(0xFF5A2E00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFB7C4D3),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF161C23),
    onSurface = Color(0xFFE8ECF1),
    surfaceVariant = Color(0xFF232B35),
    onSurfaceVariant = Color(0xFFB4BFCC),
    surfaceContainer = Color(0xFF1B222A),
    surfaceContainerHigh = Color(0xFF232B35),
)

@Composable
fun WeightKitTheme(theme: Int, content: @Composable () -> Unit) {
    val dark = when (theme) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
