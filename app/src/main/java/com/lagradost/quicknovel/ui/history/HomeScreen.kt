package com.lagradost.quicknovel.ui.history


import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseDialog
import com.lagradost.quicknovel.compose.BaseStyles
import com.lagradost.quicknovel.compose.CloudStreamTheme
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.compose.CloudStreamThemeMode
import com.lagradost.quicknovel.compose.circle
import com.lagradost.quicknovel.compose.ripple
import com.lagradost.quicknovel.compose.rounded
import com.lagradost.quicknovel.util.ResultCached

@Composable
fun HistoryScreen() {
    val viewModel = viewModel<HistoryViewModel2>()
    val state by viewModel.state.collectAsStateWithLifecycle()

    /*val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel.effect, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effect.collect { effect ->
                when (effect) {
                    else -> {}
                }
            }
        }
    }*/

    HistoryScreenImpl(state, viewModel::onAction)
}

@Composable
fun HistoryScreenImpl(
    state: HistoryState,
    action: (HistoryAction) -> Unit
) {
    HistoryDialog(state.dialog, action)

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceVariant,
                ),
                title = {
                    Text(stringResource(R.string.history))
                },
                actions = {
                    IconButton(onClick = { action(HistoryAction.AskDeleteAll) }) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.clear_all_24px),
                            contentDescription = stringResource(R.string.history_more_options),
                            tint = colors.onBackground,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding),
            contentPadding = PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            items(state.history, key = { item -> item.id }) { result ->
                ResultCachedCompact(
                    result = result,
                    action = action,
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}


@Composable
fun HistoryDialog(
    dialog: HistoryDialog?,
    action: (HistoryAction) -> Unit
) {
    if (dialog == null) return

    val about = dialog.about
    if (about == null) {
        BaseDialog(
            title = stringResource(R.string.remove_history),
            text = stringResource(R.string.remove_all_history),
            confirmText = stringResource(R.string.remove),
            dismissText = stringResource(R.string.cancel),
            dismiss = {
                action(HistoryAction.DismissDialog)
            },
            confirm = {
                action(HistoryAction.DeleteAll)
            }
        )
    } else {
        BaseDialog(
            title = stringResource(R.string.remove),
            text = stringResource(
                R.string.remove_from_history_format,
                about.name
            ),
            confirmText = stringResource(R.string.remove),
            dismissText = stringResource(R.string.cancel),
            dismiss = {
                action(HistoryAction.DismissDialog)
            },
            confirm = {
                action(HistoryAction.ResultAction(about, ResultOperation.Delete))
            }
        )
    }
}


@Composable
fun ResultCachedCompact(
    result: ResultCached,
    action: (HistoryAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val streamInteractionSource = remember { MutableInteractionSource() }
    val deleteInteractionSource = remember { MutableInteractionSource() }

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
                    action(HistoryAction.ResultAction(result, ResultOperation.Open))
                },
                onLongClick = {
                    action(HistoryAction.ResultAction(result, ResultOperation.Metadata))
                }
            )
            .ripple(interactionSource)
    ) {
        AsyncImage(
            contentScale = ContentScale.Crop,
            model = result.poster,
            contentDescription = result.name,
            modifier = Modifier
                .width(67.5.dp)
                .fillMaxHeight()
                .rounded()
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = {
                        action(HistoryAction.ResultAction(result, ResultOperation.Open))
                    },
                    onLongClick = {
                        action(HistoryAction.ResultAction(result, ResultOperation.Metadata))
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
                result.name,
                maxLines = 2,
                style = BaseStyles.textStyle
            )
            Text(
                "${result.totalChapters} ${stringResource(R.string.read_action_chapters)}",
                style = BaseStyles.textAltStyle
            )
        }

        Spacer(Modifier.weight(1f))

        Image(
            imageVector = ImageVector.vectorResource(R.drawable.ic_baseline_delete_outline_24),
            contentDescription = stringResource(R.string.remove_history),
            modifier = Modifier
                .size(54.dp)
                .combinedClickable(
                    interactionSource = deleteInteractionSource,
                    indication = null,
                    onClick = {
                        action(HistoryAction.ResultAction(result, ResultOperation.AskDelete))
                    }
                )
                .circle()
                .ripple(deleteInteractionSource)
                .padding(15.dp)
        )

        Image(
            imageVector = ImageVector.vectorResource(R.drawable.netflix_play),
            contentDescription = stringResource(R.string.stream_read),
            modifier = Modifier
                .size(54.dp)
                .combinedClickable(
                    interactionSource = streamInteractionSource,
                    indication = null,
                    onClick = {
                        action(HistoryAction.ResultAction(result, ResultOperation.Stream))
                    }
                )
                .circle()
                .ripple(streamInteractionSource)
                .padding(15.dp)
        )

        Spacer(Modifier.width(10.dp))
    }
}


@Preview(name = "Dark") // Dark background
@Composable
private fun SettingsScreenPreview() {
    CloudStreamTheme(CloudStreamThemeMode.Dark) {
        HistoryScreen()
    }
}
