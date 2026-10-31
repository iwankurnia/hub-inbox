package com.iwankurnia.hub.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Seed colour; used when dynamic colour is off or unavailable (before Android 12). */
val HubSeed = Color(0xFF0B57D0)

/** Hub-specific colours that are not part of the Material colour scheme. */
object HubColors {
    val snoozedBar = Color(0xFFE5A900)
    val snoozedChip = Color(0xFFFFE2A8)
    val snoozedOnChip = Color(0xFF4A3200)
    val readReveal = Color(0xFFB7F2C7)
    val onReadReveal = Color(0xFF0B3D1A)
}

/** Light scheme derived from the #0B57D0 seed. */
val HubLightColors = lightColorScheme(
    primary = HubSeed,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF575E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDBE2F9),
    onSecondaryContainer = Color(0xFF141B2C),
    tertiary = Color(0xFF715573),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFBD7FC),
    onTertiaryContainer = Color(0xFF29132D),
    background = Color(0xFFF7F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF7F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E3EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3FA),
    surfaceContainer = Color(0xFFE8ECF4),
    surfaceContainerHigh = Color(0xFFE2E6EE),
    surfaceContainerHighest = Color(0xFFDCE0E8),
)

/** Dark scheme derived from the #0B57D0 seed. */
val HubDarkColors = darkColorScheme(
    primary = Color(0xFFA8C7FA),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFBFC6DC),
    onSecondary = Color(0xFF293041),
    secondaryContainer = Color(0xFF3F4759),
    onSecondaryContainer = Color(0xFFDBE2F9),
    tertiary = Color(0xFFDEBCDF),
    onTertiary = Color(0xFF402843),
    tertiaryContainer = Color(0xFF583E5B),
    onTertiaryContainer = Color(0xFFFBD7FC),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E2E8),
    surface = Color(0xFF101418),
    onSurface = Color(0xFFE1E2E8),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF8E9199),
    outlineVariant = Color(0xFF43474E),
    surfaceContainerLowest = Color(0xFF0B0F13),
    surfaceContainerLow = Color(0xFF181C20),
    surfaceContainer = Color(0xFF1D2126),
    surfaceContainerHigh = Color(0xFF272A2F),
    surfaceContainerHighest = Color(0xFF32353A),
)
