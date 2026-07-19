package com.lagradost.quicknovel.ui.download

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.MainActivity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponseAction
import com.lagradost.quicknovel.SearchResponseOperation
import com.lagradost.quicknovel.compose.BaseSearchBar
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.IsScrolling
import com.lagradost.quicknovel.compose.RoundedImageShape
import com.lagradost.quicknovel.compose.SinglePairSelectDialog
import com.lagradost.quicknovel.compose.animatedOutline
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.ui.mainpage.SearchResponseGrid
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DownloadScreen(
    state: DownloadPageState,
    action: (DownloadPageAction) -> Unit
) {
    DownloadSort(
        state.downloadSortingMethod,
        state.regularSortingMethod,
        state.sortingMethodDialog,
        action
    )
    val pagesNames = listOf(
        R.string.tab_downloads,
        R.string.type_reading,
        R.string.type_dropped,
        R.string.type_none,
        R.string.type_completed,
        R.string.type_on_hold,
        R.string.type_plan_to_read,
    )

    var fabExpanded by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(snapAnimationSpec = null)
    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                modifier = Modifier.padding(bottom = 30.dp),
                onClick = {
                    action(DownloadPageAction.ShowSorting)
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
                    action(DownloadPageAction.Search(query))
                },
                onSearch = { query ->
                    action(DownloadPageAction.Search(query))
                },
                scrollBehavior = scrollBehavior,
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search_icon),
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier.size(24.dp)
                    )
                },
                placeholder = stringResource(R.string.search_downloads)
            )
        }, modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        if (state.pages.isEmpty()) return@Scaffold

        val pagerState = rememberPagerState(
            initialPage = state.activePage,
            pageCount = { pagesNames.size }
        )

        val currentPage = pagerState.currentPage
        LaunchedEffect(currentPage) {
            action(DownloadPageAction.SelectPage(currentPage))
        }

        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .weight(1.0f)
            ) { page ->
                DownloadRow(
                    page,
                    state.pages.getOrNull(page) ?: ImmutableSearchList(),
                    action,
                    scrollingChange = { isScrollingUp ->
                        fabExpanded = isScrollingUp
                    })
            }

            SecondaryScrollableTabRow(
                currentPage,
                edgePadding = 0.dp,
                containerColor = colors.surfaceVariant,
                indicator = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(30.dp)
                            .zIndex(-1.0f)
                            .tabIndicatorOffset(currentPage, matchContentSize = false)
                            .circle()
                            .background(colors.onBackground)
                    )
                }, divider = {}
            ) {
                pagesNames.forEachIndexed { index, row ->
                    val selected = index == currentPage
                    Tab(
                        modifier = Modifier.height(30.dp),
                        selected = selected, onClick = {
                            pagerState.requestScrollToPage(index)
                        }, text = {
                            Text(
                                stringResource(row), color = if (selected) {
                                    colors.background
                                } else {
                                    colors.onBackground
                                }
                            )
                        })
                }
            }
        }
    }
}

@Composable
fun DownloadSort(
    downloadSortingMethod: SortingMethodType,
    regularSortingMethod: SortingMethodType,
    sortingMethodDialog: Boolean?,
    action: (DownloadPageAction) -> Unit,
) {
    if (sortingMethodDialog == null) return
    val data = if (sortingMethodDialog) {
        sortingMethods
    } else {
        normalSortingMethods
    }
    val key = if (sortingMethodDialog) {
        downloadSortingMethod
    } else {
        regularSortingMethod
    }

    SinglePairSelectDialog(
        entries = data.associate { (it.id to it.inverse) to stringResource(it.name) },
        selectedKey = key,
        title = stringResource(R.string.filter_dialog_sort_by),
        confirmText = stringResource(R.string.sort_apply),
        dismissText = stringResource(R.string.sort_cancel),
        dismiss = {
            action(DownloadPageAction.DismissSorting)
        },
        confirm = { key ->
            if (sortingMethodDialog) {
                action(DownloadPageAction.SelectSortingMethod(downloadSortingMethod = key))
            } else {
                action(DownloadPageAction.SelectSortingMethod(regularSortingMethod = key))
            }
            action(DownloadPageAction.DismissSorting)
        }
    )
}

@Composable
fun DownloadRow(
    index: Int,
    row: ImmutableSearchList?,
    action: (DownloadPageAction) -> Unit,
    scrollingChange: (Boolean) -> Unit
) {
    if (row == null) return

    val searchAction = remember<(SearchResponseAction) -> Unit>(action) {
        { item ->
            action(DownloadPageAction.ResultAction(item))
        }
    }
    var refreshing by remember { mutableStateOf(false) }
    val state: LazyGridState = rememberLazyGridState()
    state.IsScrolling(up = {
        scrollingChange(true)
    }, down = {
        scrollingChange(false)
    })

    val scope = rememberCoroutineScope()
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            action(DownloadPageAction.Refresh)
            scope.launch {
                delay(200.milliseconds)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize()
    ) {
        SearchResponseGrid(
            items = row,
            action = searchAction,
            modifier = Modifier,
            listState = state,
            footer = if (index == 0) {
                ::Footer
            } else {
                null
            }
        )
    }
}

@Composable
fun Footer() {
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = {
                MainActivity.importEpub()
            })
            .rounded()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .rounded()
                .ripple(interactionSource),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(40.dp),
                painter = painterResource(R.drawable.ic_baseline_add_24),
                contentDescription = stringResource(R.string.import_epub),
                tint = colors.onBackground
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.import_epub),
                style = TextStyle(
                    color = colors.onBackground,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                ), maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
@PreviewLightDark
fun DownloadScreenPreview() {
    CloudStreamTheme {
        DownloadScreen(
            state = DownloadPageState(), action = {})
    }
}
