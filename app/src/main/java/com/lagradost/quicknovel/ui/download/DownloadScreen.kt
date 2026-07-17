package com.lagradost.quicknovel.ui.download

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentRecomposeScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Modifier.Companion
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponseAction
import com.lagradost.quicknovel.SearchResponseOperation
import com.lagradost.quicknovel.compose.BaseSearchBar
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.ui.mainpage.SearchResponseGrid
import com.lagradost.quicknovel.ui.search.HomeAction
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DownloadScreen(state: DownloadPageState, action: (DownloadPageAction) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(snapAnimationSpec = null)
    Scaffold(
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
        //.nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        if (state.filteredPages.isEmpty()) return@Scaffold

        val pagerState = rememberPagerState(
            pageCount = { state.filteredPages.size }
        )

        val currentPage = pagerState.currentPage
        Column {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(innerPadding)
                    .weight(1.0f)
            ) { page ->
                DownloadRow(state.filteredPages[page], action)
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
                state.filteredPages.forEachIndexed { index, row ->
                    val selected = index == currentPage
                    Tab(
                        modifier = Modifier.height(30.dp),
                        selected = selected, onClick = {
                            pagerState.requestScrollToPage(index)
                        }, text = {
                            Text(
                                stringResource(row.name), color = if (selected) {
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
fun DownloadRow(row: DownloadRow, action: (DownloadPageAction) -> Unit) {
    val searchAction = remember<(SearchResponseAction) -> Unit>(action) {
        { item ->
            action(DownloadPageAction.ResultAction(item))
        }
    }
    var refreshing by remember { mutableStateOf(false) }

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
        SearchResponseGrid(items = row.row, action = searchAction, modifier = Modifier)
    }
}

@Composable
@PreviewLightDark
fun DownloadScreenPreview() {
    CloudStreamTheme {
        DownloadScreen(
            state = DownloadPageState(
                pages = persistentListOf(
                    DownloadRow(R.string.tab_downloads, persistentListOf()),
                    DownloadRow(R.string.type_reading, persistentListOf()),
                    DownloadRow(R.string.type_dropped, persistentListOf()),
                    DownloadRow(R.string.type_none, persistentListOf()),
                    DownloadRow(R.string.type_completed, persistentListOf()),
                    DownloadRow(R.string.type_on_hold, persistentListOf()),
                    DownloadRow(R.string.type_plan_to_read, persistentListOf()),
                )
            ), action = {})
    }
}
