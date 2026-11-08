package com.nichita.gallery.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import com.nichita.gallery.GalleryApplication
import com.nichita.gallery.R
import com.nichita.gallery.data.MediaPermissions
import com.nichita.gallery.data.MediaItem
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import kotlin.math.ceil

@Composable
fun GalleryScreen(model: GalleryViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val app = context.applicationContext as GalleryApplication
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { model.refresh(force = true) }
    val deletion = rememberMediaDeleteAction(model::mediaDeleted) {
        model.refresh(force = true)
        Toast.makeText(app, R.string.delete_failed, Toast.LENGTH_LONG).show()
    }
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
            Uri.fromParts("package", context.packageName, null))) }, onDelete = deletion.request, deletionBusy = deletion.busy,
        search = model::search, setTheme = model::setTheme, setOldest = model::setOldest,
        setAlbumNames = model::setAlbumNames, toggleFavorite = model::toggleFavorite, toggleFavorites = model::toggleFavorites)
}

@Composable
internal fun GalleryContent(
    state: GalleryUiState, thumbnails: ImageLoader, viewer: () -> ImageLoader,
    selectTab: (GalleryTab) -> Unit, refresh: () -> Unit, setColumns: (Int) -> Unit,
    requestAccess: () -> Unit, openSettings: () -> Unit,
    onDelete: (MediaItem) -> Unit = {}, deletionBusy: Boolean = false,
    search: (String) -> Unit = {}, setTheme: (ThemeMode) -> Unit = {},
    setOldest: (Boolean) -> Unit = {}, setAlbumNames: (Boolean) -> Unit = {},
    toggleFavorite: (MediaItem) -> Unit = {}, toggleFavorites: () -> Unit = {}
) {
    var albumId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPosition by rememberSaveable { mutableIntStateOf(0) }
    var pinching by remember { mutableStateOf(false) }
    val preview = remember { mutableFloatStateOf(1f) }
    val library = (if (state.tab == GalleryTab.Photos) state.displayLibrary else state.allLibrary) ?: state.library
    val albumLibrary = state.allLibrary ?: state.library
    val album = remember(albumLibrary, albumId) { albumLibrary.albums.find { it.id == albumId } }
    val pageLibrary = if (state.tab == GalleryTab.Search) state.searchLibrary else library
    val visibleItems = if (state.tab == GalleryTab.Albums && albumId != null) album?.items.orEmpty() else pageLibrary.items
    val photoScroll = rememberLazyGridState()
    val albumsScroll = rememberLazyGridState()
    val albumScroll = rememberLazyGridState()
    val searchScroll = rememberLazyGridState()
    val settingsScroll = androidx.compose.foundation.lazy.rememberLazyListState()
    var photosHeading by rememberSaveable { mutableIntStateOf(0) }
    var albumsHeading by rememberSaveable { mutableIntStateOf(0) }
    var searchHeading by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = state.tab == GalleryTab.Albums && albumId != null && selectedId == null) { albumId = null }
    LaunchedEffect(pageLibrary, album, state.loading, state.searching) {
        if (!state.loading && !state.searching && selectedId != null && visibleItems.isEmpty()) selectedId = null
        if (!state.loading && albumId != null && album == null) { selectedId = null; albumId = null }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
        ) {

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
            val tab = state.tab
            val header: @Composable () -> Unit = {
                TerminalHeader(stringResource(if (state.favoritesOnly && tab == GalleryTab.Photos) R.string.favorites else tab.label),
                    search = { selectTab(GalleryTab.Search) }, settings = { selectTab(GalleryTab.Settings) },
                    refresh = refresh, favoritesOnly = state.favoritesOnly,
                    toggleFavorites = { toggleFavorites(); selectTab(GalleryTab.Photos) })
            }
            if (tab == GalleryTab.Settings) {
                GallerySettings(state, setTheme, setOldest, setAlbumNames, setColumns, requestAccess, openSettings, settingsScroll, header)
                return@BoxWithConstraints
            }
            val openAlbum = if (tab == GalleryTab.Albums) album else null
            val mediaGrid = tab != GalleryTab.Albums || openAlbum != null
            val columns = if (mediaGrid) (state.columns * (maxWidth.value / 400f).toInt().coerceAtLeast(1)).coerceAtMost(12) else 1
            val scroll = when {
                tab == GalleryTab.Search -> searchScroll
                tab == GalleryTab.Photos -> photoScroll
                openAlbum != null -> albumScroll
                else -> albumsScroll
            }
            val sections = openAlbum?.sections ?: pageLibrary.sections
            val navigation = remember { Animatable(1f) }
            var navigationStarted by remember { mutableStateOf(false) }
            LaunchedEffect(tab, albumId) {
                if (navigationStarted) {
                    navigation.snapTo(0.88f)
                    navigation.animateTo(1f, tween(GalleryMotion.Change, easing = GalleryMotion.Easing))
                }
                navigationStarted = true
            }
            val density = LocalDensity.current
            val gap = 3.dp
            val headerHeight = with(density) { (MaterialTheme.typography.titleMedium.lineHeight.toDp() + 24.dp).coerceAtLeast(52.dp) }
            val cellPixels = with(density) { ceil(((maxWidth - 24.dp - gap * (columns - 1)).toPx() / columns).toDouble()).toInt().coerceAtLeast(1) }
            val headingPixels = when (tab) {
                GalleryTab.Photos -> photosHeading
                GalleryTab.Albums -> albumsHeading
                else -> searchHeading
            }
            var albumTitlePixels by remember(openAlbum?.id) { mutableIntStateOf(0) }
            val map = remember(sections, columns, cellPixels, headerHeight, headingPixels, albumTitlePixels, density) {
                with(density) { GalleryScrollMap(sections.map { it.items.size }, columns, cellPixels.toFloat(),
                    headerHeight.toPx(), headingPixels.toFloat(), gap.toPx(), 24.dp.toPx(),
                    if (openAlbum != null) albumTitlePixels.coerceAtLeast(1).toFloat() else 0f) }
            }
            val columnDescription = stringResource(R.string.grid_columns, columns)
            LazyVerticalGrid(columns = GridCells.Fixed(columns), state = scroll,
                modifier = Modifier.fillMaxSize().testTag("media_grid").semantics { contentDescription = columnDescription }
                    .graphicsLayer { scaleX = preview.floatValue; scaleY = preview.floatValue; alpha = navigation.value }
                    .gridPinch(columns, mediaGrid, { preview.floatValue = it }, { pinching = it }) { value ->
                        if (value != columns) {
                            val anchor = scroll.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "media" }
                            val index = anchor?.index ?: scroll.firstVisibleItemIndex
                            val offset = if (anchor?.index == scroll.firstVisibleItemIndex) scroll.firstVisibleItemScrollOffset else 0
                            scroll.requestScrollToItem(index, offset)
                            setColumns(value)
                        }
                    },
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 0.dp,
                    bottom = 110.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
                item(key = "heading", span = { GridItemSpan(maxLineSpan) }, contentType = "heading") {
                    Column(Modifier.onSizeChanged {
                        when (tab) {
                            GalleryTab.Photos -> photosHeading = it.height
                            GalleryTab.Albums -> albumsHeading = it.height
                            else -> searchHeading = it.height
                        }
                    }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        header()
                        if (tab == GalleryTab.Search) OutlinedTextField(value = state.query, onValueChange = search,
                            modifier = Modifier.fillMaxWidth().testTag("search_input"),
                            placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true,
                            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) })
                        if (state.access.canRead && !state.access.isFull) {
                            Text(stringResource(R.string.limited_library), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    tab == GalleryTab.Search && state.searching && pageLibrary.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                    tab == GalleryTab.Search && pageLibrary.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(if (state.query.isBlank()) R.string.search_start else R.string.search_empty),
                            modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    tab == GalleryTab.Photos && state.favoritesOnly && library.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(stringResource(R.string.favorite_empty), modifier = Modifier.padding(24.dp))
                    }
                    state.library.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryMessage(R.string.empty_title, R.string.empty_message) { TextButton(onClick = refresh) { Text(stringResource(R.string.refresh)) } }
                    }
                    mediaGrid -> {
                        if (openAlbum != null) item(key = "album_title", span = { GridItemSpan(maxLineSpan) }) {
                            Row(Modifier.onSizeChanged { albumTitlePixels = it.height }, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { albumId = null }) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.all_albums)) }
                                Text(openAlbum.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            }
                        }
                        sections.forEach { section ->
                            item(key = "month:${section.month}", span = { GridItemSpan(maxLineSpan) }, contentType = "month") {
                                Box(Modifier.fillMaxWidth().height(headerHeight).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("> ", color = MaterialTheme.colorScheme.primary, fontFamily = FontFamily.Monospace)
                                        Text(section.month, style = MaterialTheme.typography.labelLarge, fontFamily = FontFamily.Monospace,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        Text(" (${section.items.size})", style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            items(section.items, key = { it.id }, contentType = { "media" }) { media ->
                                MediaThumbnail(media, thumbnails, cellPixels, columns,
                                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(enabled = !pinching) {
                                        selectedPosition = visibleItems.indexOfFirst { it.id == media.id }.coerceAtLeast(0)
                                        selectedId = media.id
                                    })
                            }
                        }
                    }
                    else -> items(library.albums, key = { it.id }, contentType = { "album" }) { entry ->
                        Column {
                            Row(Modifier.fillMaxWidth().clickable { albumScroll.requestScrollToItem(0); albumId = entry.id }
                                .padding(vertical = 10.dp, horizontal = 4.dp).heightIn(min = 64.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                val coverPixels = with(density) { 64.dp.roundToPx() }
                                MediaThumbnail(entry.items.first(), thumbnails, coverPixels, 3,
                                    Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)))
                                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                                    Text(entry.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.item_count, entry.items.size, entry.items.size), style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Icon(painterResource(R.drawable.ic_chevron), null, Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
                if (mediaGrid && state.access.canRead && !state.error && sections.isNotEmpty()) {
                    GalleryScrollControls(
                        scroll,
                        map,
                        sections.map { it.month },
                        Modifier.fillMaxSize()
                    )
                } else if (
                    tab == GalleryTab.Albums &&
                    openAlbum == null &&
                    library.albums.isNotEmpty()
                ) {
                    AlbumScrollControls(
                        albumsScroll,
                        library.albums.size,
                        Modifier.fillMaxSize(),
                        columns,
                        headingPixels,
                        library.albums
                    )
                }
            }

            // Floating navigation above gallery content
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                TerminalNavigation(
                    selected = state.tab,
                    select = selectTab
                )
            }
        }
    }
    val selectedIndex = remember(visibleItems, selectedId, selectedPosition) {
        if (selectedId == null || visibleItems.isEmpty()) -1
        else visibleItems.indexOfFirst { it.id == selectedId }.takeIf { it >= 0 } ?: selectedPosition.coerceIn(0, visibleItems.lastIndex)
    }
    if (selectedIndex >= 0) MediaViewer(visibleItems, visibleItems[selectedIndex].id, viewer(), onCurrentItem = {
        selectedId = it
        selectedPosition = visibleItems.indexOfFirst { item -> item.id == it }.coerceAtLeast(0)
    }, onClose = { selectedId = null }, onDelete = onDelete, thumbnails = thumbnails, deletionBusy = deletionBusy,
        favorites = state.favorites, toggleFavorite = toggleFavorite)
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
