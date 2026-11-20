package ru.yandex.station.remote.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val OledBlack = Color(0xFF000000)
val DarkSurface = Color(0xFF141417)
val DarkSurfaceElevated = Color(0xFF1F1F24)
val AccentYandex = Color(0xFFFFCC00)
val AccentRed = Color(0xFFFF334B)
val AccentBlue = Color(0xFF3B82F6)
val TextPrimary = Color(0xFFEEEEEE)
val TextSecondary = Color(0xFF9E9E9E)
val StatusGreen = Color(0xFF4CAF50)
val StatusRed = Color(0xFFF44336)

private val DarkColorScheme = darkColorScheme(
    primary = AccentYandex,
    onPrimary = Color.Black,
    secondary = AccentBlue,
    onSecondary = Color.White,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondary
)

@Composable
fun YandexRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
