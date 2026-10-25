package com.imadlabs.icall.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.imadlabs.icall.sip.Prefs

// ImadLabs brand tokens (ImadKhan1999/Graphics brand/colors/tokens.json).
val ElectricCobalt = Color(0xFF0066FF)
val CyberMint = Color(0xFF00F5A0)
val QuantumCore = Color(0xFF00FFC2)
val SkyDiamond = Color(0xFF38BDF8)
val DeepVoid = Color(0xFF070B14)
val SlateObsidian = Color(0xFF0B0F19)
val SurfaceCard = Color(0xFF111827)
val SurfaceHover = Color(0xFF162032)
val BorderDark = Color(0xFF1E293B)
val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)

val Brand = ElectricCobalt
val CallGreen = Color(0xFF10B981)
val HangupRed = Color(0xFFEF4444)
val Away = Color(0xFFF59E0B)
val Offline = Color(0xFF64748B)

const val TELEGRAM_URL = "https://t.me/ImadLabs"

private val Dark = darkColorScheme(
    primary = CyberMint,
    onPrimary = DeepVoid,
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFB8FFE4),
    secondary = SkyDiamond,
    onSecondary = DeepVoid,
    secondaryContainer = Color(0xFF0B3A66),
    onSecondaryContainer = Color(0xFFD3ECFF),
    tertiary = ElectricCobalt,
    background = DeepVoid,
    onBackground = TextPrimary,
    surface = SlateObsidian,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = DeepVoid,
    surfaceContainerLow = SlateObsidian,
    surfaceContainer = SurfaceCard,
    surfaceContainerHigh = SurfaceHover,
    surfaceContainerHighest = BorderDark,
    outline = Color(0xFF334155),
    outlineVariant = BorderDark,
    error = HangupRed,
)

private val Light = lightColorScheme(
    primary = ElectricCobalt,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E5FF),
    onPrimaryContainer = Color(0xFF001A43),
    secondary = Color(0xFF0284C7),
    tertiary = Color(0xFF00A86E),
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFF8FAFC),
    error = HangupRed,
)

/** "dark" (default, ImadLabs apps are dark-first), "light" or "system". Read by [ICallTheme] so a change recomposes. */
object ThemeMode {
    // First read happens in composition, after Prefs.init() in SipManager.init().
    var value by mutableStateOf(runCatching { Prefs.themeMode }.getOrDefault("dark"))
    fun set(mode: String) { Prefs.themeMode = mode; value = mode }
}

@Composable
fun ICallTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeMode.value) { "light" -> false; "system" -> isSystemInDarkTheme(); else -> true }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
