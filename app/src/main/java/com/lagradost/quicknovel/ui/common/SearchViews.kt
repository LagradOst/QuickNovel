package com.lagradost.quicknovel.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
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
import com.lagradost.quicknovel.NotificationHelper.etaToString
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.RoundedImageShape
import com.lagradost.quicknovel.compose.animatedOutline
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.isLandscape
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
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
            items = items, key = { item ->
                @OptIn(ExperimentalUuidApi::class) item.randomUuid
            }) { item ->
            SearchResponseRow(
                response = item, action = searchAction, modifier = Modifier.animateItem()
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
            state.sorted, key = { id -> id }) { id ->
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
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = {
                action(SearchResponseAction(response, SearchResponseOperation.Open))
            }, onLongClick = {
                action(SearchResponseAction(response, SearchResponseOperation.Metadata))
            })
            .downloadOutline(if (response.generating) DownloadState.IsDownloading else response.downloadState?.status)
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
                    })
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
                style = BaseStyles.textStyle,
                overflow = TextOverflow.Ellipsis
            )

            if (response.downloadState != null) {
                if (response.downloadState.progress != response.downloadState.total) {
                    Text(
                        "${response.downloadState.progress}/${response.downloadState.total}${
                            response.downloadState.etaMs?.let {
                                " • " + etaToString(
                                    it
                                )
                            } ?: ""
                        }", style = BaseStyles.textAltStyle)
                } else {
                    // TODO not use chapter text for bytes?
                    // TODO not show for imported?
                    Text(
                        "${response.downloadState.progress} ${
                            stringResource(
                                if (response.downloadState.progress == 1L) {
                                    R.string.chapter
                                } else {
                                    R.string.chapters
                                }
                            )
                        }", style = BaseStyles.textAltStyle
                    )
                }
            } else if (response.chapters != null) {
                Text(
                    "${response.chapters} ${stringResource(R.string.read_action_chapters)}",
                    style = BaseStyles.textAltStyle
                )
            } else if (response.latestChapterName != null) {
                Text(
                    response.latestChapterName,
                    style = BaseStyles.textAltStyle
                )
            }
        }

        Spacer(Modifier.weight(1f))

        if (response.downloadState != null && response.epubSize != null && response.hasNewChapters) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .circle()
                    .background(colors.primary)
                    .padding(vertical = 3.dp, horizontal = 13.dp)
            ) {
                Text(
                    text = "+${(response.downloadState.progress - response.epubSize)}",
                    color = colors.background
                )
            }
        }

        if (response.downloadState != null) {
            RefreshButton(response, action)
        } else {
            if (response.id != null) {
                Icon(
                    painter = painterResource(R.drawable.ic_baseline_delete_outline_24),
                    contentDescription = stringResource(R.string.remove_history),
                    modifier = Modifier
                        .size(54.dp)
                        .combinedClickable(
                            interactionSource = deleteInteractionSource,
                            indication = null,
                            onClick = {
                                action(
                                    SearchResponseAction(
                                        response, SearchResponseOperation.AskDelete
                                    )
                                )
                            })
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
                        interactionSource = streamInteractionSource, indication = null, onClick = {
                            action(SearchResponseAction(response, SearchResponseOperation.Stream))
                        })
                    .circle()
                    .ripple(streamInteractionSource)
                    .padding(15.dp)
            )
        }

        Spacer(Modifier.width(10.dp))
    }
}

