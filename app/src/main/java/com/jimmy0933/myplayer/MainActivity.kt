package com.jimmy0933.myplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
    var listMode by remember { mutableStateOf(false) }
    var viewerItems by remember { mutableStateOf<List<LocalMedia>?>(null) }
    var viewerIndex by remember { mutableIntStateOf(0) }

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
    val visible = remember(allMedia, filter, sort, ascending, query) {
        MediaLibrary.sorted(allMedia, filter, sort, ascending, query)
    }

    if (viewerItems != null) {
        BackHandler { viewerItems = null }
        val viewerList = viewerItems.orEmpty()
        if (viewerIndex in viewerList.indices) {
            ViewerScreen(
                media = viewerList[viewerIndex],
                position = viewerIndex,
                total = viewerList.size,
                loader = loader,
                onBack = { viewerItems = null },
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
            listMode = listMode,
            onToggleMode = { listMode = !listMode },
            onRefresh = { refresh++ },
            onPermission = { permissionLauncher.launch(MediaLibrary.requestedPermissions()) },
            onOpen = { index ->
                viewerItems = visible.toList()
                viewerIndex = index
            },
        )
    }
}

@Composable
private fun GalleryScreen(
    allMedia: List<LocalMedia>,
    visible: List<LocalMedia>,
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
    listMode: Boolean,
    onToggleMode: () -> Unit,
    onRefresh: () -> Unit,
    onPermission: () -> Unit,
    onOpen: (Int) -> Unit,
) {
    val photoCount = remember(allMedia) { allMedia.count { !it.isVideo } }
    val videoCount = remember(allMedia) { allMedia.count { it.isVideo } }
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
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Default.Refresh, "重新掃描", tint = Subtle)
                }
            }

            Spacer(Modifier.height(19.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricChip("照片", photoCount.toString(), Icons.Default.Image, Modifier.weight(1f))
                MetricChip("影片", videoCount.toString(), Icons.Default.Movie, Modifier.weight(1f))
            }

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜尋照片或影片名稱", color = Subtle) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Subtle) },
                trailingIcon = if (query.isNotEmpty()) {
                    { IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Close, "清除搜尋") } }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(17.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Panel.copy(alpha = .7f),
                    unfocusedContainerColor = Panel.copy(alpha = .7f),
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = Color(0xFF30405E),
                ),
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("全部", filter == MediaFilter.ALL) { onFilter(MediaFilter.ALL) }
                FilterPill("圖片", filter == MediaFilter.PHOTOS) { onFilter(MediaFilter.PHOTOS) }
                FilterPill("影片", filter == MediaFilter.VIDEOS) { onFilter(MediaFilter.VIDEOS) }
            }
            Spacer(Modifier.height(13.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "共 ${visible.size} 個項目",
                    style = MaterialTheme.typography.labelLarge,
                    color = Subtle,
                    modifier = Modifier.weight(1f),
                )
                var sortMenu by remember { mutableStateOf(false) }
                Box {
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
                IconButton(onClick = onReverse, modifier = Modifier.size(38.dp)) {
                    Icon(
                        if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        if (ascending) "正排" else "倒排",
                        tint = Accent,
                    )
                }
                IconButton(onClick = onToggleMode, modifier = Modifier.size(38.dp)) {
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
            visible.isEmpty() -> MessageState(
                icon = Icons.Default.PhotoLibrary,
                title = if (allMedia.isEmpty()) "目前沒有可讀取的媒體" else "找不到符合條件的檔案",
                description = if (allMedia.isEmpty()) "你可以重新選取允許瀏覽的照片與影片。"
                              else "試試其他篩選或搜尋條件。",
                action = if (allMedia.isEmpty()) "管理媒體權限" else "重新掃描",
                onClick = if (allMedia.isEmpty()) onPermission else onRefresh,
            )
            listMode -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(visible, key = { _, m -> "${m.isVideo}-${m.id}" }) { index, item ->
                    MediaListItem(item, loader) { onOpen(index) }
                }
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp),
            ) {
                itemsIndexed(visible, key = { _, m -> "${m.isVideo}-${m.id}" }) { index, item ->
                    MediaGridItem(item, loader) { onOpen(index) }
                }
            }
        }
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
