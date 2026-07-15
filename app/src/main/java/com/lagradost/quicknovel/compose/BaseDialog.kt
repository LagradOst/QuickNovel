package com.lagradost.quicknovel.compose

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseStyles.blackButtonColors
import com.lagradost.quicknovel.compose.BaseStyles.whiteButtonColors
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors

@Composable
fun ActionDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,

    dismiss: () -> Unit,
    confirm: () -> Unit
) {
    AlertDialog(
        containerColor = colors.background,
        onDismissRequest = dismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            Button(
                onClick = confirm,
                colors = whiteButtonColors
            ) { Text(text = confirmText) }
        },
        dismissButton = {
            Button(
                onClick = dismiss, colors = blackButtonColors
            ) { Text(text = dismissText) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleSelectDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    /* visual */
    entries: List<String>,
    /* Selected keys used in entries */
    selectedIndex: Int,
    confirmText: String? = null,
    dismissText: String? = null,
    dismiss: () -> Unit,
    confirm: (Int) -> Unit
) {
    SingleSelectDialog(
        modifier = modifier,
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        tonalElevation = tonalElevation,
        icon = icon,
        title = title,
        properties = properties,
        entries = entries.mapIndexed { index, string -> index to string }.associate { it },
        selectedKey = selectedIndex,
        confirmText = confirmText,
        dismissText = dismissText,
        dismiss = dismiss,
        confirm = confirm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SingleSelectDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    /* key - visual */
    entries: Map<out T, String>,
    /* Selected keys used in entries */
    selectedKey: T,
    confirmText: String? = null,
    dismissText: String? = null,
    dismiss: () -> Unit,
    confirm: (T) -> Unit
) {
    val selected = remember { mutableStateOf(selectedKey) }

    AlertDialog(
        properties = properties,
        tonalElevation = tonalElevation,
        modifier = modifier,
        icon = icon,
        shape = shape,
        iconContentColor = iconContentColor,
        containerColor = containerColor,
        onDismissRequest = dismiss,
        title = title?.let { { Text(text = it) } },
        text = {
            LazyColumn {
                entries.forEach { (key, value) ->
                    item(key = key) {
                        val isSelected = selected.value == key
                        SingleSelectionItem(isSelected,value) {
                            if(confirmText == null) {
                                confirm(key)
                            } else {
                                selected.value = key
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirmText != null) {
                Button(
                    onClick = {
                        confirm(selected.value)
                    },
                    colors = whiteButtonColors
                ) { Text(text = confirmText) }
            }
        },
        dismissButton = {
            if (dismissText != null) {
                Button(
                    onClick = dismiss, colors = blackButtonColors
                ) { Text(text = dismissText) }
            }
        }
    )
}

@Composable
fun SingleSelectionItem(isSelected : Boolean, text : String, onClick :  () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                }
            )
            .rounded()
            .ripple(interactionSource),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelected) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_check_24_listview),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colors.onBackground
            )
            Text(
                text,
                color = colors.onBackground,
                modifier = Modifier.padding(15.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(24.dp))
            Text(
                text,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(15.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> MultiSelectDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: String? = null,
    properties: DialogProperties = DialogProperties(),
    /* key - visual */
    entries: Map<out T, String>,
    /* Selected keys used in entries */
    selectedKeys: Set<T>,
    confirmText: String? = null,
    dismissText: String? = null,
    dismiss: () -> Unit,
    confirm: (Set<T>) -> Unit
) {
    val selected = remember {
        entries.keys
            .filter { selectedKeys.contains(it) }
            .toMutableStateList()
    }

    AlertDialog(
        properties = properties,
        tonalElevation = tonalElevation,
        modifier = modifier,
        icon = icon,
        shape = shape,
        iconContentColor = iconContentColor,
        containerColor = containerColor,
        onDismissRequest = {
            if (confirmText == null) {
                confirm(selected.toSet())
            } else {
                dismiss()
            }
        },
        title = title?.let { { Text(text = it) } },
        text = {
            LazyColumn {
                entries.forEach { (key, value) ->
                    item(key = key) {
                        val isSelected = selected.contains(key)
                        SingleSelectionItem(isSelected,value) {
                            if (isSelected) {
                                selected.remove(key)
                            } else {
                                selected.add(key)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (confirmText != null) {
                Button(
                    onClick = {
                        confirm(selected.toSet())
                    },
                    colors = whiteButtonColors
                ) { Text(text = confirmText) }
            }
        },
        dismissButton = {
            if (dismissText != null) {
                Button(
                    onClick = dismiss, colors = blackButtonColors
                ) { Text(text = dismissText) }
            }
        }
    )
}