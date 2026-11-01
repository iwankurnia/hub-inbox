package com.iwankurnia.hub.widget

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.iwankurnia.hub.HubIntents
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.HubRepository
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.icon.AppIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.text.DateFormat

/** Vertical padding of the list widget, top plus bottom. */
internal val LIST_PADDING_DP = 16.dp

/** Height of the header row. */
internal val HEADER_DP = 28.dp

/** Height of one item row; the 3dp bar fills it. */
internal val ROW_DP = 40.dp

/** Extra height of the newest row for its Read chip. */
internal val CHIPS_DP = 26.dp

/** Height the list widget needs for [rows] item rows: padding, header, the newest row with chips, the rest plain. */
fun listHeightFor(rows: Int): Dp = LIST_PADDING_DP + HEADER_DP + CHIPS_DP + ROW_DP * rows

/**
 * Rows that fit in [size]: the newest row with its chips always, then as many plain rows as the height allows,
 * clamped to 1..6. The responsive sizes give 1, 2, 4 and 6 rows (spec 4.9: 4x2 two, 4x3 four, 4x4 six).
 */
fun rowsFor(size: DpSize): Int {
    val spare = size.height - listHeightFor(1)
    val extra = if (spare < 0.dp) 0 else (spare.value / ROW_DP.value).toInt()
    return (1 + extra).coerceIn(1, MAX_ROWS)
}

private const val MAX_ROWS = 6

/** The item a widget "Read" chip marks read. */
val ItemIdKey = ActionParameters.Key<Long>("itemId")

/** The multi-row widget: the newest unread items, with Read on the newest (spec 4.9). */
class HubListWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(listHeightFor(1), listHeightFor(2), listHeightFor(4), listHeightFor(6)).map { DpSize(250.dp, it) }.toSet(),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) = coroutineScope {
        val entryPoint = widgetEntryPoint(context)
        // One collection for the whole session: stateIn waits for the first state, then keeps following.
        // Room re-runs the count query on every change to the items table, so this also keeps a running
        // session current between pushes from GlanceWidgetRefresher.
        val icons = entryPoint.appIconCache()
        val states = listStates(entryPoint.repository(), context.dpToPx(ICON_DP), icons::iconBitmapOnMain)
            .stateIn(this)
        provideContent {
            val state by states.collectAsState()
            HubWidgetTheme { HubListContent(state) }
        }
    }
}

/**
 * The list widget's state on every unread-count change: the query runs on IO, and each icon comes from
 * [iconBitmap], which in production is [AppIconCache.iconBitmapOnMain] (icons are only drawn on the main thread).
 */
internal fun listStates(
    repository: HubRepository,
    iconPx: Int,
    iconBitmap: suspend (packageName: String, sizePx: Int) -> Bitmap?,
): Flow<ListState> =
    repository.unreadCount().map { unread ->
        val items = withContext(Dispatchers.IO) { repository.latestUnread(MAX_ROWS) }
        ListState(unread, items.map { item -> ListRow(item, iconBitmap(item.packageName, iconPx)) })
    }

/**
 * Marks one item read from the widget's "Read" chip, clears its system notification (spec 4.1), then
 * redraws the widget. Glance creates it through the no-argument constructor; tests replace [redraw],
 * which needs WorkManager.
 */
class MarkReadAction(
    private val redraw: suspend (Context, GlanceId) -> Unit = { context, glanceId ->
        HubListWidget().update(context, glanceId)
    },
) : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val itemId = parameters[ItemIdKey] ?: return
        val entryPoint = widgetEntryPoint(context)
        val repository = entryPoint.repository()
        repository.markRead(listOf(itemId))
        repository.notifKeys(listOf(itemId)).forEach(entryPoint.liveActions()::cancel)
        redraw(context, glanceId)
    }
}

internal data class ListState(val unread: Int, val rows: List<ListRow>)

internal data class ListRow(val item: NotificationItem, val icon: Bitmap?)

private const val ICON_DP = 28

@Composable
private fun HubListContent(state: ListState) {
    val rows = rowsFor(LocalSize.current)
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(28.dp)
            .padding(horizontal = 16.dp, vertical = LIST_PADDING_DP / 2),
    ) {
        Header(state.unread)
        if (state.rows.isEmpty()) {
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = LocalContext.current.getString(R.string.widget_empty),
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        } else {
            state.rows.take(rows).forEachIndexed { index, row -> ItemRow(row, showActions = index == 0) }
        }
    }
}

@Composable
private fun Header(unread: Int) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .height(HEADER_DP)
            .clickable(actionStartActivity(HubIntents.open(context))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_stat_hub),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
        )
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = context.getString(R.string.widget_header),
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
        )
        if (unread > 0) {
            Text(
                text = countLabel(unread),
                modifier = GlanceModifier.background(GlanceTheme.colors.primary)
                    .cornerRadius(10.dp)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                style = TextStyle(color = GlanceTheme.colors.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
        }
        Image(
            provider = ImageProvider(R.drawable.widget_ic_open_in_full),
            contentDescription = context.getString(R.string.widget_open_hub),
            modifier = GlanceModifier.size(16.dp),
            colorFilter = ColorFilter.tint(GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun ItemRow(row: ListRow, showActions: Boolean) {
    val context = LocalContext.current
    val item = row.item
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .height(if (showActions) ROW_DP + CHIPS_DP else ROW_DP)
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(HubIntents.open(context, itemId = item.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The 3dp unread bar fills the row, whose height is the same constant rowsFor counts with.
        Box(
            modifier = GlanceModifier.width(3.dp)
                .fillMaxHeight()
                .background(GlanceTheme.colors.primary)
                .cornerRadius(2.dp),
        ) {}
        Spacer(modifier = GlanceModifier.width(8.dp))
        AppIcon(row.icon)
        Spacer(modifier = GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    modifier = GlanceModifier.defaultWeight(),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = formatTime(item.sortAt),
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp),
                )
            }
            Text(
                text = item.sender?.let { "$it: ${item.text}" } ?: item.text,
                maxLines = 1,
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            )
            if (showActions) {
                Row(modifier = GlanceModifier.padding(top = 4.dp)) {
                    Chip(
                        label = context.getString(R.string.widget_action_read),
                        modifier = GlanceModifier.clickable(
                            actionRunCallback<MarkReadAction>(actionParametersOf(ItemIdKey to item.id)),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIcon(icon: Bitmap?) {
    if (icon != null) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(ICON_DP.dp),
        )
    } else {
        Box(
            modifier = GlanceModifier.size(ICON_DP.dp)
                .background(GlanceTheme.colors.secondaryContainer)
                .cornerRadius(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_stat_hub),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onSecondaryContainer),
            )
        }
    }
}

@Composable
private fun Chip(label: String, modifier: GlanceModifier) {
    Text(
        text = label,
        modifier = modifier.background(GlanceTheme.colors.primaryContainer)
            .cornerRadius(10.dp)
            .padding(horizontal = 10.dp, vertical = 3.dp),
        style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
}

/** Time of day for today's items, a short date otherwise. */
private fun formatTime(timeMs: Long): String =
    DateUtils.formatSameDayTime(timeMs, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()
