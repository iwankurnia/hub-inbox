package com.iwankurnia.hub.ui.hub

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.iwankurnia.hub.PendingRequest
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.HubTab
import com.iwankurnia.hub.data.ItemState
import com.iwankurnia.hub.data.NotificationItem
import com.iwankurnia.hub.icon.AppIconCache
import com.iwankurnia.hub.ui.sheet.DetailSheet
import java.time.ZoneId
import kotlinx.coroutines.launch

/**
 * The home screen (spec 4.2, 4.4, 4.5): the "Hub" title, All / Unread / Deleted tabs, the app
 * chips and the date-banded stream with swipes, select mode and the detail sheet. A [request]
 * from a widget or notification is applied once and then reported through [onRequestConsumed].
 */
@Composable
fun HubScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    request: PendingRequest? = null,
    onRequestConsumed: () -> Unit = {},
    viewModel: HubViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val iconCache = remember { AppIconCache(context.applicationContext) }
    val zone = remember { ZoneId.systemDefault() }

    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val packageFilter by viewModel.packageFilter.collectAsStateWithLifecycle()
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val openItemId by viewModel.openItemId.collectAsStateWithLifecycle()
    val undo by viewModel.undo.collectAsStateWithLifecycle()
    val hiddenIds by viewModel.hiddenIds.collectAsStateWithLifecycle()
    val keepDeletedDays by viewModel.keepDeletedDays.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        nowMs = System.currentTimeMillis()
        onPauseOrDispose {}
    }
    LaunchedEffect(items.itemCount) { nowMs = System.currentTimeMillis() }

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var snoozePicker by remember { mutableStateOf(false) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        r.tab?.let(viewModel::selectTab)
        r.packageName?.let(viewModel::filterApp)
        (r.itemId ?: r.replyItemId)?.let(viewModel::open)
        onRequestConsumed()
        if (r.openSearch) onOpenSearch()
    }

    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = undoMessage(context, action, nowMs, zone),
            actionLabel = context.getString(R.string.action_undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.dismissUndo(action)
    }

    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    fun loadedItems(): List<NotificationItem> = items.itemSnapshotList.items

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                if (selection.isEmpty()) {
                    HubTopBar(
                        showOverflow = tab == HubTab.UNREAD,
                        onOpenSearch = onOpenSearch,
                        onOpenSettings = onOpenSettings,
                        onMarkAllRead = {
                            viewModel.markAllRead()
                            scope.launch { snackbar.showSnackbar(context.getString(R.string.snackbar_all_marked_read)) }
                        },
                    )
                } else {
                    val selectedItems = loadedItems().filter { it.id in selection }
                    SelectBar(
                        count = selection.size,
                        showReadAndSnooze = tab != HubTab.DELETED,
                        canOpenInApp = selectedItems.map { it.packageName }.distinct().size == 1,
                        onClose = viewModel::clearSelection,
                        onRead = viewModel::readSelected,
                        onSnooze = { snoozePicker = true },
                        onDelete = viewModel::deleteSelected,
                        onSelectAll = { viewModel.selectAll(loadedItems().filterNot { it.id in hiddenIds }.map { it.id }) },
                        onOpenInApp = {
                            selectedItems.firstOrNull()?.let { openApp(context, it.packageName) }
                            viewModel.clearSelection()
                        },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) { padding ->
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                HubTabs(tab = tab, unreadCount = unreadCount, onSelect = viewModel::selectTab)
                AppChips(
                    apps = apps,
                    selected = packageFilter,
                    iconCache = iconCache,
                    onSelect = viewModel::filterApp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (tab == HubTab.DELETED) {
                    DeletedBanner(keepDeletedDays = keepDeletedDays, onEmptyNow = { confirmEmpty = true })
                }
                HubList(
                    items = items,
                    tab = tab,
                    nowMs = nowMs,
                    zone = zone,
                    iconCache = iconCache,
                    selection = selection,
                    hiddenIds = hiddenIds,
                    viewModel = viewModel,
                )
            }
        }
        openItemId?.let { id -> DetailSheet(itemId = id, onDismiss = viewModel::closeSheet) }
    }

    if (snoozePicker) {
        SelectSnoozeSheet(
            zone = zone,
            onPick = { until ->
                snoozePicker = false
                viewModel.snoozeSelected(until)
            },
            onDismiss = { snoozePicker = false },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text(stringResource(R.string.empty_now_confirm_title)) },
            text = { Text(stringResource(R.string.empty_now_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    viewModel.emptyDeleted()
                    scope.launch { snackbar.showSnackbar(context.getString(R.string.snackbar_emptied)) }
                }) { Text(stringResource(R.string.action_empty_now)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.hub_action_cancel)) }
            },
        )
    }
}

