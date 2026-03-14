package com.myweld.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = MyWeldColors.DarkPrimary,
    onPrimary = MyWeldColors.DarkOnPrimary,
    primaryContainer = MyWeldColors.DarkPrimaryContainer,
    onPrimaryContainer = MyWeldColors.DarkOnPrimaryContainer,
    secondary = MyWeldColors.DarkSecondary,
    onSecondary = MyWeldColors.DarkOnSecondary,
    secondaryContainer = MyWeldColors.DarkSecondaryContainer,
    onSecondaryContainer = MyWeldColors.DarkOnSecondaryContainer,
    tertiary = MyWeldColors.DarkTertiary,
    onTertiary = MyWeldColors.DarkOnTertiary,
    tertiaryContainer = MyWeldColors.DarkTertiaryContainer,
    onTertiaryContainer = MyWeldColors.DarkOnTertiaryContainer,
    background = MyWeldColors.DarkBackground,
    onBackground = MyWeldColors.DarkOnBackground,
    surface = MyWeldColors.DarkSurface,
    onSurface = MyWeldColors.DarkOnSurface,
    surfaceVariant = MyWeldColors.DarkSurfaceVariant,
    onSurfaceVariant = MyWeldColors.DarkOnSurfaceVariant,
    outline = MyWeldColors.DarkOutline,
    outlineVariant = MyWeldColors.DarkOutlineVariant,
    error = MyWeldColors.DarkError,
    onError = MyWeldColors.DarkOnError,
    errorContainer = MyWeldColors.DarkErrorContainer,
    onErrorContainer = MyWeldColors.DarkOnErrorContainer,
)

private val LightColorScheme = lightColorScheme(
    primary = MyWeldColors.LightPrimary,
    onPrimary = MyWeldColors.LightOnPrimary,
    primaryContainer = MyWeldColors.LightPrimaryContainer,
    onPrimaryContainer = MyWeldColors.LightOnPrimaryContainer,
    secondary = MyWeldColors.LightSecondary,
    onSecondary = MyWeldColors.LightOnSecondary,
    secondaryContainer = MyWeldColors.LightSecondaryContainer,
    onSecondaryContainer = MyWeldColors.LightOnSecondaryContainer,
    tertiary = MyWeldColors.LightTertiary,
    onTertiary = MyWeldColors.LightOnTertiary,
    tertiaryContainer = MyWeldColors.LightTertiaryContainer,
    onTertiaryContainer = MyWeldColors.LightOnTertiaryContainer,
    background = MyWeldColors.LightBackground,
    onBackground = MyWeldColors.LightOnBackground,
    surface = MyWeldColors.LightSurface,
    onSurface = MyWeldColors.LightOnSurface,
    surfaceVariant = MyWeldColors.LightSurfaceVariant,
    onSurfaceVariant = MyWeldColors.LightOnSurfaceVariant,
    outline = MyWeldColors.LightOutline,
    outlineVariant = MyWeldColors.LightOutlineVariant,
    error = MyWeldColors.LightError,
    onError = MyWeldColors.LightOnError,
    errorContainer = MyWeldColors.LightErrorContainer,
    onErrorContainer = MyWeldColors.LightOnErrorContainer,
)

@Composable
fun MyWeldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MyWeldTypography,
        shapes = MyWeldShapes,
        content = content,
    )
}
