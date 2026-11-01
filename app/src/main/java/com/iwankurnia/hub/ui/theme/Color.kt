package com.iwankurnia.hub.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Direction A, "tightened Material" (Iwan, 2026-11-01): a neutral scheme where the accent is the
 * text colour itself. Primary is onSurface (near-black on light, near-white on dark), containers
 * are greys, and only error keeps its Material colours (delete reveal, Empty-now banner).
 */

private val LightBackground = Color(0xFFFBFBFE)
private val LightSurfaceContainer = Color(0xFFF2F3F8)
private val LightSurfaceContainerHigh = Color(0xFFE8E9EF)
private val LightOutlineVariant = Color(0xFFE2E3EA)
private val LightOnSurface = Color(0xFF1A1C1E)
private val LightOnSurfaceVariant = Color(0xFF6B6F78)
private val LightSecondaryText = Color(0xFF8A8E97)

private val DarkBackground = Color(0xFF0F1114)
private val DarkSurfaceContainer = Color(0xFF1B1E23)
private val DarkSurfaceContainerHigh = Color(0xFF23272D)
private val DarkOutlineVariant = Color(0xFF2A2F36)
private val DarkOnSurface = Color(0xFFE6E8EC)
private val DarkOnSurfaceVariant = Color(0xFFA9ADB5)
private val DarkSecondaryText = Color(0xFF8E9199)

/** Neutral light scheme; `secondary` is the quiet text colour (read times, hints). */
val HubLightColors = lightColorScheme(
    primary = LightOnSurface,
    onPrimary = LightBackground,
    primaryContainer = LightSurfaceContainerHigh,
    onPrimaryContainer = LightOnSurface,
    // On the dark snackbar: a light blue action that stays readable.
    inversePrimary = Color(0xFFA8C7FA),
    secondary = LightSecondaryText,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSurfaceContainerHigh,
    onSecondaryContainer = LightOnSurface,
    tertiary = LightOnSurfaceVariant,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightSurfaceContainerHigh,
    onTertiaryContainer = LightOnSurface,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightBackground,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceContainerHigh,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceTint = LightOnSurface,
    inverseSurface = Color(0xFF2F3033),
    inverseOnSurface = Color(0xFFF1F1F4),
    outline = LightSecondaryText,
    outlineVariant = LightOutlineVariant,
    surfaceBright = LightBackground,
    surfaceDim = Color(0xFFDCDDE3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FB),
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightOutlineVariant,
)

/** Neutral dark scheme; `secondary` is the quiet text colour (read times, hints). */
val HubDarkColors = darkColorScheme(
    primary = DarkOnSurface,
    onPrimary = DarkBackground,
    primaryContainer = DarkSurfaceContainerHigh,
    onPrimaryContainer = DarkOnSurface,
    // On the light snackbar: a blue action that stays readable.
    inversePrimary = Color(0xFF0B57D0),
    secondary = DarkSecondaryText,
    onSecondary = DarkBackground,
    secondaryContainer = DarkSurfaceContainerHigh,
    onSecondaryContainer = DarkOnSurface,
    tertiary = DarkOnSurfaceVariant,
    onTertiary = DarkBackground,
    tertiaryContainer = DarkSurfaceContainerHigh,
    onTertiaryContainer = DarkOnSurface,
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkBackground,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceContainerHigh,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceTint = DarkOnSurface,
    inverseSurface = DarkOnSurface,
    inverseOnSurface = Color(0xFF2F3033),
    outline = DarkSecondaryText,
    outlineVariant = DarkOutlineVariant,
    surfaceBright = Color(0xFF34383F),
    surfaceDim = DarkBackground,
    surfaceContainerLowest = Color(0xFF0A0C0E),
    surfaceContainerLow = Color(0xFF15181C),
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkOutlineVariant,
)

/** Hub-specific colours that are not part of the Material colour scheme. */
@Immutable
data class HubExtraColors(
    /** Behind a row swiped towards read or unread. */
    val readReveal: Color,
    val onReadReveal: Color,
)

internal val HubLightExtraColors = HubExtraColors(readReveal = Color(0xFFE3E5EA), onReadReveal = LightOnSurface)
internal val HubDarkExtraColors = HubExtraColors(readReveal = DarkOutlineVariant, onReadReveal = DarkOnSurface)

internal val LocalHubColors = staticCompositionLocalOf { HubLightExtraColors }

/** The current [HubExtraColors], light or dark with the system. */
object HubColors {
    val readReveal: Color
        @Composable @ReadOnlyComposable
        get() = LocalHubColors.current.readReveal

    val onReadReveal: Color
        @Composable @ReadOnlyComposable
        get() = LocalHubColors.current.onReadReveal
}
