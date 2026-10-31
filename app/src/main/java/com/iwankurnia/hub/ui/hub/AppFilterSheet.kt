package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.icon.AppIconCache

/**
 * The app filter, opened from the top bar: "Every app" first, then one row per app with items,
 * most recently seen first, each with its unread count. Picking a row applies the filter and
 * closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterSheet(
    apps: List<AppCount>,
    selected: String?,
    iconCache: AppIconCache,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.filter_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn {
                item(key = "every-app") {
                    FilterRow(
                        label = stringResource(R.string.chip_every_app),
                        selected = selected == null,
                        unread = apps.sumOf { it.unread },
                        leading = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                        onClick = { onSelect(null); onDismiss() },
                    )
                }
                items(apps, key = { it.packageName }) { app ->
                    val label = remember(app.packageName) { iconCache.label(app.packageName) }
                    FilterRow(
                        label = label,
                        selected = app.packageName == selected,
                        unread = app.unread,
                        leading = { AppIcon(app.packageName, iconCache, size = 28.dp) },
                        onClick = { onSelect(app.packageName); onDismiss() },
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    label: String,
    selected: Boolean,
    unread: Int,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = leading,
        trailingContent = {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            } else if (unread > 0) {
                Badge { Text(unread.toString()) }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
