package com.lagradost.quicknovel.compose

import android.graphics.Matrix
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.quicknovel.R
import com.lagradost.quicknovel.compose.CloudStreamTheme.colors
import kotlin.math.max

@Composable
fun Modifier.animatedOutline( width: Dp = 2.dp,  defaultPalette: List<Color>): Modifier {
    val imageShape = RoundedCornerShape(dimensionResource(R.dimen.roundedImageRadius))

    val rotation = LocalSharedInfiniteTransition.current.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(3000, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "scale"
    )
    return drawWithContent {
        drawContent()
        val shader = SweepGradientShader(
            center = center,
            colors = defaultPalette
        )
        shader.setLocalMatrix(Matrix().apply {
            postRotate(rotation.value, center.x, center.y)
        })
        val rotatingBrush = object : ShaderBrush() {
            override fun createShader(size: Size): Shader = shader
        }
        drawOutline(
            outline = imageShape.createOutline(size, layoutDirection, this),
            brush = rotatingBrush,
            style = Stroke(width = width.toPx())
        )
    }
}


/*
@Stable
class TransformableBrush(
    private val brush: ShaderBrush
) : ShaderBrush() {

    override val intrinsicSize: Size
        get() = brush.intrinsicSize

    private var internalShader: Shader? = null
    private val localMatrix: Matrix = Matrix()

    override fun createShader(size: Size): Shader {
        return brush.createShader(size).also {
            internalShader = it
            it.setLocalMatrix(localMatrix)
        }
    }

    // Allows transforming the brush by modifying the localMatrix
    fun transform(transformer: Matrix.() -> Unit) {
        transformer.invoke(localMatrix)
        internalShader?.setLocalMatrix(localMatrix)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TransformableBrush) return false
        if (brush != other.brush) return false
        if (localMatrix != other.localMatrix) return false
        return true
    }

    override fun hashCode(): Int {
        return 31 * brush.hashCode() + localMatrix.hashCode()
    }

    override fun toString(): String {
        return "TransformableBrush(brush=$brush)"
    }
}

@Composable
inline fun rememberTransformableBrush(
    crossinline getBrush: @DisallowComposableCalls () -> Brush,
): TransformableBrush {
    return remember {
        val brush = getBrush()
        check(brush is ShaderBrush)
        TransformableBrush(brush = brush)
    }
}

@Composable
fun Modifier.gradientEffect(
    gradientColors: List<Color>,
    animationSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 2000,
            easing = LinearEasing,
        ),
        repeatMode = RepeatMode.Reverse,
    ),
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "Gradient")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = animationSpec,
        label = "GradientProgress",
    )

    val gradientBrush = rememberTransformableBrush {
        val colorStops = buildList {
            gradientColors.forEachIndexed { index, color ->
                add((index.toFloat() / gradientColors.size) to color)
            }
        }.toTypedArray()
        Brush.horizontalGradient(
            *colorStops,
            startX = 0f,
            endX = gradientColors.size.toFloat(),
        )
    }

    return drawWithContent {
        drawContent()
        gradientBrush.transform {
            val x = progress * max(0, gradientColors.size - 2) * size.width
            setScale(size.width, 1f)
            postTranslate(-x, 0f)
        }
        drawBehind {
            drawRect(gradientBrush)
        }
       // drawOutline(brush = gradientBrush, outline = Outline.Rounded(RoundRect(rect = this.size.toRect())))
    }
}

@Composable
fun Modifier.shimmerEffect(
    shimmerSize: Dp = 152.dp,
    shimmerColor: Color = Color.White.copy(alpha = 0.25f),
    animationSpec: InfiniteRepeatableSpec<Float> = infiniteRepeatable(
        animation = tween(
            durationMillis = 1000,
            delayMillis = 2000,
            easing = EaseInOutQuad,
        ),
    ),
): Modifier {
    val infiniteTransition = rememberInfiniteTransition(label = "Shimmer")
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = animationSpec,
        label = "ShimmerProgress",
    )
    val shimmerBrush = rememberTransformableBrush {
        Brush.horizontalGradient(
            colors = listOf(
                Color.Transparent,
                shimmerColor,
                Color.Transparent,
            ),
        )
    }

    return drawWithContent {
        val shimmerSizePx = shimmerSize.toPx()
        drawContent()
        val adjustedWidth = size.width + shimmerSizePx * 2
        val x = adjustedWidth * progress - shimmerSizePx
        shimmerBrush.transform {
            setTranslate(x, 0f)
        }
        drawRect(
            brush = shimmerBrush,
            topLeft = Offset(x = x, y = 0f),
            size = Size(
                width = shimmerSizePx,
                height = size.height,
            ),
        )
    }
}
*/