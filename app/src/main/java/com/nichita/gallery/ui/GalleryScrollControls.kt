package com.nichita.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nichita.gallery.R
import kotlinx.coroutines.launch

@Composable
internal fun GalleryScrollControls(grid: LazyGridState, map: GalleryScrollMap, months: List<String>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val topVisible by remember(grid, map.columns) { derivedStateOf { grid.firstVisibleItemIndex > map.columns * 6 } }
    Box(modifier) {
        MonthScrollbar(grid, map, months, Modifier.fillMaxSize().padding(vertical = 8.dp))
        AnimatedVisibility(topVisible, enter = fadeIn() + scaleIn(initialScale = 0.9f), exit = fadeOut() + scaleOut(targetScale = 0.9f),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
            SmallFloatingActionButton(onClick = {
                scope.launch {
                    if (grid.firstVisibleItemIndex > map.columns * 30) grid.scrollToItem(0) else grid.animateScrollToItem(0)
                }
            }, containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(24.dp), modifier = Modifier.testTag("scroll_to_top")) {
                Icon(painterResource(R.drawable.ic_up), stringResource(R.string.scroll_to_top))
            }
        }
    }
}

@Composable
private fun MonthScrollbar(grid: LazyGridState, map: GalleryScrollMap, months: List<String>, modifier: Modifier) {
    var dragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }
    val fraction by remember(grid, map) { derivedStateOf {
        val range = (map.totalHeight - grid.layoutInfo.viewportSize.height).coerceAtLeast(1f)
        ((map.offsetFor(grid.firstVisibleItemIndex) + grid.firstVisibleItemScrollOffset) / range).coerceIn(0f, 1f)
    } }
    val density = LocalDensity.current
    val thumbHeight = with(density) { 44.dp.toPx() }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(R.string.fast_scroll)
    BoxWithConstraints(modifier) {
        val trackHeight = with(density) { maxHeight.toPx() }
        val travel = (trackHeight - thumbHeight).coerceAtLeast(1f)
        fun jump(progress: Float) {
            val range = (map.totalHeight - grid.layoutInfo.viewportSize.height).coerceAtLeast(0f)
            val (index, offset) = map.target(progress.coerceIn(0f, 1f) * range)
            grid.requestScrollToItem(index, offset)
        }
        Canvas(Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight().testTag("month_scrollbar").semantics {
            contentDescription = description
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            setProgress { jump(it); true }
        }.pointerInput(map, trackHeight) {
            detectDragGestures(onDragStart = {
                dragging = true
                dragProgress = ((it.y - thumbHeight / 2) / travel).coerceIn(0f, 1f)
                jump(dragProgress)
            }, onDragCancel = { dragging = false }, onDragEnd = { dragging = false }) { change, _ ->
                change.consume()
                dragProgress = ((change.position.y - thumbHeight / 2) / travel).coerceIn(0f, 1f)
                jump(dragProgress)
            }
        }) {
            val progress = if (dragging) dragProgress else fraction
            drawRoundRect(color.copy(alpha = 0.08f), topLeft = Offset(size.width - 6.dp.toPx(), 0f),
                size = Size(2.dp.toPx(), size.height), cornerRadius = CornerRadius(2.dp.toPx()))
            drawRoundRect(color.copy(alpha = if (dragging || grid.isScrollInProgress) 0.75f else 0.35f),
                topLeft = Offset(size.width - 8.dp.toPx(), progress * travel), size = Size(4.dp.toPx(), thumbHeight), cornerRadius = CornerRadius(4.dp.toPx()))
        }
        val month by remember(map, months) { derivedStateOf {
            val range = (map.totalHeight - grid.layoutInfo.viewportSize.height).coerceAtLeast(0f)
            months.getOrElse(map.monthAt(dragProgress * range)) { "" }
        } }
        AnimatedVisibility(dragging, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopEnd)
            .offset { IntOffset(-with(density) { 32.dp.roundToPx() }, (dragProgress * travel).toInt().coerceAtLeast(0)) }) {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.inverseSurface) {
                Text(month, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).testTag("month_indicator"), maxLines = 1)
            }
        }
    }
}
