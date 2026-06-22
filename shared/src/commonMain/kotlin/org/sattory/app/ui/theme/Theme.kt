package org.sattory.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6557C7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E0FF),
    onPrimaryContainer = Color(0xFF211A55),
    secondary = Color(0xFF5E5B71),
    background = Color(0xFFF9F8FD),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFE9E7EF),
    outlineVariant = Color(0xFFCAC7D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    primaryContainer = Color(0xFF4D459F),
    background = Color(0xFF121217),
    surface = Color(0xFF1A191F),
    surfaceVariant = Color(0xFF292830),
)

@Composable
fun SattoryTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
