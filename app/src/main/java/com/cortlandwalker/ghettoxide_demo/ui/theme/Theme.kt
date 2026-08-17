package com.cortlandwalker.ghettoxide_demo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Coral500,
    secondary = Mint300,
    background = Ink900,
    surface = SurfaceDark,
    surfaceVariant = Ink700,
    onPrimary = SurfaceLight,
    onSecondary = Ink900,
    onBackground = SurfaceLight,
    onSurface = SurfaceLight,
    onSurfaceVariant = Mist200
)

private val LightColorScheme = lightColorScheme(
    primary = Coral700,
    secondary = Mint700,
    background = Slate100,
    surface = SurfaceLight,
    surfaceVariant = Mist200,
    onPrimary = SurfaceLight,
    onSecondary = SurfaceLight,
    onBackground = Ink900,
    onSurface = Ink900,
    onSurfaceVariant = Ink700
)

@Composable
fun GhettoxideTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
