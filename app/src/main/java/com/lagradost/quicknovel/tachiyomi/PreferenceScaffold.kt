package com.lagradost.quicknovel.tachiyomi

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

@Composable
fun PreferenceScaffold(
    titleRes: String,
    actions: @Composable RowScope.() -> Unit = {},
    onBackPressed: (() -> Unit)? = null,
    itemsProvider: @Composable () -> List<Preference>,
) {
    Scaffold(
        /*topBar = {
            AppBar(
                title = titleRes,
                navigateUp = onBackPressed,
                actions = actions,
                scrollBehavior = it,
            )
        },*/
        content = { contentPadding ->
            PreferenceScreen(
                items = itemsProvider(),
                contentPadding = contentPadding,
            )
        },
    )
}