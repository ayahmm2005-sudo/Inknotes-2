package com.example.inknotes.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = InkContainer,
    onPrimaryContainer = InkDeep,
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = TealContainer,
    onSecondaryContainer = InkDeep,
    background = Paper,
    onBackground = InkDeep,
    surface = Paper,
    onSurface = InkDeep,
    surfaceVariant = PaperDim,
    onSurfaceVariant = Graphite,
    surfaceContainerLowest = PaperRaised,
    surfaceContainer = PaperRaised,
    surfaceContainerHigh = PaperDim,
    outlineVariant = PaperDim,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = NightInk,
    onPrimary = InkDeep,
    primaryContainer = Ink,
    onPrimaryContainer = InkContainer,
    secondary = NightTeal,
    onSecondary = InkDeep,
    secondaryContainer = Teal,
    onSecondaryContainer = TealContainer,
    background = NightPaper,
    onBackground = NightText,
    surface = NightPaper,
    onSurface = NightText,
    surfaceVariant = NightDim,
    onSurfaceVariant = NightText.copy(alpha = 0.7f),
    surfaceContainerLowest = NightRaised,
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightDim,
    outlineVariant = NightDim,
)

@Composable
fun InkNotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
