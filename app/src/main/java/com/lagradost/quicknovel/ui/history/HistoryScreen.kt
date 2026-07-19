package com.lagradost.quicknovel.ui.history


import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponseAction
import com.lagradost.quicknovel.SearchResponseOperation
import com.lagradost.quicknovel.compose.ActionDialog
import com.lagradost.quicknovel.compose.BaseSearchBar
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.IsScrolling
import com.lagradost.quicknovel.compose.SinglePairSelectDialog
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.tachiyomi.AndroidPreferenceStore
import com.lagradost.quicknovel.tachiyomi.collectAsState
import com.lagradost.quicknovel.ui.common.SearchList
import com.lagradost.quicknovel.ui.common.SearchListRow
import com.lagradost.quicknovel.ui.download.DownloadPageAction
import com.lagradost.quicknovel.ui.download.ImmutableSearchList
import com.lagradost.quicknovel.ui.download.normalSortingMethods
import com.lagradost.quicknovel.ui.history.HistoryAction.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
fun HistoryScreen(
    state: HistoryState,
    action: (HistoryAction) -> Unit
) {
    LaunchedEffect(Unit) {
        action(Refresh)
    }

    val searchAction = remember<(SearchResponseAction) -> Unit> {
        { data ->
            action(ResultAction(data))
        }
    }

    val context = LocalContext.current
    val store = AndroidPreferenceStore(context)
    val historyIsRow = store.getBoolean(stringResource(R.string.history_list_view_key), true)
    val historyIsRowState by historyIsRow.collectAsState()

    var fabExpanded by remember { mutableStateOf(false) }
    HistoryDialog(state.dialog, action)
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(snapAnimationSpec = null)
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    action(ShowSorting)
                },
                containerColor = colors.surfaceVariant,
                contentColor = colors.onBackground,
                text = {
                    Text(stringResource(R.string.filter_dialog_sort_by))
                },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_sort_24dp),
                        contentDescription = stringResource(R.string.filter_dialog_sort_by)
                    )
                },
                expanded = fabExpanded
            )
        },
        topBar = {
            BaseSearchBar(
                content = {
                    Spacer(modifier = Modifier.height(5.dp))
                },
                onQueryChange = { query ->
                    action(Search(query))
                },
                onSearch = { query ->
                    action(Search(query))
                },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { action(AskDeleteAll) }) {
                            Icon(
                                painter = painterResource(R.drawable.clear_all_24px),
                                contentDescription = stringResource(R.string.history_more_options),
                                tint = colors.onBackground
                            )
                        }
                        IconButton(onClick = {
                            historyIsRow.set(!historyIsRowState)
                        }) {
                            Icon(
                                painter = painterResource(if (historyIsRowState) R.drawable.ic_baseline_grid_view_24 else R.drawable.ic_baseline_list_24),
                                contentDescription = stringResource(if (historyIsRowState) R.string.grid_view else R.string.list_view),
                                modifier = Modifier.size(24.dp),
                                tint = colors.onBackground
                            )
                        }
                    }

                },
                scrollBehavior = scrollBehavior,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search_icon),
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier.size(24.dp)
                    )
                },
            )
        }, modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        val lazyGridState: LazyGridState = rememberLazyGridState()
        lazyGridState.IsScrolling(up = {
            fabExpanded = true
        }, down = {
            fabExpanded = false
        })

        SearchList(
            isRow = historyIsRowState,
            lazyGridState = lazyGridState,
            modifier = Modifier
                .padding(innerPadding),
            state = state.history,
            searchAction = searchAction
        )
    }
}


@Composable
fun HistoryDialog(
    dialog: HistoryDialog?,
    action: (HistoryAction) -> Unit
) {
    when (dialog) {
        null -> {}
        is HistoryDialog.DeleteAll -> {
            ActionDialog(
                title = stringResource(R.string.remove_history),
                text = stringResource(R.string.remove_all_history),
                confirmText = stringResource(R.string.remove),
                dismissText = stringResource(R.string.cancel),
                dismiss = {
                    action(DismissDialog)
                },
                confirm = {
                    action(DeleteAll)
                }
            )
        }

        is HistoryDialog.DeleteItem -> {
            ActionDialog(
                title = stringResource(R.string.remove),
                text = stringResource(
                    R.string.remove_from_history_format,
                    dialog.about.name
                ),
                confirmText = stringResource(R.string.remove),
                dismissText = stringResource(R.string.cancel),
                dismiss = {
                    action(DismissDialog)
                },
                confirm = {
                    action(
                        ResultAction(
                            SearchResponseAction(
                                dialog.about,
                                SearchResponseOperation.Delete
                            )
                        )
                    )
                }
            )
        }

        is HistoryDialog.Sort -> {
            val data = normalSortingMethods
            SinglePairSelectDialog(
                entries = data.associate { (it.id to it.inverse) to stringResource(it.name) },
                selectedKey = dialog.method,
                title = stringResource(R.string.filter_dialog_sort_by),
                confirmText = stringResource(R.string.sort_apply),
                dismissText = stringResource(R.string.sort_cancel),
                dismiss = {
                    action(DismissDialog)
                },
                confirm = { key ->
                    action(SelectSortingMethod(key))
                    action(DismissDialog)
                }
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    CloudStreamTheme {
        HistoryScreen(state = HistoryState(), action = {})
    }
}
