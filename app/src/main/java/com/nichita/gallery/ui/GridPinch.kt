package com.nichita.gallery.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt

internal fun pinchedColumns(columns: Int, zoom: Float): Int = (columns / zoom.coerceIn(0.1f, 10f)).roundToInt().coerceIn(2, 8)

internal fun Modifier.gridPinch(
    columns: Int,
    enabled: Boolean,
    onPreview: (Float) -> Unit,
    onPinching: (Boolean) -> Unit,
    onCommit: (Int) -> Unit
): Modifier = pointerInput(columns, enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        var zoom = 1f
        var pinching = false
        try {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.count { it.pressed } >= 2) {
                    if (!pinching) { pinching = true; onPinching(true) }
                    zoom *= event.calculateZoom()
                    onPreview(zoom.coerceIn(0.94f, 1.06f))
                }
                if (pinching) event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
            if (pinching) onCommit(pinchedColumns(columns, zoom))
        } finally {
            onPreview(1f)
            onPinching(false)
        }
    }
}
