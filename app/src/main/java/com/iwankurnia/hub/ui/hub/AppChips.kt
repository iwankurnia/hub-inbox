package com.iwankurnia.hub.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iwankurnia.hub.R
import com.iwankurnia.hub.data.AppCount
import com.iwankurnia.hub.icon.AppIconCache

private val ChipShape = RoundedCornerShape(10.dp)

/** "Every app", then one tonal chip per app with items, most recently seen first. */
@Composable
fun AppChips(
    apps: List<AppCount>,
    selected: String?,
    iconCache: AppIconCache,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "every-app") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.chip_every_app)) },
                shape = ChipShape,
            )
        }
        items(apps, key = { it.packageName }) { app ->
            val label = remember(app.packageName) { iconCache.label(app.packageName) }
            val isSelected = app.packageName == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(if (isSelected) null else app.packageName) },
                label = { Text(label) },
                leadingIcon = { AppIcon(app.packageName, iconCache, size = 14.dp) },
                shape = ChipShape,
            )
        }
    }
}
