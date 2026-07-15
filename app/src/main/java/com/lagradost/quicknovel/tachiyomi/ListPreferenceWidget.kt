package com.lagradost.quicknovel.tachiyomi

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.SingleSelectDialog

@Composable
fun <T> ListPreferenceWidget(
    value: T,
    title: String,
    subtitle: String?,
    icon: Painter?,
    entries: Map<out T, String>,
    onValueChange: (T) -> Unit,
    iconProvider: (@Composable (key: T, value: String) -> Unit)? = null
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = title,
        subtitle = subtitle,
        icon = icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        SingleSelectDialog(
            dismiss = { isDialogShown = false },
            title = title,
            entries = entries,
            selectedKey = value,
            confirm = { key ->
                onValueChange(key)
                isDialogShown = false
            },
            confirmText = stringResource(R.string.ok),
            dismissText = stringResource(R.string.cancel),
            iconProvider = iconProvider
        )


        /*AlertDialog(
            onDismissRequest = { isDialogShown = false },
            title = { Text(text = title) },
            text = {
                Box {
                    val state = rememberLazyListState()
                    LazyColumn(state = state) {
                        entries.forEach { current ->
                            val isSelected = value == current.key
                            item {
                                DialogRow(
                                    label = current.value,
                                    isSelected = isSelected,
                                    onSelected = {
                                        onValueChange(current.key!!)
                                        isDialogShown = false
                                    },
                                )
                            }
                        }
                    }
                    if (!state.isScrolledToStart()) HorizontalDivider(modifier = Modifier.align(Alignment.TopCenter))
                    if (!state.isScrolledToEnd()) HorizontalDivider(modifier = Modifier.align(Alignment.BottomCenter))
                }
            },
            confirmButton = {
                TextButton(onClick = { isDialogShown = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )*/
    }
}

@Composable
private fun DialogRow(
    label: String,
    isSelected: Boolean,
    onSelected: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .selectable(
                selected = isSelected,
                onClick = { if (!isSelected) onSelected() },
            )
            .fillMaxWidth()
            .minimumInteractiveComponentSize(),
    ) {
        RadioButton(
            selected = isSelected,
            onClick = null,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge.merge(),
            modifier = Modifier.padding(start = 24.dp),
        )
    }
}
