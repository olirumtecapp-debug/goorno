package com.creativeam.goorno.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Paleta Oficial GoOrNo v2.2.0 — Clean, Clara, Moderna e de Alto Contraste
val CleanLightBg = Color(0xFFF3F7F6)         // Fundo claro gelo/menta suave
val PureWhiteSurface = Color(0xFFFFFFFF)     // Cards brancos puros e limpos
val SoftSurfaceVariant = Color(0xFFEAF2EF)   // Caixas internas suaves
val CleanCardBorder = Color(0xFFD3E2DD)      // Bordas sutis e elegantes

// Cores da Marca GoOrNo
val PetrolHeader = Color(0xFF073B32)         // Verde-Petróleo Oficial (TopBar e Botões Principais)
val PetrolDarkText = Color(0xFF0A2E27)       // Texto principal escuro de altíssima leitura
val MutedText = Color(0xFF4A6B63)            // Texto secundário limpo

val LimePrimary = Color(0xFF829B03)          // Verde-Lima moderno para destaques
val LimeBright = Color(0xFF97B304)           // Verde-Lima vibrante da logo
val LimeSoftBg = Color(0xFFF2F8D2)           // Fundo suave lima para badges

// Semáforo Financeiro Moderno
val TrafficGreen = Color(0xFF10E548)
val TrafficGreenSoft = Color(0xFFD1FAE5)
val TrafficYellow = Color(0xFFFFB800)
val TrafficYellowSoft = Color(0xFFFEF3C7)
val TrafficRed = Color(0xFFFF3B30)
val TrafficRedSoft = Color(0xFFFEE2E2)
val TrafficGray = Color(0xFF64748B)

// Aliases de compatibilidade com componentes
val PetrolDarkBg = CleanLightBg
val PetrolSurface = PureWhiteSurface
val PetrolCardBorder = CleanCardBorder
val OliveSecondary = Color(0xFF0D9488)

private val GoOrNoLightColorScheme = lightColorScheme(
    primary = PetrolHeader,
    onPrimary = Color.White,
    primaryContainer = SoftSurfaceVariant,
    onPrimaryContainer = PetrolDarkText,
    secondary = LimeBright,
    onSecondary = PetrolDarkText,
    tertiary = TrafficGreen,
    onTertiary = Color.White,
    background = CleanLightBg,
    onBackground = PetrolDarkText,
    surface = PureWhiteSurface,
    onSurface = PetrolDarkText,
    surfaceVariant = SoftSurfaceVariant,
    onSurfaceVariant = MutedText,
    outline = CleanCardBorder
)

@Composable
fun GoOrNoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GoOrNoLightColorScheme,
        content = content
    )
}
