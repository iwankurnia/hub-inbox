package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.iwankurnia.hub.R

/**
 * The top bar in select mode: × and "N selected", then Read, Delete and an overflow with
 * Select all and Open in app. [showRead] is off in the Deleted tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectBar(
    count: Int,
    showRead: Boolean,
    canOpenInApp: Boolean,
    onClose: () -> Unit,
    onRead: () -> Unit,
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
            if (showRead) {
                IconButton(onClick = onRead) {
                    Icon(Icons.Rounded.DoneAll, contentDescription = stringResource(R.string.action_mark_read))
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
