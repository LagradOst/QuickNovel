package com.lagradost.quicknovel.ui.result

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream4.compose.ActionDialog
import com.lagradost.cloudstream4.compose.BlackButton
import com.lagradost.cloudstream4.compose.Colors
import com.lagradost.cloudstream4.compose.RoundedShape
import com.lagradost.cloudstream4.compose.SinglePairSelectDialog
import com.lagradost.cloudstream4.compose.SingleSelectDialog
import com.lagradost.cloudstream4.compose.WhiteButton
import com.lagradost.cloudstream4.compose.WhiteFilterChip
import com.lagradost.cloudstream4.compose.circle
import com.lagradost.cloudstream4.compose.rounded
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.RootBaseSearchBar
import com.lagradost.quicknovel.downloader.BookDownloadStatus
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.mvvm.throwableToMessage
import com.lagradost.quicknovel.ui.ReadType
import com.lagradost.quicknovel.ui.common.ChapterFilterMethodType
import com.lagradost.quicknovel.ui.common.DownloadStateAction
import com.lagradost.quicknovel.ui.common.HorizontalTab
import com.lagradost.quicknovel.ui.common.ImmutableChapterData
import com.lagradost.quicknovel.ui.common.ImmutableChapterList
import com.lagradost.quicknovel.ui.common.ImmutableDownloadState
import com.lagradost.quicknovel.ui.common.ImmutableReview
import com.lagradost.quicknovel.ui.common.ImmutableSearchResponse
import com.lagradost.quicknovel.ui.common.LoadingButton
import com.lagradost.quicknovel.ui.common.LoadingLine
import com.lagradost.quicknovel.ui.common.LoadingPoster
import com.lagradost.quicknovel.ui.common.LoadingWeight
import com.lagradost.quicknovel.ui.common.LoadingWidth
import com.lagradost.quicknovel.ui.common.SearchList
import com.lagradost.quicknovel.ui.common.SearchResponseAction
import com.lagradost.quicknovel.ui.common.SearchResponseOperation
import com.lagradost.quicknovel.ui.common.chapterSortingMethods
import com.lagradost.quicknovel.ui.common.html
import com.lagradost.quicknovel.ui.common.loading
import com.lagradost.quicknovel.ui.result.ResultPageAction.ChapterAction
import com.lagradost.quicknovel.ui.result.ResultPageAction.DismissDialog
import com.lagradost.quicknovel.ui.result.ResultPageAction.DownloadSelection
import com.lagradost.quicknovel.ui.result.ResultPageAction.ExpandReviews
import com.lagradost.quicknovel.ui.result.ResultPageAction.MarkSelection
import com.lagradost.quicknovel.ui.result.ResultPageAction.Reload
import com.lagradost.quicknovel.ui.result.ResultPageAction.ResultAction
import com.lagradost.quicknovel.ui.result.ResultPageAction.SelectChapters
import com.lagradost.quicknovel.ui.result.ResultPageAction.SetBookmark
import com.lagradost.quicknovel.ui.result.ResultPageAction.SetChapterFilterMethod
import com.lagradost.quicknovel.ui.result.ResultPageAction.SetChapterFilterQuery
import com.lagradost.quicknovel.ui.result.ResultPageAction.SetChapterSort
import com.lagradost.quicknovel.util.AppUtils.openInBrowser
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.SettingsHelper.getRatingReview
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.toPx
import com.mihon.material.padding
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.launch

/*
object ResultScreen : Screen {
    @Composable
    override fun Content() {
        val viewModel: ResultViewModel2 =
            viewModel(factory = ResultViewModel2.provideFactory(requireArguments()))
        val state by viewModel.state.collectAsStateWithLifecycle()

        ObserveEffect(viewModel.effect) { _ ->
            // Not yet implemented
        }
        ResultScreen(state,viewModel::onAction)
    }
}*/

object ResultScreen {
    @Composable
    fun ResultScreen(state: ResultState, onAction: (ResultPageAction) -> Unit) {
        com.mihon.material.Scaffold(
            bottomBar = {
                BottomBar(state, onAction)
            }
        ) { innerPadding ->
            if (state.loadingResponse) {
                LoadingScreen(Modifier.padding(innerPadding))
            } else if (state.responseError != null) {
                ErrorScreen(
                    padding = innerPadding,
                    error = state.responseError,
                    onAction = onAction,
                    url = state.loadUrl
                )
            } else {
                ResultScreenImpl(innerPadding, state, onAction)
            }
        }
    }

