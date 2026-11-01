package com.iwankurnia.hub.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iwankurnia.hub.R
import com.iwankurnia.hub.ui.launchApp

/** Full-screen search (spec 4.7): search bar, app chips, recent searches or highlighted results; a tap opens the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val recents by viewModel.recents.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val packageFilter by viewModel.packageFilter.collectAsStateWithLifecycle()
    val openApp by viewModel.openApp.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    LaunchedEffect(openApp) {
        val packageName = openApp ?: return@LaunchedEffect
        launchApp(context, packageName)
        viewModel.appOpened()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding(),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.extraLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = viewModel::setQuery,
                onSearch = { viewModel.submit() },
                expanded = true,
                onExpandedChange = { if (!it) onBack() },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setQuery("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
                modifier = Modifier.focusRequester(focusRequester),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = packageFilter == null,
                    onClick = { viewModel.filterApp(null) },
                    label = { Text(stringResource(R.string.chip_every_app)) },
                )
            }
            items(apps, key = { it.packageName }) { app ->
                val label = remember(app.packageName) { viewModel.appLabel(app.packageName) }
                val icon = remember(app.packageName) { viewModel.appIcon(app.packageName) }
                FilterChip(
                    selected = packageFilter == app.packageName,
                    onClick = { viewModel.filterApp(app.packageName) },
                    label = { Text(label) },
                    leadingIcon = { AppIconImage(icon = icon, label = label, size = 18.dp) },
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

        if (query.trim().length < SearchViewModel.MIN_CHARS) {
            RecentSearches(recents = recents, onPick = { viewModel.setQuery(it); viewModel.submit() })
        } else {
            val now = remember(results) { System.currentTimeMillis() }
            val appCount = remember(results) { results.map { it.packageName }.distinct().size }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.search_results),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = stringResource(R.string.search_results_summary, results.size, appCount),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (results.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.search_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                itemsIndexed(results, key = { _, item -> item.id }) { index, item ->
                    val label = remember(item.packageName) { viewModel.appLabel(item.packageName) }
                    val icon = remember(item.packageName) { viewModel.appIcon(item.packageName) }
                    SearchResultRow(
                        item = item,
                        query = query,
                        appLabel = label,
                        icon = icon,
                        nowMs = now,
                        onClick = { viewModel.open(item.id) },
                    )
                    if (index < results.lastIndex) HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(recents: List<String>, onPick: (String) -> Unit) {
    if (recents.isEmpty()) return
    Column {
        Text(
            text = stringResource(R.string.search_recent),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn {
            items(recents) { recent ->
                ListItem(
                    headlineContent = { Text(recent) },
                    leadingContent = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.clickable { onPick(recent) },
                )
            }
        }
    }
}
