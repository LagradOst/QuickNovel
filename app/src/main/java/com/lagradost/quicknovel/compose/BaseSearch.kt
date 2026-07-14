package com.lagradost.quicknovel.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SearchBarDefaults.inputFieldColors
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.TopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import com.lagradost.quicknovel.ui.history.HistoryAction
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseSearchBar(
    modifier: Modifier = Modifier,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    textFieldState: TextFieldState = rememberTextFieldState(),
    trailingIcon: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    CustomFlexibleTopAppBar(scrollBehavior = scrollBehavior) {
        Column(
            modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .background(colors.surfaceVariant)
                .semantics { isTraversalGroup = true },
        ) {
            SearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .semantics { traversalIndex = 0f },
                colors = SearchBarColors(
                    containerColor = colors.background,
                    dividerColor = Color.Transparent,
                    inputFieldColors = inputFieldColors()
                ),
                inputField = {
                    SearchBarDefaults.InputField(
                        query = textFieldState.text.toString(),
                        onQueryChange = {
                            textFieldState.edit { replace(0, length, it) }
                            onQueryChange(textFieldState.text.toString())
                        },
                        onSearch = {
                            onSearch(textFieldState.text.toString())
                            expanded = false
                        },
                        expanded = false,
                        onExpandedChange = { },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = leadingIcon,
                        trailingIcon = trailingIcon
                    )
                },
                expanded = expanded,
                onExpandedChange = { expanded = it },
                content = { },
            )
            Spacer(modifier = Modifier.height(5.dp))
            content()
        }
    }
}

@ExperimentalMaterial3Api
@Composable
fun CustomFlexibleTopAppBar(
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    content: @Composable () -> Unit,
) {
    // Sets the app bar's height offset to collapse the entire bar's height when content is
    // scrolled.
    var heightOffsetLimit by remember {
        mutableFloatStateOf(0f)
    }
    LaunchedEffect(heightOffsetLimit) {
        if (scrollBehavior?.state?.heightOffsetLimit != heightOffsetLimit) {
            scrollBehavior?.state?.heightOffsetLimit = heightOffsetLimit
        }
    }

    // Set up support for resizing the top app bar when vertically dragging the bar itself.
    val appBarDragModifier = if (scrollBehavior != null && !scrollBehavior.isPinned) {
        Modifier.draggable(
            orientation = Orientation.Vertical,
            state = rememberDraggableState { delta ->
                scrollBehavior.state.heightOffset += delta
            },
            onDragStopped = { velocity ->
                settleAppBar(
                    scrollBehavior.state,
                    velocity,
                    scrollBehavior.flingAnimationSpec,
                    scrollBehavior.snapAnimationSpec
                )
            }
        )
    } else {
        Modifier
    }

    // Compose a Surface with a TopAppBarLayout content.
    // The surface's background color is animated as specified above.
    // The height of the app bar is determined by subtracting the bar's height offset from the
    // app bar's defined constant height value (i.e. the ContainerHeight token).
    Surface(modifier = modifier.then(appBarDragModifier)) {
        Layout(
            content = content,
            modifier = modifier,
            measurePolicy = { measurables, constraints ->
                val placeable = measurables.first().measure(constraints.copy(minWidth = 0))
                heightOffsetLimit = placeable.height.toFloat() * -1
                val scrollOffset = scrollBehavior?.state?.heightOffset ?: 0f
                val height = placeable.height.toFloat() + scrollOffset
                val layoutHeight = maxOf(0,height.roundToInt())
                layout(constraints.maxWidth, layoutHeight) {
                    placeable.place(0, scrollOffset.toInt())
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private suspend fun settleAppBar(
    state: TopAppBarState,
    velocity: Float,
    flingAnimationSpec: DecayAnimationSpec<Float>?,
    snapAnimationSpec: AnimationSpec<Float>?
): Velocity {
    // Check if the app bar is completely collapsed/expanded. If so, no need to settle the app bar,
    // and just return Zero Velocity.
    // Note that we don't check for 0f due to float precision with the collapsedFraction
    // calculation.
    if (state.collapsedFraction < 0.01f || state.collapsedFraction == 1f) {
        return Velocity.Zero
    }
    var remainingVelocity = velocity
    // In case there is an initial velocity that was left after a previous user fling, animate to
    // continue the motion to expand or collapse the app bar.
    if (flingAnimationSpec != null && abs(velocity) > 1f) {
        var lastValue = 0f
        AnimationState(
            initialValue = 0f,
            initialVelocity = velocity,
        )
            .animateDecay(flingAnimationSpec) {
                val delta = value - lastValue
                val initialHeightOffset = state.heightOffset
                state.heightOffset = initialHeightOffset + delta
                val consumed = abs(initialHeightOffset - state.heightOffset)
                lastValue = value
                remainingVelocity = this.velocity
                // avoid rounding errors and stop if anything is unconsumed
                if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
            }
    }
    // Snap if animation specs were provided.
    if (snapAnimationSpec != null) {
        if (state.heightOffset < 0 &&
            state.heightOffset > state.heightOffsetLimit
        ) {
            AnimationState(initialValue = state.heightOffset).animateTo(
                if (state.collapsedFraction < 0.5f) {
                    0f
                } else {
                    state.heightOffsetLimit
                },
                animationSpec = snapAnimationSpec
            ) { state.heightOffset = value }
        }
    }

    return Velocity(0f, remainingVelocity)
}