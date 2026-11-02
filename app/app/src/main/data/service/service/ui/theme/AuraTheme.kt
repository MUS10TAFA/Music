package com.luxury.auraplayer.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AuraBlack = Color(0xFF000000)
val AuraSurfaceDark = Color(0xFF0A0A0C)
val AuraCardDark = Color(0xFF141419)
val AuraCardElevated = Color(0xFF1F1F26)
val AuraGoldPrimary = Color(0xFFD4AF37)
val AuraGoldLight = Color(0xFFF3E5AB)
val AuraAccentPurple = Color(0xFF7B2CBF)
val AuraTextWhite = Color(0xFFF8F9FA)
val AuraTextGray = Color(0xFFA0A0AB)
val AuraGlassBorder = Color(0x26FFFFFF)

private val LuxuryDarkColorScheme = darkColorScheme(
    primary = AuraGoldPrimary,
    onPrimary = AuraBlack,
    primaryContainer = AuraCardElevated,
    onPrimaryContainer = AuraGoldLight,
    secondary = AuraAccentPurple,
    background = AuraBlack,
    onBackground = AuraTextWhite,
    surface = AuraSurfaceDark,
    onSurface = AuraTextWhite,
    surfaceVariant = AuraCardDark,
    onSurfaceVariant = AuraTextGray,
    outline = AuraGlassBorder
)

@Composable
fun AuraPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LuxuryDarkColorScheme,
        content = content
    )
}
