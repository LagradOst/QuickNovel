package com.lagradost.quicknovel.compose

import android.widget.Space
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.shouldUsePrecisionPointerComponentSizing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.BaseStyles.blackButtonColors
import com.lagradost.quicknovel.compose.BaseStyles.whiteButtonColors
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.ui.history.HistoryAction
import com.lagradost.quicknovel.ui.history.ResultOperation

@Composable
fun BaseDialog(
    title: String,
    text: String,
    confirmText: String,
    dismissText: String,

    dismiss: () -> Unit,
    confirm: () -> Unit
) {
    AlertDialog(
        containerColor = colors.background,
        onDismissRequest = dismiss,
        title = { Text(text = title) },
        text = { Text(text = text) },
        confirmButton = {
            Button(
                onClick = confirm,
                colors = whiteButtonColors
            ) { Text(text = confirmText) }
        },
        dismissButton = {
            Button(
                onClick = dismiss, colors = blackButtonColors
            ) { Text(text = dismissText) }
        }
    )
}

private val DialogPaddingValue =
    if (shouldUsePrecisionPointerComponentSizing()) 20.dp else 24.dp
private val TextPaddingValue = if (shouldUsePrecisionPointerComponentSizing()) 16.dp else 24.dp
private val DialogPadding = PaddingValues(all = DialogPaddingValue)
private val IconPadding = PaddingValues(bottom = 16.dp)
private val TitlePadding = PaddingValues(bottom = 16.dp)
private val TextPadding = PaddingValues(bottom = TextPaddingValue)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseDialog(
    modifier: Modifier = Modifier,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = colors.background,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    dismiss: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    BasicAlertDialog(onDismissRequest = dismiss, properties = properties) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor,
            tonalElevation = tonalElevation,
        ) {
            Column(modifier = Modifier.padding(DialogPadding)) {
                icon?.let {
                    CompositionLocalProvider(LocalContentColor provides iconContentColor) {
                        Box(
                            Modifier
                                .padding(IconPadding)
                                .align(Alignment.CenterHorizontally)
                        ) {
                            icon()
                        }
                    }
                }
                title?.let {
                    ProvideTextStyle(typography.headlineSmall) {
                        Box(
                            // Align the title to the center when an icon is present.
                            Modifier
                                .padding(TitlePadding)
                                .align(
                                    if (icon == null) {
                                        Alignment.Start
                                    } else {
                                        Alignment.CenterHorizontally
                                    }
                                )
                        ) {
                            title()
                        }
                    }
                }
                LazyColumn {
                    itemsIndexed(items) { index, text ->
                        val interactionSource = remember { MutableInteractionSource() }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = {
                                        onSelect(index)
                                    },
                                    onLongClick = {
                                    }
                                )
                                .rounded()
                                .ripple(interactionSource),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (index == selected) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_baseline_check_24_listview),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(text, color = colors.onBackground, modifier = Modifier.padding(15.dp))
                            } else {
                                Spacer(modifier = Modifier.size(24.dp))
                                Text(text, color = colors.onSurfaceVariant, modifier = Modifier.padding(15.dp))
                            }
                        }
                    }
                }
                /*text?.let {
                    val textStyle = DialogTokens.SupportingTextFont.value
                    ProvideContentColorTextStyle(
                        contentColor = textContentColor,
                        textStyle = textStyle,
                    ) {
                        Box(
                            Modifier.weight(weight = 1f, fill = false)
                                .padding(TextPadding)
                                .align(Alignment.Start)
                        ) {
                            text()
                        }
                    }
                }
                Box(modifier = Modifier.align(Alignment.End)) {
                    val textStyle = DialogTokens.ActionLabelTextFont.value
                    ProvideContentColorTextStyle(
                        contentColor = buttonContentColor,
                        textStyle = textStyle,
                        content = buttons,
                    )
                }*/
            }
        }
    }
}