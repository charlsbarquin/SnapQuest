package com.charlsemilbarquin.frameandfact.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = SnapQuestSky,
    onPrimary = SnapQuestNavy,
    secondary = SnapQuestTeal,
    onSecondary = SnapQuestNavy,
    secondaryContainer = SnapQuestDarkSuccessContainer,
    onSecondaryContainer = SnapQuestLight,
    tertiary = SnapQuestAmber,
    onTertiary = SnapQuestNavy,
    tertiaryContainer = SnapQuestDarkRewardContainer,
    onTertiaryContainer = SnapQuestLight,
    error = SnapQuestCoral,
    onError = SnapQuestNavy,
    errorContainer = SnapQuestDarkErrorContainer,
    onErrorContainer = SnapQuestLight,
    primaryContainer = SnapQuestDarkPrimaryContainer,
    onPrimaryContainer = SnapQuestLight,
    background = SnapQuestNavy,
    onBackground = SnapQuestLight,
    surface = SnapQuestSurface,
    onSurface = SnapQuestLight,
    surfaceContainerLowest = SnapQuestNavy,
    surfaceContainerLow = SnapQuestSurface,
    surfaceContainer = SnapQuestSurface,
    surfaceContainerHigh = SnapQuestElevated,
    surfaceContainerHighest = SnapQuestElevated,
    surfaceVariant = SnapQuestElevated,
    onSurfaceVariant = SnapQuestMuted,
    outline = SnapQuestBorder,
    outlineVariant = SnapQuestBorder
)

private val LightColorScheme = lightColorScheme(
    primary = SnapQuestLightPrimary,
    onPrimary = Color.White,
    secondary = SnapQuestLightTeal,
    onSecondary = Color.White,
    secondaryContainer = SnapQuestLightSuccessContainer,
    onSecondaryContainer = SnapQuestLightOnSurface,
    tertiary = SnapQuestLightAmber,
    onTertiary = Color.White,
    tertiaryContainer = SnapQuestLightRewardContainer,
    onTertiaryContainer = SnapQuestLightOnSurface,
    error = SnapQuestLightCoral,
    onError = Color.White,
    errorContainer = SnapQuestLightErrorContainer,
    onErrorContainer = SnapQuestLightOnSurface,
    primaryContainer = SnapQuestLightPrimaryContainer,
    onPrimaryContainer = SnapQuestLightOnSurface,
    background = SnapQuestLightBackground,
    onBackground = SnapQuestLightOnSurface,
    surface = SnapQuestLightSurface,
    onSurface = SnapQuestLightOnSurface,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SnapQuestLightSurfaceVariant,
    surfaceContainer = SnapQuestLightSurface,
    surfaceContainerHigh = Color(0xFFEEF5FF),
    surfaceContainerHighest = SnapQuestLightSurfaceVariant,
    surfaceVariant = SnapQuestLightSurfaceVariant,
    onSurfaceVariant = SnapQuestLightMuted,
    outline = Color(0xFFB6C7D8),
    outlineVariant = Color(0xFFD1DDE8)
)

@Composable
fun FrameFactTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = FrameFactShapes,
        content = content
    )
}
