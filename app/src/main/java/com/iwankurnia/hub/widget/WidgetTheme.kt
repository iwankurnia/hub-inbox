package com.iwankurnia.hub.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
import androidx.glance.color.DynamicThemeColorProviders
import androidx.glance.material3.ColorProviders
import com.iwankurnia.hub.ui.theme.HubDarkColors
import com.iwankurnia.hub.ui.theme.HubLightColors
import kotlin.math.roundToInt

/**
 * The app's neutral direction A schemes, light and dark. In them primary is the text colour and
 * primaryContainer the high surface container, so the widgets carry no accent colour.
 */
private val NeutralColorProviders: ColorProviders = ColorProviders(light = HubLightColors, dark = HubDarkColors)

/**
 * The widgets follow the app's "Dynamic colour" setting (Iwan, 2026-11-01: "make the widget follow
 * theme color"): the wallpaper scheme on Android 12 and up when [dynamicColor] is on, the neutral
 * scheme otherwise. Light and dark follow the system either way. The widgets draw their accents
 * from primary / primaryContainer, which on the neutral scheme are the text colour and the high
 * surface container, so they stay monochrome there.
 */
@Composable
internal fun HubWidgetTheme(dynamicColor: Boolean, content: @Composable () -> Unit) {
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else NeutralColorProviders
    GlanceTheme(colors = colors, content = content)
}

/** Unread count as shown in a pill or badge; large counts are capped. */
internal fun countLabel(count: Int): String = if (count > MAX_SHOWN_COUNT) "$MAX_SHOWN_COUNT+" else count.toString()

/** Widget bitmaps are drawn at the device density so icons stay sharp. */
internal fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

private const val MAX_SHOWN_COUNT = 99
