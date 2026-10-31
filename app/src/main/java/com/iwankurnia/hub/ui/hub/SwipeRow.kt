package com.iwankurnia.hub.ui.hub

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.iwankurnia.hub.R
import com.iwankurnia.hub.ui.theme.HubColors
import kotlinx.coroutines.launch

/**
 * Swipe right to read (green reveal), swipe left to delete (error-container reveal). With
 * [readEnabled] off (the Deleted tab) only the left swipe works. [stayAfterRead] says whether the
 * row remains in the list after it is read, in which case it springs back into place.
 */
@Composable
fun SwipeRow(
    readEnabled: Boolean,
    stayAfterRead: Boolean,
    gesturesEnabled: Boolean,
    onRead: () -> Unit,
    onDelete: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = readEnabled,
        gesturesEnabled = gesturesEnabled,
        onDismiss = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onRead()
                    if (stayAfterRead) scope.launch { state.reset() }
                }
                SwipeToDismissBoxValue.EndToStart -> onDelete()
                SwipeToDismissBoxValue.Settled -> Unit
            }
        },
        backgroundContent = {
            SwipeReveal(direction = state.dismissDirection, armed = state.targetValue != SwipeToDismissBoxValue.Settled)
        },
    ) {
        content()
    }
}

@Composable
private fun SwipeReveal(direction: SwipeToDismissBoxValue, armed: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (armed) 1.2f else 0.9f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "revealScale",
    )
    val reading = direction == SwipeToDismissBoxValue.StartToEnd
    val deleting = direction == SwipeToDismissBoxValue.EndToStart
    val background = when {
        reading -> HubColors.readReveal
        deleting -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val foreground = if (reading) HubColors.onReadReveal else MaterialTheme.colorScheme.onErrorContainer
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(background)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, if (reading) Alignment.Start else Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reading) {
            Icon(Icons.Rounded.MarkEmailRead, contentDescription = null, tint = foreground, modifier = Modifier.scale(scale))
            Text(stringResource(R.string.action_read), color = foreground, fontWeight = FontWeight.SemiBold)
        } else if (deleting) {
            Text(stringResource(R.string.action_delete), color = foreground, fontWeight = FontWeight.SemiBold)
            Icon(Icons.Rounded.Delete, contentDescription = null, tint = foreground, modifier = Modifier.scale(scale))
        }
    }
}
