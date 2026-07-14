package com.lagradost.quicknovel.ui.mainpage

import android.content.Intent
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponse
import com.lagradost.quicknovel.compose.BackHandler
import com.lagradost.quicknovel.compose.BaseDialog
import com.lagradost.quicknovel.compose.BaseSearchBar
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.BaseStyles.blackButtonColors
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.isLandscape
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.ui.history.HistoryAction
import com.lagradost.quicknovel.ui.history.ResultOperation

@Composable
fun MainPageScreen(viewModel: MainPageViewModel2) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    /*val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel.effect, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    else -> {}
                }
            }
        }
    }*/

    MainScreenImpl(state) { action ->
        when (action) {
            is MainPageAction.OpenInBrowser -> {
                val url = action.url
                if (url.isBlank()) return@MainScreenImpl
                val i = Intent(Intent.ACTION_VIEW)
                i.data = url.toUri()
                activity?.startActivity(i)
            }

            else -> viewModel.onAction(action)
        }
    }
}

@Composable
fun MainScreenDialog(
    dialog: MainPageDialog,
    action: (MainPageAction) -> Unit
) {
    val title = when (dialog.type) {
        DialogType.Tags -> stringResource(R.string.filter_dialog_genre)
        DialogType.Category -> stringResource(R.string.filter_dialog_general)
        DialogType.OrderBy -> stringResource(R.string.filter_dialog_order_by)
    }

    BaseDialog(
        dismiss = {
            action(MainPageAction.Dismiss)
        },
        title = { Text(text = title) },
        items = dialog.options,
        selected = dialog.selected,
        onSelect = { selected ->
            action(MainPageAction.SelectDialog(dialog.type, selected))
        })
}

@Composable
fun MainScreenImpl(state: MainPageState, action: (MainPageAction) -> Unit) {
    if (state.openQuery) {
        BackHandler {
            action(MainPageAction.Back)
        }
    }

    if (state.dialog != null) {
        MainScreenDialog(state.dialog, action)
    }

    val listState = rememberLazyGridState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(snapAnimationSpec = null)
    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && state.filter.error == null && !state.filter.loading) {
            action(MainPageAction.Expand)
        }
    }

    Scaffold(
        topBar = {
            MainPageSearchBar(
                openQuery = state.openQuery,
                loading = if (state.openQuery) {
                    state.query.loading
                } else {
                    state.filter.loading
                },
                url = state.filter.url,
                action = action,
                query = state.filterVisual,
                scrollBehavior = scrollBehavior
            )
        }, modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (isLandscape) 6 else 3),
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = if (state.openQuery) state.query.items else state.filter.items,
                key = { item -> item.url }) { result ->
                SearchResponse(result, action)
            }
        }
    }
}


@Composable
fun SearchResponse(
    response: SearchResponse, action: (MainPageAction) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = {
                action(MainPageAction.ResultAction(response, SearchOperation.Open))
            }, onLongClick = {
                action(MainPageAction.ResultAction(response, SearchOperation.Metadata))
            })
            .rounded()
    ) {
        AsyncImage(
            contentScale = ContentScale.Crop,
            model = response.posterUrl, // TODO headers
            contentDescription = response.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .rounded()
                .ripple(interactionSource)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = response.name, style = TextStyle(
                    color = colors.onBackground,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                ), maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MainPageSearchBar(
    openQuery: Boolean,
    loading: Boolean,
    url: String,
    query: FilterQueryVisual,
    action: (MainPageAction) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    BaseSearchBar(
        onQueryChange = { _ ->
            // action(MainPageAction.Search(query))
        },
        onSearch = { query ->
            action(MainPageAction.Search(query))
        },
        leadingIcon = {
            if (openQuery) {
                IconButton(onClick = {
                    action(MainPageAction.Back)
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_arrow_back_24),
                        contentDescription = stringResource(R.string.back_to_search),
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Icon(
                    painter = painterResource(R.drawable.search_icon),
                    contentDescription = stringResource(R.string.search),
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        scrollBehavior = scrollBehavior,
        trailingIcon = {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = colors.onBackground
                )
            } else if (!openQuery) {
                IconButton(onClick = {
                    action(MainPageAction.OpenInBrowser(url))
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_baseline_public_24),
                        contentDescription = stringResource(R.string.open_in_browser),
                        tint = colors.onBackground
                    )
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp, start = 5.dp, end = 5.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            query.category?.let {
                SelectButton(it) {
                    action(MainPageAction.OpenDialog(DialogType.Category))
                }
            }
            query.orderBy?.let {
                SelectButton(it) {
                    action(MainPageAction.OpenDialog(DialogType.OrderBy))
                }
            }
            query.tag?.let {
                SelectButton(it) {
                    action(MainPageAction.OpenDialog(DialogType.Tags))
                }
            }
        }
    }
}

@Composable
fun RowScope.SelectButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        Modifier
            .weight(1.0f)
            .padding(horizontal = 5.dp),
        colors = blackButtonColors,
    ) {
        Text(text)
        Spacer(Modifier.width(10.dp))
        Icon(
            painter = painterResource(R.drawable.arrow_drop_down_24px), contentDescription = text
        )
    }
}