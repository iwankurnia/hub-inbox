package com.iwankurnia.hub.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.AutoDelete
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.MarkEmailUnread
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.icon.AppIconCache
import com.iwankurnia.hub.ui.search.AppIconImage

private const val APPS_SHOWN = 5

/** True when Android has granted Hub notification access. */
fun hasListenerAccess(context: Context): Boolean =
    NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

/** Opens the system page where notification access is granted. */
fun openListenerSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
}

/** Settings in Android 16 grouped containers (spec 4.8). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val hideSilent by viewModel.hideSilent.collectAsStateWithLifecycle()
    val keepDays by viewModel.keepDeletedDays.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val clearShade by viewModel.clearShade.collectAsStateWithLifecycle()
    val unreadBadge by viewModel.unreadBadge.collectAsStateWithLifecycle()
    val iconCache = remember { AppIconCache(context.applicationContext) }
    var showAllApps by rememberSaveable { mutableStateOf(false) }
    var keepDialog by rememberSaveable { mutableStateOf(false) }
    var accessGranted by remember { mutableStateOf(hasListenerAccess(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { accessGranted = hasListenerAccess(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            SectionLabel(stringResource(R.string.settings_section_apps))
            Group {
                val shown = if (showAllApps) apps else apps.take(APPS_SHOWN)
                shown.forEachIndexed { index, (count, included) ->
                    if (index > 0) Hairline()
                    AppRow(count, included, iconCache) { viewModel.setAppIncluded(count.packageName, it) }
                }
                if (!showAllApps && apps.size > APPS_SHOWN) {
                    Hairline()
                    SettingsRow(
                        icon = Icons.Rounded.AddCircle,
                        title = stringResource(R.string.settings_more_apps, apps.size - APPS_SHOWN),
                        onClick = { showAllApps = true },
                        trailing = { Chevron() },
                    )
                }
            }

            SectionLabel(stringResource(R.string.settings_section_behaviour))
            Group {
                SettingsRow(
                    icon = Icons.Rounded.AutoDelete,
                    title = stringResource(R.string.settings_keep_deleted),
                    subtitle = keepLabel(keepDays),
                    onClick = { keepDialog = true },
                    trailing = { Chevron() },
                )
                Hairline()
                SettingsRow(
                    icon = Icons.Rounded.NotificationsOff,
                    title = stringResource(R.string.settings_hide_silent),
                    subtitle = stringResource(R.string.settings_hide_silent_summary),
                    onClick = { viewModel.setHideSilent(!hideSilent) },
                    trailing = { Switch(checked = hideSilent, onCheckedChange = viewModel::setHideSilent) },
                )
                Hairline()
                SettingsRow(
                    icon = Icons.Rounded.ClearAll,
                    title = stringResource(R.string.settings_clear_shade),
                    subtitle = stringResource(R.string.settings_clear_shade_summary),
                    onClick = { viewModel.setClearShade(!clearShade) },
                    trailing = { Switch(checked = clearShade, onCheckedChange = viewModel::setClearShade) },
                )
                Hairline()
                SettingsRow(
                    icon = Icons.Rounded.MarkEmailUnread,
                    title = stringResource(R.string.settings_unread_badge),
                    subtitle = stringResource(R.string.settings_unread_badge_summary),
                    onClick = { viewModel.setUnreadBadge(!unreadBadge) },
                    trailing = { Switch(checked = unreadBadge, onCheckedChange = viewModel::setUnreadBadge) },
                )
            }

            SectionLabel(stringResource(R.string.settings_section_system))
            Group {
                SettingsRow(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.settings_notification_access),
                    subtitle = stringResource(
                        if (accessGranted) R.string.settings_access_granted else R.string.settings_access_not_granted,
                    ),
                    subtitleColor = if (accessGranted) GrantedGreen else MaterialTheme.colorScheme.error,
                    onClick = { openListenerSettings(context) },
                    trailing = { Chevron() },
                )
                Hairline()
                val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                SettingsRow(
                    icon = Icons.Rounded.Palette,
                    title = stringResource(R.string.settings_theme),
                    subtitle = stringResource(
                        if (dynamicColor && dynamicAvailable) {
                            R.string.settings_theme_summary
                        } else {
                            R.string.settings_theme_summary_no_dynamic
                        },
                    ),
                    onClick = if (dynamicAvailable) ({ viewModel.setDynamicColor(!dynamicColor) }) else null,
                    trailing = {
                        Switch(
                            checked = dynamicColor && dynamicAvailable,
                            onCheckedChange = viewModel::setDynamicColor,
                            enabled = dynamicAvailable,
                        )
                    },
                )
            }
        }
    }

    if (keepDialog) {
        KeepDeletedDialog(
            current = keepDays,
            onPick = { viewModel.setKeepDeletedDays(it); keepDialog = false },
            onDismiss = { keepDialog = false },
        )
    }
}

private val GrantedGreen = Color(0xFF0B6B2E)

@Composable
private fun keepLabel(days: Int): String = stringResource(
    when (days) {
        7 -> R.string.settings_keep_deleted_7_days
        90 -> R.string.settings_keep_deleted_90_days
        SettingsViewModel.KEEP_FOREVER -> R.string.settings_keep_deleted_forever
        else -> R.string.settings_keep_deleted_30_days
    },
)

@Composable
private fun KeepDeletedDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_keep_deleted)) },
        text = {
            Column {
                SettingsViewModel.KEEP_OPTIONS.forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = days == current, onClick = { onPick(days) }, role = Role.RadioButton)
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = days == current, onClick = null)
                        Text(text = keepLabel(days), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun AppRow(count: AppCount, included: Boolean, iconCache: AppIconCache, onToggle: (Boolean) -> Unit) {
    val label = remember(count.packageName) { iconCache.label(count.packageName) }
    val icon = remember(count.packageName) { iconCache.icon(count.packageName) }
    SettingsRow(
        leading = { AppIconImage(icon = icon, label = label, size = 32.dp) },
        title = label,
        subtitle = if (included) {
            stringResource(R.string.settings_app_counts, count.total, count.unread)
        } else {
            stringResource(R.string.settings_app_hidden)
        },
        onClick = { onToggle(!included) },
        trailing = { Switch(checked = included, onCheckedChange = onToggle) },
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun Hairline() {
    HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One row of a grouped container: leading icon, title and subtitle, optional trailing control. */
@Composable
internal fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when {
            leading != null -> leading()
            icon != null -> Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (subtitleColor == Color.Unspecified) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        subtitleColor
                    },
                )
            }
        }
        trailing?.invoke()
    }
}
