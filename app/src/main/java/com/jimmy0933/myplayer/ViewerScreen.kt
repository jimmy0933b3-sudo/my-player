@file:OptIn(
    androidx.media3.common.util.UnstableApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

package com.jimmy0933.myplayer

import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
    progressBarScale: Float,
    progressBarWidth: Float,
    controlsAutoHideSeconds: Int,
    showPlayButton: Boolean,
    showPreviousNextButtons: Boolean,
    volumeSettings: PlaybackVolumeSettings,
    volumeBarTransparency: Float,
    volumeBarThickness: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    hasPreviousVideo: Boolean,
    hasNextVideo: Boolean,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    landscapeDisplayMode: LandscapeVideoDisplay,
    decoderPreferences: DecoderPreferences,
) {
    val context = LocalContext.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Recover from an actual decoding error once, using installed software
    // decoders first. Preserve the playhead; never repeatedly restart a file.
    var softwareRetryAttempted by remember(media.uri) { mutableStateOf(false) }
    var restartAtMs by remember(media.uri) { mutableLongStateOf(0L) }
    var videoDecoderName by remember(media.uri) { mutableStateOf<String?>(null) }
    var currentVideoSize by remember(media.uri) { mutableStateOf<VideoSize?>(null) }
    var audioDecoderName by remember(media.uri) { mutableStateOf<String?>(null) }
    var decoderStatus by remember(media.uri) { mutableStateOf<String?>(null) }
    // A successful fallback shouldn't leave a permanent banner over the video.
    // Keep terminal error messages visible so unsupported files are diagnosable.
    LaunchedEffect(decoderStatus) {
        if (decoderStatus?.startsWith("已重新選用解碼器") == true) {
            delay(2_500L)
            decoderStatus = null
        }
    }
    val effectiveDecoder = if (softwareRetryAttempted) {
        decoderPreferences.copy(video = VideoDecoderPreference.SOFTWARE_FIRST)
    } else decoderPreferences
    val player = remember(media.uri, effectiveDecoder) {
        if (media.isVideo) ExoPlayer.Builder(
            context, DecoderSettings.renderers(context, effectiveDecoder),
        )
            // Exact seeking decodes and discards frames after the previous
            // keyframe. Nearest keyframe trades small time error for fast resume.
            .setSeekParameters(SeekParameters.CLOSEST_SYNC)
            .build() else null
    }
    // Apply immediately to each newly created ExoPlayer (also on decoder retry).
    // Device/system volume stays untouched.
    LaunchedEffect(player, volumeSettings.effectiveVolume) {
        player?.volume = volumeSettings.effectiveVolume
    }
    DisposableEffect(player, media.uri) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                currentVideoSize = videoSize
            }

            override fun onPlayerError(error: PlaybackException) {
                if (decoderPreferences.recoverOnPlaybackError &&
                    !softwareRetryAttempted &&
                    error.errorCode in 4001..4006) {
                    restartAtMs = (player?.currentPosition ?: 0L).coerceAtLeast(0L)
                    decoderStatus = "硬體／系統解碼失敗，正在改用軟體解碼優先重試"
                    softwareRetryAttempted = true
                } else {
                    decoderStatus = "無法播放：解碼器或格式不支援（${error.errorCodeName}）"
                }
            }
        }
        val analytics = object : AnalyticsListener {
            override fun onVideoDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                videoDecoderName = decoderName
                if (softwareRetryAttempted) decoderStatus = "已重新選用解碼器：$decoderName"
            }
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                audioDecoderName = decoderName
            }
        }
        player?.apply {
            addListener(listener)
            addAnalyticsListener(analytics)
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            if (restartAtMs > 0L) seekTo(restartAtMs)
            playWhenReady = true
        }
        onDispose {
            player?.removeListener(listener)
            player?.removeAnalyticsListener(analytics)
            player?.release()
        }
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
    var pendingSliderFraction by remember(media.uri) { mutableStateOf<Float?>(null) }
    var seekHint by remember(media.uri) { mutableStateOf<String?>(null) }
    val tapScope = rememberCoroutineScope()
    var previousZoomTapMillis by remember(media.uri) { mutableLongStateOf(-1L) }
    var previousZoomTapPosition by remember(media.uri) { mutableStateOf(Offset.Zero) }
    var pendingZoomSingleTap by remember(media.uri) { mutableStateOf<Job?>(null) }
    DisposableEffect(media.uri) {
        onDispose { pendingZoomSingleTap?.cancel() }
    }
    // Any touch or control action renews the idle timer and reveals the overlays.
    var lastInteraction by remember(media.uri) { mutableIntStateOf(0) }
    fun notifyInteraction() {
        lastInteraction++
        showControls = true
    }
    // One user-configurable timer controls both the title and bottom bar.
    // 0 = always visible; photos and paused videos also keep controls visible.
    // Each interaction restarts the countdown, including while scrubbing.
    LaunchedEffect(media.uri, playing, lastInteraction, controlsAutoHideSeconds) {
        val timeoutMs = PlaybackControlsTimeout.delayMillis(controlsAutoHideSeconds)
        if (media.isVideo && playing && timeoutMs != null) {
            delay(timeoutMs)
            showControls = false
        } else if (media.isVideo) {
            showControls = true
        }
    }

    LaunchedEffect(player) {
        while (isActive && player != null) {
            currentMs = player.currentPosition.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(250)
        }
    }
    val totalDuration = player?.duration?.takeIf { it > 0L } ?: media.durationMs
    val sliderPosition = pendingSliderFraction ?: if (totalDuration > 0L) {
        (currentMs.toFloat() / totalDuration).coerceIn(0f, 1f)
    } else 0f

    var playbackViewport by remember { mutableStateOf(IntSize.Zero) }
    val landscapeAutoScale =
        if (isLandscape && media.isVideo && landscapeDisplayMode == LandscapeVideoDisplay.SMART) {
            landscapeSmartScale(
                playbackViewport.width,
                playbackViewport.height,
                currentVideoSize?.width ?: 0,
                currentVideoSize?.height ?: 0,
                currentVideoSize?.pixelWidthHeightRatio ?: 1f,
                currentVideoSize?.unappliedRotationDegrees ?: 0,
            )
        } else 1f
    val videoResizeMode =
        if (isLandscape && landscapeDisplayMode == LandscapeVideoDisplay.FILL) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            AspectRatioFrameLayout.RESIZE_MODE_FIT
        }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .onSizeChanged { playbackViewport = it },
        contentAlignment = Alignment.Center,
    ) {
        if (media.isVideo) {
            AndroidVideo(
                player = player,
                resizeMode = videoResizeMode,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    // SMART reduces black bars without the substantial cropping
                    // of full ZOOM. Pinch-to-zoom remains independently available.
                    scaleX = zoom * landscapeAutoScale
                    scaleY = zoom * landscapeAutoScale
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
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Do not reveal overlays on pointer-down: a tap outside the
                    // center must be able to both hide and show them reliably.
                    var vertical = 0f
                    var horizontal = 0f
                    var transformed = false
                    var seekOrigin: Long? = null
                    var pendingSwipeTarget: Long? = null
                    do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        when {
                            pressed.size >= 2 -> {
                                transformed = true
                                // A two-finger pinch cancels any pending one-finger seek.
                                pendingSwipeTarget = null
                                seekOrigin = null
                                seekHint = null
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
                                    val target = swipeSeekTarget(base, horizontal, duration)
                                    pendingSwipeTarget = target
                                    // Update the live preview without flushing/restarting
                                    // the decoder on every pointer movement.
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

                    if (!transformed) {
                        pendingSwipeTarget?.let { finalTarget ->
                            player?.seekTo(finalTarget)
                            currentMs = finalTarget
                        }
                    }

                    val isTap = !transformed && seekOrigin == null &&
                        abs(vertical) < 10.dp.toPx() && abs(horizontal) < 10.dp.toPx()
                    // Swipes and pinches still refresh the existing auto-hide timer.
                    // Only a genuine tap can toggle the overlays.
                    if (media.isVideo && !isTap) notifyInteraction()
                    if (!transformed && zoom <= 1.01f && seekOrigin == null &&
                        abs(vertical) > 85.dp.toPx() && abs(vertical) > abs(horizontal) * 1.2f) {
                        pendingZoomSingleTap?.cancel()
                        pendingZoomSingleTap = null
                        previousZoomTapMillis = -1L
                        onChange(if (vertical < 0) 1 else -1)
                    } else if (isTap) {
                        // Double-tap while zoomed restores BOTH scale and panning.
                        // Hold a zoomed single tap briefly so the first tap does not
                        // accidentally toggle video playback during a double tap.
                        val videoAction = videoTapAction(
                            down.position.x, down.position.y,
                            size.width.toFloat(), size.height.toFloat(),
                        )
                        fun performSingleTap() {
                            if (media.isVideo) {
                                when (videoAction) {
                                    VideoTapAction.PLAY_OR_PAUSE -> {
                                        // Keep the existing center-tap playback gesture.
                                        notifyInteraction()
                                        if (player?.isPlaying == true) {
                                            player.pause()
                                            playing = false
                                        } else {
                                            player?.play()
                                            playing = true
                                        }
                                    }
                                    VideoTapAction.TOGGLE_CONTROLS -> {
                                        // Manual toggle applies to the title, details,
                                        // progress bar and right-side volume together.
                                        if (showControls) {
                                            showControls = false
                                        } else {
                                            notifyInteraction()
                                        }
                                    }
                                }
                            } else {
                                showControls = !showControls
                            }
                        }
                        if (shouldResetZoomOnDoubleTap(
                                zoom = zoom,
                                previousTapMillis = previousZoomTapMillis,
                                currentTapMillis = down.uptimeMillis,
                                previousX = previousZoomTapPosition.x,
                                previousY = previousZoomTapPosition.y,
                                currentX = down.position.x,
                                currentY = down.position.y,
                                maxDistancePx = 48.dp.toPx(),
                            )) {
                            pendingZoomSingleTap?.cancel()
                            pendingZoomSingleTap = null
                            previousZoomTapMillis = -1L
                            zoom = 1f
                            translation = Offset.Zero
                            showControls = true
                        } else if (zoom > 1.01f) {
                            pendingZoomSingleTap?.cancel()
                            previousZoomTapMillis = down.uptimeMillis
                            previousZoomTapPosition = down.position
                            pendingZoomSingleTap = tapScope.launch {
                                delay(310L)
                                performSingleTap()
                                previousZoomTapMillis = -1L
                                pendingZoomSingleTap = null
                            }
                        } else {
                            pendingZoomSingleTap?.cancel()
                            pendingZoomSingleTap = null
                            previousZoomTapMillis = -1L
                            performSingleTap()
                        }
                    } else {
                        // A pinch, pan or seek is not part of the double-tap gesture.
                        pendingZoomSingleTap?.cancel()
                        pendingZoomSingleTap = null
                        previousZoomTapMillis = -1L
                    }
                    seekHint = null
                }
            },
        )

        if (decoderStatus != null) {
            Text(
                decoderStatus.orEmpty(),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 56.dp)
                    .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
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

        if (showControls && media.isVideo) {
            PlaybackVolumeControl(
                settings = volumeSettings,
                transparency = volumeBarTransparency,
                thicknessDp = volumeBarThickness,
                onVolumeChange = { notifyInteraction(); onVolumeChange(it) },
                onToggleMute = { notifyInteraction(); onToggleMute() },
                modifier = Modifier.align(Alignment.CenterEnd)
                    .displayCutoutPadding()
                    .navigationBarsPadding()
                    .padding(end = if (isLandscape) 10.dp else 8.dp),
                sliderHeight = if (isLandscape) 124.dp else 184.dp,
            )
        }
        if (showControls) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .8f), Color.Transparent)))
                    // Content fills the screen. Viewer actions still avoid
                    // camera/display cutouts, even when status bars are hidden.
                    .displayCutoutPadding()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 16.dp,
                        top = if (isLandscape) 2.dp else 12.dp,
                        bottom = if (isLandscape) 8.dp else 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { notifyInteraction(); onBack() }) {
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
                    IconButton(onClick = {
                        notifyInteraction()
                        zoom = 1f
                        translation = Offset.Zero
                    }) {
                        Icon(Icons.Default.ZoomOutMap, "重設縮放", tint = Color.White)
                    }
                }
                IconButton(onClick = {
                    notifyInteraction()
                    player?.pause()
                    playing = false
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
                    // Keep playback buttons and seek bar above Android gesture
                    // navigation without cropping the underlying video.
                    .navigationBarsPadding()
                    .padding(
                        horizontal = if (isLandscape) 12.dp else 20.dp,
                        vertical = if (isLandscape) 4.dp else 22.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (media.isVideo) {
                    val previewSeekFraction: (Float) -> Unit = { fraction ->
                        notifyInteraction()
                        // Do not seek on each Slider tick; only update the thumb.
                        pendingSliderFraction = fraction.coerceIn(0f, 1f)
                    }
                    val commitSeekFraction: () -> Unit = {
                        pendingSliderFraction?.let { fraction ->
                            val target = progressSeekTarget(fraction, totalDuration)
                            if (totalDuration > 0L) {
                                player?.seekTo(target)
                                currentMs = target
                            }
                        }
                        pendingSliderFraction = null
                    }
                    if (isLandscape) {
                        // A single compact row keeps the landscape video unobstructed.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (showPreviousNextButtons) {
                                IconButton(
                                    enabled = hasPreviousVideo,
                                    onClick = { notifyInteraction(); onPreviousVideo() },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Default.SkipPrevious, "上一部影片",
                                        tint = if (hasPreviousVideo) Color.White else Color.Gray,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            if (showPlayButton) {
                                IconButton(
                                    onClick = {
                                        notifyInteraction()
                                        if (playing) {
                                            player?.pause()
                                            playing = false
                                        } else {
                                            player?.play()
                                            playing = true
                                        }
                                    },
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Icon(
                                        if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                        if (playing) "暫停" else "播放",
                                        tint = Color.White, modifier = Modifier.size(36.dp),
                                    )
                                }
                            }
                            if (showPreviousNextButtons) {
                                IconButton(
                                    enabled = hasNextVideo,
                                    onClick = { notifyInteraction(); onNextVideo() },
                                    modifier = Modifier.size(40.dp),
                                ) {
                                    Icon(
                                        Icons.Default.SkipNext, "下一部影片",
                                        tint = if (hasNextVideo) Color.White else Color.Gray,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                            Text(
                                durationLabel(currentMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center,
                            ) {
                                VideoProgressSlider(
                                    value = sliderPosition,
                                    onValueChange = previewSeekFraction,
                                    onValueChangeFinished = commitSeekFraction,
                                    modifier = Modifier
                                        .fillMaxWidth(PlaybackProgressStyle.width(progressBarWidth))
                                        .height(PlaybackProgressStyle.touchHeightDp(progressBarScale).dp),
                                    scale = progressBarScale,
                                )
                            }
                            Text(
                                durationLabel(totalDuration),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    } else {
                        VideoProgressSlider(
                            value = sliderPosition,
                            onValueChange = previewSeekFraction,
                            onValueChangeFinished = commitSeekFraction,
                            modifier = Modifier
                                .fillMaxWidth(PlaybackProgressStyle.width(progressBarWidth))
                                .height(PlaybackProgressStyle.touchHeightDp(progressBarScale).dp),
                            scale = progressBarScale,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(durationLabel(currentMs), style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            Text(durationLabel(totalDuration), style = MaterialTheme.typography.labelMedium)
                        }
                        if (showPlayButton || showPreviousNextButtons) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                            ) {
                                if (showPreviousNextButtons) {
                                    IconButton(
                                        enabled = hasPreviousVideo,
                                        onClick = { notifyInteraction(); onPreviousVideo() },
                                    ) {
                                        Icon(
                                            Icons.Default.SkipPrevious, "上一部影片",
                                            tint = if (hasPreviousVideo) Color.White else Color.Gray,
                                            modifier = Modifier.size(30.dp),
                                        )
                                    }
                                }
                                if (showPlayButton) {
                                    IconButton(onClick = {
                                        notifyInteraction()
                                        if (playing) {
                                            player?.pause()
                                            playing = false
                                        } else {
                                            player?.play()
                                            playing = true
                                        }
                                    }) {
                                        Icon(
                                            if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
                                            if (playing) "暫停" else "播放",
                                            tint = Color.White, modifier = Modifier.size(48.dp),
                                        )
                                    }
                                }
                                if (showPreviousNextButtons) {
                                    IconButton(
                                        enabled = hasNextVideo,
                                        onClick = { notifyInteraction(); onNextVideo() },
                                    ) {
                                        Icon(
                                            Icons.Default.SkipNext, "下一部影片",
                                            tint = if (hasNextVideo) Color.White else Color.Gray,
                                            modifier = Modifier.size(30.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!isLandscape) {
                    Text("上下滑切換照片／影片  ·  雙指縮放",
                        color = Color(0xFFB8C5DD), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/**
 * Volume goes vertically along the right edge, entirely separate from the
 * horizontal playback seekbar. This control participates in overlay auto-hide.
 */
@Composable
private fun PlaybackVolumeControl(
    settings: PlaybackVolumeSettings,
    transparency: Float,
    thicknessDp: Float,
    onVolumeChange: (Float) -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
    sliderHeight: androidx.compose.ui.unit.Dp = 184.dp,
) {
    val volume = settings.effectiveVolume
    val muted = volume <= 0f
    Column(
        modifier = modifier
            .graphicsLayer { alpha = VolumeBarStyle.alpha(transparency) }
            .background(Color.Black.copy(alpha = .60f), RoundedCornerShape(20.dp))
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "${(volume * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        VerticalVolumeSlider(
            value = volume,
            thicknessDp = thicknessDp,
            onValueChange = onVolumeChange,
            modifier = Modifier.width(44.dp).height(sliderHeight),
        )
        IconButton(
            onClick = onToggleMute,
            modifier = Modifier.size(42.dp),
        ) {
            Icon(
                imageVector = when {
                    muted -> Icons.Default.VolumeOff
                    volume < 0.5f -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeUp
                },
                contentDescription = if (muted) "取消靜音" else "立即靜音",
                tint = if (muted) Color(0xFF92A9FF) else Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Supports both tap-to-set and vertical dragging; up=louder, down=quieter. */
@Composable
private fun VerticalVolumeSlider(
    value: Float,
    thicknessDp: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnChange by rememberUpdatedState(onValueChange)
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "影片音量"
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(0f, 1f), 0f..1f)
                setProgress { target ->
                    currentOnChange(target.coerceIn(0f, 1f))
                    true
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    currentOnChange(
                        VerticalVolumeGeometry.fractionAt(
                            offset.y, size.height.toFloat(), 12.dp.toPx(),
                        ),
                    )
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        currentOnChange(
                            VerticalVolumeGeometry.fractionAt(
                                offset.y, size.height.toFloat(), 12.dp.toPx(),
                            ),
                        )
                    },
                    onVerticalDrag = { change, _ ->
                        currentOnChange(
                            VerticalVolumeGeometry.fractionAt(
                                change.position.y, size.height.toFloat(), 12.dp.toPx(),
                            ),
                        )
                        change.consume()
                    },
                )
            },
    ) {
        val inset = 12.dp.toPx()
        val trackWidth = VolumeBarStyle.trackDp(thicknessDp).dp.toPx()
        val x = size.width / 2f
        val top = inset
        val bottom = (size.height - inset).coerceAtLeast(top)
        val thumbY = VerticalVolumeGeometry.yAt(value, size.height, inset)
        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = Offset(x, top),
            end = Offset(x, bottom),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color(0xFF92A9FF),
            start = Offset(x, thumbY),
            end = Offset(x, bottom),
            strokeWidth = trackWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(
            color = Color.White,
            radius = VolumeBarStyle.thumbRadiusDp(thicknessDp).dp.toPx(),
            center = Offset(x, thumbY),
        )
    }
}

@Composable
private fun VideoProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    scale: Float = PlaybackProgressStyle.DEFAULT_SCALE,
) {
    // Same thin, directly seekable track in both screen orientations.
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = Color(0xFF92A9FF),
            activeTrackColor = Color(0xFF92A9FF),
            inactiveTrackColor = Color.White.copy(alpha = .25f),
        ),
        thumb = {
            Box(
                Modifier.size(PlaybackProgressStyle.thumbDp(scale).dp)
                    .background(Color(0xFF92A9FF), CircleShape),
            )
        },
        track = { state ->
            Box(
                Modifier.fillMaxWidth().height(PlaybackProgressStyle.trackDp(scale).dp)
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

// Use a deliberately compact central region for single-tap play/pause.
// Bottom 36% is always excluded, even if controls are hidden, so tapping near
// the seekbar never accidentally toggles playback. Swipes/pinch remain full-screen.
internal fun isCenterVideoTap(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): Boolean = width > 0f && height > 0f &&
    x >= width * 0.25f && x <= width * 0.75f &&
    y >= height * 0.28f && y <= height * 0.64f

/** An outside-center tap changes overlay visibility, never playback. */
internal enum class VideoTapAction { PLAY_OR_PAUSE, TOGGLE_CONTROLS }

internal fun videoTapAction(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): VideoTapAction = if (isCenterVideoTap(x, y, width, height)) {
    VideoTapAction.PLAY_OR_PAUSE
} else {
    VideoTapAction.TOGGLE_CONTROLS
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
private fun AndroidVideo(
    player: ExoPlayer?,
    resizeMode: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { context ->
            LayoutInflater.from(context).inflate(R.layout.view_player, null, false) as PlayerView
        },
        modifier = modifier,
        update = { view ->
            view.resizeMode = resizeMode
            if (view.player !== player) view.player = player
        },
        onRelease = { view -> view.player = null },
    )
}
