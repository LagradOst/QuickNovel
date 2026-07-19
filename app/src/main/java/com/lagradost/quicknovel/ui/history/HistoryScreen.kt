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
import com.lagradost.quicknovel.ui.download.DownloadPageAction
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
                    IconButton(onClick = { action(HistoryAction.AskDeleteAll) }) {
                        Icon(
                            painter = painterResource(R.drawable.clear_all_24px),
                            contentDescription = stringResource(R.string.history_more_options),
                            tint = colors.onBackground
                        )
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
        val lazyState = rememberLazyListState()
        lazyState.IsScrolling(up = {
            fabExpanded = true
        }, down = {
            fabExpanded = false
        })

        LazyColumn(
            state = lazyState,
            modifier = Modifier
                .padding(innerPadding),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(
                state.history.sorted,
                key = { id -> id },
                contentType = { id -> state.history.data[id] }) { id ->
                SearchResponseRow(
                    response = state.history.data[id]!!,
                    action = searchAction,
                    modifier = Modifier.animateItem()
                )
            }
        }
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
                    action(HistoryAction.DismissDialog)
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


@Composable
fun SearchResponseRow(
    response: ImmutableSearchResponse,
    action: (SearchResponseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val streamInteractionSource = remember { MutableInteractionSource() }
    val deleteInteractionSource = remember { MutableInteractionSource() }

    val imageRequest = response.imageRequest()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .rounded()
            .background(colors.surfaceContainer)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    action(SearchResponseAction(response, SearchResponseOperation.Open))
                },
                onLongClick = {
                    action(SearchResponseAction(response, SearchResponseOperation.Metadata))
                }
            )
            .ripple(interactionSource)
    ) {
        AsyncImage(
            contentScale = ContentScale.Crop,
            model = imageRequest,
            contentDescription = response.name,
            modifier = Modifier
                .width(67.5.dp)
                .fillMaxHeight()
                .rounded()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        action(SearchResponseAction(response, SearchResponseOperation.Open))
                    },
                    onLongClick = {
                        action(SearchResponseAction(response, SearchResponseOperation.Metadata))
                    }
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .wrapContentHeight()
                .align(Alignment.CenterVertically)
                .padding(10.dp)
        ) {
            Text(
                response.name,
                maxLines = 2,
                style = BaseStyles.textStyle
            )
            Text(
                "${response.totalChapters} ${stringResource(R.string.read_action_chapters)}",
                style = BaseStyles.textAltStyle
            )
        }

        Spacer(Modifier.weight(1f))

        Icon(
            painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
            contentDescription = stringResource(R.string.remove_history),
            modifier = Modifier
                .size(54.dp)
                .combinedClickable(
                    interactionSource = deleteInteractionSource,
                    indication = null,
                    onClick = {
                        action(SearchResponseAction(response, SearchResponseOperation.AskDelete))
                    }
                )
                .circle()
                .ripple(deleteInteractionSource)
                .padding(15.dp)
        )

        Icon(
            painter = painterResource(R.drawable.netflix_play),
            contentDescription = stringResource(R.string.stream_read),
            modifier = Modifier
                .size(54.dp)
                .combinedClickable(
                    interactionSource = streamInteractionSource,
                    indication = null,
                    onClick = {
                        action(SearchResponseAction(response, SearchResponseOperation.Stream))
                    }
                )
                .circle()
                .ripple(streamInteractionSource)
                .padding(15.dp)
        )

        Spacer(Modifier.width(10.dp))
    }
}


@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    CloudStreamTheme {
        HistoryScreen(state = HistoryState(), action = {})
    }
}
