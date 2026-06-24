package org.sattory.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF4F46A5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4E0FF),
    onPrimaryContainer = Color(0xFF151044),
    secondary = Color(0xFF006875),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF9DEFFB),
    onSecondaryContainer = Color(0xFF001F25),
    tertiary = Color(0xFF7A5800),
    tertiaryContainer = Color(0xFFFFDEA1),
    background = Color(0xFFFAF9F6),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFE7E4EC),
    outlineVariant = Color(0xFFC9C5D0),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC8BFFF),
    primaryContainer = Color(0xFF393187),
    secondary = Color(0xFF56D6E7),
    secondaryContainer = Color(0xFF004E58),
    tertiary = Color(0xFFEFC16B),
    tertiaryContainer = Color(0xFF5C4200),
    background = Color(0xFF121314),
    surface = Color(0xFF1B1B1F),
    surfaceVariant = Color(0xFF46434D),
    outlineVariant = Color(0xFF46434D),
)

private val SattoryTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)

@Composable
fun SattoryTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = SattoryTypography,
        content = content,
    )
}
