package com.creativeam.goorno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta Oficial GoOrNo (Verde-Petróleo Escuro + Verde-Lima)
val PetrolDarkBg = Color(0xFF04241E)
val PetrolSurface = Color(0xFF073B32)
val PetrolCardBorder = Color(0xFF126152)
val LimePrimary = Color(0xFF97B304)
val LimeBright = Color(0xFFB8D906)
val OliveSecondary = Color(0xFF7C9303)
val TrafficGreen = Color(0xFF0E9F6E)
val TrafficYellow = Color(0xFFD97706)
val TrafficRed = Color(0xFFDC2626)
val TrafficGray = Color(0xFF4B5563)

private val GoOrNoColorScheme = darkColorScheme(
    primary = LimePrimary,
    onPrimary = PetrolDarkBg,
    secondary = LimeBright,
    onSecondary = PetrolDarkBg,
    tertiary = TrafficGreen,
    background = PetrolDarkBg,
    onBackground = Color.White,
    surface = PetrolSurface,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF0B483D),
    onSurfaceVariant = Color(0xFFD1FAE5)
)

@Composable
fun GoOrNoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GoOrNoColorScheme,
        content = content
    )
}
