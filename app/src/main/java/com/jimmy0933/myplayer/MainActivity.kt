package com.jimmy0933.myplayer

import android.Manifest
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
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
    var filter by remember { mutableStateOf(MediaFilter.ALL) }
    var sort by remember { mutableStateOf(MediaSort.CREATED) }
    var ascending by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var listMode by remember { mutableStateOf(false) }
    var browserMode by remember { mutableStateOf(BrowserMode.ALL) }
    var currentFolderKey by remember { mutableStateOf<String?>(null) }
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
    val galleryListState = rememberLazyListState()
    val galleryGridState = rememberLazyGridState()
    var openedMediaUri by remember { mutableStateOf<String?>(null) }
    var pendingReturnUri by remember { mutableStateOf<String?>(null) }
    fun closeViewer() {
        // Keep the existing exact offset unless the user swiped to another item.
        val lastViewedUri = viewerItems?.getOrNull(viewerIndex)?.uri?.toString()
        pendingReturnUri = lastViewedUri?.takeIf { it != openedMediaUri }
        viewerItems = null
        openedMediaUri = null
    }
    var deleteConfirmation by remember { mutableStateOf<LocalMedia?>(null) }
    var deletePending by remember { mutableStateOf<LocalMedia?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }

    // Let the sensor rotate the viewer even when Android auto-rotate is disabled.
    // Keep the media library upright; Compose and ExoPlayer survive orientation
    // changes because the activity handles orientation/screenSize config changes.
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

    // Start at the top of a new folder/search/sort, but not after closing a viewer.
    LaunchedEffect(browserMode, currentFolderKey, filter, sort, ascending, query) {
        galleryListState.scrollToItem(0)
        galleryGridState.scrollToItem(0)
    }

    fun removeDeletedMedia(target: LocalMedia) {
        allMedia = allMedia.filterNot { it.uri == target.uri }
        val openItems = viewerItems
        if (openItems != null) {
            val remaining = openItems.filterNot { it.uri == target.uri }
            if (remaining.isEmpty()) {
                viewerItems = null
                openedMediaUri = null
                pendingReturnUri = null
                viewerIndex = 0
            } else {
                viewerIndex = viewerIndex.coerceIn(0, remaining.lastIndex)
                viewerItems = remaining
            }
        }
        refresh++ // Reconcile with MediaStore, including dates and count chips.
    }

    fun directlyDeleteMedia(target: LocalMedia) {
        try {
            val count = context.contentResolver.delete(target.uri, null, null)
            if (count > 0) {
                removeDeletedMedia(target)
            } else {
                deleteError = "找不到此檔案，可能已被其他應用程式刪除。"
                refresh++
            }
        } catch (_: SecurityException) {
            deleteError = "沒有刪除這個檔案的權限，請確認系統授權。"
        } catch (_: Exception) {
            deleteError = "刪除失敗，請稍後重試。"
        } finally {
            deleting = false
        }
    }

    // Android 11+ performs the deletion in the system confirmation UI.
    // Android 10 grants single-item write access, after which deletion is retried.
    val systemDeleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val target = deletePending
        deletePending = null
        deleting = false
        if (result.resultCode == Activity.RESULT_OK && target != null) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                directlyDeleteMedia(target)
            } else {
                removeDeletedMedia(target)
            }
        }
        // A cancelled system prompt must leave the original file and list untouched.
    }
    val legacyWriteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val target = deletePending
        deletePending = null
        deleting = false
        if (granted && target != null) directlyDeleteMedia(target)
        else if (!granted) deleteError = "刪除需要儲存空間寫入權限。"
    }
    fun requestMediaDeletion(target: LocalMedia) {
        deleting = true
        deleteError = null
        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    deletePending = target
                    val request = MediaStore.createDeleteRequest(
                        context.contentResolver,
                        listOf(target.uri),
                    )
                    systemDeleteLauncher.launch(
                        IntentSenderRequest.Builder(request.intentSender).build(),
                    )
                }
                Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> {
                    try {
                        val count = context.contentResolver.delete(target.uri, null, null)
                        if (count > 0) removeDeletedMedia(target)
                        else {
                            deleteError = "找不到此檔案，可能已被刪除。"
                            refresh++
                        }
                        deleting = false
                    } catch (e: RecoverableSecurityException) {
                        deletePending = target
                        systemDeleteLauncher.launch(
                            IntentSenderRequest.Builder(
                                e.userAction.actionIntent.intentSender,
                            ).build(),
                        )
                    }
                }
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED -> {
                    deletePending = target
                    legacyWriteLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                else -> directlyDeleteMedia(target)
            }
        } catch (_: Exception) {
            deletePending = null
            deleting = false
            deleteError = "無法啟動刪除流程，請稍後再試。"
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refresh++ }

    DisposableEffect(activity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++
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
                onDelete = { deleteConfirmation = viewerList[viewerIndex] },
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
            browserMode = browserMode,
            currentFolderKey = currentFolderKey,
            favoriteFolders = favoriteFolders,
            onBrowserModeChange = { selected ->
                browserMode = selected
                currentFolderKey = null
            },
            onFolderChange = { key ->
                browserMode = BrowserMode.FOLDERS
                currentFolderKey = key
            },
            onToggleFavorite = ::toggleFavorite,
            loading = loading,
            hasPermission = MediaLibrary.hasAccess(context),
            loader = loader,
            filter = filter,
            onFilter = { filter = it },
            sort = sort,
            onSort = { sort = it },
            ascending = ascending,
            onReverse = { ascending = !ascending },
            query = query,
            onQuery = { query = it },
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
                openedMediaUri = visible.getOrNull(index)?.uri?.toString()
                pendingReturnUri = null
                viewerItems = visible.toList()
                viewerIndex = index
            },
        )
    }
    deleteConfirmation?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteConfirmation = null },
            title = { Text("刪除此${if (target.isVideo) "影片" else "圖片"}？") },
            text = {
                Text(
                    "確定要從手機刪除「${target.name}」嗎？此操作會刪除原始檔案，無法在播放器內復原。",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        deleteConfirmation = null
                        requestMediaDeletion(target)
                    },
                ) { Text("刪除", color = Color(0xFFFF8792)) }
            },
            dismissButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = { deleteConfirmation = null },
                ) { Text("取消") }
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
    BackHandler(enabled = browserMode != BrowserMode.ALL) {
        if (browserMode == BrowserMode.FOLDERS && currentFolderKey != null) {
            onFolderChange(MediaFolders.parentKey(currentFolderKey))
        } else {
            onBrowserModeChange(BrowserMode.ALL)
        }
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
                            MediaListItem(entry.value, loader) { onOpen(entry.index) }
                        }
                    }
                } else {
                    itemsIndexed(visible, key = { _, m -> "${m.isVideo}-${m.id}" }) { index, item ->
                        MediaListItem(item, loader) { onOpen(index) }
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
                            MediaGridItem(entry.value, loader) { onOpen(entry.index) }
                        }
                    }
                } else {
                    itemsIndexed(visible, key = { _, m -> "${m.isVideo}-${m.id}" }) { index, item ->
                        MediaGridItem(item, loader) { onOpen(index) }
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

@Composable
private fun MediaGridItem(media: LocalMedia, loader: ImageLoader, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .background(Panel, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Thumb(
            media, loader,
            Modifier.fillMaxWidth().aspectRatio(1f),
        )
        Text(
            media.name,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun MediaListItem(media: LocalMedia, loader: ImageLoader, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(media, loader, Modifier.size(84.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(media.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(5.dp))
            Text(
                "${dateLabel(media.createdAtMs)}  ·  ${MediaLibrary.readableSize(media.bytes)}",
                color = Subtle,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(if (media.isVideo) "影片" else "圖片", color = Mint, style = MaterialTheme.typography.labelSmall)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Subtle)
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
