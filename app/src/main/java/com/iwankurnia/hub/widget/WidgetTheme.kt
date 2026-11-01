package com.iwankurnia.hub.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceTheme
import androidx.glance.color.ColorProviders
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
 * Always the neutral scheme, never wallpaper colours: the widgets stay monochrome whatever the
 * app's dynamic colour setting. Light and dark follow the system.
 */
@Composable
internal fun HubWidgetTheme(content: @Composable () -> Unit) {
    GlanceTheme(colors = NeutralColorProviders, content = content)
}

/** Unread count as shown in a pill or badge; large counts are capped. */
internal fun countLabel(count: Int): String = if (count > MAX_SHOWN_COUNT) "$MAX_SHOWN_COUNT+" else count.toString()

/** Widget bitmaps are drawn at the device density so icons stay sharp. */
internal fun Context.dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).roundToInt()

private const val MAX_SHOWN_COUNT = 99
