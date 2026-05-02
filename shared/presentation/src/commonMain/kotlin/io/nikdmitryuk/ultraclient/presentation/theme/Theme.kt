package io.nikdmitryuk.ultraclient.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = UltraPrimary,
    onPrimary = UltraOnPrimary,
    background = UltraBackground,
    surface = UltraSurface,
    surfaceVariant = UltraSurfaceVariant,
    onSurface = UltraOnSurface,
    onSurfaceVariant = UltraOnSurfaceVariant,
    error = UltraError
)

@Composable
fun UltraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
