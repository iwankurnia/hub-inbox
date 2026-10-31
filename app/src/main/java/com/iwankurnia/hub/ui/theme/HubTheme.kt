package com.iwankurnia.hub.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Hub's Material 3 theme. Light and dark follow the system; colours come from the wallpaper on
 * Android 12 and up when [dynamicColor] is on, otherwise from the #0B57D0 seed.
 *
 * Uses [MaterialTheme]: the material3 1.4.0 that Compose BOM 2026.02.01 resolves ships
 * `MaterialExpressiveTheme` as internal, so it cannot be called yet.
 */
@Composable
fun HubTheme(
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> HubDarkColors
        else -> HubLightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = HubTypography,
        content = content,
    )
}
