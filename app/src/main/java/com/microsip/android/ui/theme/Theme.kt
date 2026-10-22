package com.microsip.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFF1E6FD9)
val CallGreen = Color(0xFF1FA851)
val HangupRed = Color(0xFFE0393E)
val Away = Color(0xFFF2A100)
val Offline = Color(0xFF9E9E9E)

private val Light = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E5FB),
    onPrimaryContainer = Color(0xFF0B2A55),
    secondary = Color(0xFF4F6077),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB8F5),
    onPrimary = Color(0xFF0B2A55),
    primaryContainer = Color(0xFF184F9A),
    onPrimaryContainer = Color(0xFFD6E5FB),
    secondary = Color(0xFFB4C4DB),
)

@Composable
fun MicroSipTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