@Composable
fun RefreshButton(
    response: ImmutableSearchResponse,
    action: (SearchResponseAction) -> Unit,
) {
    require(response.downloadState != null)
    if (response.isImported) {
        return
    }
    val refreshInteractionSource = remember { MutableInteractionSource() }

    val icon = when (response.downloadState.status) {
        DownloadState.IsDownloading -> R.drawable.ic_baseline_pause_24
        DownloadState.IsPaused -> R.drawable.netflix_play
        DownloadState.IsStopped -> R.drawable.arrow_circle_down_24px
        DownloadState.IsFailed -> R.drawable.arrow_circle_down_24px
        DownloadState.IsDone -> R.drawable.ic_baseline_check_24
        DownloadState.IsPending -> {
            Spacer(Modifier.width(54.dp))
            return
        }

        DownloadState.Nothing -> R.drawable.arrow_circle_down_24px
    }

    val operation = when (response.downloadState.status) {
        DownloadState.IsDownloading -> SearchResponseOperation.Pause
        DownloadState.IsPaused -> SearchResponseOperation.Resume
        DownloadState.IsStopped -> SearchResponseOperation.Download
        DownloadState.IsFailed -> SearchResponseOperation.Download
        DownloadState.IsDone -> SearchResponseOperation.Download
        DownloadState.IsPending -> return
        DownloadState.Nothing -> SearchResponseOperation.Download
    }

    Icon(
        painter = painterResource(icon),
        contentDescription = stringResource(R.string.download),
        modifier = Modifier
            .size(54.dp)
            .combinedClickable(
                interactionSource = refreshInteractionSource, indication = null, onClick = {
                    action(
                        SearchResponseAction(
                            response, operation
                        )
                    )
                })
            .circle()
            .ripple(refreshInteractionSource)
            .padding(15.dp)
    )
}


@Composable
fun Modifier.downloadOutline(downloadState: DownloadState?): Modifier {
    var targetAlpha by remember { mutableFloatStateOf(0f) }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 1000),
        label = "BorderWidthAnimation"
    )

    return when (downloadState) {
        DownloadState.IsDownloading, DownloadState.IsPending -> {
            LaunchedEffect(downloadState) {
                targetAlpha = 1f
            }
            animatedOutline(
                defaultPalette = listOf(
                    Color.Transparent,
                    colors.primary.copy(alpha = alpha),
                    Color.Transparent,
                    colors.primary.copy(alpha = alpha),
                )
            )
        }

        DownloadState.IsPaused -> {
            border(
                width = 1.5.dp, color = colors.onBackground, shape = RoundedImageShape()
            )
        }

        DownloadState.IsDone -> {
            LaunchedEffect(downloadState) {
                delay(1000.milliseconds)
                targetAlpha = 0f
            }
            if (alpha > 0f) {
                border(
                    width = 1.5.dp,
                    color = colors.primary.copy(alpha = alpha),
                    shape = RoundedImageShape()
                )
            } else {
                this
            }
        }

        DownloadState.IsFailed -> {
            border(
                width = 1.5.dp, color = Color.Red, shape = RoundedImageShape()
            )
        }

        else -> this
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .rounded()
                // We do the funny and assign generating = downloading
                .downloadOutline(if (response.generating) DownloadState.IsDownloading else response.downloadState?.status)
                .ripple(interactionSource),
            contentAlignment = Alignment.BottomStart
        ) {
            AsyncImage(
                contentScale = ContentScale.Crop,
                model = imageRequest,
                contentDescription = response.name,
                modifier = Modifier.fillMaxSize()
            )


            if(response.id != null) {
                Row(modifier = Modifier.padding(5.dp)) {
                Box(
                    modifier = Modifier
                        .rounded()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(vertical = 3.dp, horizontal = 5.dp)
                ) {
                    Text(
                        text = "${response.chaptersRead}/${response.chapters}",
                        color = Color.White,
                        style = TextStyle(fontSize = 12.sp),
                    )
                }

                if (response.downloadState != null && response.epubSize != null && response.hasNewChapters) {
                    Box(
                        modifier = Modifier
                            .padding(start = 5.dp)
                            .rounded()
                            .background(colors.primary)
                            .padding(vertical = 3.dp, horizontal = 5.dp)
                    ) {
                        Text(
                            text = "+${(response.downloadState.progress - response.epubSize)}",
                            color = colors.background,
                            style = TextStyle(fontSize = 12.sp),
                        )
                    }
                }
            }
            }
        }

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
            items = items, key = { item ->
                @OptIn(ExperimentalUuidApi::class) item.randomUuid
            }) { response ->
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