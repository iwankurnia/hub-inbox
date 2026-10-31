package com.iwankurnia.hub.ui.sheet

import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.data.ThreadEntry
import com.iwankurnia.hub.snooze.SnoozeTimes
import com.iwankurnia.hub.ui.theme.HubColors
import kotlinx.coroutines.launch

/** Everything the sheet body shows; assembled from [DetailViewModel]. */
data class DetailState(
    val item: NotificationItem,
    val appLabel: String,
    val icon: Drawable?,
    val thread: List<ThreadEntry>,
    val weekCount: Int,
    val canReply: Boolean,
    val liveActionTitles: List<String>,
    val snoozeOptions: List<SnoozeTimes.Option>,
    val nowMs: Long,
)

/** Callbacks from the sheet body. [onReply] returns false when the reply could not be sent. */
class DetailSheetActions(
    val onMarkRead: () -> Unit = {},
    val onSnooze: (Long) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onOpenSnooze: () -> Unit = {},
    val onOpenInApp: () -> Unit = {},
    val onReply: (String) -> Boolean = { false },
    val onLiveAction: (Int) -> Unit = {},
) {
    companion object {
        val None = DetailSheetActions()
    }
}

/** The tap-to-open bottom sheet of spec 4.3: header, thread, reply box, actions and snooze picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(itemId: Long, onDismiss: () -> Unit) {
    // One view model for every item; its flows follow setItem and stop soon after the sheet closes.
    val viewModel = hiltViewModel<DetailViewModel>()
    val item by viewModel.item.collectAsStateWithLifecycle()
    val thread by viewModel.thread.collectAsStateWithLifecycle()
    val weekCount by viewModel.weekCount.collectAsStateWithLifecycle()
    val canReply by viewModel.canReply.collectAsStateWithLifecycle()
    val snoozeOptions by viewModel.snoozeOptions.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) {
        viewModel.setItem(itemId)
        viewModel.onOpened()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        // Until the new item loads, the flows may still hold the previously opened one.
        val current = item?.takeIf { it.id == itemId } ?: return@ModalBottomSheet
        val packageName = current.packageName
        val appLabel = remember(packageName) { viewModel.appLabel(packageName) }
        val icon = remember(packageName) { viewModel.appIcon(packageName) }
        val liveActionTitles = remember(current.notifKey) { viewModel.liveActionTitles() }
        DetailSheetBody(
            state = DetailState(
                item = current,
                appLabel = appLabel,
                icon = icon,
                thread = thread,
                weekCount = weekCount,
                canReply = canReply,
                liveActionTitles = liveActionTitles,
                snoozeOptions = snoozeOptions,
                nowMs = viewModel.now(),
            ),
            actions = DetailSheetActions(
                onMarkRead = { viewModel.markRead(); close() },
                onSnooze = { viewModel.snooze(it); close() },
                onDelete = { viewModel.delete(); close() },
                onOpenSnooze = viewModel::refreshSnoozeOptions,
                onOpenInApp = viewModel::openInApp,
                onReply = viewModel::reply,
                onLiveAction = viewModel::runLiveAction,
            ),
        )
    }
}

/** The sheet's content without the sheet itself, so it can be tested on its own. */
@Composable
fun DetailSheetBody(state: DetailState, actions: DetailSheetActions, modifier: Modifier = Modifier) {
    var snoozeOpen by rememberSaveable { mutableStateOf(false) }
    var customOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(state)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.thread.forEach { entry -> Bubble(entry, state.nowMs) }
        }
        if (state.canReply) ReplyBox(onReply = actions.onReply)
        ActionChips(
            state = state,
            snoozeOpen = snoozeOpen,
            onToggleSnooze = {
                if (!snoozeOpen) actions.onOpenSnooze()
                snoozeOpen = !snoozeOpen
            },
            actions = actions,
        )
        if (snoozeOpen) {
            SnoozePicker(
                options = state.snoozeOptions,
                onPick = actions.onSnooze,
                onPickCustom = { customOpen = true },
            )
        }
    }

    if (customOpen) {
        SnoozeDateTimeDialog(
            nowMs = state.nowMs,
            onPick = { customOpen = false; actions.onSnooze(it) },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun Header(state: DetailState) {
    val weekLine = pluralStringResource(R.plurals.sheet_count_this_week, state.weekCount, state.weekCount)
    val context = listOfNotNull(
        state.item.subText?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.sheet_channel, it) },
        weekLine,
    ).joinToString(" · ")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AppIconImage(icon = state.icon, label = state.appLabel, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = state.appLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = context,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Bubble(entry: ThreadEntry, nowMs: Long) {
    val outgoing = entry is ThreadEntry.Outgoing
    val (sender, text, at) = when (entry) {
        is ThreadEntry.Incoming -> Triple(entry.item.sender, entry.item.bigText ?: entry.item.text, entry.item.postedAt)
        is ThreadEntry.Outgoing -> Triple(null, entry.reply.text, entry.reply.sentAt)
    }
    val shape = if (outgoing) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 6.dp, bottomEnd = 18.dp)
    }
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (outgoing) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
        ) {
            Surface(
                shape = shape,
                color = if (outgoing) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                    sender?.let {
                        Text(text = it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    Text(text = text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                text = timeLabel(at, nowMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ReplyBox(onReply: (String) -> Boolean) {
    var text by rememberSaveable { mutableStateOf("") }
    var failed by remember { mutableStateOf(false) }
    val send = {
        if (onReply(text)) {
            text = ""
            failed = false
        } else {
            failed = true
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; failed = false },
            placeholder = { Text(stringResource(R.string.reply_hint)) },
            isError = failed,
            supportingText = if (failed) {
                { Text(stringResource(R.string.reply_failed)) }
            } else {
                null
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = send, enabled = text.isNotBlank()) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = stringResource(R.string.action_send))
        }
    }
}

@Composable
private fun ActionChips(
    state: DetailState,
    snoozeOpen: Boolean,
    onToggleSnooze: () -> Unit,
    actions: DetailSheetActions,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AssistChip(
            onClick = actions.onMarkRead,
            label = { Text(stringResource(R.string.action_mark_read)) },
            leadingIcon = { ChipIcon { Icon(Icons.Rounded.MarkEmailRead, contentDescription = null) } },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = colors.primaryContainer,
                labelColor = colors.onPrimaryContainer,
                leadingIconContentColor = colors.onPrimaryContainer,
            ),
            border = null,
        )
        AssistChip(
            onClick = onToggleSnooze,
            label = { Text(stringResource(R.string.action_snooze)) },
            leadingIcon = { ChipIcon { Icon(Icons.Rounded.Snooze, contentDescription = null) } },
            colors = AssistChipDefaults.assistChipColors(
                containerColor = HubColors.snoozedChip,
                labelColor = HubColors.snoozedOnChip,
                leadingIconContentColor = HubColors.snoozedOnChip,
            ),
            border = if (snoozeOpen) BorderStroke(2.dp, HubColors.snoozedBar) else null,
        )
        AssistChip(
            onClick = actions.onDelete,
            label = { Text(stringResource(R.string.action_delete)) },
            leadingIcon = { ChipIcon { Icon(Icons.Rounded.Delete, contentDescription = null) } },
            colors = AssistChipDefaults.assistChipColors(
                labelColor = colors.error,
                leadingIconContentColor = colors.error,
            ),
            border = BorderStroke(1.dp, colors.error),
        )
        AssistChip(
            onClick = actions.onOpenInApp,
            label = { Text(stringResource(R.string.action_open_in, state.appLabel)) },
            trailingIcon = { ChipIcon { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) } },
        )
        state.liveActionTitles.forEachIndexed { index, title ->
            AssistChip(onClick = { actions.onLiveAction(index) }, label = { Text(title) })
        }
    }
}

@Composable
private fun ChipIcon(content: @Composable () -> Unit) {
    Box(modifier = Modifier.size(AssistChipDefaults.IconSize)) { content() }
}
