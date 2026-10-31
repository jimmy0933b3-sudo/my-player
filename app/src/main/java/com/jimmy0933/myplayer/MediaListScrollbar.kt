package com.jimmy0933.myplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import kotlin.math.roundToInt

/**
 * Narrow right-side draggable fast-scroll rail for media list mode.
 *
 * Uses the LazyColumn's real item count (including folder and date rows),
 * so dragging can jump through large libraries without composing all items.
 * The hit area is 28dp wide and separate from the list's long-press targets.
 */
@Composable
internal fun MediaListScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val scrollTargets = remember { Channel<Int>(Channel.CONFLATED) }
    LaunchedEffect(listState, scrollTargets) {
        scrollTargets.consumeAsFlow().collectLatest { index ->
            listState.scrollToItem(index)
        }
    }

    val layout = listState.layoutInfo
    val totalItems = layout.totalItemsCount
    val visibleItems = layout.visibleItemsInfo.size
    if (totalItems <= visibleItems || visibleItems == 0) return

    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier.width(28.dp).fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        val trackHeightPx = constraints.maxHeight.toFloat()
        val minimumThumbPx = with(density) { 48.dp.toPx() }
        val thumbHeightPx = ListScrollbarMath.thumbHeightPx(
            trackHeightPx, totalItems, visibleItems, minimumThumbPx,
        )
        val averageHeightPx = layout.visibleItemsInfo.map { it.size }.average().toFloat() +
            layout.mainAxisItemSpacing
        val fraction = ListScrollbarMath.positionFraction(
            listState.firstVisibleItemIndex,
            listState.firstVisibleItemScrollOffset,
            averageHeightPx,
            totalItems,
            visibleItems,
            isAtEnd = !listState.canScrollForward,
        )
        val thumbTravelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        val thumbTopPx = fraction * thumbTravelPx

        Box(
            Modifier.fillMaxSize()
                .semantics { contentDescription = "右側清單快速捲動條" }
                .pointerInput(listState, trackHeightPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        val initialLayout = listState.layoutInfo
                        val initialCount = initialLayout.totalItemsCount
                        val initialVisible = initialLayout.visibleItemsInfo.size
                        val initialThumb = ListScrollbarMath.thumbHeightPx(
                            trackHeightPx, initialCount, initialVisible, minimumThumbPx,
                        )
                        val startFraction = ListScrollbarMath.positionFraction(
                            listState.firstVisibleItemIndex,
                            listState.firstVisibleItemScrollOffset,
                            initialLayout.visibleItemsInfo.map { it.size }.average().toFloat() +
                                initialLayout.mainAxisItemSpacing,
                            initialCount, initialVisible, !listState.canScrollForward,
                        )
                        val originalThumbTop = startFraction * (trackHeightPx - initialThumb)
                        val grabOffset = if (down.position.y in
                            originalThumbTop..(originalThumbTop + initialThumb)) {
                            down.position.y - originalThumbTop
                        } else {
                            initialThumb / 2f
                        }

                        fun jumpToFinger(y: Float) {
                            val current = listState.layoutInfo
                            val count = current.totalItemsCount
                            val visible = current.visibleItemsInfo.size
                            val currentThumb = ListScrollbarMath.thumbHeightPx(
                                trackHeightPx, count, visible, minimumThumbPx,
                            )
                            val target = ListScrollbarMath.indexForTrackPosition(
                                y, grabOffset, trackHeightPx, currentThumb, count, visible,
                            )
                            scrollTargets.trySend(target)
                        }
                        jumpToFinger(down.position.y)

                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) {
                                pressed = event.changes.any { it.pressed }
                            } else {
                                pressed = change.pressed
                                if (pressed) jumpToFinger(change.position.y)
                                change.consume()
                            }
                        }
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier.fillMaxHeight().width(3.dp)
                    .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
                    .align(Alignment.Center),
            )
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, thumbTopPx.roundToInt()) }
                    .width(9.dp)
                    .height(with(density) { thumbHeightPx.toDp() })
                    .background(Color(0xFF91A9FF), RoundedCornerShape(10.dp)),
            )
        }
    }
}
