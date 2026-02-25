package com.obelus.obelusscan.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.obelus.ui.theme.NeonCyan
import com.obelus.ui.theme.NeonRed
import com.obelus.ui.theme.RaceAccent

// System Default (Darker Material 3 variant)
val SystemDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

val SystemLightColorScheme = lightColorScheme(
    primary = Color(0xFF0055FF),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

// OLED Pure Black
val OledColorScheme = darkColorScheme(
    primary = NeonCyan,
    background = Color.Black,
    surface = Color(0xFF0A0A0A),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.LightGray
)

// Cyber theme (Deep Blue/Purple Neon)
val CyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    background = Color(0xFF050510),
    surface = Color(0xFF0A0A1A),
    secondary = Color(0xFFFF00FF), // Magenta
    onPrimary = Color.Black,
    onBackground = Color(0xFFE0E0FF),
    onSurface = Color.White
)

// Sport theme (Red/Carbon)
val SportColorScheme = darkColorScheme(
    primary = NeonRed,
    background = Color(0xFF111111),
    surface = Color(0xFF1A1515), // Subtle red tint
    secondary = RaceAccent,
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color(0xFFDDDDDD)
)

