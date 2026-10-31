package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iwankurnia.hub.R
import com.iwankurnia.hub.snooze.SnoozeTimes
import com.iwankurnia.hub.ui.sheet.SnoozeDateTimeDialog
import com.iwankurnia.hub.ui.sheet.SnoozePicker
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * The top bar in select mode: × and "N selected", then Read, Snooze, Delete and an overflow with
 * Select all and Open in app. [showReadAndSnooze] is off in the Deleted tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectBar(
    count: Int,
    showReadAndSnooze: Boolean,
    canOpenInApp: Boolean,
    onClose: () -> Unit,
    onRead: () -> Unit,
    onSnooze: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onOpenInApp: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.selection_count, count), fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_close))
            }
        },
        actions = {
            if (showReadAndSnooze) {
                IconButton(onClick = onRead) {
                    Icon(Icons.Rounded.DoneAll, contentDescription = stringResource(R.string.action_mark_read))
                }
                IconButton(onClick = onSnooze) {
                    Icon(Icons.Rounded.Snooze, contentDescription = stringResource(R.string.action_snooze))
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.action_delete))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_select_all)) },
                        onClick = {
                            menuOpen = false
                            onSelectAll()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_open_in_app)) },
                        enabled = canOpenInApp,
                        onClick = {
                            menuOpen = false
                            onOpenInApp()
                        },
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    )
}

/**
 * The select bar's Snooze: the shared [SnoozePicker] in a bottom sheet like the detail sheet's, with
 * "Pick date & time…" opening [SnoozeDateTimeDialog]. Times are worked out from now each time it opens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectSnoozeSheet(onPick: (Long) -> Unit, onDismiss: () -> Unit, zone: ZoneId = ZoneId.systemDefault()) {
    val context = LocalContext.current
    val nowMs = remember { System.currentTimeMillis() }
    val options = remember(nowMs, zone) { SnoozeTimes.options(nowMs, zone, SnoozeTimes.Labels.from(context)) }
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val pick: (Long) -> Unit = { untilMs ->
        customOpen = false
        scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(untilMs) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        SnoozePicker(
            options = options,
            onPick = pick,
            onPickCustom = { customOpen = true },
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        )
    }

    if (customOpen) {
        SnoozeDateTimeDialog(nowMs = nowMs, onPick = pick, onDismiss = { customOpen = false }, zone = zone)
    }
}
