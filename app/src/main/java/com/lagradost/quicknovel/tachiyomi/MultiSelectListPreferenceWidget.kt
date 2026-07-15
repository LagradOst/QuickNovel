package com.lagradost.quicknovel.tachiyomi

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.MultiSelectDialog
import kotlinx.collections.immutable.toPersistentSet

@Composable
fun MultiSelectListPreferenceWidget(
    preference: Preference.PreferenceItem.MultiSelectListPreference,
    values: Set<String>,
    onValuesChange: (Set<String>) -> Unit,
) {
    var isDialogShown by remember { mutableStateOf(false) }

    TextPreferenceWidget(
        title = preference.title,
        subtitle = preference.subtitleProvider(values, preference.entries),
        icon = preference.icon,
        onPreferenceClick = { isDialogShown = true },
    )

    if (isDialogShown) {
        MultiSelectDialog(
            title = preference.title,
            entries = preference.entries,
            properties = DialogProperties(
                usePlatformDefaultWidth = true,
            ),
            confirmText = stringResource(R.string.ok),
            confirm = { selection : Set<String> ->
                onValuesChange(selection)
                isDialogShown = false
            },
            dismissText = stringResource(R.string.cancel),
            dismiss = {
                isDialogShown = false
            },
            selectedKeys = values.toPersistentSet(),
        )
    }
}
