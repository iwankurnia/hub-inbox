package com.iwankurnia.hub.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.iwankurnia.hub.R
import com.iwankurnia.hub.ui.settings.SettingsRow
import com.iwankurnia.hub.ui.settings.SettingsViewModel
import com.iwankurnia.hub.ui.settings.hasListenerAccess
import com.iwankurnia.hub.ui.settings.openListenerSettings
import kotlinx.coroutines.launch

/**
 * First run (mockup screen 0): explains why Hub needs notification access and links to the system
 * page. Once access is granted, asks to post its own notifications (API 33+) and calls [onDone].
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var finished by remember { mutableStateOf(false) }
    var askedToNotify by rememberSaveable { mutableStateOf(false) }

    val finish: () -> Unit = {
        if (!finished) {
            finished = true
            scope.launch {
                viewModel.completeOnboarding()
                onDone()
            }
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { finish() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (!hasListenerAccess(context)) return@LifecycleEventEffect
        if (!askedToNotify && needsNotificationPermission(context)) {
            askedToNotify = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stat_hub),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.onboarding_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(28.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SettingsRow(
                        icon = Icons.Rounded.Notifications,
                        title = stringResource(R.string.onboarding_access_title),
                        subtitle = stringResource(R.string.onboarding_access_summary),
                        onClick = { openListenerSettings(context) },
                        trailing = { Chevron() },
                    )
                    HorizontalDivider(thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow(
                        icon = Icons.Rounded.Alarm,
                        title = stringResource(R.string.onboarding_alarms_title),
                        subtitle = stringResource(R.string.onboarding_alarms_summary),
                        onClick = { openExactAlarmSettings(context) },
                        trailing = { Chevron() },
                    )
                }
            }
            Button(
                onClick = { openListenerSettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .height(56.dp),
            ) {
                Text(stringResource(R.string.onboarding_allow_access))
            }
        }
    }
}

@Composable
private fun Chevron() {
    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/** On API 31+, opens "Alarms & reminders" for Hub when exact alarms are not yet allowed. */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    if (alarmManager.canScheduleExactAlarms()) return
    context.startActivity(
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
    )
}
