package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.icon.AppIconCache
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val IconSize = 34.dp
private val IconShape = RoundedCornerShape(9.dp)

/**
 * One BB10-style row: 4dp unread bar, 34dp app icon (or a tick when selected), title and time,
 * one-line preview, then the Restore chip in Deleted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HubRow(
    item: NotificationItem,
    nowMs: Long,
    zone: ZoneId,
    iconCache: AppIconCache,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val unread = !item.isRead && item.state == ItemState.ACTIVE
    val deleted = item.state == ItemState.DELETED
    val barColour = if (unread) colors.primary else Color.Transparent
    val unreadDescription = stringResource(R.string.cd_unread)
    val selectedDescription = stringResource(R.string.cd_selected)
    val state = listOfNotNull(
        unreadDescription.takeIf { unread },
        selectedDescription.takeIf { selected },
    ).joinToString()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(if (selected) colors.primaryContainer else colors.surfaceContainerLowest)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .semantics { stateDescription = state },
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(barColour),
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (deleted) 0.75f else 1f)
                    .padding(start = 8.dp, end = 16.dp, top = 9.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) SelectedTick() else AppIcon(item.packageName, iconCache, IconSize, greyscale = item.isRead)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.title,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = DateBands.rowTime(if (deleted) item.deletedAt ?: item.sortAt else item.sortAt, nowMs, zone),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (unread) colors.primary else colors.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = preview(item),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.5.sp),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (deleted) {
                        RestoreChip(onRestore)
                    }
                }
            }
        }
        HorizontalDivider(thickness = Dp.Hairline, color = colors.outlineVariant)
    }
}

private fun preview(item: NotificationItem): String =
    if (item.sender.isNullOrBlank()) item.text else "${item.sender}: ${item.text}"

@Composable
private fun SelectedTick() {
    Box(
        modifier = Modifier
            .size(IconSize)
            .clip(IconShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
    }
}

@Composable
private fun RestoreChip(onRestore: () -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        AssistChip(
            onClick = onRestore,
            label = { Text(stringResource(R.string.action_restore), fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
            leadingIcon = { Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(14.dp)) },
            modifier = Modifier.padding(top = 2.dp).height(26.dp),
            shape = RoundedCornerShape(13.dp),
            border = null,
            colors = AssistChipDefaults.assistChipColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                leadingIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
    }
}

/** The app's launcher icon in a rounded square. */
@Composable
fun AppIcon(packageName: String, iconCache: AppIconCache, size: Dp, greyscale: Boolean = false) {
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon = rememberAppIcon(packageName, iconCache, sizePx)
    val label = stringResource(R.string.cd_app_icon, iconCache.label(packageName))
    Box(
        modifier = Modifier
            .semantics { contentDescription = label }
            .size(size)
            .clip(if (size == IconSize) IconShape else RoundedCornerShape(size / 4))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(
                bitmap = icon,
                contentDescription = null,
                modifier = Modifier.size(size),
                // A read item keeps its icon but loses its colour, like a BB10 read row.
                colorFilter = if (greyscale) GreyscaleFilter else null,
            )
        } else {
            Text(
                text = iconCache.label(packageName).take(1).uppercase(),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.4f).sp,
            )
        }
    }
}

/** Loads the icon off the main thread; null until loaded or when the app is not installed. */
@Composable
fun rememberAppIcon(packageName: String, iconCache: AppIconCache, sizePx: Int): ImageBitmap? {
    val icon by produceState<ImageBitmap?>(initialValue = null, packageName, sizePx) {
        value = withContext(Dispatchers.IO) {
            iconCache.icon(packageName)?.toBitmap(sizePx, sizePx)?.asImageBitmap()
        }
    }
    return icon
}

/** Drops all saturation; applied to the app icon of a read item. */
private val GreyscaleFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
