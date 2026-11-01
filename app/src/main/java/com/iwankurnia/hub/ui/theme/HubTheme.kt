package com.iwankurnia.hub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext

/**
 * Hub's Material 3 theme. Light and dark follow the system; the Material colour scheme is always the
 * neutral direction A one ([HubLightColors], [HubDarkColors]), so rows, sheets, search and settings
 * never change colour. [dynamicColor] only changes the accent scheme ([HubColors.accent]) that the
 * top bar, the tabs and the date bands draw from: the wallpaper scheme on Android 12 and up, the
 * neutral scheme otherwise (Iwan, 2026-11-01: "just top, date band and tab bar applied, list still
 * as is").
 *
 * Uses [MaterialTheme]: the material3 1.4.0 that Compose BOM 2026.02.01 resolves ships
 * `MaterialExpressiveTheme` as internal, so it cannot be called yet.
 */
@Composable
fun HubTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = if (darkTheme) HubDarkColors else HubLightColors
    val accent = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> colorScheme
    }
    CompositionLocalProvider(
        LocalHubColors provides if (darkTheme) HubDarkExtraColors else HubLightExtraColors,
        LocalHubAccent provides accent,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HubTypography,
            content = content,
        )
    }
}
