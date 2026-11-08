package com.nichita.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.nichita.gallery.R
import com.nichita.gallery.data.MediaItem
import com.nichita.gallery.data.MediaType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun MediaViewer(items: List<MediaItem>, initialId: String, loader: ImageLoader, onCurrentItem: (String) -> Unit, onClose: () -> Unit) {
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0), pageCount = { items.size })
    var zoomedId by remember { mutableStateOf<String?>(null) }
    val current = items.getOrNull(pager.currentPage) ?: return
    val currentCallback by rememberUpdatedState(onCurrentItem)
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.currentPage }.collect { page -> items.getOrNull(page)?.let { currentCallback(it.id) } }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = Color.White) }
                val date = remember(current.creationDate) {
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(current.creationDate))
                }
                Text(date, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${pager.currentPage + 1} / ${items.size}", color = Color.LightGray,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 16.dp))
            }
            HorizontalPager(state = pager, key = { items[it].id }, userScrollEnabled = zoomedId != current.id,
                modifier = Modifier.weight(1f).fillMaxWidth()) { page ->
                val media = items[page]
                val active = page == pager.currentPage
                if (media.type == MediaType.Video && active) {
                    LocalVideoPlayer(media)
                } else {
                    ZoomablePhoto(media, loader, active) { zoomed ->
                        if (zoomed) zoomedId = media.id else if (zoomedId == media.id) zoomedId = null
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(media: MediaItem, loader: ImageLoader, active: Boolean, onZoomed: (Boolean) -> Unit) {
    var scale by remember(media.id) { mutableFloatStateOf(1f) }
    var offset by remember(media.id) { mutableStateOf(Offset.Zero) }
    var failed by remember(media.id) { mutableStateOf(false) }
    var decodedRatio by remember(media.id) { mutableFloatStateOf(0f) }
    LaunchedEffect(active) { if (!active) { scale = 1f; offset = Offset.Zero; onZoomed(false) } }
    val context = LocalContext.current
    val request = remember(media.contentUri, active) {
        ImageRequest.Builder(context).data(media.contentUri).size(if (active) 2560 else 512).build()
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val ratio = if (decodedRatio > 0f) decodedRatio else if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else width / height
        val fittedWidth = minOf(width, height * ratio)
        val fittedHeight = minOf(height, width / ratio)
        fun clamp(value: Offset, zoom: Float): Offset {
            val maxX = ((fittedWidth * zoom - width) / 2).coerceAtLeast(0f)
            val maxY = ((fittedHeight * zoom - height) / 2).coerceAtLeast(0f)
            return Offset(value.x.coerceIn(-maxX, maxX), value.y.coerceIn(-maxY, maxY))
        }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = clamp(offset + pan, scale)
            onZoomed(scale > 1f)
        }
        Box(Modifier.fillMaxSize().clip(androidx.compose.ui.graphics.RectangleShape)
            .pointerInput(media.id) {
                detectTapGestures(onDoubleTap = {
                    scale = if (scale > 1f) 1f else 3f
                    offset = Offset.Zero
                    onZoomed(scale > 1f)
                })
            }.transformable(transform, canPan = { scale > 1f })) {
            AsyncImage(model = request, imageLoader = loader, contentDescription = media.filename,
                contentScale = ContentScale.Fit, onError = { failed = true }, onSuccess = {
                    failed = false
                    val size = it.painter.intrinsicSize
                    if (size.width > 0 && size.height > 0) decodedRatio = size.width / size.height
                },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y
                })
        }
        if (failed) Text(stringResource(R.string.media_unavailable), color = Color.White, modifier = Modifier.padding(24.dp))
    }
}
