package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.iwankurnia.hub.R
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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

/** One fixed snooze option; [untilMs] is when the item wakes. */
data class SnoozeChoice(val labelRes: Int, val untilMs: Long)

/**
 * The fixed snooze options of spec 4.6: in 1 hour, this afternoon (14:00, only before 14:00),
 * tomorrow 08:00 and next Monday 08:00.
 */
fun snoozeChoices(nowMs: Long, zone: ZoneId): List<SnoozeChoice> {
    val now = Instant.ofEpochMilli(nowMs).atZone(zone)
    val afternoon = now.with(LocalTime.of(14, 0))
    val tomorrow = now.toLocalDate().plusDays(1).atTime(8, 0).atZone(zone)
    val monday = now.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(8, 0).atZone(zone)
    return listOfNotNull(
        SnoozeChoice(R.string.snooze_in_one_hour, nowMs + 60 * 60 * 1000L),
        SnoozeChoice(R.string.snooze_this_afternoon, afternoon.toInstant().toEpochMilli()).takeIf { now.isBefore(afternoon) },
        SnoozeChoice(R.string.snooze_tomorrow, tomorrow.toInstant().toEpochMilli()),
        SnoozeChoice(R.string.snooze_monday, monday.toInstant().toEpochMilli()),
    )
}

/** Stand-in snooze picker for select mode until the sheet track's picker is wired in. */
@Composable
fun SnoozeChoicesDialog(nowMs: Long, zone: ZoneId, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val choices = remember(nowMs, zone) { snoozeChoices(nowMs, zone) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snooze_until_header)) },
        text = {
            Column {
                choices.forEach { choice ->
                    ListItem(
                        headlineContent = { Text(stringResource(choice.labelRes)) },
                        trailingContent = { Text(DateBands.untilTime(choice.untilMs, nowMs, zone)) },
                        modifier = Modifier.clickable { onPick(choice.untilMs) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.hub_action_cancel)) }
        },
    )
}
