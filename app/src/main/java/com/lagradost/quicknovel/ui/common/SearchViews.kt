package com.lagradost.quicknovel.ui.common

import android.text.format.Formatter.formatFileSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.compose.RoundedShape
import com.lagradost.cloudstream4.compose.circle
import com.lagradost.cloudstream4.compose.rounded
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.lagradost.quicknovel.downloader.BookDownloadStatus
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SlopStatus
import com.lagradost.quicknovel.compose.animatedOutline
import com.lagradost.quicknovel.compose.isLandscape
import com.mihon.material.padding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

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
                item.randomUuid
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
        contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 80.dp),
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
    val imageRequest = response.imageRequest()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .height(100.dp)
            .rounded()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = {
                action(
                    SearchResponseAction(
                        response, if (response.downloadState != null) {
                            SearchResponseOperation.Read
                        } else {
                            SearchResponseOperation.Open
                        }
                    )
                )
            }, onLongClick = {
                action(SearchResponseAction(response, SearchResponseOperation.Metadata))
            }).let { modifier ->
                /** For stub, we always show an outline */
                if (
                    !response.generating
                    && response.downloadState != null
                    && response.downloadState.downloadedChapters.length() > (response.chapters ?: 1)
                    && (response.downloadState.downloadStatus == BookDownloadStatus.Idle || response.downloadState.downloadStatus == BookDownloadStatus.Done)
                ) {
                    modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedShape()
                    )
                } else {
                    modifier.downloadOutline(if (response.generating) BookDownloadStatus.Downloading else response.downloadState?.downloadStatus)
                }
            }
    ) {
        Box(
            contentAlignment = Alignment.TopStart, modifier = Modifier
                .rounded()
                .combinedClickable(
                    onClick = {
                        action(SearchResponseAction(response, SearchResponseOperation.Open))
                    },
                    onLongClick = {
                        action(SearchResponseAction(response, SearchResponseOperation.Metadata))
                    })
        ) {
            AsyncImage(
                contentScale = ContentScale.Crop,
                model = imageRequest,
                contentDescription = response.name,
                modifier = Modifier
                    .width(67.5.dp)
                    .fillMaxHeight()
            )
            when (response.slopStatus) {
                SlopStatus.AiAssisted, SlopStatus.AiGenerated -> {
                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier
                            .padding(MaterialTheme.padding.extraSmall)
                            .rounded()
                            .background(color = MaterialTheme.colorScheme.onBackground)
                            .padding(5.dp)
                    )
                }

                else -> {

                }
            }
        }

        Column(
            modifier = Modifier
                .wrapContentHeight()
                .align(Alignment.CenterVertically)
                .padding(10.dp)
                .weight(1.0f)
        ) {
            Text(
                response.name,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyMedium,
                overflow = TextOverflow.Ellipsis
            )

            val chaptersEnding = stringResource(
                if (response.chapters == 1) {
                    R.string.chapter
                } else {
                    R.string.chapters
                }
            )

            val text = if (response.downloadState != null) {
                val downloadState = response.downloadState

                if (downloadState.chapterProgress != null) {
                    "${downloadState.chapterProgress.progress}/${downloadState.chapterProgress.total} $chaptersEnding"
                } else if (downloadState.downloadedBytes != downloadState.totalBytes) {
                    val prefixText = "${
                        formatFileSize(
                            LocalContext.current,
                            downloadState.downloadedBytes
                        )
                    } / ${formatFileSize(LocalContext.current, downloadState.totalBytes)}"
                    "$prefixText $chaptersEnding"
                } else if (!response.isImported) {
                    val downloaded = downloadState.totalDownloadedAfterOffset
                    val chapters = response.chapters
                    if (chapters != null) {
                        if (downloaded != chapters) {
                            "$downloaded/$chapters $chaptersEnding"
                        } else {
                            "$chapters $chaptersEnding"
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else if (response.chapters != null) {
                "${response.lastChaptersRead}/${response.chapters} $chaptersEnding"
            } else {
                response.latestChapterName
            }

            if (text != null) {
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            response.downloadState?.estimateDownloadTimeFormat?.let { text ->
                Text(
                    text = text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(
            Modifier
                .width(5.dp)
                .weight(0.01f)
        )

        if (response.downloadState != null && response.epubSize != null) {
            val afterOffset = response.downloadState.totalDownloadedAfterOffset
            val added = afterOffset - response.epubSize
            if (added > 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 5.dp)
                        .circle()
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 3.dp, horizontal = 13.dp)
                ) {
                    Text(
                        text = "+$added",
                        color = MaterialTheme.colorScheme.background
                    )
                }
            }
        }

        if (response.downloadState != null) {
            RefreshButton(response, action)
        } else {
            if (response.id != null) {
                IconButton(
                    onClick = {
                        action(
                            SearchResponseAction(
                                response, SearchResponseOperation.AskDelete
                            )
                        )
                    },
                    modifier = Modifier.size(54.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.delete_24px),
                        contentDescription = stringResource(R.string.remove_history),
                    )
                }
            }

            IconButton(
                onClick = {
                    action(SearchResponseAction(response, SearchResponseOperation.Stream))
                },
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.play_arrow_24px),
                    contentDescription = stringResource(R.string.stream_read),
                )
            }
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

    /*if (response.downloadState.downloadStatus == BookDownloadStatus.Pending) {
        Spacer(Modifier.width(54.dp))
        return
    }*/

    val action = response.downloadState.action

    IconButton(
        onClick = {
            action(
                SearchResponseAction(
                    response, action.operation
                )
            )
        },
        modifier = Modifier.size(54.dp)
    ) {
        action.icon?.let { icon ->
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(R.string.download),
            )
        }
    }
}


