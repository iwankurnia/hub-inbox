package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Badge
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.icon.AppIconCache
import kotlinx.coroutines.launch

/**
 * The app filter, opened from the top bar: "Every app" first, then one row per app with items, most
 * recently seen first, each with its unread count. "Every app" clears the filter and closes the sheet;
 * an app row ticks or unticks that app and leaves the sheet open; Done closes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppFilterSheet(
    apps: List<AppCount>,
    selected: Set<String>,
    iconCache: AppIconCache,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { onDismiss() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            Text(
                text = stringResource(R.string.filter_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                item(key = "every-app") {
                    FilterRow(
                        label = stringResource(R.string.chip_every_app),
                        selected = selected.isEmpty(),
                        unread = apps.sumOf { it.unread },
                        leading = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                        trailing = { if (selected.isEmpty()) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        onClick = { onClear(); close() },
                    )
                }
                items(apps, key = { it.packageName }) { app ->
                    val label = remember(app.packageName) { iconCache.label(app.packageName) }
                    val ticked = app.packageName in selected
                    FilterRow(
                        label = label,
                        selected = ticked,
                        unread = app.unread,
                        leading = { AppIcon(app.packageName, iconCache, size = 28.dp) },
                        trailing = { Checkbox(checked = ticked, onCheckedChange = { onToggle(app.packageName) }) },
                        onClick = { onToggle(app.packageName) },
                    )
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = close) { Text(stringResource(R.string.action_done)) }
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
    trailing: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        leadingContent = leading,
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (unread > 0) Badge { Text(unread.toString()) }
                trailing()
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