@Composable
private fun HubTopBar(
    showOverflow: Boolean,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onMarkAllRead: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = stringResource(R.string.hub_title),
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onOpenSearch) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings))
        }
        if (showOverflow) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_mark_all_read)) },
                        onClick = {
                            menuOpen = false
                            onMarkAllRead()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HubTabs(tab: HubTab, unreadCount: Int, onSelect: (HubTab) -> Unit) {
    val tabs = listOf(HubTab.ALL to R.string.tab_all, HubTab.UNREAD to R.string.tab_unread, HubTab.DELETED to R.string.tab_deleted)
    PrimaryTabRow(selectedTabIndex = tabs.indexOfFirst { it.first == tab }) {
        tabs.forEach { (value, label) ->
            Tab(
                selected = value == tab,
                onClick = { onSelect(value) },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(label))
                        if (value == HubTab.UNREAD && unreadCount > 0) {
                            Badge(
                                containerColor = if (value == tab) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                contentColor = if (value == tab) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            ) { Text(unreadCount.toString()) }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun DeletedBanner(keepDeletedDays: Int, onEmptyNow: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (keepDeletedDays > 0) {
                    stringResource(R.string.deleted_banner, keepDeletedDays)
                } else {
                    stringResource(R.string.deleted_banner_forever)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onEmptyNow) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_empty_now), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun HubList(
    items: LazyPagingItems<NotificationItem>,
    tab: HubTab,
    nowMs: Long,
    zone: ZoneId,
    iconCache: AppIconCache,
    selection: Set<Long>,
    hiddenIds: Set<Long>,
    viewModel: HubViewModel,
) {
    if (items.itemCount == 0 && items.loadState.refresh is LoadState.NotLoading) {
        EmptyState(tab)
        return
    }
    val todayLabel = stringResource(R.string.date_today)
    val yesterdayLabel = stringResource(R.string.date_yesterday)
    val selecting = selection.isNotEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        for (index in 0 until items.itemCount) {
            val peeked = items.peek(index)
            if (peeked != null && peeked.id in hiddenIds) continue
            if (peeked != null) {
                val previous = previousVisible(items, index, hiddenIds)
                val at = bandTime(peeked)
                if (previous == null || !DateBands.sameBand(bandTime(previous), at, nowMs, zone)) {
                    stickyHeader(key = "band-${peeked.id}", contentType = "band") {
                        BandHeader(DateBands.forInstant(at, nowMs, zone, todayLabel = todayLabel, yesterdayLabel = yesterdayLabel))
                    }
                }
            }
            item(key = peeked?.id ?: "placeholder-$index", contentType = "row") {
                val item = items[index] ?: return@item
                SwipeRow(
                    readEnabled = tab != HubTab.DELETED,
                    stayAfterRead = tab == HubTab.ALL,
                    gesturesEnabled = !selecting,
                    onRead = { viewModel.swipeRead(item.id) },
                    onDelete = { if (tab == HubTab.DELETED) viewModel.purge(item.id) else viewModel.swipeDelete(item.id) },
                ) {
                    HubRow(
                        item = item,
                        nowMs = nowMs,
                        zone = zone,
                        iconCache = iconCache,
                        selected = item.id in selection,
                        onClick = { if (selecting) viewModel.toggleSelect(item.id) else viewModel.open(item.id) },
                        onLongClick = { viewModel.toggleSelect(item.id) },
                        onRestore = { viewModel.restore(item.id) },
                    )
                }
            }
        }
    }
}

/** The nearest row above [index] that is not hidden pending a purge; null at the top or before a placeholder. */
private fun previousVisible(items: LazyPagingItems<NotificationItem>, index: Int, hiddenIds: Set<Long>): NotificationItem? {
    var i = index - 1
    while (i >= 0) {
        val candidate = items.peek(i) ?: return null
        if (candidate.id !in hiddenIds) return candidate
        i--
    }
    return null
}

/** Deleted items band by when they were deleted, everything else by sort time. */
private fun bandTime(item: NotificationItem): Long =
    if (item.state == ItemState.DELETED) item.deletedAt ?: item.sortAt else item.sortAt

@Composable
private fun BandHeader(band: DateBand) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 5.dp),
    ) {
        Text(
            text = band.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = band.detail,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyState(tab: HubTab) {
    val text = when (tab) {
        HubTab.ALL -> R.string.empty_all
        HubTab.UNREAD -> R.string.empty_unread
        HubTab.DELETED -> R.string.empty_deleted
    }
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun undoMessage(context: Context, action: UndoAction, nowMs: Long, zone: ZoneId): String {
    val res = context.resources
    val single = action.count == 1 && action.label.isNotEmpty()
    return when (action.kind) {
        UndoKind.READ -> if (single) {
            res.getString(R.string.snackbar_marked_read, action.label)
        } else {
            res.getQuantityString(R.plurals.snackbar_n_marked_read, action.count, action.count)
        }
        UndoKind.DELETE -> if (single) {
            res.getString(R.string.snackbar_deleted, action.label)
        } else {
            res.getQuantityString(R.plurals.snackbar_n_deleted, action.count, action.count)
        }
        UndoKind.PURGE -> if (single) {
            res.getString(R.string.snackbar_deleted_for_good, action.label)
        } else {
            res.getQuantityString(R.plurals.snackbar_n_deleted, action.count, action.count)
        }
        UndoKind.RESTORE -> res.getString(R.string.snackbar_restored, action.label)
        UndoKind.SNOOZE -> {
            val until = DateBands.untilTime(action.untilMs ?: nowMs, nowMs, zone)
            if (action.count == 1) {
                res.getString(R.string.snackbar_snoozed, until)
            } else {
                res.getQuantityString(R.plurals.snackbar_n_snoozed, action.count, action.count, until)
            }
        }
    }
}

private fun openApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let(context::startActivity)
}
