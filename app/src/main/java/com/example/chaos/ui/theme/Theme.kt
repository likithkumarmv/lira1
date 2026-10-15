package com.example.chaos.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LiraColorScheme = darkColorScheme(
    primary = CyanNeon,
    onPrimary = LiraBackground,
    primaryContainer = SkyBlue,
    onPrimaryContainer = TextPrimary,
    secondary = CyanGlow,
    onSecondary = LiraBackground,
    background = LiraBackground,
    onBackground = TextPrimary,
    surface = LiraSurface,
    onSurface = TextPrimary,
    surfaceVariant = LiraSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = LiraBorder
)

@Composable
fun CHAOSTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Force consistent cyber theme
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LiraColorScheme,
        typography = Typography,
        content = content
    )
}