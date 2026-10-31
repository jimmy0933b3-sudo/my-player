@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.jimmy0933.myplayer

import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
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
    onChange: (Int) -> Unit,
) {
    val context = LocalContext.current
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
                                if (zoom > 1.01f) {
                                    translation = boundPan(
                                        translation + delta, zoom, size.width, size.height,
                                    )
                                    pointer.consume()
                                } else if (!transformed) {
                                    horizontal += delta.x
                                    vertical += delta.y
                                    if (media.isVideo && abs(horizontal) > 18.dp.toPx() &&
                                        abs(horizontal) > abs(vertical) * 1.15f) {
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
                                    }
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!transformed && zoom <= 1.01f && seekOrigin == null &&
                        abs(vertical) > 85.dp.toPx() && abs(vertical) > abs(horizontal) * 1.2f) {
                        onChange(if (vertical < 0) 1 else -1)
                    } else if (!transformed && seekOrigin == null &&
                        abs(vertical) < 10.dp.toPx() && abs(horizontal) < 10.dp.toPx()) {
                        showControls = !showControls
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
                    .padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 40.dp),
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
                    Text(
                        "${position + 1} / $total   ·   ${MediaLibrary.readableSize(media.bytes)}",
                        color = Color(0xFFCDD7E8), style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (zoom > 1.01f) {
                    IconButton(onClick = { zoom = 1f; translation = Offset.Zero }) {
                        Icon(Icons.Default.ZoomOutMap, "重設縮放", tint = Color.White)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .91f))))
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (media.isVideo) {
                    Slider(
                        value = sliderPosition,
                        onValueChange = { fraction ->
                            val target = (fraction * totalDuration).toLong()
                            if (totalDuration > 0) {
                                player?.seekTo(target)
                                currentMs = target
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF92A9FF),
                            activeTrackColor = Color(0xFF92A9FF),
                            inactiveTrackColor = Color.White.copy(alpha = .25f),
                        ),
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
                        }) { Icon(Icons.Default.FastRewind, "倒退10秒", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        Spacer(Modifier.width(28.dp))
                        IconButton(onClick = {
                            if (playing) player?.pause() else player?.play()
                        }) {
                            Icon(
                                if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                if (playing) "暫停" else "播放",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                        Spacer(Modifier.width(28.dp))
                        IconButton(onClick = {
                            val next = (player?.currentPosition ?: 0L) + 10_000L
                            player?.seekTo(next.coerceAtMost(totalDuration.coerceAtLeast(0L)))
                        }) { Icon(Icons.Default.FastForward, "快轉10秒", tint = Color.White, modifier = Modifier.size(30.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("上下滑切換  ·  左右滑快轉／倒退  ·  雙指縮放",
                        color = Color(0xFFB8C5DD), style = MaterialTheme.typography.labelSmall)
                } else {
                    Text("上下滑切換照片／影片  ·  雙指縮放",
                        color = Color(0xFFB8C5DD), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

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
