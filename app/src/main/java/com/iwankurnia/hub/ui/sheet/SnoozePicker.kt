package com.iwankurnia.hub.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iwankurnia.hub.R
import com.iwankurnia.hub.snooze.SnoozeTimes
import com.iwankurnia.hub.ui.theme.HubColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private val CardShape = RoundedCornerShape(14.dp)

/**
 * The snooze choices of spec 4.6: amber cards in two columns, then a full-width "Pick date & time…".
 * Used inside the detail sheet and, after the merge, from the list's select bar.
 */
@Composable
fun SnoozePicker(
    options: List<SnoozeTimes.Option>,
    onPick: (Long) -> Unit,
    onPickCustom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.snooze_until_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { option ->
                    Surface(
                        onClick = { onPick(option.atMs) },
                        shape = CardShape,
                        color = HubColors.snoozedChip,
                        contentColor = HubColors.snoozedOnChip,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(text = option.label, style = MaterialTheme.typography.titleSmall)
                            Text(text = option.detail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
        Surface(
            onClick = onPickCustom,
            shape = CardShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.EditCalendar, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(text = stringResource(R.string.snooze_pick_date_time), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

/** A date picker followed by a time picker; [onPick] receives the chosen moment in [zone]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnoozeDateTimeDialog(
    nowMs: Long,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val today = remember(nowMs) { Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate() }
    val todayUtcMs = remember(today) { today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }

    val date = pickedDate
    if (date == null) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = todayUtcMs,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= todayUtcMs
                override fun isSelectableYear(year: Int) = year >= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        dateState.selectedDateMillis?.let { ms ->
                            pickedDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val timeState = rememberTimePickerState(initialHour = 8, initialMinute = 0, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.snooze_pick_date_time)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val at = date.atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone)
                        // Today at a time already gone would wake straight away; the dialog may also have been open a while.
                        onPick(SnoozeTimes.clampSnoozeTime(at.toInstant().toEpochMilli(), System.currentTimeMillis()))
                    },
                ) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
        ) {
            TimePicker(state = timeState)
        }
    }
}
