package com.jimmy0933.myplayer

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collect
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val Background = Color(0xFF0B1120)
private val Panel = Color(0xFF172239)
private val Accent = Color(0xFF91A9FF)
private val Mint = Color(0xFF86E6CB)
private val Subtle = Color(0xFFAAB8D2)

private enum class BrowserMode { ALL, FOLDERS, FAVORITES }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 17, 32)
        window.navigationBarColor = android.graphics.Color.rgb(11, 17, 32)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    secondary = Mint,
                    background = Background,
                    surface = Panel,
                    onSurface = Color.White,
                    onBackground = Color.White,
                ),
            ) {
                MyPlayerApp()
            }
        }
    }
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
private fun MyPlayerApp() {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val loader = remember(context) {
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .build()
    }
    var allMedia by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var loading by remember { mutableStateOf(true) }
    var refresh by remember { mutableIntStateOf(0) }
    var permissionAsked by remember { mutableStateOf(false) }
    val playbackPrefs = remember(context) {
        context.getSharedPreferences("my_player_playback_settings", android.content.Context.MODE_PRIVATE)
    }
    var playbackSettingsOpen by remember { mutableStateOf(false) }
    var decoderSettingsOpen by remember { mutableStateOf(false) }
    // The system's Home / Back / Recents or gesture navigation indicator is
    // independent of the app's own previous/next and play/pause controls.
    var hideSystemNavigationBarDuringVideo by remember {
        mutableStateOf(
            playbackPrefs.getBoolean(
                "hide_system_navigation_bar_during_video",
                PlaybackSystemNavigation.DEFAULT_HIDE_WHILE_PLAYING,
            ),
        )
    }
    fun updateSystemNavigationBarVisibility(hide: Boolean) {
        hideSystemNavigationBarDuringVideo = hide
        playbackPrefs.edit().putBoolean("hide_system_navigation_bar_during_video", hide).apply()
    }
    var decoderPreferences by remember(context) {
        mutableStateOf(DecoderSettings.load(context))
    }
    fun saveDecoderSettings(updated: DecoderPreferences) {
        decoderPreferences = updated
        DecoderSettings.save(context, updated)
    }
    var progressBarScale by remember {
        mutableFloatStateOf(
            PlaybackProgressStyle.scale(
                playbackPrefs.getFloat("progress_size", PlaybackProgressStyle.DEFAULT_SCALE),
            ),
        )
    }
    var progressBarWidth by remember {
        mutableFloatStateOf(
            PlaybackProgressStyle.width(
                playbackPrefs.getFloat("progress_width", PlaybackProgressStyle.DEFAULT_WIDTH),
            ),
        )
    }
    fun updateProgressScale(newValue: Float) {
        progressBarScale = PlaybackProgressStyle.scale(newValue)
        playbackPrefs.edit().putFloat("progress_size", progressBarScale).apply()
    }
    fun updateProgressWidth(newValue: Float) {
        progressBarWidth = PlaybackProgressStyle.width(newValue)
        playbackPrefs.edit().putFloat("progress_width", progressBarWidth).apply()
    }
    var landscapeDisplayMode by remember {
        mutableStateOf(
            LandscapeVideoDisplay.fromSaved(
                playbackPrefs.getString("landscape_display_mode", null),
            ),
        )
    }
    fun updateLandscapeDisplayMode(newMode: LandscapeVideoDisplay) {
        landscapeDisplayMode = newMode
        playbackPrefs.edit().putString("landscape_display_mode", newMode.name).apply()
    }
    var controlsAutoHideSeconds by remember {
        mutableIntStateOf(
            PlaybackControlsTimeout.normalize(
                playbackPrefs.getInt(
                    "controls_auto_hide_seconds",
                    PlaybackControlsTimeout.DEFAULT_SECONDS,
                ),
            ),
        )
    }
    fun updateControlsAutoHideSeconds(newValue: Int) {
        controlsAutoHideSeconds = PlaybackControlsTimeout.normalize(newValue)
        playbackPrefs.edit().putInt("controls_auto_hide_seconds", controlsAutoHideSeconds).apply()
    }
    // Migrate the previous default-visible play/pause setting once. Existing
    // installations need to hide the button too, not just fresh installs.
    // After migration, explicitly enabling it in Settings remains persistent.
    var showPlayButton by remember {
        if (!playbackPrefs.getBoolean("play_button_hidden_default_migrated", false)) {
            playbackPrefs.edit()
                .putBoolean("show_play_button", false)
                .putBoolean("play_button_hidden_default_migrated", true)
                .apply()
        }
        mutableStateOf(playbackPrefs.getBoolean("show_play_button", false))
    }
    var showPreviousNextButtons by remember {
        mutableStateOf(playbackPrefs.getBoolean("show_previous_next_buttons", false))
    }
    fun updatePlayButtonVisibility(value: Boolean) {
        showPlayButton = value
        playbackPrefs.edit().putBoolean("show_play_button", value).apply()
    }
    fun updatePreviousNextVisibility(value: Boolean) {
        showPreviousNextButtons = value
        playbackPrefs.edit().putBoolean("show_previous_next_buttons", value).apply()
    }
    // Shared across videos and preserved between app launches. Muting never
    // overwrites the user's last audible volume.
    var playbackVolume by remember {
        mutableStateOf(
            PlaybackVolumeSettings(
                level = PlaybackVolumeSettings.normalizeLevel(
                    playbackPrefs.getFloat("video_volume", PlaybackVolumeSettings.DEFAULT_LEVEL),
                ),
                muted = playbackPrefs.getBoolean("video_muted", false),
            ),
        )
    }
    fun savePlaybackVolume(updated: PlaybackVolumeSettings) {
        playbackVolume = updated
        playbackPrefs.edit()
            .putFloat("video_volume", updated.level)
            .putBoolean("video_muted", updated.muted)
            .apply()
    }
    var volumeBarTransparency by remember {
        mutableFloatStateOf(
            VolumeBarStyle.transparency(
                playbackPrefs.getFloat(
                    "volume_bar_transparency", VolumeBarStyle.DEFAULT_TRANSPARENCY,
                ),
            ),
        )
    }
    var volumeBarThickness by remember {
        mutableFloatStateOf(
            VolumeBarStyle.trackDp(
                playbackPrefs.getFloat("volume_bar_thickness", VolumeBarStyle.DEFAULT_TRACK_DP),
            ),
        )
    }
    fun updateVolumeBarTransparency(value: Float) {
        volumeBarTransparency = VolumeBarStyle.transparency(value)
        playbackPrefs.edit().putFloat("volume_bar_transparency", volumeBarTransparency).apply()
    }
    fun updateVolumeBarThickness(value: Float) {
        volumeBarThickness = VolumeBarStyle.trackDp(value)
        playbackPrefs.edit().putFloat("volume_bar_thickness", volumeBarThickness).apply()
    }
    val galleryPrefs = remember(context) {
        context.getSharedPreferences("my_player_gallery_position", android.content.Context.MODE_PRIVATE)
    }
    var filter by remember {
        mutableStateOf(runCatching {
            MediaFilter.valueOf(galleryPrefs.getString("filter", "ALL") ?: "ALL")
        }.getOrDefault(MediaFilter.ALL))
    }
    val initialSort = remember(galleryPrefs) {
        val saved = galleryPrefs.getString("sort", null)
        val version = galleryPrefs.getInt("sort_version", 0)
        SortPreferenceMigration.migrate(saved, version).also { migrated ->
            if (version < SortPreferenceMigration.VERSION) {
                galleryPrefs.edit()
                    .putString("sort", migrated.name)
                    .putInt("sort_version", SortPreferenceMigration.VERSION)
                    .apply()
            }
        }
    }
    var sort by remember { mutableStateOf(initialSort) }
    var ascending by remember { mutableStateOf(galleryPrefs.getBoolean("ascending", false)) }
    var query by remember { mutableStateOf(galleryPrefs.getString("query", "").orEmpty()) }
    var searchExpanded by remember { mutableStateOf(query.isNotEmpty()) }
    var listMode by remember { mutableStateOf(galleryPrefs.getBoolean("list_mode", false)) }
    var browserMode by remember {
        mutableStateOf(runCatching {
            BrowserMode.valueOf(galleryPrefs.getString("browser", "ALL") ?: "ALL")
        }.getOrDefault(BrowserMode.ALL))
    }
    var currentFolderKey by remember { mutableStateOf(galleryPrefs.getString("folder", null)) }
    val favoritePrefs = remember(context) {
        context.getSharedPreferences("my_player_folders", android.content.Context.MODE_PRIVATE)
    }
    var favoriteFolders by remember {
        mutableStateOf(favoritePrefs.getStringSet("favorite_folders", emptySet<String>())
            .orEmpty().toSet())
    }
    val folders = remember(allMedia) { MediaFolders.index(allMedia) }
    fun toggleFavorite(key: String) {
        val next = if (key in favoriteFolders) favoriteFolders - key else favoriteFolders + key
        favoriteFolders = next
        favoritePrefs.edit().putStringSet("favorite_folders", next).apply()
    }
    var viewerItems by remember { mutableStateOf<List<LocalMedia>?>(null) }
    var viewerIndex by remember { mutableIntStateOf(0) }
    // Retain the gallery viewport even while the fullscreen viewer is displayed.
    val galleryListState = rememberLazyListState(
        initialFirstVisibleItemIndex = galleryPrefs.getInt("list_index", 0).coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = galleryPrefs.getInt("list_offset", 0).coerceAtLeast(0),
    )
    val galleryGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = galleryPrefs.getInt("grid_index", 0).coerceAtLeast(0),
        initialFirstVisibleItemScrollOffset = galleryPrefs.getInt("grid_offset", 0).coerceAtLeast(0),
    )
    fun saveScroll() {
        galleryPrefs.edit()
            .putInt("list_index", galleryListState.firstVisibleItemIndex)
            .putInt("list_offset", galleryListState.firstVisibleItemScrollOffset)
            .putInt("grid_index", galleryGridState.firstVisibleItemIndex)
            .putInt("grid_offset", galleryGridState.firstVisibleItemScrollOffset)
            .apply()
    }
    fun saveBrowser() {
        galleryPrefs.edit()
            .putString("filter", filter.name)
            .putString("sort", sort.name)
            .putBoolean("ascending", ascending)
            .putString("query", query)
            .putBoolean("list_mode", listMode)
            .putString("browser", browserMode.name)
            .putString("folder", currentFolderKey)
            .apply()
    }
    LaunchedEffect(filter, sort, ascending, query, listMode, browserMode, currentFolderKey) {
        saveBrowser()
    }
    LaunchedEffect(galleryListState) {
        snapshotFlow {
            galleryListState.firstVisibleItemIndex to galleryListState.firstVisibleItemScrollOffset
        }.debounce(350).collect { saveScroll() }
    }
    LaunchedEffect(galleryGridState) {
        snapshotFlow {
            galleryGridState.firstVisibleItemIndex to galleryGridState.firstVisibleItemScrollOffset
        }.debounce(350).collect { saveScroll() }
    }
    var openedMediaUri by remember { mutableStateOf<String?>(null) }
    var pendingReturnUri by remember { mutableStateOf<String?>(null) }
    fun closeViewer() {
        // Keep the existing exact offset unless the user swiped to another item.
        val lastViewedUri = viewerItems?.getOrNull(viewerIndex)?.uri?.toString()
        pendingReturnUri = lastViewedUri?.takeIf { it != openedMediaUri }
        viewerItems = null
        openedMediaUri = null
    }
    var deleteConfirmation by remember { mutableStateOf<List<LocalMedia>?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf(emptySet<String>()) }
    var mediaManageInfo by remember { mutableStateOf(false) }
    var canManageMedia by remember {
        mutableStateOf(Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context))
    }
    var pendingBatch by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var deleteQueue by remember { mutableStateOf(emptyList<LocalMedia>()) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deletedCount by remember { mutableIntStateOf(0) }
    var failedCount by remember { mutableIntStateOf(0) }
    var cancelledDeletion by remember { mutableStateOf(false) }
    var currentChunkLimit by remember { mutableIntStateOf(BatchDeletePolicy.MAX_REQUEST_ITEMS) }
    var approvedLegacyUri by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val requestedScreenOrientation = if (viewerItems != null) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    SideEffect {
        if (activity.requestedOrientation != requestedScreenOrientation) {
            activity.requestedOrientation = requestedScreenOrientation
        }
    }

    // The status bar always stays hidden in the viewer. Allow the user to
    // independently show Android's own Back/Home/Recents navigation bar during
    // video playback, without changing the photo viewer or gallery defaults.
    // Re-evaluate when switching between videos and photos, or on rotation.
    val isViewerOpen = viewerItems != null
    val isVideoViewerOpen = viewerItems?.getOrNull(viewerIndex)?.isVideo == true
    val viewerOrientation = LocalConfiguration.current.orientation
    DisposableEffect(
        activity, isViewerOpen, isVideoViewerOpen,
        hideSystemNavigationBarDuringVideo, viewerOrientation,
    ) {
        val insetsController = WindowCompat.getInsetsController(
            activity.window, activity.window.decorView,
        )
        val statusBars = WindowInsetsCompat.Type.statusBars()
        val navigationBars = WindowInsetsCompat.Type.navigationBars()
        if (PlaybackSystemNavigation.hideStatusBar(isViewerOpen)) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(statusBars)
            if (PlaybackSystemNavigation.hideNavigationBar(
                    isViewerOpen, isVideoViewerOpen, hideSystemNavigationBarDuringVideo,
                )) {
                insetsController.hide(navigationBars)
            } else {
                insetsController.show(navigationBars)
            }
        } else {
            insetsController.show(statusBars or navigationBars)
        }
        onDispose {
            // A previous viewer effect must not leave the gallery immersive.
            if (isViewerOpen) insetsController.show(statusBars or navigationBars)
        }
    }

    fun removeDeletedMedia(items: List<LocalMedia>) {
        if (items.isEmpty()) return
        val removed = items.mapTo(mutableSetOf()) { it.uri.toString() }
        allMedia = allMedia.filterNot { it.uri.toString() in removed }
        selectedUris = selectedUris - removed
        viewerItems?.let { before ->
            val activeUri = before.getOrNull(viewerIndex)?.uri?.toString()
            val after = before.filterNot { it.uri.toString() in removed }
            if (after.isEmpty()) {
                viewerItems = null
                openedMediaUri = null
                pendingReturnUri = null
                viewerIndex = 0
            } else {
                val newIndex = after.indexOfFirst { it.uri.toString() == activeUri }
                viewerIndex = if (newIndex >= 0) newIndex else viewerIndex.coerceAtMost(after.lastIndex)
                viewerItems = after
            }
        }
    }

    lateinit var processNext: () -> Unit
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val approved = pendingBatch
        pendingBatch = emptyList()
        if (result.resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                approvedLegacyUri = approved.firstOrNull()?.uri?.toString()
            } else {
                removeDeletedMedia(approved)
                deletedCount += approved.size
                deleteQueue = deleteQueue.drop(approved.size)
            }
        } else {
            cancelledDeletion = true
            deleteQueue = emptyList()
        }
        processNext()
    }
    val legacyWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) processNext()
        else {
            cancelledDeletion = true
            deleteQueue = emptyList()
            processNext()
        }
    }

    processNext = {
        if (deleteQueue.isEmpty()) {
            deleting = false
            pendingBatch = emptyList()
            approvedLegacyUri = null
            if (!cancelledDeletion && failedCount == 0) {
                selectionMode = false
                selectedUris = emptySet()
            }
            if (failedCount > 0 || cancelledDeletion) {
                deleteError = "已刪除 " + deletedCount + " 個。" +
                    (if (failedCount > 0) "失敗 " + failedCount + " 個。" else "") +
                    (if (cancelledDeletion) "已取消剩餘檔案，未刪除的仍保留。" else "")
            }
            refresh++
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val batch = deleteQueue.take(currentChunkLimit)
            try {
                val request = MediaStore.createDeleteRequest(
                    context.contentResolver, batch.map { it.uri },
                )
                pendingBatch = batch
                systemDeleteLauncher.launch(
                    IntentSenderRequest.Builder(request.intentSender).build(),
                )
            } catch (_: Exception) {
                // Isolate an invalid URI without losing the other selected items.
                if (batch.size > 1) {
                    currentChunkLimit = 1
                } else {
                    failedCount++
                    deleteQueue = deleteQueue.drop(1)
                }
                scope.launch { processNext() }
            }
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED) {
            legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            val item = deleteQueue.first()
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.delete(item.uri, null, null) }
                }
                val failure = outcome.exceptionOrNull()
                if (failure is RecoverableSecurityException &&
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
                    approvedLegacyUri != item.uri.toString()) {
                    try {
                        pendingBatch = listOf(item)
                        systemDeleteLauncher.launch(
                            IntentSenderRequest.Builder(
                                failure.userAction.actionIntent.intentSender,
                            ).build(),
                        )
                    } catch (_: Exception) {
                        failedCount++
                        deleteQueue = deleteQueue.drop(1)
                        processNext()
                    }
                } else {
                    if ((outcome.getOrNull() ?: 0) > 0) {
                        removeDeletedMedia(listOf(item))
                        deletedCount++
                    } else {
                        failedCount++
                    }
                    approvedLegacyUri = null
                    deleteQueue = deleteQueue.drop(1)
                    processNext()
                }
            }
        }
    }

    fun beginDeletion(items: List<LocalMedia>) {
        if (deleting) return
        val unique = BatchDeletePolicy.unique(items) { it.uri.toString() }
        if (unique.isEmpty()) return
        deleting = true
        deleteError = null
        deletedCount = 0
        failedCount = 0
        cancelledDeletion = false
        currentChunkLimit = BatchDeletePolicy.MAX_REQUEST_ITEMS
        approvedLegacyUri = null
        deleteQueue = unique
        processNext()
    }

    fun requestDeletion(items: List<LocalMedia>) {
        if (items.isEmpty() || deleting) return
        val granted = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context)
        canManageMedia = granted
        if (BatchDeletePolicy.needsAppConfirmation(Build.VERSION.SDK_INT, granted)) {
            deleteConfirmation = items
        } else {
            beginDeletion(items)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                canManageMedia = Build.VERSION.SDK_INT >= 31 && MediaStore.canManageMedia(context)
                refresh++
            }
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                saveScroll()
                saveBrowser()
            }
        }
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        if (!MediaLibrary.hasAccess(context) && !permissionAsked) {
            permissionAsked = true
            permissionLauncher.launch(MediaLibrary.requestedPermissions())
        }
    }
    LaunchedEffect(refresh) {
        loading = true
        allMedia = if (MediaLibrary.hasAccess(context)) MediaLibrary.scan(context) else emptyList()
        loading = false
    }
    val visible = remember(allMedia, filter, sort, ascending, query, browserMode, currentFolderKey) {
        val sorted = MediaLibrary.sorted(allMedia, filter, sort, ascending, query)
        when (browserMode) {
            BrowserMode.ALL -> sorted
            BrowserMode.FOLDERS -> currentFolderKey?.let { MediaFolders.directMedia(sorted, it) }
                ?: emptyList()
            BrowserMode.FAVORITES -> emptyList()
        }
    }
    val folderRows = remember(folders, favoriteFolders, browserMode, currentFolderKey) {
        when (browserMode) {
            BrowserMode.ALL -> emptyList()
            BrowserMode.FOLDERS -> MediaFolders.children(folders, currentFolderKey)
            BrowserMode.FAVORITES -> favoriteFolders.map {
                folders[it] ?: MediaFolders.unavailableFavorite(it)
            }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }
    LaunchedEffect(folders, browserMode, currentFolderKey, loading) {
        if (!loading && browserMode == BrowserMode.FOLDERS &&
            currentFolderKey != null && currentFolderKey !in folders) {
            currentFolderKey = MediaFolders.parentKey(currentFolderKey!!)
        }
    }

    if (viewerItems != null) {
        BackHandler { closeViewer() }
        val viewerList = viewerItems.orEmpty()
        if (viewerIndex in viewerList.indices) {
            val videoFlags = viewerList.map { it.isVideo }
            val previousVideoIndex = if (showPreviousNextButtons) {
                adjacentVideoIndex(videoFlags, viewerIndex, -1)
            } else null
            val nextVideoIndex = if (showPreviousNextButtons) {
                adjacentVideoIndex(videoFlags, viewerIndex, 1)
            } else null
            ViewerScreen(
                media = viewerList[viewerIndex],
                position = viewerIndex,
                total = viewerList.size,
                loader = loader,
                progressBarScale = progressBarScale,
                progressBarWidth = progressBarWidth,
                controlsAutoHideSeconds = controlsAutoHideSeconds,
                showPlayButton = showPlayButton,
                showPreviousNextButtons = showPreviousNextButtons,
                volumeSettings = playbackVolume,
                volumeBarTransparency = volumeBarTransparency,
                volumeBarThickness = volumeBarThickness,
                onVolumeChange = { savePlaybackVolume(playbackVolume.withSlider(it)) },
                onToggleMute = { savePlaybackVolume(playbackVolume.toggleMute()) },
                hasPreviousVideo = previousVideoIndex != null,
                hasNextVideo = nextVideoIndex != null,
                onPreviousVideo = { previousVideoIndex?.let { viewerIndex = it } },
                onNextVideo = { nextVideoIndex?.let { viewerIndex = it } },
                landscapeDisplayMode = landscapeDisplayMode,
                decoderPreferences = decoderPreferences,
                onBack = { closeViewer() },
                onDelete = { requestDeletion(listOf(viewerList[viewerIndex])) },
                onChange = { direction ->
                    viewerIndex = (viewerIndex + direction).coerceIn(0, viewerList.lastIndex)
                },
            )
        } else {
            LaunchedEffect(viewerItems) { viewerItems = null }
        }
    } else {
        GalleryScreen(
            allMedia = allMedia,
            visible = visible,
            listState = galleryListState,
            gridState = galleryGridState,
            returnToMediaUri = pendingReturnUri,
            onReturnPositionHandled = { pendingReturnUri = null },
            folders = folderRows,
            selectionMode = selectionMode,
            selectedUris = selectedUris,
            deleting = deleting,
            onLongSelect = { media ->
                if (!deleting) {
                    selectionMode = true
                    selectedUris = selectedUris + media.uri.toString()
                }
            },
            onToggleSelection = { media ->
                if (!deleting) {
                    val id = media.uri.toString()
                    selectedUris = if (id in selectedUris) selectedUris - id else selectedUris + id
                }
            },
            onExitSelection = { selectionMode = false; selectedUris = emptySet() },
            onSelectAll = { selectedUris = visible.mapTo(mutableSetOf()) { it.uri.toString() } },
            onDeleteSelected = {
                requestDeletion(visible.filter { it.uri.toString() in selectedUris })
            },
            canManageMedia = canManageMedia,
            onMediaManagementInfo = { mediaManageInfo = true },
            onPlaybackSettings = { playbackSettingsOpen = true },
            browserMode = browserMode,
            currentFolderKey = currentFolderKey,
            favoriteFolders = favoriteFolders,
            onBrowserModeChange = { selected ->
                if (browserMode != selected) {
                    saveScroll()
                    browserMode = selected
                    currentFolderKey = null
                    scope.launch {
                        galleryListState.scrollToItem(0)
                        galleryGridState.scrollToItem(0)
                    }
                }
            },
            onFolderChange = { key ->
                if (browserMode != BrowserMode.FOLDERS || currentFolderKey != key) {
                    saveScroll()
                    browserMode = BrowserMode.FOLDERS
                    currentFolderKey = key
                    scope.launch {
                        galleryListState.scrollToItem(0)
                        galleryGridState.scrollToItem(0)
                    }
                }
            },
            onToggleFavorite = ::toggleFavorite,
            loading = loading,
            hasPermission = MediaLibrary.hasAccess(context),
            loader = loader,
            filter = filter,
            onFilter = {
                if (filter != it) {
                    filter = it
                    scope.launch {
                        galleryListState.scrollToItem(0)
                        galleryGridState.scrollToItem(0)
                    }
                }
            },
            sort = sort,
            onSort = {
                if (sort != it) {
                    sort = it
                    scope.launch {
                        galleryListState.scrollToItem(0)
                        galleryGridState.scrollToItem(0)
                    }
                }
            },
            ascending = ascending,
            onReverse = {
                ascending = !ascending
                scope.launch {
                    galleryListState.scrollToItem(0)
                    galleryGridState.scrollToItem(0)
                }
            },
            query = query,
            onQuery = { newQuery ->
                query = newQuery
                scope.launch {
                    galleryListState.scrollToItem(0)
                    galleryGridState.scrollToItem(0)
                }
            },
            searchExpanded = searchExpanded,
            onSearchToggle = {
                searchExpanded = !searchExpanded
                if (!searchExpanded) query = ""
            },
            listMode = listMode,
            onToggleMode = { listMode = !listMode },
            onRefresh = { refresh++ },
            onPermission = { permissionLauncher.launch(MediaLibrary.requestedPermissions()) },
            onOpen = { index ->
                saveScroll()
                openedMediaUri = visible.getOrNull(index)?.uri?.toString()
                pendingReturnUri = null
                viewerItems = visible.toList()
                viewerIndex = index
            },
        )
    }
    deleteConfirmation?.let { targets ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteConfirmation = null },
            title = { Text("確認刪除 " + targets.size + " 個檔案？") },
            text = {
                Text(
                    if (targets.size == 1) {
                        "確定從手機刪除「" + targets.first().name + "」？原始檔案將永久刪除。"
                    } else {
                        "確定永久刪除選取的 " + targets.size + " 個圖片或影片？此操作不可復原。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleteConfirmation = null
                        beginDeletion(targets)
                    },
                ) { Text("刪除", color = Color(0xFFFF8792)) }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deleteConfirmation = null }) {
                    Text("取消")
                }
            },
        )
    }
    if (mediaManageInfo) {
        AlertDialog(
            onDismissRequest = { mediaManageInfo = false },
            title = { Text("媒體管理權限") },
            text = {
                Text(
                    if (canManageMedia) {
                        "已允許媒體管理。刪除檔案時，App 只會確認一次，不再逐次顯示 Android 系統確認。"
                    } else {
                        "可選擇授予 Android「媒體管理」特殊權限，之後在 App 確認一次即可批次刪除。未授予時仍可使用 Android 系統的刪除確認視窗。"
                    },
                )
            },
            confirmButton = {
                if (!canManageMedia && Build.VERSION.SDK_INT >= 31) {
                    TextButton(onClick = {
                        mediaManageInfo = false
                        try {
                            context.startActivity(
                                Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA).apply {
                                    data = Uri.parse("package:" + context.packageName)
                                },
                            )
                        } catch (_: Exception) {
                            deleteError = "無法開啟媒體管理權限設定。"
                        }
                    }) { Text("前往授權設定") }
                } else {
                    TextButton(onClick = { mediaManageInfo = false }) { Text("完成") }
                }
            },
            dismissButton = {
                if (!canManageMedia) TextButton(onClick = { mediaManageInfo = false }) {
                    Text("稍後再說")
                }
            },
        )
    }
    if (playbackSettingsOpen) {
        AlertDialog(
            onDismissRequest = { playbackSettingsOpen = false },
            title = { Text("播放介面設定") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        "進度條粗細：" + (progressBarScale * 100).toInt() + "%",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = progressBarScale,
                        onValueChange = ::updateProgressScale,
                        valueRange = 0.8f..2.5f,
                    )
                    Text(
                        "進度條長度：" + (progressBarWidth * 100).toInt() + "%",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = progressBarWidth,
                        onValueChange = ::updateProgressWidth,
                        valueRange = 0.6f..1f,
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "系統導覽列",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "播放影片時隱藏底部系統導覽列",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = hideSystemNavigationBarDuringVideo,
                            onCheckedChange = ::updateSystemNavigationBarVisibility,
                        )
                    }
                    Text(
                        "開啟時隱藏手機底部的返回／首頁／最近使用鍵或手勢提示列，進度條可使用更多畫面；關閉則保留系統導覽列。切換影片、橫直向與暫停時皆適用。",
                        color = Subtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "影片播放按鈕",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "顯示播放／暫停鍵",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = showPlayButton,
                            onCheckedChange = ::updatePlayButtonVisibility,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "顯示上一部／下一部按鈕",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = showPreviousNextButtons,
                            onCheckedChange = ::updatePreviousNextVisibility,
                        )
                    }
                    Text(
                        "點影片中央播放／暫停；點中央以外的空白處可顯示或隱藏標題、說明、進度條與音量控制。上下滑動仍可切換影片。",
                        color = Subtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "右側音量條外觀",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "音量條透明度：${(volumeBarTransparency * 100).roundToInt()}%",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = volumeBarTransparency,
                        onValueChange = ::updateVolumeBarTransparency,
                        valueRange = 0f..VolumeBarStyle.MAX_TRANSPARENCY,
                    )
                    Text(
                        "音量條粗細：${volumeBarThickness.roundToInt()} dp",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = volumeBarThickness,
                        onValueChange = ::updateVolumeBarThickness,
                        valueRange = VolumeBarStyle.MIN_TRACK_DP..VolumeBarStyle.MAX_TRACK_DP,
                        steps = (VolumeBarStyle.MAX_TRACK_DP - VolumeBarStyle.MIN_TRACK_DP).toInt() - 1,
                    )
                    Text(
                        "預覽：透明度會套用至整個右側音量控制，包括背景、音量數值與靜音鍵；粗細僅調整音量軌道與滑動圓點。",
                        color = Subtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp)
                            .background(Panel, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            modifier = Modifier
                                .graphicsLayer { alpha = VolumeBarStyle.alpha(volumeBarTransparency) }
                                .background(Color.Black.copy(alpha = .60f), RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "50%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Box(
                                modifier = Modifier.width(44.dp).height(64.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    Modifier.width(volumeBarThickness.dp).height(56.dp)
                                        .background(Color.White.copy(alpha = .28f), CircleShape),
                                )
                                Box(
                                    Modifier.align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp)
                                        .width(volumeBarThickness.dp).height(28.dp)
                                        .background(Accent, CircleShape),
                                )
                                Box(
                                    Modifier.size(
                                        (VolumeBarStyle.thumbRadiusDp(volumeBarThickness) * 2f).dp,
                                    ).background(Color.White, CircleShape),
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "橫向影片顯示",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            LandscapeVideoDisplay.FIT to "完整",
                            LandscapeVideoDisplay.SMART to "減少黑邊",
                            LandscapeVideoDisplay.FILL to "填滿",
                        ).forEach { (mode, label) ->
                            FilterChip(
                                selected = landscapeDisplayMode == mode,
                                onClick = { updateLandscapeDisplayMode(mode) },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        "預設「減少黑邊」會依影片比例適度放大，最多放大 18%。" +
                            "「完整」不裁切；「填滿」可能裁切影片上下或左右內容。",
                        color = Subtle,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    HorizontalDivider()
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "標題與進度條消失時間：" +
                            if (controlsAutoHideSeconds == PlaybackControlsTimeout.NEVER) {
                                "不自動隱藏"
                            } else {
                                "${controlsAutoHideSeconds} 秒"
                            },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = if (controlsAutoHideSeconds == PlaybackControlsTimeout.NEVER) {
                            PlaybackControlsTimeout.DEFAULT_SECONDS.toFloat()
                        } else controlsAutoHideSeconds.toFloat(),
                        onValueChange = {
                            updateControlsAutoHideSeconds(it.roundToInt())
                        },
                        valueRange = PlaybackControlsTimeout.MIN_SECONDS.toFloat()..
                            PlaybackControlsTimeout.MAX_SECONDS.toFloat(),
                        steps = PlaybackControlsTimeout.MAX_SECONDS -
                            PlaybackControlsTimeout.MIN_SECONDS - 1,
                        enabled = controlsAutoHideSeconds != PlaybackControlsTimeout.NEVER,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "永不自動隱藏",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Switch(
                            checked = controlsAutoHideSeconds == PlaybackControlsTimeout.NEVER,
                            onCheckedChange = { alwaysVisible ->
                                updateControlsAutoHideSeconds(
                                    if (alwaysVisible) PlaybackControlsTimeout.NEVER
                                    else PlaybackControlsTimeout.DEFAULT_SECONDS,
                                )
                            },
                        )
                    }
                    Text(
                        "影片播放時，沒有操作達指定秒數後，自動隱藏標題、進度條與右側音量控制。暫停時不會自動隱藏，也能手動點畫面切換。",
                        style = MaterialTheme.typography.bodySmall,
                        color = Subtle,
                    )
                    TextButton(onClick = {
                        updateProgressScale(PlaybackProgressStyle.DEFAULT_SCALE)
                        updateProgressWidth(PlaybackProgressStyle.DEFAULT_WIDTH)
                        updateControlsAutoHideSeconds(PlaybackControlsTimeout.DEFAULT_SECONDS)
                        updateLandscapeDisplayMode(LandscapeVideoDisplay.SMART)
                        updatePlayButtonVisibility(false)
                        updatePreviousNextVisibility(false)
                        updateSystemNavigationBarVisibility(PlaybackSystemNavigation.DEFAULT_HIDE_WHILE_PLAYING)
                        updateVolumeBarTransparency(VolumeBarStyle.DEFAULT_TRANSPARENCY)
                        updateVolumeBarThickness(VolumeBarStyle.DEFAULT_TRACK_DP)
                    }) {
                        Text("恢復預設")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "預覽（直向、橫向播放皆適用）",
                        color = Subtle,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                            .background(Panel, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressBarWidth)
                                .height(PlaybackProgressStyle.trackDp(progressBarScale).dp)
                                .background(Color.White.copy(alpha = .3f), CircleShape),
                        ) {
                            Box(
                                modifier = Modifier.fillMaxWidth(.4f).fillMaxHeight()
                                    .background(Accent, CircleShape),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    playbackSettingsOpen = false
                    decoderSettingsOpen = true
                }) {
                    Text("解碼器設定")
                }
            },
            dismissButton = {
                TextButton(onClick = { playbackSettingsOpen = false }) {
                    Text("完成")
                }
            },
        )
    }
    if (decoderSettingsOpen) {
        DecoderSettingsDialog(
            settings = decoderPreferences,
            onChange = ::saveDecoderSettings,
            onDismiss = { decoderSettingsOpen = false },
        )
    }
    deleteError?.let { message ->
        AlertDialog(
            onDismissRequest = { deleteError = null },
            title = { Text("刪除未完成") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { deleteError = null }) { Text("知道了") }
            },
        )
    }
}

