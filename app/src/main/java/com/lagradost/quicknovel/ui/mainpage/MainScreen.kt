package com.lagradost.quicknovel.ui.mainpage

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Interpolatable.Companion.lerp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.graphics.rotationMatrix
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.ImmutableSearchResponse
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.SearchResponseAction
import com.lagradost.quicknovel.SearchResponseOperation
import com.lagradost.quicknovel.compose.BackHandler
import com.lagradost.quicknovel.compose.BaseSearchBar
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.BaseStyles.blackButtonColors
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.LocalSharedInfiniteTransition
import com.lagradost.quicknovel.compose.RoundedImageShape
import com.lagradost.quicknovel.compose.SingleSelectDialog
import com.lagradost.quicknovel.compose.animatedOutline
import com.lagradost.quicknovel.compose.isLandscape
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.ui.search.SearchRow
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.ExperimentalUuidApi

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

    SingleSelectDialog(
        entries = dialog.options,
        dismiss = {
            action(MainPageAction.Dismiss)
        },
        title = title,
        selectedIndex = dialog.selected,
        confirm = { selected ->
            action(MainPageAction.SelectDialog(dialog.type, selected))
        })


    /*BaseDialog(
        dismiss = {
            action(MainPageAction.Dismiss)
        },
        title = { Text(text = title) },
        items = dialog.options,
        selected = dialog.selected,
        onSelect = { selected ->
            action(MainPageAction.SelectDialog(dialog.type, selected))
        })*/
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun MainPageScreen(state: MainPageState, action: (MainPageAction) -> Unit) {
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

    val searchAction = remember<(SearchResponseAction) -> Unit>(action) {
        { action ->
            action(MainPageAction.ResultAction(action))
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
        SearchResponseGrid(
            listState = listState,
            items = if (state.openQuery) state.query.items else state.filter.items,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            action = searchAction,
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResponseDialog(
    dialog: SearchRow,
    action: (SearchResponseAction) -> Unit,
    dismiss: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    ModalBottomSheet(
        sheetState = sheetState,
        containerColor = colors.background,
        onDismissRequest = dismiss,
        modifier = Modifier.fillMaxSize(),
        dragHandle = { },
        shape = RectangleShape
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .combinedClickable(
                    interactionSource = interactionSource,
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                        }.invokeOnCompletion {
                            dismiss()
                        }
                    }
                )
                .ripple(interactionSource)
                .padding(horizontal = 10.dp)
        ) {
            Text(
                text = dialog.name,
                color = colors.onBackground,
                fontSize = 20.sp,
            )
            Icon(
                painter = painterResource(R.drawable.arrow_drop_down_24px),
                tint = colors.onBackground,
                contentDescription = null,
            )
        }

        if (dialog.error != null) {
            Text(
                style = BaseStyles.textStyle,
                text = dialog.error.toString(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (dialog.items.isEmpty()) {
            Text(
                style = BaseStyles.textStyle,
                text = stringResource(R.string.no_data),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SearchResponseGrid(
                items = dialog.items,
                modifier = Modifier
                    .fillMaxSize(),
                action = action,
            )
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
@Composable
fun SearchResponseGrid(
    listState: LazyGridState = rememberLazyGridState(),
    items: ImmutableList<ImmutableSearchResponse>,
    action: (SearchResponseAction) -> Unit,
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
            key = { item -> item.randomUuid }
        ) { response ->
            SearchResponseItem(
                response = response,
                action = action,
                modifier = Modifier
                    .animateItem()
                    .fillMaxWidth()
                    .wrapContentHeight()
            )
        }
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
                    val downloadState = response.downloadState?.state ?: return@let  modifier
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
                        else  -> {
                            val color = when(downloadState) {
                                DownloadState.IsPaused -> colors.onBackground
                                DownloadState.IsDone -> colors.primary
                                DownloadState.IsFailed -> Color.Red
                                else -> return@let modifier
                            }

                            modifier.border(width = 2.dp, color = color, shape = RoundedImageShape())
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


@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    CloudStreamTheme {
        MainPageScreen(
            state = MainPageState(),
            action = {})
    }
}
