package com.nichita.gallery.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nichita.gallery.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
internal fun GalleryScrollControls(grid: LazyGridState, map: GalleryScrollMap, months: List<String>, modifier: Modifier = Modifier) {
    Box(modifier) {
        MonthScrollbar(grid, map, months, Modifier.fillMaxSize().padding(vertical = 8.dp))
        BackToTop(grid, map.columns,
            modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(
                end = 16.dp,
                bottom = 120.dp
            )
            .padding(bottom = 12.dp))
    }
}

@Composable
private fun BackToTop(grid: LazyGridState, columns: Int, modifier: Modifier) {
    var visible by remember(grid) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf<Job?>(null) }
    LaunchedEffect(grid, columns) {
        snapshotFlow { grid.firstVisibleItemIndex }.collect {
            if (it > columns * 8) visible = true else if (it < columns * 3) visible = false
        }
    }
    AnimatedVisibility(visible, enter = fadeIn(tween(210, easing = GalleryMotion.Easing)) + slideInVertically(tween(210, easing = GalleryMotion.Easing)) { it },
        exit = fadeOut(tween(210, easing = GalleryMotion.Easing)) + slideOutVertically(tween(210, easing = GalleryMotion.Easing)) { it }, modifier = modifier) {
        Surface(onClick = {
            jump?.cancel()
            jump = scope.launch {
                if (grid.firstVisibleItemIndex > columns * 30) grid.scrollToItem(columns * 2)
                grid.animateScrollToItem(0)
            }
        }, color = MaterialTheme.colorScheme.primary, contentColor = androidx.compose.ui.graphics.Color.White,
            shape = androidx.compose.foundation.shape.CircleShape, shadowElevation = 0.dp,
            modifier = Modifier.size(52.dp).testTag("scroll_to_top")) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_up), stringResource(R.string.back_to_top), Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun scrollbarAlpha(grid: LazyGridState, dragging: Boolean): State<Float> {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(grid, dragging) {
        snapshotFlow { grid.isScrollInProgress }.collectLatest { scrolling ->
            if (dragging || scrolling) visible = true
            else { delay(1200); visible = false }
        }
    }
    return animateFloatAsState(if (visible || dragging) 1f else 0f, tween(180, easing = GalleryMotion.Easing), label = "Scrollbar visibility")
}

@Composable
private fun MonthScrollbar(grid: LazyGridState, map: GalleryScrollMap, months: List<String>, modifier: Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val fraction by remember(grid, map) { derivedStateOf {
        val range = (map.totalHeight - grid.layoutInfo.viewportSize.height).coerceAtLeast(1f)
        ((map.offsetFor(grid.firstVisibleItemIndex) + grid.firstVisibleItemScrollOffset) / range).coerceIn(0f, 1f)
    } }
    val alpha = scrollbarAlpha(grid, dragging)
    val density = LocalDensity.current
    val thumb = with(density) { 44.dp.toPx() }
    val color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.fast_scroll)
    BoxWithConstraints(modifier) {
        val travel = (with(density) { maxHeight.toPx() } - thumb).coerceAtLeast(1f)
        fun jump(value: Float) {
            val (index, offset) = map.target(value.coerceIn(0f, 1f) * (map.totalHeight - grid.layoutInfo.viewportSize.height).coerceAtLeast(0f))
            grid.requestScrollToItem(index, offset)
        }
        Canvas(Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight().testTag("month_scrollbar").semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            setProgress { jump(it); true }
        }.pointerInput(map, travel) {
            try {
                detectDragGestures(onDragStart = {
                    dragging = true; dragProgress = ((it.y - thumb / 2) / travel).coerceIn(0f, 1f); jump(dragProgress)
                }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { change, _ ->
                    change.consume(); dragProgress = ((change.position.y - thumb / 2) / travel).coerceIn(0f, 1f); jump(dragProgress)
                }
            } finally { dragging = false }
        }) {
            drawRoundRect(color.copy(alpha = 0.08f * alpha.value), Offset(size.width - 6.dp.toPx(), 0f), Size(2.dp.toPx(), size.height), CornerRadius(2.dp.toPx()))
            drawRoundRect(color.copy(alpha = alpha.value * if (dragging) 0.8f else 0.45f),
                Offset(size.width - 8.dp.toPx(), (if (dragging) dragProgress else fraction) * travel), Size(4.dp.toPx(), thumb), CornerRadius(4.dp.toPx()))
        }
        val month by remember(map, months, grid) { derivedStateOf {
            // Use the actual first visible media index after the requested jump has laid out.
            val index = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.contentType == "media" }?.index ?: grid.firstVisibleItemIndex
            months.getOrElse(map.monthAt(map.offsetFor(index))) { "" }
        } }
        AnimatedVisibility(dragging, enter = fadeIn(tween(160, easing = GalleryMotion.Easing)), exit = fadeOut(tween(160, easing = GalleryMotion.Easing)), modifier = Modifier.align(Alignment.TopEnd)
            .offset { IntOffset(-with(density) { 32.dp.roundToPx() }, (dragProgress * travel).toInt().coerceAtLeast(0)) }) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Text(month, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).testTag("month_indicator"), maxLines = 1)
            }
        }
    }
}