@Composable
private fun GalleryScreen(
    allMedia: List<LocalMedia>,
    visible: List<LocalMedia>,
    listState: LazyListState,
    gridState: LazyGridState,
    returnToMediaUri: String?,
    onReturnPositionHandled: () -> Unit,
    folders: List<MediaFolder>,
    selectionMode: Boolean,
    selectedUris: Set<String>,
    deleting: Boolean,
    onLongSelect: (LocalMedia) -> Unit,
    onToggleSelection: (LocalMedia) -> Unit,
    onExitSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    canManageMedia: Boolean,
    onMediaManagementInfo: () -> Unit,
    onPlaybackSettings: () -> Unit,
    browserMode: BrowserMode,
    currentFolderKey: String?,
    favoriteFolders: Set<String>,
    onBrowserModeChange: (BrowserMode) -> Unit,
    onFolderChange: (String?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    loading: Boolean,
    hasPermission: Boolean,
    loader: ImageLoader,
    filter: MediaFilter,
    onFilter: (MediaFilter) -> Unit,
    sort: MediaSort,
    onSort: (MediaSort) -> Unit,
    ascending: Boolean,
    onReverse: () -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    searchExpanded: Boolean,
    onSearchToggle: () -> Unit,
    listMode: Boolean,
    onToggleMode: () -> Unit,
    onRefresh: () -> Unit,
    onPermission: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    BackHandler(enabled = selectionMode || browserMode != BrowserMode.ALL) {
        if (selectionMode) onExitSelection()
        else if (browserMode == BrowserMode.FOLDERS && currentFolderKey != null) {
            onFolderChange(MediaFolders.parentKey(currentFolderKey))
        } else {
            onBrowserModeChange(BrowserMode.ALL)
        }
    }
    fun tapItem(index: Int) {
        val item = visible.getOrNull(index) ?: return
        if (selectionMode) onToggleSelection(item) else onOpen(index)
    }
    fun longTapItem(index: Int) {
        visible.getOrNull(index)?.let(onLongSelect)
    }
    val photoCount = remember(allMedia) { allMedia.count { !it.isVideo } }
    val videoCount = remember(allMedia) { allMedia.count { it.isVideo } }
    // Only chronological sorting displays date sections. Keep the original
    // indices so tapping an item still opens the correct position in the viewer.
    val dateGroups = remember(visible, sort) {
        MediaLibrary.groupByDay(visible, sort)
    }
    LaunchedEffect(returnToMediaUri, visible, folders, sort, listMode, loading) {
        val targetUri = returnToMediaUri ?: return@LaunchedEffect
        if (loading) return@LaunchedEffect
        val mediaIndex = visible.indexOfFirst { it.uri.toString() == targetUri }
        if (mediaIndex >= 0) {
            val dayKeys = if (sort != MediaSort.SIZE) {
                visible.map {
                    MediaLibrary.createdDayKey(MediaLibrary.sortTimestamp(it, sort))
                }
            } else {
                List(visible.size) { "" }
            }
            val itemIndex = galleryLazyItemIndex(
                mediaIndex = mediaIndex,
                dayKeys = dayKeys,
                folderRows = folders.size,
                groupedByDay = sort != MediaSort.SIZE,
            )
            if (itemIndex >= 0) {
                if (listMode) listState.scrollToItem(itemIndex)
                else gridState.scrollToItem(itemIndex)
            }
        }
        onReturnPositionHandled()
    }
    var detailsExpanded by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF192947), Background, Background),
                ),
            )
            // Android 15/16 draws edge-to-edge. Keep the compact header and
            // all right-side actions below the status bar and display cutout,
            // and the gallery items above the system navigation bar.
            .safeDrawingPadding(),
    ) {
        // All gallery navigation and primary actions share a single icon-only
        // toolbar. Equal-width slots keep everything on one row on narrow phones.
        // Content descriptions preserve accessibility without visible labels.
        Column(
            Modifier.fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                BrowserIconTab(
                    title = "全部影像",
                    image = Icons.Default.PhotoLibrary,
                    selected = browserMode == BrowserMode.ALL,
                    modifier = Modifier.weight(1f),
                ) { onBrowserModeChange(BrowserMode.ALL) }
                BrowserIconTab(
                    title = "資料夾",
                    image = Icons.Default.Folder,
                    selected = browserMode == BrowserMode.FOLDERS,
                    modifier = Modifier.weight(1f),
                ) { onBrowserModeChange(BrowserMode.FOLDERS) }
                BrowserIconTab(
                    title = "最愛",
                    image = Icons.Default.Star,
                    selected = browserMode == BrowserMode.FAVORITES,
                    modifier = Modifier.weight(1f),
                ) { onBrowserModeChange(BrowserMode.FAVORITES) }

                Spacer(Modifier.width(4.dp))

                IconButton(
                    onClick = onSearchToggle,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(
                            if (searchExpanded) Accent.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        ),
                ) {
                    Icon(
                        if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                        if (searchExpanded) "收合搜尋" else "展開搜尋",
                        tint = if (searchExpanded) Mint else Subtle,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = { detailsExpanded = !detailsExpanded },
                    modifier = Modifier.weight(1f).fillMaxHeight()
                        .background(
                            if (detailsExpanded) Accent.copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(12.dp),
                        ),
                ) {
                    Icon(
                        if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.Tune,
                        if (detailsExpanded) "收合更多工具" else "展開更多工具",
                        tint = if (detailsExpanded) Mint else Subtle,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = onToggleMode,
                    enabled = browserMode != BrowserMode.FAVORITES,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Icon(
                        if (listMode) Icons.Default.GridView else Icons.Default.ViewList,
                        if (listMode) "切換網格" else "切換清單",
                        tint = if (browserMode == BrowserMode.FAVORITES) Subtle.copy(alpha = 0.35f)
                               else Accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                IconButton(
                    onClick = onPlaybackSettings,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        "播放進度條設定",
                        tint = Subtle,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            if (selectionMode) {
                Spacer(Modifier.height(5.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(Panel, RoundedCornerShape(11.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已選 " + selectedUris.size + " 個",
                        modifier = Modifier.weight(1f),
                        color = Mint,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(enabled = !deleting && visible.isNotEmpty(), onClick = onSelectAll) {
                        Text("全選")
                    }
                    IconButton(
                        enabled = !deleting,
                        onClick = onExitSelection,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(Icons.Default.Close, "取消多選", tint = Subtle)
                    }
                    TextButton(
                        enabled = !deleting && selectedUris.isNotEmpty(),
                        onClick = onDeleteSelected,
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            null,
                            tint = Color(0xFFFF9DA6),
                            modifier = Modifier.size(18.dp),
                        )
                        Text(if (deleting) "刪除中" else "刪除", color = Color(0xFFFF9DA6))
                    }
                }
            }

            if (searchExpanded) {
                Spacer(Modifier.height(5.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜尋圖片或影片", color = Subtle) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Subtle) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { onQuery("") }) {
                                Icon(Icons.Default.Close, "清除搜尋文字")
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Panel.copy(alpha = .7f),
                        unfocusedContainerColor = Panel.copy(alpha = .7f),
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF30405E),
                    ),
                )
            }

            if (detailsExpanded) {
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onRefresh) {
                    Icon(
                        Icons.Default.Refresh, null, tint = Subtle,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("重新掃描媒體", color = Subtle)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricChip(
                        "照片", photoCount.toString(), Icons.Default.Image, Modifier.weight(1f),
                    )
                    MetricChip(
                        "影片", videoCount.toString(), Icons.Default.Movie, Modifier.weight(1f),
                    )
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    TextButton(onClick = onMediaManagementInfo) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            null,
                            tint = if (canManageMedia) Mint else Subtle,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (canManageMedia) "媒體管理：已授權" else "設定媒體管理權限",
                            color = Subtle,
                        )
                    }
                }
            }

            if (browserMode == BrowserMode.FOLDERS) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(35.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = currentFolderKey != null,
                        onClick = {
                            onFolderChange(currentFolderKey?.let { MediaFolders.parentKey(it) })
                        },
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "返回上一層",
                            tint = if (currentFolderKey == null) Subtle else Accent,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Text(
                        currentFolderKey?.let { MediaFolders.folderLabel(it) } ?: "手機目錄",
                        modifier = Modifier.weight(1f),
                        color = Mint,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    currentFolderKey?.let { folder ->
                        IconButton(
                            onClick = { onToggleFavorite(folder) },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                if (folder in favoriteFolders) Icons.Default.Star
                                else Icons.Default.StarBorder,
                                if (folder in favoriteFolders) "取消收藏此目錄"
                                else "將此目錄加入最愛",
                                tint = if (folder in favoriteFolders) Color(0xFFFFCD69) else Subtle,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                    }
                }
            }

            if (browserMode != BrowserMode.FAVORITES) {
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    FilterPill("全部", filter == MediaFilter.ALL) { onFilter(MediaFilter.ALL) }
                    FilterPill("圖片", filter == MediaFilter.PHOTOS) { onFilter(MediaFilter.PHOTOS) }
                    FilterPill("影片", filter == MediaFilter.VIDEOS) { onFilter(MediaFilter.VIDEOS) }
                    Spacer(Modifier.weight(1f))
                    Box {
                        IconButton(
                            onClick = { sortMenu = true },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                if (sort == MediaSort.SIZE) Icons.Default.Storage
                                else Icons.Default.CalendarMonth,
                                when (sort) {
                                    MediaSort.MODIFIED -> "按修改時間排序"
                                    MediaSort.CREATED -> "按建立時間排序"
                                    MediaSort.SIZE -> "按檔案大小排序"
                                },
                                tint = Accent,
                                modifier = Modifier.size(21.dp),
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenu,
                            onDismissRequest = { sortMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("修改時間（預設）") },
                                leadingIcon = { Icon(Icons.Default.Update, null) },
                                onClick = { onSort(MediaSort.MODIFIED); sortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("建立時間") },
                                leadingIcon = { Icon(Icons.Default.CalendarMonth, null) },
                                onClick = { onSort(MediaSort.CREATED); sortMenu = false },
                            )
                            DropdownMenuItem(
                                text = { Text("檔案大小") },
                                leadingIcon = { Icon(Icons.Default.Storage, null) },
                                onClick = { onSort(MediaSort.SIZE); sortMenu = false },
                            )
                        }
                    }
                    IconButton(onClick = onReverse, modifier = Modifier.size(34.dp)) {
                        Icon(
                            if (ascending) Icons.Default.ArrowUpward
                            else Icons.Default.ArrowDownward,
                            if (ascending) "正排" else "倒排",
                            tint = Accent,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
                Text(
                    folders.size.toString() + " 個最愛資料夾",
                    style = MaterialTheme.typography.labelSmall,
                    color = Subtle,
                )
            }
        }
        when {
            !hasPermission -> MessageState(
                icon = Icons.Default.PermMedia,
                title = "開啟你的媒體庫",
                description = "允許讀取照片與影片後，即可瀏覽手機裡的內容。也能只授權選取的項目。",
                action = "授權照片與影片",
                onClick = onPermission,
            )
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Accent)
            }
            browserMode == BrowserMode.FAVORITES && folders.isEmpty() -> MessageState(
                icon = Icons.Default.StarBorder,
                title = "還沒有最愛資料夾",
                description = "在資料夾旁點星星，就能把常用目錄收藏在這裡。",
                action = "瀏覽資料夾",
                onClick = { onBrowserModeChange(BrowserMode.FOLDERS) },
            )
            visible.isEmpty() && folders.isEmpty() -> MessageState(
                icon = if (browserMode == BrowserMode.FOLDERS) Icons.Default.FolderOpen else Icons.Default.PhotoLibrary,
                title = if (allMedia.isEmpty()) "目前沒有可讀取的媒體"
                        else if (browserMode == BrowserMode.FOLDERS) "這個資料夾沒有可讀取的圖片或影片"
                        else "找不到符合條件的檔案",
                description = if (allMedia.isEmpty()) "你可以重新選取允許瀏覽的照片與影片。"
                              else "試試其他篩選，或重新掃描手機媒體。",
                action = if (allMedia.isEmpty()) "管理媒體權限" else "重新掃描",
                onClick = if (allMedia.isEmpty()) onPermission else onRefresh,
            )
            listMode -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    // Leave a narrow independent touch lane for the draggable scrollbar.
                    contentPadding = PaddingValues(start = 16.dp, end = 42.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                items(folders, key = { "folder-${it.key}" }) { folder ->
                    FolderListItem(
                        folder = folder,
                        favorite = folder.key in favoriteFolders,
                        loader = loader,
                        onOpen = { if (folder.available) onFolderChange(folder.key) },
                        onToggleFavorite = { onToggleFavorite(folder.key) },
                    )
                }
                if (sort != MediaSort.SIZE) {
                    dateGroups.forEach { group ->
                        item(key = "date-${group.key}") {
                            DateGroupHeader(group.label, group.entries.size)
                        }
                        items(
                            items = group.entries,
                            key = { "${it.value.isVideo}-${it.value.id}" },
                        ) { entry ->
                            MediaListItem(
                                entry.value, loader,
                                selected = entry.value.uri.toString() in selectedUris,
                                onClick = { tapItem(entry.index) },
                                onLongClick = { longTapItem(entry.index) },
                            )
                        }
                    }
                } else {
                    itemsIndexed(visible, key = { _, m -> "${m.isVideo}-${m.id}" }) { index, item ->
                        MediaListItem(
                            item, loader,
                            selected = item.uri.toString() in selectedUris,
                            onClick = { tapItem(index) },
                            onLongClick = { longTapItem(index) },
                        )
                    }
                }
                }
                MediaListScrollbar(
                    listState = listState,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                state = gridState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                items(
                    items = folders,
                    key = { "folder-${it.key}" },
                    span = { GridItemSpan(maxLineSpan) },
                ) { folder ->
                    FolderListItem(
                        folder = folder,
                        favorite = folder.key in favoriteFolders,
                        loader = loader,
                        onOpen = { if (folder.available) onFolderChange(folder.key) },
                        onToggleFavorite = { onToggleFavorite(folder.key) },
                    )
                }
                if (sort != MediaSort.SIZE) {
                    dateGroups.forEach { group ->
                        item(key = "date-${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                            DateGroupHeader(group.label, group.entries.size)
                        }
                        items(
                            items = group.entries,
                            key = { "${it.value.isVideo}-${it.value.id}" },
                        ) { entry ->
                            MediaGridItem(
                                entry.value, loader,
                                selected = entry.value.uri.toString() in selectedUris,
                                onClick = { tapItem(entry.index) },
                                onLongClick = { longTapItem(entry.index) },
                            )
                        }
                    }
                } else {
                    itemsIndexed(visible, key = { _, m -> "${m.isVideo}-${m.id}" }) { index, item ->
                        MediaGridItem(
                            item, loader,
                            selected = item.uri.toString() in selectedUris,
                            onClick = { tapItem(index) },
                            onLongClick = { longTapItem(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserIconTab(
    title: String,
    image: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.fillMaxHeight()
            .background(
                if (selected) Accent.copy(alpha = 0.27f) else Color.Transparent,
                RoundedCornerShape(12.dp),
            )
            .semantics { stateDescription = if (selected) "已選取" else "未選取" },
    ) {
        Icon(
            image,
            contentDescription = title,
            tint = if (selected) Mint else Subtle,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun FolderListItem(
    folder: MediaFolder,
    favorite: Boolean,
    loader: ImageLoader,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Panel, RoundedCornerShape(15.dp))
            .clickable(enabled = folder.available, onClick = onOpen)
            .padding(start = 9.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(64.dp)
                .background(Color(0xFF293A58), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            folder.preview?.let { sample ->
                Thumb(sample, loader, Modifier.fillMaxSize())
            }
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = Mint,
                modifier = Modifier.align(Alignment.BottomEnd)
                    .background(Color(0xDD162039), RoundedCornerShape(6.dp))
                    .padding(3.dp).size(21.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, color = Color.White, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(
                if (folder.available) "${folder.mediaCount} 個圖片／影片（含子目錄）"
                else "暫時無法存取，仍可取消收藏",
                color = Subtle, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                MediaFolders.folderLabel(folder.key),
                color = Mint.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (favorite) Icons.Default.Star else Icons.Default.StarBorder,
                if (favorite) "取消收藏資料夾" else "收藏資料夾",
                tint = if (favorite) Color(0xFFFFCD69) else Subtle,
            )
        }
        if (folder.available) {
            Icon(Icons.Default.ChevronRight, null, tint = Subtle, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun DateGroupHeader(label: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 13.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.CalendarToday,
            contentDescription = null,
            tint = Mint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "$count 個項目",
            color = Subtle,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Color(0xFF33415C),
            thickness = 1.dp,
        )
    }
}

@Composable
private fun MetricChip(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Panel.copy(alpha = .8f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Mint, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, color = Subtle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (active) Accent else Panel, RoundedCornerShape(30.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (active) Background else Subtle,
        )
    }
}

@Composable
private fun Thumb(media: LocalMedia, loader: ImageLoader, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(media.uri) {
        ImageRequest.Builder(context)
            .data(media.uri)
            .apply { if (media.isVideo) videoFrameMillis(1000L) }
            .crossfade(true)
            .build()
    }
    Box(modifier.background(Color(0xFF26344F)), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = request,
            imageLoader = loader,
            contentDescription = media.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.isVideo) {
            Row(
                Modifier.align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = .75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(13.dp), tint = Mint)
                Text(durationLabel(media.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGridItem(
    media: LocalMedia,
    loader: ImageLoader,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .background(Panel, RoundedCornerShape(14.dp))
            .border(2.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Box {
            Thumb(media, loader, Modifier.fillMaxWidth().aspectRatio(1f))
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    "已選取",
                    tint = Mint,
                    modifier = Modifier.align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Panel, CircleShape)
                        .size(25.dp),
                )
            }
        }
        Text(
            media.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall, color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaListItem(
    media: LocalMedia,
    loader: ImageLoader,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(if (selected) Accent.copy(alpha = .2f) else Panel, RoundedCornerShape(14.dp))
            .border(2.dp, if (selected) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(media, loader, Modifier.size(84.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.name, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                dateLabel(media.createdAtMs) + " · " + MediaLibrary.readableSize(media.bytes),
                color = Subtle, style = MaterialTheme.typography.labelMedium,
            )
            Text(if (media.isVideo) "影片" else "圖片",
                color = Mint, style = MaterialTheme.typography.labelSmall)
        }
        Icon(
            if (selected) Icons.Default.CheckCircle else Icons.Default.ChevronRight,
            if (selected) "已選取" else null,
            tint = if (selected) Mint else Subtle,
        )
    }
}

@Composable
private fun MessageState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    action: String,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(72.dp))
        Spacer(Modifier.height(18.dp))
        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(description, color = Subtle, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onClick) { Text(action) }
    }
}

internal fun dateLabel(timeMs: Long): String =
    if (timeMs <= 0L) "未知日期"
    else SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(timeMs))

internal fun durationLabel(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0L) / 1000L)
    val hours = seconds / 3600
    val minutes = (seconds / 60) % 60
    val remainder = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
           else "%02d:%02d".format(minutes, remainder)
}
