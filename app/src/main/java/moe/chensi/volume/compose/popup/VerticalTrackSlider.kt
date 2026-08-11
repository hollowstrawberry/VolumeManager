package moe.chensi.volume.compose.popup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A vertical, text-free equivalent of [moe.chensi.volume.compose.TrackSlider], used only by the
 * redesigned system-overlay popup. The main activity continues to use the original horizontal
 * TrackSlider, which is left untouched.
 *
 * The filled portion grows upward from the bottom of the bar, matching common vertical volume
 * bar conventions.
 */
@Composable
fun VerticalTrackSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trackColor: Color = PopupColors.Track,
    onTrackColor: Color = PopupColors.OnTrack,
    fillColor: Color = PopupColors.Accent,
    onFillColor: Color = PopupColors.OnAccent,
    cornerRadius: Dp = 24.dp,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val coercedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val latestValue by rememberUpdatedState(coercedValue)
    val density = LocalDensity.current
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    val fillHeightPercentage =
        (coercedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(GenericShape { size, _ ->
                addRoundRect(
                    RoundRect(
                        0f, 0f, size.width, size.height, cornerRadius = CornerRadius(cornerRadiusPx)
                    )
                )
            })
            .background(trackColor)
            .pointerInput(enabled) {
                if (enabled) {
                    var startValue = 0f
                    var startY = 0f

                    detectVerticalDragGestures(onDragStart = { offset ->
                        startValue = latestValue
                        startY = offset.y
                    }) { change, _ ->
                        val dragAmount = change.position.y - startY
                        // Dragging up (negative dy) should raise the value.
                        val changedPercentage = -dragAmount / size.height.toFloat()
                        val totalRange = valueRange.endInclusive - valueRange.start
                        val newValue = startValue + changedPercentage * totalRange
                        val coercedNewValue =
                            newValue.coerceIn(valueRange.start, valueRange.endInclusive)
                        if (coercedNewValue != latestValue) {
                            onValueChange(coercedNewValue)
                        }
                    }
                }
            },
    ) {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            CompositionLocalProvider(LocalContentColor provides onTrackColor) {
                content()
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(fillHeightPercentage.coerceIn(0f, 1f))
                .clip(GenericShape { size, _ ->
                    addRoundRect(
                        RoundRect(
                            0f,
                            0f,
                            size.width,
                            size.height,
                            cornerRadius = CornerRadius(with(density) { 2.dp.toPx() })
                        )
                    )
                })
                .background(fillColor)
        ) {
            Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                CompositionLocalProvider(LocalContentColor provides onFillColor) {
                    content()
                }
            }
        }
    }
}