@Composable
internal fun AlbumScrollControls(grid: LazyGridState, albumCount: Int, modifier: Modifier = Modifier,
    columns: Int = 3, headingPixels: Int = 0, contentKey: Any = albumCount) {
    val density = LocalDensity.current
    val width by remember(grid) { derivedStateOf { grid.layoutInfo.viewportSize.width } }
    val geometry = remember(contentKey, columns, width, density, headingPixels) {
        AlbumRowGeometry(albumCount, columns, with(density) { 3.dp.toPx() }, headingPixels.toFloat())
    }
    var version by remember(geometry) { mutableIntStateOf(0) }
    LaunchedEffect(geometry, grid) {
        snapshotFlow { grid.layoutInfo }.collect { layout ->
            var changed = false
            layout.visibleItemsInfo.groupBy { it.row }.forEach { (row, items) ->
                if (geometry.measure(row, items.maxOf { it.size.height }.toFloat())) changed = true
            }
            if (changed) { geometry.rebuild(); version++ }
        }
    }
    val model = remember(geometry, version) { geometry.snapshot() }
    val currentModel by rememberUpdatedState(model)
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    var dragModel by remember { mutableStateOf<AlbumRowSnapshot?>(null) }
    val fraction by remember(grid, model) { derivedStateOf {
        val layout = grid.layoutInfo
        val first = layout.visibleItemsInfo.firstOrNull()
        when {
            !grid.canScrollBackward -> 0f
            !grid.canScrollForward -> 1f
            first == null -> 0f
            else -> ((model.offset(first.row) - first.offset.y + layout.viewportStartOffset) /
                (model.totalHeight + with(density) { 24.dp.toPx() } - layout.viewportSize.height).coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
    } }
    val alpha = scrollbarAlpha(grid, dragging)
    val color = if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.scroll_albums)
    BoxWithConstraints(modifier) {
        val thumb = with(density) { 44.dp.toPx() }
        val travel = (with(density) { maxHeight.toPx() } - thumb).coerceAtLeast(1f)
        fun jump(value: Float) {
            val snapshot = dragModel ?: currentModel
            val range = (snapshot.totalHeight + with(density) { 24.dp.toPx() } - grid.layoutInfo.viewportSize.height).coerceAtLeast(0f)
            val (index, offset) = snapshot.target(value.coerceIn(0f, 1f) * range)
            grid.requestScrollToItem(index, offset)
        }
        Canvas(Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight().testTag("albums_scrollbar").semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            setProgress { jump(it); true }
        }.pointerInput(geometry, travel) {
            try {
                detectDragGestures(onDragStart = {
                    dragModel = currentModel; dragging = true
                    dragProgress = ((it.y - thumb / 2) / travel).coerceIn(0f, 1f); jump(dragProgress)
                }, onDragEnd = { dragging = false; dragModel = null }, onDragCancel = { dragging = false; dragModel = null }) { change, _ ->
                    change.consume(); dragProgress = ((change.position.y - thumb / 2) / travel).coerceIn(0f, 1f); jump(dragProgress)
                }
            } finally { dragging = false; dragModel = null }
        }) {
            drawRoundRect(color.copy(alpha = alpha.value * if (dragging) 0.8f else 0.45f),
                Offset(size.width - 8.dp.toPx(), (if (dragging) dragProgress else fraction) * travel), Size(4.dp.toPx(), thumb), CornerRadius(4.dp.toPx()))
        }
        BackToTop(grid, columns, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }
}
