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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
    val galleryPrefs = remember(context) {
        context.getSharedPreferences("my_player_gallery_position", android.content.Context.MODE_PRIVATE)
    }
    var filter by remember {
        mutableStateOf(runCatching {
            MediaFilter.valueOf(galleryPrefs.getString("filter", "ALL") ?: "ALL")
        }.getOrDefault(MediaFilter.ALL))
    }
    var sort by remember {
        mutableStateOf(runCatching {
            MediaSort.valueOf(galleryPrefs.getString("sort", "CREATED") ?: "CREATED")
        }.getOrDefault(MediaSort.CREATED))
    }
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
            ViewerScreen(
                media = viewerList[viewerIndex],
                position = viewerIndex,
                total = viewerList.size,
                loader = loader,
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
        if (sort == MediaSort.CREATED) MediaLibrary.groupByCreatedDay(visible)
        else emptyList()
    }
    LaunchedEffect(returnToMediaUri, visible, folders, sort, listMode, loading) {
        val targetUri = returnToMediaUri ?: return@LaunchedEffect
        if (loading) return@LaunchedEffect
        val mediaIndex = visible.indexOfFirst { it.uri.toString() == targetUri }
        if (mediaIndex >= 0) {
            val dayKeys = if (sort == MediaSort.CREATED) {
                visible.map { MediaLibrary.createdDayKey(it.createdAtMs) }
            } else {
                List(visible.size) { "" }
            }
            val itemIndex = galleryLazyItemIndex(
                mediaIndex = mediaIndex,
                dayKeys = dayKeys,
                folderRows = folders.size,
                groupedByDay = sort == MediaSort.CREATED,
            )
            if (itemIndex >= 0) {
                if (listMode) listState.scrollToItem(itemIndex)
                else gridState.scrollToItem(itemIndex)
            }
        }
        onReturnPositionHandled()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF192947), Background, Background),
                ),
            ),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 19.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(42.dp).background(Accent.copy(alpha = .16f), RoundedCornerShape(13.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.SmartDisplay, contentDescription = null, tint = Accent)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("MY PLAYER", style = MaterialTheme.typography.labelMedium, color = Mint, fontWeight = FontWeight.Bold)
                    Text("我的影像庫", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                }
                IconButton(onClick = onSearchToggle) {
                    Icon(
                        if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                        if (searchExpanded) "收合搜尋" else "展開搜尋",
                        tint = if (searchExpanded) Accent else Subtle,
                    )
                }
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "重新掃描", tint = Subtle)
                }
                if (Build.VERSION.SDK_INT >= 31) {
                    IconButton(onClick = onMediaManagementInfo) {
                        Icon(
                            Icons.Default.AdminPanelSettings,
                            "設定媒體管理權限",
                            tint = if (canManageMedia) Mint else Subtle,
                        )
                    }
                }
            }
            if (selectionMode) {
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth()
                        .background(Panel, RoundedCornerShape(14.dp))
                        .padding(start = 9.dp, end = 3.dp, top = 3.dp, bottom = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "已選 " + selectedUris.size + " 個",
                        modifier = Modifier.weight(1f),
                        color = Mint,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(enabled = !deleting && visible.isNotEmpty(), onClick = onSelectAll) {
                        Text("全選")
                    }
                    IconButton(enabled = !deleting, onClick = onExitSelection) {
                        Icon(Icons.Default.Close, "取消多選", tint = Subtle)
                    }
                    Button(
                        enabled = !deleting && selectedUris.isNotEmpty(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        onClick = onDeleteSelected,
                    ) {
                        Icon(Icons.Default.DeleteOutline, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(if (deleting) "刪除中" else "刪除")
                    }
                }
            }

            if (searchExpanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜尋照片或影片名稱", color = Subtle) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Subtle) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "清除搜尋文字") } }
                    } else null,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Panel.copy(alpha = .7f),
                        unfocusedContainerColor = Panel.copy(alpha = .7f),
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF30405E),
                    ),
                )
            }
            Spacer(Modifier.height(17.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricChip("照片", photoCount.toString(), Icons.Default.Image, Modifier.weight(1f))
                MetricChip("影片", videoCount.toString(), Icons.Default.Movie, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                BrowserTab("全部影像", Icons.Default.PhotoLibrary, browserMode == BrowserMode.ALL) {
                    onBrowserModeChange(BrowserMode.ALL)
                }
                BrowserTab("資料夾", Icons.Default.Folder, browserMode == BrowserMode.FOLDERS) {
                    onBrowserModeChange(BrowserMode.FOLDERS)
                }
                BrowserTab("最愛", Icons.Default.Star, browserMode == BrowserMode.FAVORITES) {
                    onBrowserModeChange(BrowserMode.FAVORITES)
                }
            }
            if (browserMode == BrowserMode.FOLDERS) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = currentFolderKey != null,
                        onClick = { onFolderChange(currentFolderKey?.let { MediaFolders.parentKey(it) }) },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(Icons.Default.ArrowBack, "返回上一層", tint = if (currentFolderKey == null) Subtle else Accent)
                    }
                    Text(
                        currentFolderKey?.let { MediaFolders.folderLabel(it) } ?: "手機目錄",
                        modifier = Modifier.weight(1f),
                        color = Mint,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    currentFolderKey?.let { folder ->
                        IconButton(onClick = { onToggleFavorite(folder) }) {
                            Icon(
                                if (folder in favoriteFolders) Icons.Default.Star else Icons.Default.StarBorder,
                                if (folder in favoriteFolders) "取消收藏此目錄" else "將此目錄加入最愛",
                                tint = if (folder in favoriteFolders) Color(0xFFFFCD69) else Subtle,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(11.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("全部", filter == MediaFilter.ALL) { onFilter(MediaFilter.ALL) }
                FilterPill("圖片", filter == MediaFilter.PHOTOS) { onFilter(MediaFilter.PHOTOS) }
                FilterPill("影片", filter == MediaFilter.VIDEOS) { onFilter(MediaFilter.VIDEOS) }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (browserMode) {
                        BrowserMode.ALL -> "共 ${visible.size} 個項目"
                        BrowserMode.FOLDERS -> "${folders.size} 個資料夾 · ${visible.size} 個檔案"
                        BrowserMode.FAVORITES -> "${folders.size} 個最愛資料夾"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Subtle,
                    modifier = Modifier.weight(1f),
                )
                var sortMenu by remember { mutableStateOf(false) }
                if (browserMode != BrowserMode.FAVORITES) Box {
                    TextButton(onClick = { sortMenu = true }) {
                        Icon(if (sort == MediaSort.CREATED) Icons.Default.CalendarMonth else Icons.Default.Storage, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (sort == MediaSort.CREATED) "建立時間" else "檔案大小")
                    }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
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
                if (browserMode != BrowserMode.FAVORITES) IconButton(onClick = onReverse, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        if (ascending) "正排" else "倒排",
                        tint = Accent,
                    )
                }
                if (browserMode != BrowserMode.FAVORITES) IconButton(onClick = onToggleMode, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (listMode) Icons.Default.GridView else Icons.Default.ViewList,
                        if (listMode) "切換網格" else "切換清單",
                        tint = Subtle,
                    )
                }
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
            listMode -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
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
                if (sort == MediaSort.CREATED) {
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
                if (sort == MediaSort.CREATED) {
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
private fun BrowserTab(
    title: String,
    image: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(if (selected) Accent.copy(alpha = 0.22f) else Panel, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(image, null, tint = if (selected) Mint else Subtle, modifier = Modifier.size(17.dp))
        Text(title, color = if (selected) Color.White else Subtle,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelMedium)
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
            .padding(horizontal = 24.dp, vertical = 10.dp),
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
