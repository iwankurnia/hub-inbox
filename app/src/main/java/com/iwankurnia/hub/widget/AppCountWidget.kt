package com.iwankurnia.hub.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.iwankurnia.hub.HubIntents
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.icon.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext

/** App tiles that fit beside the total in [widthDp]: 100dp for padding, total and divider, 48dp per app, 1 to 8. */
fun appsThatFit(widthDp: Int): Int = ((widthDp - FIXED_DP) / APP_SLOT_DP).coerceIn(1, MAX_APPS)

/** Apps with nothing unread show no badge and are dimmed instead. */
fun showBadge(unread: Int): Boolean = unread > 0

/** The single-row widget: total unread, then each recent app with its unread badge (spec 4.9). */
class AppCountWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) = coroutineScope {
        val entryPoint = widgetEntryPoint(context)
        // One collection for the whole session: stateIn waits for the first state, then keeps following.
        // appCounts is a Room flow, so a running session also follows changes between refreshes.
        val states = barStates(context, entryPoint.repository(), entryPoint.appIconCache()).stateIn(this)
        provideContent {
            val state by states.collectAsState()
            HubWidgetTheme { AppCountContent(state) }
        }
    }

    /** Labels are looked up on IO; icons are drawn on the main thread (see [AppIconCache.iconBitmapOnMain]). */
    private fun barStates(context: Context, repository: HubRepository, icons: AppIconCache): Flow<BarState> {
        val iconPx = context.dpToPx(ICON_DP)
        return repository.appCounts().map { counts ->
            val top = counts.sortedByDescending { it.lastSeenAt }.take(MAX_APPS)
            val labels = withContext(Dispatchers.IO) { top.map { icons.label(it.packageName) } }
            val apps = top.zip(labels) { count, label ->
                AppTile(
                    packageName = count.packageName,
                    label = label,
                    unread = count.unread,
                    icon = icons.iconBitmapOnMain(count.packageName, iconPx),
                )
            }
            BarState(total = counts.sumOf { it.unread }, apps = apps)
        }
    }
}

private data class BarState(val total: Int, val apps: List<AppTile>)

private data class AppTile(val packageName: String, val label: String, val unread: Int, val icon: Bitmap?)

private const val MAX_APPS = 8

/** 28dp horizontal padding, about 61dp for the total, the 1dp divider and its 8dp spacer, rounded up. */
private const val FIXED_DP = 100

/** One [AppSlot]. */
private const val APP_SLOT_DP = 48
private const val ICON_DP = 40
private const val DIMMED_ALPHA = 0.4f

@Composable
private fun AppCountContent(state: BarState) {
    val apps = state.apps.take(appsThatFit(LocalSize.current.width.value.toInt()))
    Row(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(24.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Total(state.total)
        Box(
            modifier = GlanceModifier.width(1.dp).height(24.dp).background(GlanceTheme.colors.surfaceVariant),
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        apps.forEach { app -> AppSlot(app) }
    }
}

@Composable
private fun Total(total: Int) {
    val context = LocalContext.current
    val description = context.getString(R.string.widget_unread_total, total)
    Row(
        modifier = GlanceModifier.fillMaxHeight()
            .padding(end = 10.dp)
            .semantics { contentDescription = description }
            .clickable(actionStartActivity(HubIntents.open(context, tab = "UNREAD"))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_stat_hub),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(modifier = GlanceModifier.width(6.dp))
        Text(
            text = countLabel(total),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
    }
}

/** A 48dp slot: the 40dp icon at the bottom left, the badge overlapping its top-right corner. */
@Composable
private fun AppSlot(app: AppTile) {
    val context = LocalContext.current
    val alpha = if (showBadge(app.unread)) 1f else DIMMED_ALPHA
    Box(
        modifier = GlanceModifier.size(48.dp)
            .clickable(actionStartActivity(HubIntents.open(context, packageName = app.packageName))),
    ) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
            AppIcon(app, alpha)
        }
        if (showBadge(app.unread)) {
            Box(modifier = GlanceModifier.fillMaxSize().padding(end = 2.dp), contentAlignment = Alignment.TopEnd) {
                Badge(app.unread)
            }
        }
    }
}

@Composable
private fun AppIcon(app: AppTile, alpha: Float) {
    val description = LocalContext.current.getString(R.string.cd_app_icon, app.label)
    if (app.icon != null) {
        Image(
            provider = ImageProvider(app.icon),
            contentDescription = description,
            alpha = alpha,
            modifier = GlanceModifier.size(ICON_DP.dp).cornerRadius(12.dp),
        )
    } else {
        Box(
            modifier = GlanceModifier.size(ICON_DP.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_stat_hub),
                contentDescription = description,
                alpha = alpha,
                modifier = GlanceModifier.size(22.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            )
        }
    }
}

/** An 18dp primary badge inside a 2dp ring of the surface colour, so it stands off the icon. */
@Composable
private fun Badge(unread: Int) {
    val label = countLabel(unread)
    val singleDigit = label.length == 1
    Box(
        modifier = (if (singleDigit) GlanceModifier.size(18.dp) else GlanceModifier.height(18.dp))
            .background(GlanceTheme.colors.surface)
            .cornerRadius(9.dp)
            .padding(2.dp),
    ) {
        Box(
            modifier = (if (singleDigit) GlanceModifier.fillMaxSize() else GlanceModifier.fillMaxHeight().padding(horizontal = 4.dp))
                .background(GlanceTheme.colors.primary)
                .cornerRadius(7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            )
        }
    }
}
