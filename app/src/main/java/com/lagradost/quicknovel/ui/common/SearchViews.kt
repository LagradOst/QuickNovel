package com.lagradost.quicknovel.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponseAction
import com.lagradost.quicknovel.SearchResponseOperation
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.RoundedImageShape
import com.lagradost.quicknovel.compose.animatedOutline
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.isLandscape
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.ui.download.ImmutableSearchList
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun SearchList(
    isRow: Boolean,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier,
    state: ImmutableSearchList,
    searchAction: (SearchResponseAction) -> Unit,
    footer: @Composable (() -> Unit)? = null,
) {
    if (isRow) {
        SearchListRow(
            lazyState = lazyGridState,
            modifier = modifier,
            state = state,
            searchAction = searchAction,
            footer = footer
        )
    } else {
        SearchResponseGrid(
            listState = lazyGridState,
            modifier = modifier,
            state = state,
            searchAction = searchAction,
            footer = footer
        )
    }
}

@Composable
fun SearchList(
    isRow: Boolean,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier,
    items: ImmutableList<ImmutableSearchResponse>,
    searchAction: (SearchResponseAction) -> Unit,
    //footer: @Composable (() -> Unit)? = null,
) {
    if (isRow) {
        SearchListRow(
            lazyState = lazyGridState,
            modifier = modifier,
            items = items,
            searchAction = searchAction,
        )
    } else {
        SearchResponseGrid(
            listState = lazyGridState,
            modifier = modifier,
            items = items,
            searchAction = searchAction,
        )
    }
}

@Composable
fun SearchListRow(
    lazyState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier,
    items: ImmutableList<ImmutableSearchResponse>,
    searchAction: (SearchResponseAction) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 2 else 1),
        state = lazyState,
        modifier = modifier,
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(
            items = items,
            key = { item ->
                @OptIn(ExperimentalUuidApi::class)
                item.randomUuid
            }) { item ->
            SearchResponseRow(
                response = item,
                action = searchAction,
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
fun SearchListRow(
    lazyState: LazyGridState = rememberLazyGridState(),
    modifier: Modifier,
    state: ImmutableSearchList,
    searchAction: (SearchResponseAction) -> Unit,
    footer: @Composable (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 2 else 1),
        state = lazyState,
        modifier = modifier,
        contentPadding = PaddingValues(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        items(
            state.sorted,
            key = { id -> id },
            contentType = { id -> state.data[id] }) { id ->
            SearchResponseRow(
                response = state.data[id]!!,
                action = searchAction,
                modifier = Modifier.animateItem()
            )
        }
        if (footer != null) {
            item(key = "footer") {
                footer()
            }
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
            if(response.totalChapters != null) {
                Text(
                    "${response.totalChapters} ${stringResource(R.string.read_action_chapters)}",
                    style = BaseStyles.textAltStyle
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if(response.id != null) {
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
        }

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


@Composable
fun SearchResponseItem(
    response: ImmutableSearchResponse,
    action: (SearchResponseAction) -> Unit,
    modifier: Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val imageRequest = response.imageRequest()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = {
                action(SearchResponseAction(response, SearchResponseOperation.Open))
            }, onLongClick = {
                action(SearchResponseAction(response, SearchResponseOperation.Metadata))
            })
            .rounded()
    ) {
        AsyncImage(
            contentScale = ContentScale.Crop,
            model = imageRequest,
            contentDescription = response.name,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .rounded()
                .let { modifier ->
                    val downloadState = response.downloadState?.state ?: return@let modifier
                    return@let when (downloadState) {
                        DownloadState.IsDownloading, DownloadState.IsPending -> {
                            modifier.animatedOutline(
                                defaultPalette = listOf(
                                    Color.Transparent,
                                    colors.primary,
                                    Color.Transparent,
                                    colors.primary,
                                )
                            )
                        }

                        else -> {
                            val color = when (downloadState) {
                                DownloadState.IsPaused -> colors.onBackground
                                DownloadState.IsDone -> colors.primary
                                DownloadState.IsFailed -> Color.Red
                                else -> return@let modifier
                            }

                            modifier.border(
                                width = 2.dp,
                                color = color,
                                shape = RoundedImageShape()
                            )
                        }
                    }
                }

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
                text = response.name,
                style = TextStyle(
                    color = colors.onBackground,
                    fontSize = 13.sp,
                    lineHeight = 14.sp,
                ), maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis
            )
        }
    }
}


@OptIn(ExperimentalUuidApi::class)
@Composable
fun SearchResponseGrid(
    listState: LazyGridState = rememberLazyGridState(),
    state: ImmutableSearchList,
    searchAction: (SearchResponseAction) -> Unit,
    modifier: Modifier,
    footer: @Composable (() -> Unit)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 6 else 3),
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = state.sorted,
            key = { id -> id },
            contentType = { id -> state.data[id] }
        ) { id ->
            SearchResponseItem(
                response = state.data[id]!!,
                action = searchAction,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
        if (footer != null) {
            item(key = "footer") {
                footer()
            }
        }
    }
}

@Composable
fun SearchResponseGrid(
    listState: LazyGridState = rememberLazyGridState(),
    items: ImmutableList<ImmutableSearchResponse>,
    searchAction: (SearchResponseAction) -> Unit,
    modifier: Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (isLandscape) 6 else 3),
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(
            items = items,
            key = { item ->
                @OptIn(ExperimentalUuidApi::class)
                item.randomUuid
            }
        ) { response ->
            SearchResponseItem(
                response = response,
                action = searchAction,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
    }
}