@Composable
fun Modifier.downloadOutline(downloadState: BookDownloadStatus?): Modifier {
    var targetAlpha by remember { mutableFloatStateOf(0f) }
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 1000),
        label = "BorderWidthAnimation"
    )

    return when (downloadState) {
        BookDownloadStatus.Downloading, BookDownloadStatus.Pending -> {
            LaunchedEffect(downloadState) {
                targetAlpha = 1f
            }
            animatedOutline(
                defaultPalette = listOf(
                    Color.Transparent,
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    Color.Transparent,
                    MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                )
            )
        }

        BookDownloadStatus.Paused -> {
            border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.onBackground,
                shape = RoundedShape()
            )
        }

        BookDownloadStatus.Done -> {
            LaunchedEffect(downloadState) {
                delay(1000.milliseconds)
                targetAlpha = 0f
            }
            if (alpha > 0f) {
                border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                    shape = RoundedShape()
                )
            } else {
                this
            }
        }

        BookDownloadStatus.Failed -> {
            border(
                width = 1.5.dp, color = Color.Red, shape = RoundedShape()
            )
        }

        else -> this
    }
}

@Composable
fun SearchResponseItem(
    response: ImmutableSearchResponse,
    action: (SearchResponseAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val imageRequest = response.imageRequest()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .combinedClickable(interactionSource = interactionSource, indication = null, onClick = {
                action(
                    SearchResponseAction(
                        response, if (response.downloadState != null) {
                            SearchResponseOperation.Read
                        } else {
                            SearchResponseOperation.Open
                        }
                    )
                )
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
                .downloadOutline(if (response.generating) BookDownloadStatus.Downloading else response.downloadState?.downloadStatus)
                .indication(interactionSource = interactionSource, indication = ripple()),
            contentAlignment = Alignment.BottomStart
        ) {
            Box {
                AsyncImage(
                    contentScale = ContentScale.Crop,
                    model = imageRequest,
                    contentDescription = response.name,
                    modifier = Modifier.fillMaxSize()
                )

                when (response.slopStatus) {
                    SlopStatus.AiAssisted, SlopStatus.AiGenerated -> {
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier
                                .padding(MaterialTheme.padding.extraSmall)
                                .rounded()
                                .background(color = MaterialTheme.colorScheme.onBackground)
                                .padding(5.dp)
                        )
                    }

                    else -> {

                    }
                }
            }


            if (response.id != null) {
                Row(
                    modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                    horizontalArrangement = Arrangement.spacedBy(
                        MaterialTheme.padding.extraSmall
                    )
                ) {
                    val chapters = response.chapters
                    if (chapters != null && chapters > 1) {
                        Box(
                            modifier = Modifier
                                .rounded()
                                .background(Color.Black.copy(alpha = 0.6f))
                                .padding(4.dp)
                        ) {
                            /**+ (response.downloadState?.downloadOffset ?: 0)  */
                            Text(
                                text = "${response.lastChaptersRead}/${chapters}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    if (response.downloadState != null && response.epubSize != null) {
                        val afterOffset = response.downloadState.totalDownloadedAfterOffset
                        val added = afterOffset - response.epubSize
                        if (added > 0) {
                            Box(
                                modifier = Modifier
                                    .rounded()
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = "+$added",
                                    color = MaterialTheme.colorScheme.background,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(45.dp)
                .padding(horizontal = MaterialTheme.padding.extraSmall),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = response.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}


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
        contentPadding = PaddingValues(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 80.dp),
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

@OptIn(ExperimentalGridApi::class)
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
                item.randomUuid
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

@PreviewLightDark
@Composable
private fun RowPreview() {
    CloudStreamPreviewTheme {
        SearchResponseRow(response = ImmutableSearchResponse.preview, action = {})
    }
}

@PreviewLightDark
@Composable
private fun GridPreview() {
    CloudStreamPreviewTheme {
        SearchResponseItem(response = ImmutableSearchResponse.preview, action = {})
    }
}