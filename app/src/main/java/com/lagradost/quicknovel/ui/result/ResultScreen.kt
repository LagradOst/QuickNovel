package com.lagradost.quicknovel.ui.result

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.CommonActivity.activity
import com.lagradost.quicknovel.DownloadState
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BackHandler
import com.lagradost.quicknovel.compose.BaseStyles.blackButtonColors
import com.lagradost.quicknovel.compose.BaseStyles.whiteButtonColors
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.RoundedImageShape
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.mvvm.safe
import com.lagradost.quicknovel.ui.common.HorizontalTab
import com.lagradost.quicknovel.ui.common.ImmutableChapterData
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
import com.lagradost.quicknovel.ui.common.html
import com.lagradost.quicknovel.ui.common.loading
import com.lagradost.quicknovel.ui.common.loadingLineMargin
import com.lagradost.quicknovel.util.AppUtils.openInBrowser
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.SettingsHelper.getRatingReview
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.toPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlin.uuid.ExperimentalUuidApi


@Composable
fun ResultScreen(state: ResultState, action: (ResultPageAction) -> Unit) {
    Scaffold { innerPadding ->
        if (state.loadingResponse) {
            LoadingScreen(Modifier.padding(innerPadding))
        } else {
            ResultScreenImpl(innerPadding, state, action)
        }
    }
}

