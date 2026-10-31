@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jimmy0933.myplayer

import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun ViewerScreen(
    media: LocalMedia,
    position: Int,
    total: Int,
    loader: ImageLoader,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val player = remember(media.uri) {
        if (media.isVideo) ExoPlayer.Builder(context).build() else null
    }
    DisposableEffect(player, media.uri) {
        player?.apply {
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            playWhenReady = true
        }
        onDispose { player?.release() }
    }

    val lifecycle = (context as ComponentActivity).lifecycle
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player?.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    var zoom by remember(media.uri) { mutableFloatStateOf(1f) }
    var translation by remember(media.uri) { mutableStateOf(Offset.Zero) }
    var showControls by remember(media.uri) { mutableStateOf(true) }
    var playing by remember(media.uri) { mutableStateOf(media.isVideo) }
    var currentMs by remember(media.uri) { mutableLongStateOf(0L) }
    var seekHint by remember(media.uri) { mutableStateOf<String?>(null) }

    LaunchedEffect(player) {
        while (isActive && player != null) {
            currentMs = player.currentPosition.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(250)
        }
    }
    val totalDuration = player?.duration?.takeIf { it > 0L } ?: media.durationMs
    val sliderPosition = if (totalDuration > 0L) {
        (currentMs.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else 0f

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (media.isVideo) {
            AndroidVideo(
                player = player,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = translation.x
                    translationY = translation.y
                },
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context).data(media.uri).build(),
                imageLoader = loader,
                contentDescription = media.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = translation.x
                    translationY = translation.y
                },
            )
        }

        // A transparent gesture layer is above the content but below the controls.
        // One-finger vertical = previous / next, one-finger horizontal video = direct seek.
        // Two fingers = zoom + pan. No gray scrubbing/transition overlay is applied.
        Box(
            Modifier.fillMaxSize().pointerInput(media.uri, player) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var vertical = 0f
                    var horizontal = 0f
                    var transformed = false
                    var seekOrigin: Long? = null
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                transformed = true
                                val a = pressed[0]
                                val b = pressed[1]
                                val priorDistance = (a.previousPosition - b.previousPosition).getDistance()
                                val distance = (a.position - b.position).getDistance()
                                if (priorDistance > 1f && distance > 1f) {
                                    zoom = (zoom * distance / priorDistance).coerceIn(1f, 5f)
                                }
                                val before = Offset(
                                    (a.previousPosition.x + b.previousPosition.x) / 2,
                                    (a.previousPosition.y + b.previousPosition.y) / 2,
                                )
                                val after = Offset(
                                    (a.position.x + b.position.x) / 2,
                                    (a.position.y + b.position.y) / 2,
                                )
                                translation = boundPan(translation + after - before, zoom, size.width, size.height)
                                if (zoom <= 1.01f) translation = Offset.Zero
                                event.changes.forEach { it.consume() }
                            }
                            pressed.size == 1 -> {
                                val pointer = pressed[0]
                                val delta = pointer.position - pointer.previousPosition
                                // Video seeking has priority over zoomed-video panning.
                                // One finger horizontally seeks at *any* zoom level;
                                // use two fingers to pan/zoom a magnified video.
                                if (!transformed) {
                                    horizontal += delta.x
                                    vertical += delta.y
                                }
                                if (shouldSeekVideoSwipe(
                                        media.isVideo,
                                        transformed,
                                        horizontal,
                                        vertical,
                                        18.dp.toPx(),
                                    ) || (seekOrigin != null && !transformed)) {
                                    val base = seekOrigin ?: (player?.currentPosition ?: 0L)
                                    seekOrigin = base
                                    val duration = (player?.duration?.takeIf { it > 0L }
                                        ?: media.durationMs).coerceAtLeast(0L)
                                    val target = (base + (horizontal * 65f).toLong())
                                        .coerceIn(0L, duration)
                                    player?.seekTo(target)
                                    currentMs = target
                                    seekHint = "${durationLabel(target)} / ${durationLabel(duration)}"
                                    pointer.consume()
                                } else if (zoom > 1.01f) {
                                    // Image: single-finger pan. Zoomed video:
                                    // single-finger vertical pan; horizontal seek is reserved.
                                    val panDelta = if (media.isVideo && !transformed)
                                        Offset(0f, delta.y) else delta
                                    translation = boundPan(
                                        translation + panDelta, zoom, size.width, size.height,
                                    )
                                    pointer.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!transformed && zoom <= 1.01f && seekOrigin == null &&
                        abs(vertical) > 85.dp.toPx() && abs(vertical) > abs(horizontal) * 1.2f) {
                        onChange(if (vertical < 0) 1 else -1)
                    } else if (!transformed && seekOrigin == null &&
                        abs(vertical) < 10.dp.toPx() && abs(horizontal) < 10.dp.toPx()) {
                        if (media.isVideo) {
                            // Tap directly on the video to pause/resume, including
                            // while zoomed. Do not confuse it with swipe seeking.
                            if (player?.isPlaying == true) {
                                player.pause()
                                playing = false
                            } else {
                                player?.play()
                                playing = true
                            }
                            showControls = true
                        } else {
                            showControls = !showControls
                        }
                    }
                    seekHint = null
                }
            },
        )

        if (seekHint != null) {
            Text(
                seekHint.orEmpty(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        if (showControls) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .8f), Color.Transparent)))
                    .padding(start = 8.dp, end = 16.dp,
                        top = if (isLandscape) 2.dp else 12.dp,
                        bottom = if (isLandscape) 8.dp else 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "返回縮圖", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        media.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold, color = Color.White,
                    )
                    if (!isLandscape) {
                        Text(
                            "${position + 1} / $total   ·   ${MediaLibrary.readableSize(media.bytes)}",
                            color = Color(0xFFCDD7E8), style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                if (zoom > 1.01f) {
                    IconButton(onClick = { zoom = 1f; translation = Offset.Zero }) {
                        Icon(Icons.Default.ZoomOutMap, "重設縮放", tint = Color.White)
                    }
                }
                IconButton(onClick = {
                    player?.pause()
                    onDelete()
                }) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        "刪除此圖片或影片",
                        tint = Color(0xFFFF9DA6),
                    )
                }
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .91f))))
                    .padding(
                        horizontal = if (isLandscape) 12.dp else 20.dp,
                        vertical = if (isLandscape) 4.dp else 22.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (media.isVideo) {
                    val seekToFraction: (Float) -> Unit = { fraction ->
                        if (totalDuration > 0L) {
                            val target = (fraction * totalDuration).toLong()
                            player?.seekTo(target)
                            currentMs = target
                        }
                    }
                    if (isLandscape) {
                        // A single compact row keeps the landscape video unobstructed.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    player?.seekTo(((player?.currentPosition ?: 0L) - 10_000L).coerceAtLeast(0L))
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.FastRewind, "倒退10秒",
                                    tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            IconButton(
                                onClick = { if (playing) player?.pause() else player?.play() },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                    if (playing) "暫停" else "播放",
                                    tint = Color.White, modifier = Modifier.size(36.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    player?.seekTo(((player?.currentPosition ?: 0L) + 10_000L)
                                        .coerceAtMost(totalDuration.coerceAtLeast(0L)))
                                },
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(Icons.Default.FastForward, "快轉10秒",
                                    tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                            Text(
                                durationLabel(currentMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            VideoProgressSlider(
                                value = sliderPosition,
                                onValueChange = seekToFraction,
                                modifier = Modifier.weight(1f).height(30.dp),
                            )
                            Text(
                                durationLabel(totalDuration),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    } else {
                        VideoProgressSlider(
                            value = sliderPosition,
                            onValueChange = seekToFraction,
                            modifier = Modifier.fillMaxWidth(0.96f).height(30.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(durationLabel(currentMs), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(durationLabel(totalDuration), style = MaterialTheme.typography.labelMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = {
                                val seekTo = (player?.currentPosition ?: 0L) - 10_000L
                                player?.seekTo(seekTo.coerceAtLeast(0L))
                            }) { Icon(Icons.Default.FastRewind, "倒退10秒",
                                tint = Color.White, modifier = Modifier.size(30.dp)) }
                            Spacer(Modifier.width(28.dp))
                            IconButton(onClick = {
                                if (playing) player?.pause() else player?.play()
                            }) {
                                Icon(
                                    if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                    if (playing) "暫停" else "播放",
                                    tint = Color.White, modifier = Modifier.size(48.dp),
                                )
                            }
                            Spacer(Modifier.width(28.dp))
                            IconButton(onClick = {
                                val next = (player?.currentPosition ?: 0L) + 10_000L
                                player?.seekTo(next.coerceAtMost(totalDuration.coerceAtLeast(0L)))
                            }) { Icon(Icons.Default.FastForward, "快轉10秒",
                                tint = Color.White, modifier = Modifier.size(30.dp)) }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("上下滑切換  ·  左右滑快轉／倒退  ·  雙指縮放",
                            color = Color(0xFFB8C5DD), style = MaterialTheme.typography.labelSmall)
                    }
                } else if (!isLandscape) {
                    Text("上下滑切換照片／影片  ·  雙指縮放",
                        color = Color(0xFFB8C5DD), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun VideoProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Same thin, directly seekable track in both screen orientations.
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF92A9FF),
            activeTrackColor = Color(0xFF92A9FF),
            inactiveTrackColor = Color.White.copy(alpha = .25f),
        ),
        thumb = {
            Box(
                Modifier.size(10.dp)
                    .background(Color(0xFF92A9FF), CircleShape),
            )
        },
        track = { state ->
            Box(
                Modifier.fillMaxWidth().height(3.dp)
                    .background(Color.White.copy(alpha = .25f), CircleShape),
            ) {
                Box(
                    Modifier.fillMaxWidth(state.value.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color(0xFF92A9FF), CircleShape),
                )
            }
        },
    )
}

// Keep gesture priority deterministic regardless of zoom. Pure for regression testing.
internal fun shouldSeekVideoSwipe(
    isVideo: Boolean,
    wasPinchGesture: Boolean,
    horizontalPx: Float,
    verticalPx: Float,
    thresholdPx: Float,
): Boolean = isVideo && !wasPinchGesture &&
    abs(horizontalPx) > thresholdPx &&
    abs(horizontalPx) > abs(verticalPx) * 1.15f

private fun boundPan(raw: Offset, zoom: Float, width: Int, height: Int): Offset {
    val maxX = (zoom - 1f).coerceAtLeast(0f) * width / 2f
    val maxY = (zoom - 1f).coerceAtLeast(0f) * height / 2f
    return Offset(raw.x.coerceIn(-maxX, maxX), raw.y.coerceIn(-maxY, maxY))
}

@Composable
private fun AndroidVideo(player: ExoPlayer?, modifier: Modifier = Modifier) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.view_player, null, false) as PlayerView
        },
        modifier = modifier,
        update = { view -> view.player = player },
        onRelease = { view -> view.player = null },
    )
}
