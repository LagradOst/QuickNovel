package com.lagradost.quicknovel.ui.result

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
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
import com.lagradost.quicknovel.compose.BaseStyles.whiteButtonColors
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
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
import com.lagradost.quicknovel.ui.common.loading
import com.lagradost.quicknovel.ui.common.loadingLineMargin
import com.lagradost.quicknovel.util.AppUtils.openInBrowser
import com.lagradost.quicknovel.util.SettingsHelper.getRating
import com.lagradost.quicknovel.util.UIHelper.humanReadableByteCountSI
import com.lagradost.quicknovel.util.toPx
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlin.uuid.ExperimentalUuidApi


@Composable
fun ResultScreen(state: ResultState, action: (ResultPageAction) -> Unit) {
    Scaffold { innerPadding ->
        if (state.loading) {
            LoadingScreen(Modifier.padding(innerPadding))
        } else {
            ResultScreenImpl(Modifier.padding(innerPadding), state, action)
        }
    }
}

@Composable
fun ResultScreenImpl(
    modifier: Modifier, state: ResultState, action: (ResultPageAction) -> Unit
) {
    val response = state.response ?: return
    // val scrollState = rememberScrollState()


    val tabNames = persistentListOf(
        R.string.novel, R.string.reviews, R.string.related, R.string.chapters
    )
    val pagerState = rememberPagerState(
        initialPage = 0, pageCount = { tabNames.size })

    val outerListState = rememberLazyListState()

    val scrollAlpha = remember {
        derivedStateOf { 1.0f } // (outerListState.value.toFloat() / 200.toPx.toFloat()).coerceIn(0.0f, 1.0f)
    }

    Box {
        Box(
            modifier = modifier
                .height(height = 190.dp)
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
                    .padding(10.dp)
                    .scale(1.0f - scrollAlpha.value * 0.05f)
            ) {
                AsyncImage(
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .rounded(),
                    model = response.imageRequest(),
                    contentDescription = stringResource(R.string.poster_descript)
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
                Spacer(Modifier.height(210.dp))
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
                            .background(colors.surface)
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
                    pagerState = pagerState, names = tabNames, containerColor = colors.background
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
                                state.response.loadData?.reviews ?: persistentListOf(),
                                nestedScrollConnection = parentFirstScrollConnection
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
    reviews: PersistentList<ImmutableReview>, nestedScrollConnection: NestedScrollConnection
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .background(colors.background),
    ) {
        items(reviews, key = { item ->
            @OptIn(ExperimentalUuidApi::class) item.randomUuid
        }) { review ->
            ReviewItem(review, modifier = Modifier.animateItem())
        }
    }
}

@Composable
fun ReviewItem(review: ImmutableReview, modifier: Modifier) {
    Box(modifier = modifier.background(colors.surfaceVariant)) {

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
                .fillMaxWidth()
                .background(color = colors.onBackground.copy(alpha = 0.5f))
                .padding(horizontal = 15.dp)
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
                text = response.synopsis,
                color = colors.onBackground,
                fontSize = 14.sp,
                lineHeight = 15.sp,
                maxLines = if (expanded.value) Int.MAX_VALUE else 6,
                overflow = TextOverflow.Ellipsis
            )
        }

        val downloadState = response.downloadState ?: return

        val indicatorAmplitude: (progress: Float) -> Float = { progress ->
            // Sets the amplitude to the max on 10%, and back to zero on 95% of the progress.
            if (downloadState.status != DownloadState.IsDownloading || progress <= 0.1f || progress >= 0.95f) {
                0f
            } else {
                1f
            }
        }

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
                    2
                }
            ) {
                response.tags.forEach { tag ->
                    Text(
                        text = tag,
                        modifier = Modifier
                            .padding(5.dp)
                            .rounded()
                            .background(color = colors.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .height(1.dp)
                .fillMaxWidth()
                .background(color = colors.onBackground.copy(alpha = 0.5f))
                .padding(horizontal = 15.dp)
        )

        // Text(stringResource(R.string.downloaded), modifier = Modifier.padding(5.dp))

        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = downloadState.progress.toString())
            LinearWavyProgressIndicator(
                progress = { downloadState.progressPercentage },
                modifier = Modifier
                    .weight(1.0f)
                    .padding(10.dp),
                amplitude = indicatorAmplitude
            )
            Text(text = downloadState.total.toString())
        }

        Row {
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
                }) { Text(stringResource(R.string.stream_read)) }

            TextButton(
                colors = whiteButtonColors,
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
                }) { Text(stringResource(R.string.download)) }


            TextButton(
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
            }
        }

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
                    loading = false, error = null, response = ImmutableSearchResponse.preview()
                )
            ) { }
        }
    }
}