@Composable
fun ResultScreenImpl(
    padding: PaddingValues, state: ResultState, action: (ResultPageAction) -> Unit
) {
    val response = state.response ?: return
    // val scrollState = rememberScrollState()
    val posterInteractionSource = remember { MutableInteractionSource() }
    val posterBigInteractionSource = remember { MutableInteractionSource() }

    val isPosterShown = remember { mutableStateOf(false) }

    val tabNames = persistentListOf(
        R.string.novel, R.string.reviews, R.string.related, R.string.chapters
    )
    val pagerState = rememberPagerState(
        initialPage = 0, pageCount = { tabNames.size })

    val outerListState = rememberLazyListState()

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
                        .ripple(interactionSource = posterInteractionSource)
                        .rounded(),
                    model = response.imageRequest(),
                    contentDescription = stringResource(R.string.poster_descript),
                )
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        response.name,
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (response.author != null) {
                        Text(response.author, color = colors.primary, fontSize = 14.sp)
                    }
                    response.loadData?.status?.let { status ->
                        Text(
                            stringResource(status.resource),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    response.loadData?.chapters?.lastOrNull()?.let { chapter ->
                        Text(stringResource(R.string.latest_format, chapter.name), fontSize = 14.sp)
                    }
                }
            }
        }

        val parentFirstScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
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
            item {
                Spacer(
                    Modifier
                        .height(170.dp + padding.calculateTopPadding())
                        .fillMaxWidth()
                        .combinedClickable(
                            interactionSource = posterInteractionSource,
                            indication = null,
                            onClick = {
                                isPosterShown.value = !isPosterShown.value
                            })
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(15.dp, 15.dp))
                        .background(color = colors.background)
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(5.dp)
                            .fillMaxWidth()
                            .height(75.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(colors.surfaceContainer)
                    ) {
                        TextIcon(
                            R.string.bookmark,
                            R.drawable.ic_baseline_add_24,
                        ) {

                        }
                        TextIcon(
                            R.string.open_in_browser,
                            R.drawable.ic_baseline_public_24,
                        ) {
                            safe {
                                openInBrowser(response.url)
                            }
                        }
                        TextIcon(
                            R.string.result_share,
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
            item {
                HorizontalTab(
                    edgePadding = 15.dp,
                    pagerState = pagerState,
                    names = tabNames,
                    containerColor = colors.background
                )
            }
            item {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillParentMaxHeight()
                        .background(colors.background),
                    verticalAlignment = Alignment.Top
                ) { page ->
                    when (page) {
                        0 -> {
                            NovelPage(state, action)
                        }

                        1 -> {
                            ReviewsPage(
                                loadingReviews = state.reviews.loading,
                                reviews = state.reviews.items,
                                nestedScrollConnection = parentFirstScrollConnection,
                                action = action
                            )
                        }

                        2 -> {
                            RelatedPage(
                                nestedScrollConnection = parentFirstScrollConnection,
                                related = response.loadData?.related ?: persistentListOf(),
                                action = action
                            )
                        }

                        3 -> {
                            ChapterPage(
                                response = response,
                                chapters = response.loadData?.chapters ?: persistentListOf(),
                                action = action,
                                nestedScrollConnection = parentFirstScrollConnection
                            )
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
                    .background(color = colors.background.copy(alpha = 0.8f * animatedAlpha))
                    .combinedClickable(
                        interactionSource = posterBigInteractionSource,
                        indication = null,
                        onClick = {
                            isPosterShown.value = !isPosterShown.value
                        })
                    .ripple(posterBigInteractionSource)
                    .alpha(animatedAlpha),
                model = response.imageRequest(),
                contentDescription = stringResource(R.string.poster_descript),
            )
        }
    }
}

@Composable
fun ChapterPage(
    response: ImmutableSearchResponse,
    chapters: PersistentList<ImmutableChapterData>,
    action: (ResultPageAction) -> Unit,
    nestedScrollConnection: NestedScrollConnection
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(colors.background),
    ) {
        items(chapters, key = { item ->
            @OptIn(ExperimentalUuidApi::class) item.randomUuid
        }) { review ->
            ChapterItem(response, review, action = action, modifier = Modifier.animateItem())
        }
    }
}

@Composable
fun ChapterItem(
    response: ImmutableSearchResponse,
    chapter: ImmutableChapterData,
    action: (ResultPageAction) -> Unit,
    modifier: Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = interactionSource, onClick = {
                    action(
                        ResultPageAction.ChapterAction(
                            response,
                            chapter,
                            ChapterOperation.Stream
                        )
                    )
                }, onLongClick = {
                    // TODO show 3 dots info
                }, indication = null
            )
            .ripple(interactionSource = interactionSource)
            .padding(10.dp)
    ) {
        Text(chapter.name, fontSize = 14.sp, lineHeight = 13.sp)
        if (chapter.dateOfRelease != null) {
            Text(
                chapter.dateOfRelease,
                fontSize = 12.sp,
                lineHeight = 11.sp,
                color = colors.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ReviewsPage(
    loadingReviews: Boolean,
    reviews: PersistentList<ImmutableReview>,
    nestedScrollConnection: NestedScrollConnection,
    action: (ResultPageAction) -> Unit,
) {
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(colors.background),
    ) {
        items(reviews, key = { item ->
            @OptIn(ExperimentalUuidApi::class) item.randomUuid
        }) { review ->
            ReviewItem(review, modifier = Modifier.animateItem(), action = action)
        }
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleIndex >= totalItems - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value && !loadingReviews) {
            action(ResultPageAction.ExpandReviews)
        }
    }
}


@Composable
fun ReviewItem(
    review: ImmutableReview,
    modifier: Modifier,
    action: (ResultPageAction) -> Unit,
) {
    val textInteractionSource = remember { MutableInteractionSource() }
    val expanded = rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier
            .padding(bottom = 10.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
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
                            color = colors.onBackground.copy(alpha = 0.2f),
                            shape = CircleShape
                        )
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 10.dp),
            ) {
                Text(
                    text = review.title ?: stringResource(R.string.no_data),
                    fontSize = 14.sp,
                    lineHeight = 13.sp,
                )

                Row {
                    if (review.username != null) {
                        Text(
                            text = review.username,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 11.sp,
                            modifier = Modifier.padding(end = 5.dp)
                        )
                    }
                    if (review.date != null) {
                        Text(
                            text = review.date,
                            color = colors.onSurfaceVariant,
                            fontSize = 12.sp,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }

        FlowRow(modifier = Modifier.padding(horizontal = 6.dp)) {
            review.rating?.let {
                Text(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(color = colors.onBackground, shape = RoundedImageShape())
                        .padding(horizontal = 6.dp),
                    color = colors.background,
                    text = stringResource(R.string.overall) + " " + LocalContext.current.getRatingReview(
                        it
                    ),
                    fontSize = 13.sp
                )
            }
            review.ratings?.forEach { (rating, name) ->
                Text(
                    modifier = Modifier
                        .padding(4.dp)
                        .background(color = colors.surfaceVariant, shape = RoundedImageShape())
                        .padding(horizontal = 6.dp),
                    color = colors.onBackground,
                    text = "$name " + LocalContext.current.getRatingReview(
                        rating
                    ),
                    fontSize = 13.sp
                )
            }
        }

        Text(
            modifier = Modifier
                .padding(5.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = textInteractionSource, indication = null, onClick = {
                        expanded.value = !expanded.value
                    })
                .rounded()
                .ripple(textInteractionSource)
                .padding(5.dp),
            text = review.content.html(),
            color = colors.onBackground,
            fontSize = 14.sp,
            lineHeight = 15.sp,
            maxLines = if (expanded.value) Int.MAX_VALUE else 8,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NovelPage(
    state: ResultState, action: (ResultPageAction) -> Unit
) {
    val expanded = rememberSaveable { mutableStateOf(false) }
    val textInteractionSource = remember { MutableInteractionSource() }
    val response = state.response ?: return

    Column {
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
                response.loadData?.peopleVoted?.let { stringResource(R.string.votes_format, it) }
                    ?: stringResource(R.string.no_data))
            TextInfo(
                response.loadData?.chapters?.size?.toString() ?: stringResource(R.string.no_data),
                stringResource(R.string.chapters)
            )
        }

        Box(
            modifier = Modifier
                .height(1.dp)
                .padding(horizontal = 15.dp)
                .fillMaxWidth()
                .background(color = colors.onBackground.copy(alpha = 0.5f))
        )

        if (!response.synopsis.isNullOrBlank()) {
            Text(
                modifier = Modifier
                    .padding(5.dp)
                    .clickable(
                        interactionSource = textInteractionSource, indication = null, onClick = {
                            expanded.value = !expanded.value
                        })
                    .rounded()
                    .ripple(textInteractionSource)
                    .padding(5.dp),
                text = response.synopsis.html(),
                color = colors.onBackground,
                fontSize = 14.sp,
                lineHeight = 15.sp,
                maxLines = if (expanded.value) Int.MAX_VALUE else 8,
                overflow = TextOverflow.Ellipsis
            )
        }

        val downloadState = response.downloadState ?: return

        val tagInteractionSource = remember { MutableInteractionSource() }

        val expandedTags = rememberSaveable { mutableStateOf(false) }
        if (response.tags != null) {
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
                response.tags.forEach { tag ->
                    Text(
                        text = tag,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .padding(4.dp)
                            .rounded()
                            .background(color = colors.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
        /*
                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .padding(horizontal = 15.dp)
                        .fillMaxWidth()
                        .background(color = colors.onBackground.copy(alpha = 0.5f))
                )*/

        // Text(stringResource(R.string.downloaded), modifier = Modifier.padding(5.dp))

        /*Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = downloadState.progress.toString())
            LinearWavyProgressIndicator(
                progress = { downloadState.progressPercentage },
                modifier = Modifier
                    .weight(1.0f)
                    .padding(10.dp),
                amplitude = indicatorAmplitude
            )
            Text(text = downloadState.total.toString())
        }*/

        Row(Modifier.padding(horizontal = 10.dp)) {
            TextButton(
                colors = whiteButtonColors,
                modifier = Modifier
                    .padding(2.dp)
                    .weight(1.0f),
                onClick = {
                    action(
                        ResultPageAction.ResultAction(
                            SearchResponseAction(
                                response, SearchResponseOperation.Stream
                            )
                        )
                    )
                }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp),
                        painter = painterResource(R.drawable.ic_baseline_play_arrow_24),
                        contentDescription = null
                    )
                    Text(stringResource(R.string.stream_read))
                }
            }
            Spacer(Modifier.width(5.dp))
            TextButton(
                colors = blackButtonColors,
                modifier = Modifier
                    .padding(2.dp)
                    .weight(1.0f),
                onClick = {
                    action(
                        ResultPageAction.ResultAction(
                            SearchResponseAction(
                                response, response.downloadState.operation
                            )
                        )
                    )
                }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp),
                        painter = painterResource(R.drawable.netflix_download),
                        contentDescription = null
                    )
                    Text(stringResource(R.string.download))
                }
            }


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

        val animatedProgress: Float by animateFloatAsState(
            downloadState.progressPercentage,
            label = "alpha",
            animationSpec = tween(durationMillis = 1000),
        )

        val animatedWavy: Float by animateFloatAsState(
            if (downloadState.status != DownloadState.IsDownloading) {
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
                .padding(10.dp),
            amplitude = { animatedWavy },
            color = colors.onBackground
        )
    }
}

@Composable
fun RelatedPage(
    nestedScrollConnection: NestedScrollConnection,
    related: ImmutableList<ImmutableSearchResponse>,
    action: (ResultPageAction) -> Unit
) {
    SearchList(
        isRow = false,
        items = related,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(colors.background),
        searchAction = { value ->
            action(ResultPageAction.ResultAction(value))
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
        Text(text, fontSize = 17.sp, lineHeight = 16.sp)
        Text(
            subText,
            fontSize = 12.sp,
            lineHeight = 11.sp,
            modifier = Modifier.padding(2.dp),
            color = colors.onSurfaceVariant
        )
    }
}

@Composable
fun RowScope.TextIcon(text: Int, icon: Int, onClick: () -> Unit) {
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
            stringResource(text),
            fontSize = 16.sp,
            modifier = Modifier.padding(2.dp),
            textAlign = TextAlign.Center
        )
        Icon(
            painter = painterResource(icon), contentDescription = stringResource(text)
        )
    }
}

@Composable
fun LoadingScreen(modifier: Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LoadingPoster()
            Column {
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
        Row {
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
        Row {
            LoadingButton()
            LoadingButton()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(loadingLineMargin.dp)
                .height(35.dp)
                .loading()
        )
    }
}


@PreviewLightDark
@Composable
fun LoadingPreview() {
    CloudStreamTheme {
        Surface {
            @OptIn(ExperimentalUuidApi::class) ResultScreen(
                state = ResultState(
                    loadingResponse = false,
                    responseError = null,
                    response = ImmutableSearchResponse.preview()
                )
            ) { }
        }
    }
}

@PreviewLightDark
@Composable
fun ReviewPreview() {
    CloudStreamTheme {
        Surface {
            @OptIn(ExperimentalUuidApi::class)
            ReviewItem(
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
                action = {}
            )
        }
    }
}