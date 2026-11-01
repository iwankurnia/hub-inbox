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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.iwankurnia.hub.ui.launchApp
import java.time.ZoneId
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * The home screen (spec 4.2, 4.4, 4.5): the "Hub" title, All / Unread / Deleted tabs, the app
 * chips and the date-banded stream with swipes and select mode; tapping a row opens it in its app
 * (spec 4.3). A [request] from a widget is applied once and then reported through [onRequestConsumed].
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
    val openApp by viewModel.openApp.collectAsStateWithLifecycle()
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
    var confirmEmpty by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }

    LaunchedEffect(request) {
        val r = request ?: return@LaunchedEffect
        r.tab?.let(viewModel::selectTab)
        r.packageName?.let(viewModel::selectOnlyApp)
        r.itemId?.let(viewModel::open)
        onRequestConsumed()
        if (r.openSearch) onOpenSearch()
    }

    LaunchedEffect(openApp) {
        val packageName = openApp ?: return@LaunchedEffect
        launchApp(context, packageName)
        viewModel.appOpened()
    }

    LaunchedEffect(undo) {
        val action = undo ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = undoMessage(context, action),
            actionLabel = context.getString(R.string.action_undo),
            duration = SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.dismissUndo(action)
    }

    BackHandler(enabled = selection.isNotEmpty()) { viewModel.clearSelection() }

    fun loadedItems(): List<NotificationItem> = items.itemSnapshotList.items

    Scaffold(
        topBar = {
            if (selection.isEmpty()) {
                HubTopBar(
                    canMarkAllRead = tab != HubTab.DELETED && unreadCount > 0,
                    filterActive = packageFilter.isNotEmpty(),
                    onOpenFilter = { filterOpen = true },
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
                    showRead = tab != HubTab.DELETED,
                    canOpenInApp = selectedItems.map { it.packageName }.distinct().size == 1,
                    onClose = viewModel::clearSelection,
                    onRead = viewModel::readSelected,
                    onDelete = viewModel::deleteSelected,
                    onSelectAll = { viewModel.selectAll(loadedItems().filterNot { it.id in hiddenIds }.map { it.id }) },
                    onOpenInApp = {
                        selectedItems.firstOrNull()?.let { launchApp(context, it.packageName) }
                        viewModel.clearSelection()
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            HubTabs(tab = tab, unreadCount = unreadCount, onSelect = viewModel::selectTab)
            if (packageFilter.isNotEmpty()) {
                ActiveFilterChip(
                    label = filterLabel(context, packageFilter.map(iconCache::label).sorted()),
                    onClear = viewModel::clearFilter,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
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

    if (filterOpen) {
        AppFilterSheet(
            apps = apps,
            selected = packageFilter,
            iconCache = iconCache,
            onToggle = viewModel::toggleApp,
            onClear = viewModel::clearFilter,
            onDismiss = { filterOpen = false },
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

private val TopBarHeight = 56.dp

/** Bar icons sit a step quieter than the title (the mockup's #44474E on light). */
private const val BarIconAlpha = 0.8f

/** The compact 56dp bar: "Hub", then filter (dotted while a filter is on), search and the overflow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubTopBar(
    canMarkAllRead: Boolean,
    filterActive: Boolean,
    onOpenFilter: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onMarkAllRead: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    TopAppBar(
        title = { Text(stringResource(R.string.hub_title), style = MaterialTheme.typography.titleLarge) },
        actions = {
            IconButton(onClick = onOpenFilter) {
                BadgedBox(badge = { if (filterActive) Badge() }) {
                    Icon(Icons.Rounded.FilterList, contentDescription = stringResource(R.string.action_filter))
                }
            }
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (canMarkAllRead) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_mark_all_read)) },
                            onClick = {
                                menuOpen = false
                                onMarkAllRead()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_settings)) },
                        onClick = {
                            menuOpen = false
                            onOpenSettings()
                        },
                    )
                }
            }
        },
        expandedHeight = TopBarHeight,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = colors.surface,
            scrolledContainerColor = colors.surface,
            titleContentColor = colors.onSurface,
            actionIconContentColor = colors.onSurface.copy(alpha = BarIconAlpha),
        ),
    )
}

/** Left-aligned text tabs: the active one in onSurface over a 2dp line, the unread count as a plain number. */
@Composable
private fun HubTabs(tab: HubTab, unreadCount: Int, onSelect: (HubTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tabs = listOf(
        HubTab.ALL to R.string.tab_all,
        HubTab.UNREAD to R.string.tab_unread,
        HubTab.DELETED to R.string.tab_deleted,
    )
    Column(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            tabs.forEach { (value, label) ->
                val selected = value == tab
                val indicator = colors.onSurface
                Row(
                    modifier = Modifier
                        .selectable(selected = selected, onClick = { onSelect(value) }, role = Role.Tab)
                        .drawBehind {
                            if (selected) {
                                val height = 2.dp.toPx()
                                drawRoundRect(
                                    color = indicator,
                                    topLeft = Offset(0f, size.height - height),
                                    size = Size(size.width, height),
                                    cornerRadius = CornerRadius(height, height),
                                )
                            }
                        }
                        .padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 11.dp),
                ) {
                    Text(
                        text = stringResource(label),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.alignByBaseline(),
                    )
                    if (value == HubTab.UNREAD && unreadCount > 0) {
                        Text(
                            text = unreadCount.toString(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 5.dp).alignByBaseline(),
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
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
            TextButton(
                onClick = onEmptyNow,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
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
                    stickyHeader(key = "band-$tab-${peeked.id}", contentType = "band") {
                        BandHeader(DateBands.forInstant(at, nowMs, zone, todayLabel = todayLabel, yesterdayLabel = yesterdayLabel))
                    }
                }
            }
            item(key = peeked?.let { "row-$tab-${it.id}" } ?: "placeholder-$index", contentType = "row") {
                val item = items[index] ?: return@item
                // A hairline between rows of one band; none above the next band or at the end.
                val next = nextVisible(items, index, hiddenIds)
                val divider = next != null && DateBands.sameBand(bandTime(item), bandTime(next), nowMs, zone)
                SwipeRow(
                    readEnabled = tab != HubTab.DELETED,
                    isRead = item.isRead,
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
                        showDivider = divider,
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

/** The nearest row below [index] that is not hidden pending a purge; null at the end or before a placeholder. */
private fun nextVisible(items: LazyPagingItems<NotificationItem>, index: Int, hiddenIds: Set<Long>): NotificationItem? {
    var i = index + 1
    while (i < items.itemCount) {
        val candidate = items.peek(i) ?: return null
        if (candidate.id !in hiddenIds) return candidate
        i++
    }
    return null
}

/** Deleted items band by when they were deleted, everything else by sort time. */
private fun bandTime(item: NotificationItem): Long =
    if (item.state == ItemState.DELETED) item.deletedAt ?: item.sortAt else item.sortAt

/** A grey strip: the band label in letter-spaced capitals on the left, the short date on the right. */
@Composable
private fun BandHeader(band: DateBand) {
    val colour = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = band.label.uppercase(Locale.getDefault()),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            color = colour,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = band.detail,
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colour,
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

private fun undoMessage(context: Context, action: UndoAction): String {
    val res = context.resources
    val single = action.count == 1 && action.label.isNotEmpty()
    return when (action.kind) {
        UndoKind.READ -> if (single) {
            res.getString(R.string.snackbar_marked_read, action.label)
        } else {
            res.getQuantityString(R.plurals.snackbar_n_marked_read, action.count, action.count)
        }
        UndoKind.UNREAD -> if (single) {
            res.getString(R.string.snackbar_marked_unread, action.label)
        } else {
            res.getQuantityString(R.plurals.snackbar_n_marked_unread, action.count, action.count)
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
    }
}

/** "Only WhatsApp" for one app, "WhatsApp, Teams" for two, "N apps" for three or more. */
internal fun filterLabel(context: Context, labels: List<String>): String = when (labels.size) {
    1 -> context.getString(R.string.filter_active, labels[0])
    2 -> context.getString(R.string.filter_active_two, labels[0], labels[1])
    else -> context.resources.getQuantityString(R.plurals.filter_active_many, labels.size, labels.size)
}

/** The one chip shown under the tabs while an app filter is active; × clears it. */
@Composable
private fun ActiveFilterChip(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    InputChip(
        selected = true,
        onClick = onClear,
        label = { Text(label) },
        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear)) },
        colors = InputChipDefaults.inputChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedLabelColor = MaterialTheme.colorScheme.onSurface,
            selectedTrailingIconColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = null,
        modifier = modifier,
    )
}
