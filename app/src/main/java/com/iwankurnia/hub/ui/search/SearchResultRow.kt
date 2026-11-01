package com.iwankurnia.hub.ui.search

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iwankurnia.hub.data.NotificationItem

/**
 * One search hit, in the list row's anatomy: 3dp unread bar, 32dp icon, title and time,
 * preview, with the query tokens on a grey highlight.
 */
@Composable
fun SearchResultRow(
    item: NotificationItem,
    query: String,
    appLabel: String,
    icon: Drawable?,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val unread = !item.isRead
    val highlight = SpanStyle(background = colors.surfaceContainerHigh)
    val title = remember(item.title, query, highlight) { highlightMatches(item.title, query, highlight) }
    val preview = remember(item.text, query, highlight) { highlightMatches(item.text, query, highlight) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(if (unread) colors.onSurface else Color.Transparent),
        )
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconImage(icon = icon, label = appLabel, size = 32.dp, greyscale = item.isRead)
            Column(modifier = Modifier.weight(1f)) {
                Row {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 19.sp, letterSpacing = 0.sp),
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).alignByBaseline(),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeLabel(item.sortAt, nowMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (unread) colors.onSurface else colors.secondary,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
                    color = if (unread) colors.onSurface.copy(alpha = 0.85f) else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

/** [text] with every case-insensitive occurrence of each whitespace-separated token of [query] styled. */
fun highlightMatches(text: String, query: String, style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    query.split(Regex("\\s+")).filter(String::isNotEmpty).forEach { token ->
        var from = text.indexOf(token, ignoreCase = true)
        while (from >= 0) {
            addStyle(style, from, from + token.length)
            from = text.indexOf(token, startIndex = from + token.length, ignoreCase = true)
        }
    }
}
