package com.iwankurnia.hub.widget

import android.content.Context
import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.DateFormat

/** Rows the list widget shows at [size]: 4x2 two, 4x3 four, 4x4 six (spec 4.9). */
fun rowsFor(size: DpSize): Int = when {
    size.height < 150.dp -> 2
    size.height < 260.dp -> 4
    else -> 6
}

/** The item a widget "Read" chip marks read. */
val ItemIdKey = ActionParameters.Key<Long>("itemId")

/** The multi-row widget: the newest unread items, with Reply and Read on the newest (spec 4.9). */
class HubListWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 300.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = widgetEntryPoint(context)
        val states = listStates(entryPoint.repository(), entryPoint.appIconCache(), context.dpToPx(ICON_DP))
        val initial = states.first()
        provideContent {
            // Room re-runs the count query on every change to the items table, so this also
            // keeps a running session current between pushes from GlanceWidgetRefresher.
            val state by remember { states }.collectAsState(initial)
            HubWidgetTheme { HubListContent(state) }
        }
    }

    private fun listStates(repository: HubRepository, icons: AppIconCache, iconPx: Int): Flow<ListState> =
        repository.unreadCount().map { unread ->
            val rows = repository.latestUnread(MAX_ROWS).map { item -> ListRow(item, icons.iconBitmap(item.packageName, iconPx)) }
            ListState(unread, rows)
        }

    private companion object {
        const val MAX_ROWS = 6
    }
}

/**
 * Marks one item read from the widget's "Read" chip, then redraws the widget. Glance creates it
 * through the no-argument constructor; tests replace [redraw], which needs WorkManager.
 */
class MarkReadAction(
    private val redraw: suspend (Context, GlanceId) -> Unit = { context, glanceId ->
        HubListWidget().update(context, glanceId)
    },
) : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val itemId = parameters[ItemIdKey] ?: return
        widgetEntryPoint(context).repository().markRead(listOf(itemId))
        redraw(context, glanceId)
    }
}

private data class ListState(val unread: Int, val rows: List<ListRow>)

private data class ListRow(val item: NotificationItem, val icon: Bitmap?)

private const val ICON_DP = 28

@Composable
private fun HubListContent(state: ListState) {
    val rows = rowsFor(LocalSize.current)
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.surface)
            .cornerRadius(28.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
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
            .padding(bottom = 6.dp)
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
            .padding(vertical = 4.dp)
            .clickable(actionStartActivity(HubIntents.open(context, itemId = item.id))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The 3dp unread bar; every row here is unread.
        Box(
            modifier = GlanceModifier.width(3.dp)
                .height(if (showActions) 62.dp else 34.dp)
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
                Row(modifier = GlanceModifier.padding(top = 6.dp)) {
                    // Reply is only offered when the source notification carries a reply action.
                    if (item.hasReply) {
                        Chip(
                            label = context.getString(R.string.widget_action_reply),
                            modifier = GlanceModifier.clickable(
                                actionStartActivity(HubIntents.open(context, replyItemId = item.id)),
                            ),
                        )
                        Spacer(modifier = GlanceModifier.width(6.dp))
                    }
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
            modifier = GlanceModifier.size(ICON_DP.dp).cornerRadius(8.dp),
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
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 11.sp, fontWeight = FontWeight.Bold),
    )
}

/** Time of day for today's items, a short date otherwise. */
private fun formatTime(timeMs: Long): String =
    DateUtils.formatSameDayTime(timeMs, System.currentTimeMillis(), DateFormat.SHORT, DateFormat.SHORT).toString()
