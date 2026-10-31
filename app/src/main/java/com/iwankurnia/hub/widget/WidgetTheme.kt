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

/** The #0B57D0 seed schemes, light and dark, for launchers without dynamic colour. */
private val SeedColorProviders: ColorProviders = ColorProviders(light = HubLightColors, dark = HubDarkColors)

/** Wallpaper colours on Android 12 and up, the seed scheme before; light and dark follow the system. */
@Composable
internal fun HubWidgetTheme(content: @Composable () -> Unit) {
    val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) DynamicThemeColorProviders else SeedColorProviders
    GlanceTheme(colors = colors, content = content)
}

/** Unread count as shown in a pill or badge; large counts are capped. */
internal fun countLabel(count: Int): String = if (count > MAX_SHOWN_COUNT) "$MAX_SHOWN_COUNT+" else count.toString()

/** Widget bitmaps are drawn at the device density so icons stay sharp. */
internal fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

private const val MAX_SHOWN_COUNT = 99
