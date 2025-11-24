package com.team11.smartgym.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GreenAccent,
    onPrimary = Color.Black,
    primaryContainer = GreenAccent,
    onPrimaryContainer = Color.Black,

    secondary = GreenAccent,
    onSecondary = Color.Black,

    background = BackgroundDark,
    onBackground = OnDark,

    surface = SurfaceDark,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnDarkMuted,

    error = ErrorRed,
    onError = Color.Black
)

@Composable
fun SmartGymTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
