package com.nichita.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import com.nichita.gallery.GalleryApplication
import com.nichita.gallery.R
import com.nichita.gallery.data.*
import kotlin.math.ceil

@Composable
fun GalleryScreen(model: GalleryViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as GalleryApplication
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refresh(force = true) }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> model.start()
                Lifecycle.Event.ON_STOP -> model.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.start()
        onDispose { lifecycle.removeObserver(observer); model.stop() }
    }
    GalleryContent(state, app.thumbnails, { app.viewerImages }, model::selectTab, { model.refresh(force = true) }, model::setColumns,
        requestAccess = { launcher.launch(MediaPermissions.requested()) },
        openSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null))) })
}

@Composable
internal fun GalleryContent(
    state: GalleryUiState, thumbnails: ImageLoader, viewer: () -> ImageLoader,
    selectTab: (GalleryTab) -> Unit, refresh: () -> Unit, setColumns: (Int) -> Unit,
    requestAccess: () -> Unit, openSettings: () -> Unit
) {
    var albumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pinching by remember { mutableStateOf(false) }
    val preview = remember { mutableFloatStateOf(1f) }
    val album = remember(state.library, albumId) { state.library.albums.find { it.id == albumId } }
    val visibleItems = if (state.tab == GalleryTab.Albums && album != null) album.items else state.library.items
    val photoScroll = rememberLazyGridState()
    val albumsScroll = rememberLazyGridState()
    val albumScroll = rememberLazyGridState()
    BackHandler(enabled = albumId != null && selectedId == null) { albumId = null }
    LaunchedEffect(state.library, state.loading) {
        if (!state.loading && albumId != null && album == null) albumId = null
        if (!state.loading && selectedId != null && visibleItems.none { it.id == selectedId }) selectedId = null
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            GalleryTab.entries.forEach { tab ->
                NavigationBarItem(selected = state.tab == tab, onClick = { albumId = null; selectTab(tab) },
                    icon = { Icon(painterResource(if (tab == GalleryTab.Photos) R.drawable.ic_photos else R.drawable.ic_albums), null, Modifier.size(24.dp)) },
                    label = { Text(stringResource(if (tab == GalleryTab.Photos) R.string.photos else R.string.albums)) },
                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer))
            }
        }
    }) { insets ->
        BoxWithConstraints(Modifier.padding(insets).fillMaxSize()) {
            val tab = state.tab
            val openAlbum = if (tab == GalleryTab.Albums) album else null
            val mediaGrid = tab == GalleryTab.Photos || openAlbum != null
            val columns = if (mediaGrid) state.columns else 3
            val scroll = if (tab == GalleryTab.Photos) photoScroll else if (openAlbum != null) albumScroll else albumsScroll
            val sections = openAlbum?.sections ?: state.library.sections
            val density = LocalDensity.current
            val gap = 3.dp
            val headerHeight = with(density) { (MaterialTheme.typography.titleMedium.lineHeight.toDp() + 24.dp).coerceAtLeast(52.dp) }
            val cellPixels = with(density) { ceil(((maxWidth - 24.dp - gap * (columns - 1)).toPx() / columns).toDouble()).toInt().coerceAtLeast(1) }
            var headingPixels by remember { mutableIntStateOf(0) }
            val map = remember(sections, columns, cellPixels, headerHeight, headingPixels, density) {
                with(density) { GalleryScrollMap(sections.map { it.items.size }, columns, cellPixels.toFloat(),
                    headerHeight.toPx(), headingPixels.toFloat(), gap.toPx(), 24.dp.toPx()) }
            }
            val columnDescription = stringResource(R.string.grid_columns, columns)
            LazyVerticalGrid(columns = GridCells.Fixed(columns), state = scroll,
                modifier = Modifier.fillMaxSize().testTag("media_grid").semantics { contentDescription = columnDescription }
                    .graphicsLayer { scaleX = preview.floatValue; scaleY = preview.floatValue }
                    .gridPinch(columns, mediaGrid, { preview.floatValue = it }, { pinching = it }) { value ->
                        if (value != columns) {
                            val anchor = scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "media" }
                            val index = anchor?.index ?: scroll.firstVisibleItemIndex
                            val offset = if (anchor?.index == scroll.firstVisibleItemIndex) scroll.firstVisibleItemScrollOffset else 0
                            scroll.requestScrollToItem(index, offset)
                            setColumns(value)
                        }
                    },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    Column(Modifier.onSizeChanged { headingPixels = it.height }.padding(start = 12.dp, top = 64.dp, end = 12.dp, bottom = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (tab == GalleryTab.Photos) R.string.photos else R.string.albums),
                                style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = openSettings) { Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.app_settings)) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(if (state.access.isFull) R.string.local_library else R.string.limited_library),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.access.canRead && !state.access.isFull) TextButton(onClick = requestAccess) { Text(stringResource(R.string.manage_access)) }
                    }
                }
                when {
                    !state.access.canRead -> item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryMessage(R.string.permission_title, R.string.permission_message) {
                            Button(onClick = requestAccess) { Text(stringResource(R.string.allow_access)) }
                            TextButton(onClick = openSettings) { Text(stringResource(R.string.app_settings)) }
                        }
                    }
                    state.error -> item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryMessage(R.string.load_error, R.string.load_error_message) {
                            Button(onClick = refresh) { Text(stringResource(R.string.retry)) }
                            TextButton(onClick = openSettings) { Text(stringResource(R.string.app_settings)) }
                        }
                    }
                    state.loading && state.library.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    state.library.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryMessage(R.string.empty_title, R.string.empty_message) { TextButton(onClick = refresh) { Text(stringResource(R.string.refresh)) } }
                    }
                    mediaGrid -> {
                        if (openAlbum != null) item(key = "album_title", span = { GridItemSpan(maxLineSpan) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { albumId = null }) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.all_albums)) }
                                Text(openAlbum.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                        }
                        sections.forEach { section ->
                            item(key = "month:${section.month}", span = { GridItemSpan(maxLineSpan) }, contentType = "month") {
                                Box(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                                    Text(section.month, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            items(section.items, key = { it.id }, contentType = { "media" }) { media ->
                                MediaThumbnail(media, thumbnails, cellPixels, columns,
                                    Modifier.clip(RoundedCornerShape(6.dp)).clickable(enabled = !pinching) { selectedId = media.id })
                            }
                        }
                    }
                    else -> items(state.library.albums, key = { it.id }, contentType = { "album" }) { entry ->
                        Column(Modifier.padding(bottom = 12.dp)) {
                            MediaThumbnail(entry.items.first(), thumbnails, cellPixels, 3,
                                Modifier.clip(RoundedCornerShape(8.dp)).clickable { albumScroll.requestScrollToItem(0); albumId = entry.id })
                            Text(entry.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                            Text("${entry.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
            if (tab == GalleryTab.Photos && state.access.canRead && !state.error && sections.isNotEmpty()) {
                GalleryScrollControls(photoScroll, map, sections.map { it.month }, Modifier.fillMaxSize())
            }
        }
    }
    val selectedIndex = remember(visibleItems, selectedId) { if (selectedId == null) -1 else visibleItems.indexOfFirst { it.id == selectedId } }
    if (selectedIndex >= 0) MediaViewer(visibleItems, selectedId!!, viewer(), onCurrentItem = { selectedId = it }, onClose = { selectedId = null })
}

@Composable
private fun LibraryMessage(title: Int, message: Int, actions: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        actions()
    }
}
