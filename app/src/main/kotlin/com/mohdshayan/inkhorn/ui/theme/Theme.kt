package com.mohdshayan.inkhorn.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.mohdshayan.inkhorn.data.prefs.ThemeChoice

private fun scheme(ink: InkColors) = if (ink.isDark) {
    darkColorScheme(
        primary = ink.verdigris, onPrimary = ink.onVerdigris,
        primaryContainer = ink.leaf, onPrimaryContainer = ink.ironGall,
        secondary = ink.verdigris, onSecondary = ink.onVerdigris,
        secondaryContainer = ink.verdigris.copy(alpha = 0.16f), onSecondaryContainer = ink.ironGall,
        background = ink.vellum, onBackground = ink.ironGall,
        surface = ink.vellum, onSurface = ink.ironGall,
        surfaceVariant = ink.leaf, onSurfaceVariant = ink.fadedInk,
        surfaceContainerLowest = ink.vellum, surfaceContainerLow = ink.leaf, surfaceContainer = ink.leaf,
        surfaceContainerHigh = ink.leaf, surfaceContainerHighest = ink.leaf,
        inverseSurface = ink.ironGall, inverseOnSurface = ink.vellum, inversePrimary = ink.verdigris,
        outline = ink.fadedInk, outlineVariant = ink.divider,
        error = ink.rubric, onError = ink.vellum,
        scrim = ink.vellum.copy(alpha = 0.6f),
    )
} else {
    lightColorScheme(
        primary = ink.verdigris, onPrimary = ink.onVerdigris,
        primaryContainer = ink.leaf, onPrimaryContainer = ink.ironGall,
        secondary = ink.verdigris, onSecondary = ink.onVerdigris,
        secondaryContainer = ink.verdigris.copy(alpha = 0.14f), onSecondaryContainer = ink.ironGall,
        background = ink.vellum, onBackground = ink.ironGall,
        surface = ink.vellum, onSurface = ink.ironGall,
        surfaceVariant = ink.leaf, onSurfaceVariant = ink.fadedInk,
        surfaceContainerLowest = ink.leaf, surfaceContainerLow = ink.leaf, surfaceContainer = ink.leaf,
        surfaceContainerHigh = ink.leaf, surfaceContainerHighest = ink.leaf,
        inverseSurface = ink.ironGall, inverseOnSurface = ink.leaf, inversePrimary = ink.verdigris,
        outline = ink.fadedInk, outlineVariant = ink.divider,
        error = ink.rubric, onError = ink.leaf,
        scrim = ink.ironGall.copy(alpha = 0.32f),
    )
}

/**
 * The app theme. Dynamic colour is deliberately off. Light, dark and true black follow the Theme
 * setting; "Follow the system" picks light or dark. LocalReducedMotion and LocalInk are provided.
 */
@Composable
fun AppTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val ink = when (choice) {
        ThemeChoice.LIGHT -> LightInk
        ThemeChoice.DARK -> DarkInk
        ThemeChoice.TRUE_BLACK -> TrueBlackInk
        ThemeChoice.SYSTEM -> if (systemDark) DarkInk else LightInk
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !ink.isDark
            controller.isAppearanceLightNavigationBars = !ink.isDark
        }
    }

    CompositionLocalProvider(
        LocalReducedMotion provides rememberReducedMotion(),
        LocalInk provides ink,
    ) {
        MaterialTheme(
            colorScheme = scheme(ink),
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
