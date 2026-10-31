package com.iwankurnia.hub.ui.search

import android.graphics.drawable.Drawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.ui.sheet.AppIconImage
import com.iwankurnia.hub.ui.sheet.timeLabel

/** One search hit, in the list row's anatomy: unread bar, icon, title and time, highlighted preview. */
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
    val highlight = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer)
    val title = remember(item.title, query, highlight) { highlightMatches(item.title, query, highlight) }
    val preview = remember(item.text, query, highlight) { highlightMatches(item.text, query, highlight) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (item.isRead) Color.Transparent else MaterialTheme.colorScheme.primary),
        )
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIconImage(icon = icon, label = appLabel, size = 34.dp, greyscale = item.isRead)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = timeLabel(item.sortAt, nowMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
