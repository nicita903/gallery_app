package com.nichita.gallery.ui

import androidx.compose.animation.core.CubicBezierEasing

internal object GalleryMotion {
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val Open = 280
    const val Close = 250
    const val Change = 240
    const val Zoom = 220
    const val ShowControls = 200
    const val HideControls = 160
}
