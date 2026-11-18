package com.nichita.gallery.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

internal fun Modifier.viewerDismissGesture(
    mediaId: String, enabled: Boolean, excluded: (Offset) -> Boolean,
    onStart: () -> Unit, onDrag: (Float) -> Unit, onCancel: () -> Unit, onEnd: () -> Unit
): Modifier = pointerInput(mediaId, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (excluded(down.position)) return@awaitEachGesture
        var total = Offset.Zero
        var dragging = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.size != 1) break
                val change = event.changes.first()
                if (change.id != down.id || change.isConsumed) break
                if (!change.pressed) {
                    if (dragging) { dragging = false; onEnd() }
                    break
                }
                val delta = change.positionChange()
                if (!dragging) {
                    total += delta
                    val slop = viewConfiguration.touchSlop
                    if (abs(total.x) > slop && abs(total.x) >= abs(total.y)) break
                    if (total.y < -slop) break
                    if (total.y > slop) {
                        dragging = true
                        onStart()
                        onDrag(total.y - slop)
                        change.consume()
                    }
                } else {
                    onDrag(delta.y)
                    change.consume()
                }
            }
        } finally {
            if (dragging) onCancel()
        }
    }
}
