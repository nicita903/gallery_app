package com.nichita.gallery.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class GallerySwipe { NONE, LEFT, RIGHT }

/**
 * Observes main-screen touches without intercepting the grid's scrolling or tapping.
 * The fullscreen MediaViewer is a separate dialog, so it is not covered by this detector.
 */
@Composable
internal fun GalleryGestures(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    canGoBack: Boolean,
    onOpenMenu: () -> Unit,
    onGoBack: () -> Unit,
    onHaptic: () -> Unit,
    swipeDistance: Dp = 28.dp,
    holdTimeMs: Long = 180L,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val threshold = with(density) { swipeDistance.toPx() }
    val verticalTolerance = with(density) { 56.dp.toPx() }
    val holdTolerance = with(density) { 24.dp.toPx() }
    val reverseTolerance = with(density) { 16.dp.toPx() }
    val openMenu by rememberUpdatedState(onOpenMenu)
    val goBack by rememberUpdatedState(onGoBack)
    val haptic by rememberUpdatedState(onHaptic)
    var gestureConfirmed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (gestureConfirmed) 0.95f else 1f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "Gallery gesture scale"
    )

    Box(
        modifier = modifier.pointerInput(enabled, canGoBack, threshold, holdTimeMs) {
            if (!enabled) {
                gestureConfirmed = false
                return@pointerInput
            }
            coroutineScope {
                awaitEachGesture {
                    val first = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    var swipe = GallerySwipe.NONE
                    var activationX = first.position.x
                    var confirmed = false
                    var cancelled = false
                    var holdJob: Job? = null
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            // A second finger, including pinch gestures, cancels recognition.
                            if (event.changes.size != 1 || event.changes.first().id != first.id) {
                                cancelled = true
                                break
                            }
                            val change = event.changes.first()
                            if (!change.pressed) {
                                if (confirmed && !cancelled) {
                                    when (swipe) {
                                        GallerySwipe.RIGHT -> openMenu()
                                        GallerySwipe.LEFT -> goBack()
                                        GallerySwipe.NONE -> Unit
                                    }
                                }
                                break
                            }

                            val dx = change.position.x - first.position.x
                            val dy = change.position.y - first.position.y
                            // Vertical scroll wins. No consumption, so child controls remain usable.
                            if (abs(dy) > verticalTolerance ||
                                (swipe == GallerySwipe.NONE && abs(dy) > 12.dp.toPx() && abs(dy) > abs(dx))) {
                                cancelled = true
                                break
                            }

                            if (swipe == GallerySwipe.NONE) {
                                if (abs(dx) >= threshold && abs(dx) > abs(dy) * 1.5f) {
                                    swipe = if (dx > 0) GallerySwipe.RIGHT else GallerySwipe.LEFT
                                    if (swipe == GallerySwipe.LEFT && !canGoBack) {
                                        cancelled = true
                                        break
                                    }
                                    activationX = change.position.x
                                    holdJob = launch {
                                        delay(holdTimeMs)
                                        confirmed = true
                                        gestureConfirmed = true
                                        haptic()
                                    }
                                }
                            } else {
                                val drift = change.position.x - activationX
                                val reversed = when (swipe) {
                                    GallerySwipe.RIGHT -> drift < -reverseTolerance
                                    GallerySwipe.LEFT -> drift > reverseTolerance
                                    GallerySwipe.NONE -> false
                                }
                                if (reversed || abs(dy) > verticalTolerance) {
                                    cancelled = true
                                    break
                                }
                            }
                        }
                    } finally {
                        holdJob?.cancel()
                        gestureConfirmed = false
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin.Center
                clip = scale < 0.999f
                shape = RoundedCornerShape(20.dp)
            },
            content = content
        )
    }
}
