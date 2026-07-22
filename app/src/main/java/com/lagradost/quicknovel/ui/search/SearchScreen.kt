package com.lagradost.quicknovel.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.lagradost.quicknovel.MainAPI
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseSearchBar
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.MultiSelectDialog
import com.lagradost.quicknovel.compose.isLandscape
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.tachiyomi.AndroidPreferenceStore
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseItem
import com.lagradost.quicknovel.ui.download.DownloadPageAction
import com.lagradost.quicknovel.ui.mainpage.SearchResponseDialog
import com.lagradost.quicknovel.ui.settings.searchProvidersList
import com.lagradost.quicknovel.util.Apis.Companion.apis
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun SearchScreen(state: HomeViewModelState, action: (HomeAction) -> Unit) {
    ShowDialog(state.filterLanguages, state.isConfigureShow, action)
    BackHandler(state.isQueryOpen) {
        action(HomeAction.CloseQuery)
    }

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(snapAnimationSpec = null)
    val listState = rememberLazyGridState()


    val searchAction = remember<(SearchResponseAction) -> Unit>(action) {
        { item ->
            action(HomeAction.ResultAction(item))
        }
    }
    val dismissAction = remember(action) {
        {
            action(HomeAction.CloseRow)
        }
    }

    if (state.openRow != null) {
        SearchResponseDialog(state.openRow, action = searchAction, dismiss = dismissAction)
    }

    Scaffold(
        topBar = {
            BaseSearchBar(
                content = {
                    Spacer(modifier = Modifier.height(5.dp))
                },
                onQueryChange = { _ ->
                },
                onSearch = { query ->
                    action(HomeAction.Search(query))
                },
                trailingIcon = {
                    if (state.isLoading && state.isQueryOpen) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.onBackground
                        )
                    } else {
                        IconButton(onClick = { action(HomeAction.ConfigureApis) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_tune_24),
                                contentDescription = stringResource(R.string.search_providers),
                                tint = colors.onBackground
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                leadingIcon = {
                    if (state.isQueryOpen) {
                        IconButton(onClick = {
                            action(HomeAction.CloseQuery)
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
            )
        }, modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection)
    ) { innerPadding ->
        if (state.isQueryOpen) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(state.searchRows, key = { item -> item.name }) { row ->
                    SearchRow(row, action)
                }
            }
        } else {
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
                items(state.shownMainPageApis, key = { item -> item.name }) { api ->
                    MainAPIItem(api = api, action = action)
                }
            }
        }
    }
}

@Composable
fun ShowDialog(
    filterLanguages: ImmutableSet<String>, isDialogShown: Boolean, action: (HomeAction) -> Unit
) {
    if (!isDialogShown) return

    val entries = remember(filterLanguages) {
        apis.filter { api -> filterLanguages.contains(api.lang) }.associate { it.name to it.name }
            .toPersistentMap()
    }
    val context = LocalContext.current
    val store = AndroidPreferenceStore(context)
    val apisSettings = store.searchProvidersList()
    val keys = apisSettings.get().toPersistentSet()

    MultiSelectDialog(
        title = stringResource(R.string.search_providers),
        entries = entries,
        properties = DialogProperties(
            usePlatformDefaultWidth = true,
        ),
        confirmText = stringResource(R.string.ok),
        confirm = { selection: Set<String> ->
            apisSettings.set(selection)
            action(HomeAction.DismissConfigureApis)
        },
        dismissText = stringResource(R.string.cancel),
        dismiss = {
            action(HomeAction.DismissConfigureApis)
        },
        selectedKeys = keys,
    )
}

@Composable
fun MainAPIItem(
    api: MainAPI,
    modifier: Modifier = Modifier,
    action: (HomeAction) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .rounded()
            .background(colors.surfaceContainer)
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = {
                action(HomeAction.Open(api))
            }, onLongClick = {})
            .ripple(interactionSource)
    ) {
        Image(
            painter = painterResource(api.iconId ?: R.drawable.fiber_new_24px),
            contentDescription = api.name,
            modifier = Modifier
                .padding(10.dp)
                .size(40.dp)
                .rounded()
                .background(colorResource(api.iconBackgroundId))
        )
        Text(
            text = api.name, style = BaseStyles.textStyle, textAlign = TextAlign.Center
        )
    }
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    CloudStreamTheme {
        SearchScreen(state = HomeViewModelState(), action = {})
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
private fun SearchRow(
    row: SearchRow,
    action: (HomeAction) -> Unit
) {
    val searchAction = remember<(SearchResponseAction) -> Unit>(action) {
        { item ->
            action(HomeAction.ResultAction(item))
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Column {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .ripple(interactionSource)
                .combinedClickable(interactionSource = interactionSource, onClick = {
                    action(HomeAction.OpenRow(row))
                })
        ) {
            Text(
                row.name,
                modifier = Modifier.padding(10.dp),
                color = colors.onBackground,
                fontSize = 20.sp
            )
            Icon(
                modifier = Modifier.padding(10.dp),
                painter = painterResource(R.drawable.ic_baseline_arrow_forward_24),
                contentDescription = null,
                tint = colors.onBackground
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 10.dp)
        ) {
            items(items = row.items, key = { item -> item.randomUuid }) { response ->
                SearchResponseItem(
                    response = response,
                    action = searchAction,
                    modifier = Modifier.width(120.dp)
                )
            }
        }
    }
}