    @Composable
    fun BottomBar(state: ResultState, onAction: (ResultPageAction) -> Unit) {
        val selection = state.chapters?.selected
        if (selection.isNullOrEmpty() || state.response == null || state.response.id == null) {
            return
        }
        BackHandler {
            onAction(SelectChapters(persistentSetOf()))
        }
        val isSingle = selection.size == 1
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = MaterialTheme.colorScheme.surfaceContainer)
                .padding(MaterialTheme.padding.medium)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }) {
                    /** We only want to consume the keypress */
                },
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
        ) {
            Text(
                text = stringResource(R.string.selected_format, selection.size),
                style = MaterialTheme.typography.titleLarge
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    onAction(SelectChapters(persistentSetOf()))
                }) {
                    Icon(
                        painter = painterResource(R.drawable.close_24px),
                        contentDescription = stringResource(R.string.close)
                    )
                }
                if (isSingle) {
                    val index = selection.first()
                    val isRead = state.response.chaptersRead?.get(index) == true

                    if (index > 0) {
                        IconButton(onClick = {
                            onAction(
                                SelectChapters(
                                    newSelection = (0..index).toPersistentSet(),
                                )
                            )
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.move_selection_up),
                                contentDescription = null
                            )
                        }
                    }
                    if (index + 1 < state.chapters.data.size) {
                        IconButton(onClick = {
                            onAction(
                                SelectChapters(
                                    newSelection = (index until state.chapters.data.size).toPersistentSet(),
                                )
                            )
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.move_selection_down),
                                contentDescription = null
                            )
                        }
                    }

                    IconButton(onClick = {
                        onAction(
                            MarkSelection(
                                response = state.response,
                                selection = persistentSetOf(index),
                                setToRead = !isRead
                            )
                        )
                    }) {
                        Icon(
                            painter = if (isRead) painterResource(R.drawable.remove_done) else painterResource(
                                R.drawable.done_all
                            ),
                            contentDescription = if (isRead) stringResource(R.string.not_read) else stringResource(
                                R.string.has_read
                            )
                        )
                    }

                    if (state.response.downloadState?.downloadedChapters?.get(index) != true) {
                        IconButton(onClick = {
                            onAction(
                                DownloadSelection(
                                    response = state.response,
                                    selection = selection
                                )
                            )
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_circle_down_24px),
                                contentDescription = stringResource(R.string.download)
                            )
                        }
                    }
                } else {
                    IconButton(onClick = {
                        onAction(
                            MarkSelection(
                                response = state.response,
                                selection = selection,
                                setToRead = false
                            )
                        )
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.remove_done),
                            contentDescription = stringResource(R.string.not_read)
                        )
                    }
                    IconButton(onClick = {
                        onAction(
                            MarkSelection(
                                response = state.response,
                                selection = selection,
                                setToRead = true
                            )
                        )
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.done_all),
                            contentDescription = stringResource(R.string.has_read)
                        )
                    }

                    IconButton(onClick = {
                        onAction(
                            DownloadSelection(
                                response = state.response,
                                selection = selection
                            )
                        )
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_circle_down_24px),
                            contentDescription = stringResource(R.string.download)
                        )
                    }
                }
            }


        }
    }

    @Composable
    fun ErrorScreen(
        padding: PaddingValues,
        error: Throwable,
        url: String,
        onAction: (ResultPageAction) -> Unit,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(
                space = MaterialTheme.padding.medium,
                alignment = Alignment.CenterVertically
            ),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                style = MaterialTheme.typography.titleLarge,
                text = stringResource(R.string.error_loading_novel),
            )

            Text(
                style = MaterialTheme.typography.bodyMedium,
                text = throwableToMessage(error),
                modifier = Modifier
                    .sizeIn(maxHeight = 200.dp)
                    .verticalScroll(rememberScrollState())
            )

            Row(horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                WhiteButton(text = stringResource(R.string.reload_error)) {
                    onAction(Reload)
                }
                BlackButton(text = stringResource(R.string.open_in_browser)) {
                    openInBrowser(url)
                }
            }
        }
    }

    @Composable
    fun ResultScreenDialog(
        dialog: ResultDialog,
        onAction: (ResultPageAction) -> Unit
    ) {
        when (dialog) {
            is ResultDialog.Bookmark -> {
                SingleSelectDialog(
                    entries = ReadType.entries.associateWith { item -> stringResource(item.stringRes) },
                    dismiss = {
                        onAction(DismissDialog)
                    },
                    title = stringResource(R.string.bookmark),
                    selectedKey = dialog.selected,
                    confirm = { selected ->
                        onAction(SetBookmark(selected))
                        onAction(DismissDialog)
                    })
            }

            is ResultDialog.DeleteItem -> {
                ActionDialog(
                    title = stringResource(R.string.delete),
                    text = stringResource(
                        R.string.permanently_delete_format,
                        dialog.item.name
                    ),
                    confirmText = stringResource(R.string.delete),
                    dismissText = stringResource(R.string.cancel),
                    dismiss = {
                        onAction(DismissDialog)
                    },
                    confirm = {
                        onAction(
                            ResultPageAction.ResultAction(
                                SearchResponseAction(
                                    dialog.item,
                                    SearchResponseOperation.Delete
                                )
                            )
                        )
                        onAction(DismissDialog)
                    }
                )
            }
        }
    }

    @Composable
    fun ResultScreenImpl(
        padding: PaddingValues, state: ResultState, onAction: (ResultPageAction) -> Unit
    ) {
        if (state.dialog != null) {
            ResultScreenDialog(state.dialog, onAction)
        }

        val response = state.response ?: return
        // val scrollState = rememberScrollState()
        val posterInteractionSource = remember { MutableInteractionSource() }
        val posterBigInteractionSource = remember { MutableInteractionSource() }

        val isPosterShown = remember { mutableStateOf(false) }

        val tabNames = persistentListOf(
            R.string.novel
        ).mutate { list ->
            if (!state.loadingResponse && state.api?.hasReviews == true) {
                list.add(R.string.reviews)
            }
            if (!response.loadData?.related.isNullOrEmpty()) {
                list.add(R.string.related)
            }
            if (state.chapters != null) {
                list.add(R.string.chapters)
            }
        }
        val pagerState = rememberPagerState(
            initialPage = 0,
            pageCount = { tabNames.size }
        )
        LaunchedEffect(pagerState.currentPage) {
            /** If we scroll to a different page, then unselect stuff */
            if (tabNames.getOrNull(pagerState.currentPage) != R.string.chapters) {
                onAction(SelectChapters(persistentSetOf()))
            }
        }

        val outerListState = rememberLazyListState()
        val novelScroll = rememberLazyListState()
        val reviewScroll = rememberLazyListState()
        val relatedScroll = rememberLazyGridState()
        val chapterScroll = rememberLazyListState()

        val scope = rememberCoroutineScope()

        // The back button is overridden to scroll up, or scroll to novel
        val backEnabled = remember {
            derivedStateOf(policy = structuralEqualityPolicy()) {
                pagerState.currentPage != 0 || outerListState.firstVisibleItemIndex > 0
            }
        }
        BackHandler(enabled = backEnabled.value) {
            scope.launch {
                // If we are on the wrong page, but full scrolled up, then scroll to the first page
                if (outerListState.firstVisibleItemIndex <= 0) {
                    pagerState.animateScrollToPage(0)
                    return@launch
                }
                // Otherwise scroll up
                outerListState.animateScrollToItem(0)
                // This is needed as otherwise you get weird nested scroll behavior
                when (tabNames.getOrNull(pagerState.currentPage)) {
                    R.string.novel -> {
                        novelScroll.animateScrollToItem(0)
                    }

                    R.string.reviews -> {
                        reviewScroll.animateScrollToItem(0)
                    }

                    R.string.related -> {
                        relatedScroll.animateScrollToItem(0)
                    }

                    R.string.chapters -> {
                        chapterScroll.animateScrollToItem(0)
                    }
                }
            }
        }

        val scrollAlpha = remember {
            derivedStateOf {
                if (outerListState.firstVisibleItemIndex != 0) {
                    1.0f
                } else {
                    (outerListState.firstVisibleItemScrollOffset.toFloat() / 200.toPx.toFloat()).coerceIn(
                        0.0f,
                        1.0f
                    )
                }
            } // (outerListState.value.toFloat() / 200.toPx.toFloat()).coerceIn(0.0f, 1.0f)
        }

        Box {
            Box(
                modifier = Modifier
                    .padding(
                        start = padding.calculateStartPadding(LocalLayoutDirection.current),
                        end = padding.calculateEndPadding(LocalLayoutDirection.current),
                        bottom = padding.calculateBottomPadding()
                    )
                    .height(height = 190.dp + padding.calculateTopPadding())
                    .alpha(1.0f - scrollAlpha.value * 0.5f)
            ) {
                AsyncImage(
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    model = response.blurImageRequest(),
                    contentDescription = stringResource(R.string.poster_descript),
                    alpha = 0.3f
                )
                Row(
                    modifier = Modifier
                        .padding(top = padding.calculateTopPadding())
                        .padding(10.dp)
                        .scale(1.0f - scrollAlpha.value * 0.05f)
                ) {
                    AsyncImage(
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 100.dp, height = 150.dp)
                            .indication(
                                interactionSource = posterInteractionSource,
                                indication = ripple()
                            )
                            .rounded(),
                        model = response.imageRequest(),
                        contentDescription = stringResource(R.string.poster_descript),
                    )
                    Column(
                        modifier = Modifier.padding(MaterialTheme.padding.medium),
                        verticalArrangement = Arrangement.spacedBy(
                            MaterialTheme.padding.extraSmall
                        )
                    ) {
                        Text(
                            text = response.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (response.author != null) {
                            Text(
                                text = response.author,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        response.loadData?.status?.let { status ->
                            Text(
                                text = stringResource(status.resource),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        /** Unsure if we want to remove this due to spoilers */
                        response.loadData?.chapters?.lastOrNull()?.let { chapter ->
                            Text(
                                text = stringResource(R.string.latest_format, chapter.name),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            val parentFirstScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource
                    ): Offset {
                        val delta = available.y
                        return if (delta < 0 && outerListState.canScrollForward) {
                            val consumed = outerListState.dispatchRawDelta(-delta)
                            Offset(0f, -consumed)
                        } else {
                            Offset.Zero
                        }
                    }
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), state = outerListState) {
                item(key = "top spacer") {
                    Spacer(
                        Modifier
                            .height(170.dp + padding.calculateTopPadding())
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = posterInteractionSource,
                                indication = null,
                                onClick = {
                                    isPosterShown.value = !isPosterShown.value
                                })
                    )
                }
                item(key = "bookmark holder") {
                    Column(
                        modifier = Modifier
                            .clip(
                                RoundedCornerShape(
                                    MaterialTheme.padding.medium,
                                    MaterialTheme.padding.medium
                                )
                            )
                            .background(color = MaterialTheme.colorScheme.background)
                            .padding(MaterialTheme.padding.medium)
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(MaterialTheme.padding.extraSmall)
                                .fillMaxWidth()
                                .height(75.dp)
                                .clip(RoundedCornerShape(MaterialTheme.padding.medium))
                                .background(MaterialTheme.colorScheme.surfaceContainer)
                        ) {
                            TextIcon(
                                stringResource(if (state.bookmark == ReadType.NONE) R.string.bookmark else state.bookmark.stringRes),
                                icon = if (state.bookmark == ReadType.NONE) R.drawable.ic_baseline_add_24 else R.drawable.ic_baseline_bookmark_24,
                            ) {
                                onAction(ResultPageAction.OpenBookmark)
                            }
                            TextIcon(
                                stringResource(R.string.download_open_action),
                                R.drawable.ic_baseline_public_24,
                            ) {
                                openInBrowser(response.url)
                            }
                            TextIcon(
                                stringResource(R.string.result_share),
                                R.drawable.ic_outline_share_24,
                            ) {
                                safe {
                                    val i = Intent(Intent.ACTION_SEND)
                                    i.type = "text/plain"
                                    i.putExtra(Intent.EXTRA_SUBJECT, response.name)
                                    i.putExtra(Intent.EXTRA_TEXT, response.url)
                                    activity?.startActivity(Intent.createChooser(i, response.name))
                                }
                            }
                        }
                    }
                }
                if (tabNames.size > 1) {
                    item(key = "tab layout") {
                        HorizontalTab(
                            edgePadding = MaterialTheme.padding.medium,
                            pagerState = pagerState,
                            names = tabNames,
                            containerColor = MaterialTheme.colorScheme.background
                        )
                    }
                }

                item(key = "content") {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillParentMaxHeight()
                            .background(MaterialTheme.colorScheme.background),
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        when (tabNames[page]) {
                            R.string.novel -> {
                                NovelPage(state, onAction, novelScroll)
                            }

                            R.string.reviews -> {
                                ReviewsPage(
                                    loadingReviews = state.reviews.loading,
                                    reviews = state.reviews.items,
                                    nestedScrollConnection = parentFirstScrollConnection,
                                    onAction = onAction,
                                    innerListState = reviewScroll,
                                )
                            }

                            R.string.related -> {
                                RelatedPage(
                                    nestedScrollConnection = parentFirstScrollConnection,
                                    related = response.loadData?.related ?: persistentListOf(),
                                    onAction = onAction,
                                    innerListState = relatedScroll
                                )
                            }

                            R.string.chapters -> {
                                val chapters = state.chapters
                                if (chapters != null) {
                                    ChapterPage(
                                        response = response,
                                        chapters = chapters,
                                        onAction = onAction,
                                        nestedScrollConnection = parentFirstScrollConnection,
                                        innerListState = chapterScroll
                                    )
                                }
                            }

                            else -> {

                            }
                        }
                    }
                }
            }

            val animatedAlpha: Float by animateFloatAsState(
                if (isPosterShown.value) {
                    1f
                } else {
                    0f
                },
                label = "alpha",
                animationSpec = tween(durationMillis = 200),
            )

            BackHandler(enabled = isPosterShown.value) {
                isPosterShown.value = false
            }

            if (animatedAlpha > 0.0f) {
                AsyncImage(
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = MaterialTheme.colorScheme.background.copy(alpha = 0.8f * animatedAlpha))
                        .clickable(
                            onClick = {
                                isPosterShown.value = !isPosterShown.value
                            })
                        .alpha(animatedAlpha),
                    model = response.imageRequest(),
                    contentDescription = stringResource(R.string.poster_descript),
                )
            }
        }
    }

    @Composable
    fun BoxScope.FloatingStreamRead(
        response: ImmutableSearchResponse,
        onAction: (ResultPageAction) -> Unit
    ) {
        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            onClick = {
                onAction(
                    ResultPageAction.ResultAction(
                        SearchResponseAction(response, SearchResponseOperation.Stream)
                    )
                )
            },
            // Elevation actually changes the color, because who wanted a sane framework
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                focusedElevation = 0.dp,
                hoveredElevation = 0.dp
            ),
            containerColor = MaterialTheme.colorScheme.onBackground,
            contentColor = MaterialTheme.colorScheme.surfaceVariant,
            text = {
                Text(stringResource(R.string.stream_read))
            },
            icon = {
                Icon(
                    modifier = Modifier.size(24.dp),
                    painter = painterResource(R.drawable.netflix_play),
                    contentDescription = stringResource(R.string.stream_read)
                )
            },
            expanded = false
        )
    }

    @Composable
    fun SortHeader(chapters: ImmutableChapterList, onAction: (ResultPageAction) -> Unit) {
        var selectSortingMethodDialog by remember { mutableStateOf(false) }

        if (selectSortingMethodDialog) {
            SinglePairSelectDialog(
                entries = chapterSortingMethods.associate {
                    (it.id to it.inverse) to stringResource(
                        it.name
                    )
                },
                selectedKey = chapters.sortingMethod,
                title = stringResource(R.string.filter_dialog_sort_by),
                confirmText = stringResource(R.string.sort_apply),
                dismissText = stringResource(R.string.sort_cancel),
                dismiss = {
                    selectSortingMethodDialog = false
                },
                confirm = { key ->
                    onAction(SetChapterSort(key))
                    selectSortingMethodDialog = false
                }
            )
        }

        RootBaseSearchBar(
            windowInsets = WindowInsets(),
            onQueryChange = { query ->
                onAction(SetChapterFilterQuery(query))
            }, onSearch = { query ->
                onAction(SetChapterFilterQuery(query))
            },
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.search_icon),
                    contentDescription = stringResource(R.string.search),
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            },
            trailingIcon = {
                IconButton(onClick = {
                    selectSortingMethodDialog = true
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_sort_24dp),
                        tint = MaterialTheme.colorScheme.onBackground,
                        contentDescription = stringResource(R.string.mainpage_sort_by_button_text),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            placeholder = stringResource(R.string.search_chapters_hint)
        )
    }

    @Composable
    fun FilterHeader(chapters: ImmutableChapterList, onAction: (ResultPageAction) -> Unit) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(state = rememberScrollState())
        ) {
            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
            SelectSingleButton(
                name = R.string.all to R.string.all,
                filterMethodType = ChapterFilterMethodType.All to ChapterFilterMethodType.All,
                filtering = chapters.filtering.filterMethodType,
                onAction = onAction
            )
            SelectSingleButton(
                name = R.string.downloaded to R.string.not_downloaded,
                filterMethodType = ChapterFilterMethodType.Downloaded to ChapterFilterMethodType.UnDownloaded,
                filtering = chapters.filtering.filterMethodType,
                onAction = onAction
            )
            SelectSingleButton(
                name = R.string.has_read to R.string.not_read,
                filterMethodType = ChapterFilterMethodType.Read to ChapterFilterMethodType.UnRead,
                filtering = chapters.filtering.filterMethodType,
                onAction = onAction
            )
            Spacer(modifier = Modifier.width(MaterialTheme.padding.small))
        }
    }

    @Composable
    fun ChapterPage(
        response: ImmutableSearchResponse,
        chapters: ImmutableChapterList,
        onAction: (ResultPageAction) -> Unit,
        nestedScrollConnection: NestedScrollConnection,
        innerListState: LazyListState,
    ) {
        LazyColumn(
            state = innerListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .background(MaterialTheme.colorScheme.background),
        ) {
            item(key = "sort header") {
                SortHeader(chapters = chapters, onAction = onAction)
            }
            item(key = "filter header") {
                FilterHeader(chapters = chapters, onAction = onAction)
            }
            items(chapters.sorted, key = { index ->
                index
            }) { index ->
                ChapterItem(
                    response = response,
                    chapter = chapters.data[index]!!,
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                    selection = chapters.selected
                )
            }
        }
    }

    @Composable
    fun SelectSingleButton(
        name: Pair<Int, Int>,
        filtering: PersistentSet<ChapterFilterMethodType>,
        filterMethodType: Pair<ChapterFilterMethodType, ChapterFilterMethodType>,
        onAction: (ResultPageAction) -> Unit,
    ) {
        val (aName, bName) = name
        val (aFilter, bFilter) = filterMethodType
        val aSelected = filtering.contains(aFilter)
        val bSelected = filtering.contains(bFilter)

        WhiteFilterChip(
            selected = aSelected || bSelected,
            label = stringResource(if (bSelected) bName else aName)
        ) {
            if (aSelected) {
                onAction(SetChapterFilterMethod(persistentSetOf(bFilter)))
            } else {
                onAction(SetChapterFilterMethod(persistentSetOf(aFilter)))
            }
        }
    }

    @Composable
    fun ChapterItem(
        response: ImmutableSearchResponse,
        chapter: ImmutableChapterData,
        onAction: (ResultPageAction) -> Unit,
        selection: PersistentSet<Int>,
        modifier: Modifier
    ) {
        val isSelectingChapters = selection.isNotEmpty()
        val isSelected = selection.contains(chapter.index)

        val toggleClick = {
            onAction(
                SelectChapters(
                    if (isSelected) {
                        selection.removing(chapter.index)
                    } else {
                        selection.adding(chapter.index)
                    }
                )
            )
        }

        val backgroundColor = if (isSelected) {
            MaterialTheme.colorScheme.onBackground
        } else {
            Color.Transparent
        }

        val contentColor = if (isSelected) {
            MaterialTheme.colorScheme.background
        } else {
            MaterialTheme.colorScheme.onBackground
        }

        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = modifier
                    .fillMaxWidth()
                    .background(backgroundColor)
                    .combinedClickable(
                        role = Role.Button,
                        onClick = if (isSelectingChapters) {
                            toggleClick
                        } else {
                            {
                                onAction(
                                    ChapterAction(
                                        response,
                                        chapter,
                                        ChapterOperation.Stream
                                    )
                                )
                            }
                        },
                        onClickLabel = if (isSelectingChapters) {
                            stringResource(R.string.select_chapter)
                        } else {
                            stringResource(R.string.stream_read)
                        },
                        onLongClick = toggleClick,
                        onLongClickLabel = stringResource(R.string.select_chapter)
                    )
                    .padding(MaterialTheme.padding.small)
            ) {
                AnimatedVisibility(response.chaptersRead?.get(chapter.index) == true) {
                    Icon(
                        painter = painterResource(R.drawable.check_24px),
                        contentDescription = stringResource(R.string.has_read)
                    )
                }

                Spacer(
                    Modifier
                        .width(MaterialTheme.padding.small)
                )

                Column(modifier = Modifier.weight(1.0f)) {
                    Text(
                        text = chapter.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chapter.dateOfRelease != null) {
                        Text(
                            text = chapter.dateOfRelease,
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(
                    Modifier
                        .width(MaterialTheme.padding.small)
                        .weight(0.01f)
                )

                val downloadState = response.downloadState
                val isDownloaded = downloadState?.downloadedChapters?.get(chapter.index)
                val isDownloading = downloadState?.chapterProgress?.progress == chapter.index
                val isPending = downloadState?.pendingChapters?.get(chapter.index)

                when {
                    isDownloading -> null to null
                    isPending == true -> R.drawable.not_started to null
                    isDownloaded == true -> R.drawable.check_circle to null
                    isDownloaded == false -> R.drawable.arrow_circle_down_24px to ChapterOperation.Download
                    else -> null
                }?.let { (icon, action) ->
                    IconButton(onClick = {
                        if (action != null) {
                            onAction(
                                ChapterAction(
                                    response,
                                    chapter,
                                    action
                                )
                            )
                        }
                    }) {
                        if (icon != null) {
                            Icon(painter = painterResource(icon), contentDescription = null)
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(24.dp),
                                color = LocalContentColor.current,
                                strokeWidth = 3.0.dp
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ReviewsPage(
        loadingReviews: Boolean,
        reviews: PersistentList<ImmutableReview>,
        nestedScrollConnection: NestedScrollConnection,
        onAction: (ResultPageAction) -> Unit,
        innerListState: LazyListState,
    ) {
        LazyColumn(
            state = innerListState,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .background(MaterialTheme.colorScheme.background),
        ) {
            items(reviews, key = { item ->
                item.randomUuid
            }) { review ->
                ReviewItem(review, modifier = Modifier.animateItem(), onAction = onAction)
            }
        }

        val shouldLoadMore = remember {
            derivedStateOf {
                val lastVisibleIndex =
                    innerListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = innerListState.layoutInfo.totalItemsCount
                lastVisibleIndex >= totalItems - 5
            }
        }

        LaunchedEffect(shouldLoadMore.value) {
            if (shouldLoadMore.value && !loadingReviews) {
                onAction(ExpandReviews)
            }
        }
    }


    @Composable
    fun ReviewItem(
        review: ImmutableReview,
        modifier: Modifier,
        onAction: (ResultPageAction) -> Unit,
    ) {
        val expanded = rememberSaveable { mutableStateOf(false) }
        val showSpoiler = rememberSaveable { mutableStateOf(false) }

        Column(
            modifier = modifier
                .padding(vertical = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
            ) {
                if (review.avatarUrl != null) {
                    AsyncImage(
                        contentScale = ContentScale.Crop,
                        model = review.imageRequest(),
                        contentDescription = stringResource(R.string.user_image_avatar),
                        modifier = Modifier
                            .size(40.dp)
                            .circle()
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                    )
                }

                Column {
                    Text(
                        text = review.title ?: stringResource(R.string.no_data),
                        style = MaterialTheme.typography.titleMedium,
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(
                            MaterialTheme.padding.small
                        )
                    ) {
                        if (review.username != null) {
                            Text(
                                text = review.username,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        if (review.date != null) {
                            Text(
                                text = review.date,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            if (review.rating != null || review.ratings != null || review.containsSpoilers) {
                FlowRow(
                    modifier = Modifier
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .padding(top = MaterialTheme.padding.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    if (review.containsSpoilers) {
                        RatingChip(text = stringResource(R.string.spoilers), isWhite = true)
                    }
                    review.rating?.let {
                        RatingChip(
                            text = stringResource(R.string.overall) + " " + LocalContext.current.getRatingReview(
                                it
                            ), isWhite = true
                        )
                    }
                    review.ratings?.forEach { (rating, name) ->
                        RatingChip(
                            text = "$name " + LocalContext.current.getRatingReview(
                                rating
                            ), isWhite = false
                        )
                    }
                }
            }

            val isSpoilerActive = review.containsSpoilers && !showSpoiler.value

            Text(
                modifier = Modifier
                    .padding(horizontal = MaterialTheme.padding.small)
                    .fillMaxWidth()
                    .rounded()
                    .clickable(onClick = {
                        if (isSpoilerActive) {
                            showSpoiler.value = true
                        } else {
                            expanded.value = !expanded.value
                        }
                    })
                    .padding(MaterialTheme.padding.small)
                    .background(
                        if (isSpoilerActive) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            Color.Transparent
                        }, shape = RoundedShape()
                    ),
                text = review.content.html(),
                color = if (isSpoilerActive) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded.value) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Composable
    fun RatingChip(text: String, isWhite: Boolean) {
        Text(
            modifier = Modifier
                .background(
                    color = if (isWhite) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape
                )
                .padding(MaterialTheme.padding.small),
            color = if (isWhite) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
            text = text,
            style = MaterialTheme.typography.bodyMedium,
        )
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun NovelPage(
        state: ResultState,
        onAction: (ResultPageAction) -> Unit,
        innerListState: LazyListState,
    ) {
        val expanded = rememberSaveable { mutableStateOf(false) }
        val response = state.response ?: return

        LazyColumn(state = innerListState) {
            item(key = "infobar") {
                Row(
                    modifier = Modifier
                        .padding(5.dp)
                        .fillMaxWidth()
                        .height(60.dp)
                ) {
                    TextInfo(
                        response.loadData?.views?.let(::humanReadableByteCountSI)
                            ?: stringResource(R.string.no_data), stringResource(R.string.views)
                    )
                    TextInfo(response.rating?.let {
                        LocalContext.current.getRating(it)
                    } ?: stringResource(R.string.no_data),
                        response.loadData?.peopleVoted?.let {
                            stringResource(
                                R.string.votes_format,
                                it
                            )
                        }
                            ?: stringResource(R.string.no_data))
                    TextInfo(
                        response.loadData?.chapters?.size?.toString()
                            ?: stringResource(R.string.no_data),
                        stringResource(R.string.chapters)
                    )
                }
            }

            item(key = "spacer") {
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = MaterialTheme.padding.medium)
                        .fillMaxWidth()
                        .background(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                )
            }

            if (!response.synopsis.isNullOrBlank()) {
                item(key = "synopsis") {
                    Text(
                        modifier = Modifier
                            .padding(5.dp)
                            .clickable(
                                onClick = {
                                    expanded.value = !expanded.value
                                })
                            .rounded()
                            .padding(5.dp),
                        text = response.synopsis.html(),
                        color = MaterialTheme.colorScheme.onBackground,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (expanded.value) Int.MAX_VALUE else 8,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val tags = response.tags
            if (tags != null) {
                item(key = "tags") {
                    Tags(tags)
                }
            }

            if (response.downloadState != null) {
                item(key = "downloads") {
                    DownloadButtons(response, response.downloadState, onAction)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun DownloadButtons(
        response: ImmutableSearchResponse,
        downloadState: ImmutableDownloadState,
        onAction: (ResultPageAction) -> Unit
    ) {
        Row(Modifier.padding(horizontal = MaterialTheme.padding.medium)) {
            IconTextButton(
                modifier = Modifier
                    .weight(1.0f),
                onClick = {
                    onAction(
                        ResultPageAction.ResultAction(
                            SearchResponseAction(
                                response, SearchResponseOperation.Read
                            )
                        )
                    )
                },
                loading = response.generating,
                icon = painterResource(R.drawable.book_2_24px),
                text = stringResource(R.string.read_epub),
                isWhite = true
            )

            Spacer(Modifier.width(MaterialTheme.padding.small))

            // Overwrite nothing to done when we are finished
            val action =
                DownloadStateAction(
                    if (downloadState.downloadedChapters.length() >= (response.chapters
                            ?: 1) && downloadState.downloadStatus == BookDownloadStatus.Idle
                    ) BookDownloadStatus.Done else downloadState.downloadStatus
                )
            IconTextButton(
                modifier = Modifier.weight(1.0f),
                onClick = {
                    onAction(
                        ResultPageAction.ResultAction(
                            SearchResponseAction(
                                response, action.operation
                            )
                        )
                    )
                },
                loading = downloadState.downloadStatus == BookDownloadStatus.Pending,
                icon = action.iconBig?.let { icon -> painterResource(icon) },
                text = stringResource(action.operationName),
                isWhite = false
            )

            /*TextButton(
                colors = whiteButtonColors,
                modifier = Modifier
                    .padding(2.dp)
                    .weight(1.0f),
                onClick = {
                    action(
                        ResultPageAction.ResultAction(
                            SearchResponseAction(
                                response, SearchResponseOperation.Read
                            )
                        )
                    )
                }) {
                Text(stringResource(R.string.read_epub))
            }*/
        }

        IconTextButton(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium),
            onClick = {
                onAction(
                    ResultAction(
                        SearchResponseAction(
                            response, SearchResponseOperation.Stream
                        )
                    )
                )
            },
            icon = painterResource(R.drawable.play_arrow_24px),
            text = stringResource(R.string.stream_read),
            isWhite = true
        )

        val animatedProgress: Float by animateFloatAsState(
            response.downloadPercentage ?: 0.0f,
            label = "alpha",
            animationSpec = tween(durationMillis = 1000),
        )

        val animatedWavy: Float by animateFloatAsState(
            if (downloadState.downloadStatus != BookDownloadStatus.Pending) {
                0f
            } else {
                1f
            },
            label = "alpha",
            animationSpec = tween(durationMillis = 500),
        )

        LinearWavyProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            amplitude = { animatedWavy },
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(60.dp))
    }

    @Composable
    fun IconTextButton(
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
        loading: Boolean = false,
        icon: Painter?,
        text: String,
        isWhite: Boolean
    ) {
        TextButton(
            shape = RoundedShape(),
            colors = if (isWhite) Colors.whiteButton else Colors.blackButton,
            modifier = modifier,
            onClick = onClick
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp),
                        color = if (isWhite) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 3.0.dp
                    )
                } else {
                    if (icon != null) {
                        Icon(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(24.dp),
                            contentDescription = null,
                            painter = icon
                        )
                    }
                    Text(text = text)
                }
            }
        }
    }

    @Composable
    fun IconTextButton2(
        modifier: Modifier = Modifier,
        onClick: () -> Unit,
        icon: Painter,
        text: String,
        loading: Boolean = false,
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .rounded()
                .clickable(
                    onClick = onClick,
                )
                .padding(horizontal = MaterialTheme.padding.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(24.dp)
                        .padding(2.dp),
                    color = MaterialTheme.colorScheme.onBackground,
                    strokeWidth = 3.0.dp
                )
            } else {
                Icon(painter = icon, contentDescription = null)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(MaterialTheme.padding.medium)
            )
        }

        /*TextButton(
            shape = RoundedShape(),
            colors = if (isWhite) Colors.whiteButton else Colors.blackButton,
            modifier = modifier,
            onClick = onClick
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                        strokeWidth = 3.0.dp
                    )
                } else {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp),
                        contentDescription = null,
                        painter = icon
                    )
                    Text(text = text)
                }
            }
        }*/
    }

    @Composable
    fun Tags(tags: ImmutableList<String>) {
        val tagInteractionSource = remember { MutableInteractionSource() }

        val expandedTags = rememberSaveable { mutableStateOf(false) }
        FlowRow(
            Modifier
                .fillMaxWidth()
                .padding(5.dp)
                .rounded()
                .clickable(
                    onClick = {
                        expandedTags.value = !expandedTags.value
                    }, interactionSource = tagInteractionSource
                ), maxLines = if (expandedTags.value) {
                Int.MAX_VALUE
            } else {
                3
            }
        ) {
            tags.forEach { tag ->
                Text(
                    text = tag,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(MaterialTheme.padding.extraSmall)
                        .rounded()
                        .background(color = MaterialTheme.colorScheme.surfaceVariant)
                        .padding(7.dp)
                )
            }
        }
    }

    @Composable
    fun RelatedPage(
        nestedScrollConnection: NestedScrollConnection,
        related: ImmutableList<ImmutableSearchResponse>,
        onAction: (ResultPageAction) -> Unit,
        innerListState: LazyGridState
    ) {
        SearchList(
            lazyGridState = innerListState,
            isRow = false,
            items = related,
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection)
                .background(MaterialTheme.colorScheme.background),
            searchAction = { value: SearchResponseAction ->
                onAction(ResultPageAction.ResultAction(value))
            })
    }


    @Composable
    fun RowScope.TextInfo(
        text: String, subText: String
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
        ) {
            Text(text = text, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    fun RowScope.TextIcon(text: String, icon: Int, onClick: () -> Unit) {
        val interactionSource = remember { MutableInteractionSource() }
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1.0f)
                .fillMaxHeight()
                .clickable(interactionSource = interactionSource, onClick = onClick)
                .circle()
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(MaterialTheme.padding.extraSmall),
                textAlign = TextAlign.Center
            )
            Icon(
                painter = painterResource(icon), contentDescription = text
            )
        }
    }

    @Composable
    fun LoadingScreen(modifier: Modifier) {
        Column(
            modifier = modifier.padding(MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
            ) {
                LoadingPoster()
                Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
                ) {
                    LoadingLine()
                    LoadingLine(0.7f)
                }
            }
            LoadingLine()
            Row(horizontalArrangement = Arrangement.SpaceEvenly) {
                Spacer(Modifier.weight(0.3f))
                LoadingWeight()
                Spacer(Modifier.weight(0.3f))
                LoadingWeight()
                Spacer(Modifier.weight(0.3f))
            }
            Spacer(Modifier.height(20.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
            ) {
                LoadingWidth(100.dp)
                LoadingWidth(100.dp)
            }
            Spacer(Modifier.height(20.dp))
            LoadingLine()
            LoadingLine()
            LoadingLine(0.4f)
            Spacer(Modifier.height(20.dp))
            LoadingLine(0.3f)
            LoadingLine()
            LoadingLine()
            LoadingLine(0.4f)
            Spacer(Modifier.height(20.dp))
            LoadingLine()
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium)
            ) {
                LoadingButton()
                LoadingButton()
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(35.dp)
                    .loading()
            )
        }
    }
}

/*
@PreviewLightDark
@Composable
fun LoadingPreview() {
    CloudStreamPreviewTheme {
        Surface {
            ResultScreen.ResultScreen(
                state = ResultState(
                    loadingResponse = false,
                    responseError = null,//Throwable("hello world"),
                    response = ImmutableSearchResponse.preview(),
                    api = APIRepository(RoyalRoadProvider())
                )
            ) { }
        }
    }
}*/
/*
@PreviewLightDark
@Composable
fun ReviewPreview() {
    CloudStreamPreviewTheme {
        Surface {
            ResultScreen.ReviewItem(
                review = ImmutableReview(
                    content = "hello world",
                    title = "title",
                    username = "username",
                    date = "today",
                    avatarUrl = "https://www.royalroad.com/dist/img/anon.jpg",
                    avatarHeaders = persistentMapOf(),
                    rating = 1337,
                    ratings = persistentListOf()
                ),
                modifier = Modifier.fillMaxSize(),
                onAction = {}
            )
        }
    }
}*/

@PreviewLightDark
@Composable
fun ChapterPreview() {
    CloudStreamPreviewTheme {
        Surface {
            ResultScreen.ChapterItem(
                response = ImmutableSearchResponse.preview(),
                modifier = Modifier,
                onAction = {},
                selection = persistentSetOf(),
                chapter = ImmutableChapterData(
                    name = "hello world",
                    url = "",
                    dateOfRelease = "date",
                    views = 123,
                    index = 2
                )
            )
        }
    }
}