package com.nichita.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.video.VideoFrameDecoder
import com.nichita.gallery.R
import com.nichita.gallery.data.*
import android.content.Context
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun GalleryScreen(model: GalleryViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refresh() }
    val loader = remember(context.applicationContext) {
        ImageLoader.Builder(context.applicationContext)
            .components { add(VideoFrameDecoder.Factory()) }
            .diskCache(null)
            .build()
    }
    DisposableEffect(loader) { onDispose { loader.shutdown() } }
    DisposableEffect(lifecycle, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.start()
                Lifecycle.Event.ON_RESUME -> model.refresh()
                Lifecycle.Event.ON_STOP -> model.stop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) model.start()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) model.refresh()
        onDispose { lifecycle.removeObserver(observer); model.stop() }
    }
    GalleryContent(state, loader, model::selectTab, model::refresh,
        requestAccess = { launcher.launch(MediaPermissions.requested()) },
        openSettings = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)))
        })
}

@Composable
private fun GalleryContent(
    state: GalleryUiState,
    loader: ImageLoader,
    selectTab: (GalleryTab) -> Unit,
    refresh: () -> Unit,
    requestAccess: () -> Unit,
    openSettings: () -> Unit
) {
    var albumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Grid settings
    val context = LocalContext.current

    val preferences = remember(context) {
        context.getSharedPreferences(
            "gallery_settings",
            Context.MODE_PRIVATE
        )
    }

    var gridColumns by remember {
        mutableIntStateOf(
            preferences.getInt("grid_columns", 3).coerceIn(2, 8)
        )
    }
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
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                GalleryTab.entries.forEach { tab ->
                    val label = stringResource(if (tab == GalleryTab.Photos) R.string.photos else R.string.albums)
                    NavigationBarItem(
                        selected = state.tab == tab,
                        onClick = { albumId = null; selectTab(tab) },
                        icon = { Icon(painterResource(if (tab == GalleryTab.Photos) R.drawable.ic_photos else R.drawable.ic_albums), contentDescription = null) },
                        label = { Text(label) },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer)
                    )
                }
            }
        }
    ) { insets ->
        Crossfade(targetState = state.tab, label = "Gallery destination", modifier = Modifier.padding(insets)) { tab ->
            val openAlbum = if (tab == GalleryTab.Albums) album else null
            val sections = openAlbum?.sections ?: state.library.sections
            LazyVerticalGrid(
                columns = GridCells.Fixed(
                    if (tab == GalleryTab.Photos || openAlbum != null) {
                        gridColumns
                    } else {
                        3
                    }
                ),

                state = if (tab == GalleryTab.Photos) {
                    photoScroll
                } else if (openAlbum != null) {
                    albumScroll
                } else {
                    albumsScroll
                },

                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(tab, openAlbum != null) {

                        if (tab == GalleryTab.Photos || openAlbum != null) {

                            awaitEachGesture {

                                var accumulatedZoom = 1f

                                while (true) {

                                    val event = awaitPointerEvent()

                                    val pressedFingers =
                                        event.changes.count { it.pressed }

                                    if (pressedFingers >= 2) {

                                        accumulatedZoom *= event.calculateZoom()

                                        // Fingers moving apart -> bigger photos
                                        if (accumulatedZoom > 1.20f) {

                                            if (gridColumns > 2) {
                                                gridColumns--

                                                preferences.edit()
                                                    .putInt("grid_columns", gridColumns)
                                                    .apply()
                                            }

                                            accumulatedZoom = 1f
                                        }

                                        // Fingers moving together -> smaller photos
                                        else if (accumulatedZoom < 0.83f) {

                                            if (gridColumns < 8) {
                                                gridColumns++

                                                preferences.edit()
                                                    .putInt("grid_columns", gridColumns)
                                                    .apply()
                                            }

                                            accumulatedZoom = 1f
                                        }

                                        event.changes.forEach {
                                            it.consume()
                                        }
                                    }

                                    if (event.changes.none { it.pressed }) {
                                        break
                                    }
                                }
                            }
                        }
                    },
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(start = 12.dp, top = 64.dp, end = 12.dp, bottom = 20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(if (tab == GalleryTab.Photos) R.string.photos else R.string.albums),
                                style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                            IconButton(onClick = openSettings) {
                                Icon(painterResource(R.drawable.ic_settings), stringResource(R.string.app_settings))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(if (state.access.isFull) R.string.local_library else R.string.limited_library),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.access.canRead && !state.access.isFull) {
                            TextButton(onClick = requestAccess) { Text(stringResource(R.string.manage_access)) }
                        }
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
                        LibraryMessage(R.string.empty_title, R.string.empty_message) {
                            TextButton(onClick = refresh) { Text(stringResource(R.string.refresh)) }
                        }
                    }
                    tab == GalleryTab.Photos || openAlbum != null -> {
                        if (openAlbum != null) item(key = "album_title", span = { GridItemSpan(maxLineSpan) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { albumId = null }) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.all_albums)) }
                                Text(openAlbum.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                        }
                        sections.forEach { section ->
                            item(key = "month:${section.month}", span = { GridItemSpan(maxLineSpan) }, contentType = "month") {
                                Text(section.month, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp))
                            }
                            items(section.items, key = { it.id }, contentType = { "media" }) { media ->
                                Surface(onClick = { selectedId = media.id }, shape = RoundedCornerShape(8.dp)) {
                                    MediaThumbnail(media, loader)
                                }
                            }
                        }
                    }
                    else -> items(state.library.albums, key = { it.id }, contentType = { "album" }) { entry ->
                        Column {
                            Surface(onClick = { albumScroll.requestScrollToItem(0); albumId = entry.id }, shape = RoundedCornerShape(8.dp)) {
                                MediaThumbnail(entry.items.first(), loader)
                            }
                            Text(entry.name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp, start = 4.dp))
                            Text("${entry.items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
    val selectedIndex = remember(visibleItems, selectedId) { visibleItems.indexOfFirst { it.id == selectedId } }
    if (selectedIndex >= 0) {
        MediaViewer(visibleItems, selectedId!!, loader, onCurrentItem = { selectedId = it }, onClose = { selectedId = null })
    }
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

@Composable
private fun MediaThumbnail(media: MediaItem, loader: ImageLoader) {
    val context = LocalContext.current
    val request = remember(media.contentUri) { ImageRequest.Builder(context).data(media.contentUri).size(512).build() }
    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        AsyncImage(model = request, imageLoader = loader, contentDescription = media.filename,
            error = painterResource(R.drawable.ic_photos), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (media.type == MediaType.Video) {
            Row(Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(4.dp))
                .background(Color.Black.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_play), stringResource(R.string.video), tint = Color.White, modifier = Modifier.size(12.dp))
                Text(formatDuration(media.durationMillis ?: 0), color = Color.White, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

internal fun formatDuration(millis: Long): String {
    val seconds = millis.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
