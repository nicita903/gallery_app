package com.nichita.gallery.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.BitmapImage
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.size.Scale
import com.nichita.gallery.R
import com.nichita.gallery.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal fun focalOffset(offset: Offset, focal: Offset, center: Offset, ratio: Float, pan: Offset): Offset =
    (offset - (focal - center)) * ratio + (focal - center) + pan

internal fun clampPhotoOffset(offset: Offset, scale: Float, width: Float, height: Float, aspect: Float): Offset {
    if (width <= 0 || height <= 0 || aspect <= 0) return Offset.Zero
    val x = ((minOf(width, height * aspect) * scale - width) / 2).coerceAtLeast(0f)
    val y = ((minOf(height, width / aspect) * scale - height) / 2).coerceAtLeast(0f)
    return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
}

@Stable
internal class PhotoTransform {
    val scale = mutableFloatStateOf(1f)
    val offset = mutableStateOf(Offset.Zero)
    companion object {
        val Saver = listSaver<PhotoTransform, Float>(save = { listOf(it.scale.floatValue, it.offset.value.x, it.offset.value.y) },
            restore = { PhotoTransform().apply { scale.floatValue = it[0]; offset.value = Offset(it[1], it[2]) } })
    }
}

@Composable
internal fun ZoomablePhoto(media: MediaItem, loader: ImageLoader, thumbnails: ImageLoader, active: Boolean,
    transform: PhotoTransform, onZoomed: (Boolean) -> Unit, onTap: () -> Unit, previewPixels: Int = 384) {
    var scale by transform.scale
    var offset by transform.offset
    var decodedRatio by remember(media.id, media.cacheVersion) { mutableFloatStateOf(0f) }
    var failed by remember(media.id, media.cacheVersion) { mutableStateOf(false) }
    var loaded by remember(media.id, media.cacheVersion, active) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var animation by remember { mutableStateOf<Job?>(null) }
    val tapCallback by rememberUpdatedState(onTap)
    val zoomCallback by rememberUpdatedState(onZoomed)
    LaunchedEffect(active) { if (!active) { animation?.cancel(); scale = 1f; offset = Offset.Zero } }
    LaunchedEffect(media.id, active) { snapshotFlow { active && scale > 1.01f }.collect { zoomCallback(it) } }
    val context = LocalContext.current.applicationContext
    val full = remember(media.id, media.cacheVersion) {
        ImageRequest.Builder(context).data(media.contentUri).memoryCacheKey("viewer:${media.id}:${media.cacheVersion}")
            .size(2560).scale(Scale.FIT).precision(Precision.INEXACT).build()
    }
    val thumb = remember(media.id, media.cacheVersion, previewPixels) { thumbnailImageRequest(context, media, previewPixels) }
    val cached = remember(media.id, media.cacheVersion, previewPixels) {
        cachedThumbnail(thumbnails, media, previewPixels)?.image as? BitmapImage
    }
    val alpha = animateFloatAsState(if (loaded) 1f else 0f, tween(160, easing = GalleryMotion.Easing), label = "Image ready")
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        val aspect = decodedRatio.takeIf { it > 0f } ?: if (media.width > 0 && media.height > 0) media.width.toFloat() / media.height else width / height
        val center = Offset(width / 2, height / 2)
        fun clamp(value: Offset, zoom: Float) = clampPhotoOffset(value, zoom, width, height, aspect)
        LaunchedEffect(width, height, aspect) { offset = clamp(offset, scale) }
        Box(Modifier.fillMaxSize().then(if (active) Modifier.testTag("viewer_photo").semantics {
            stateDescription = context.getString(R.string.zoom_level, (scale * 100).roundToInt())
        } else Modifier)
            .pointerInput(media.id, active, width, height, aspect) {
                if (!active) return@pointerInput
                awaitEachGesture {
                    var transforming = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed && !it.previousPressed }) animation?.cancel()
                        val fingers = event.changes.count { it.pressed }
                        val moving = event.changes.any { it.positionChanged() }
                        if ((fingers >= 2 || scale > 1.01f) && moving) {
                            val old = scale
                            val next = (old * event.calculateZoom()).coerceIn(1f, 5f)
                            val focal = event.calculateCentroid(useCurrent = false).takeUnless { it == Offset.Unspecified } ?: center
                            offset = clamp(focalOffset(offset, focal, center, next / old, event.calculatePan()), next)
                            scale = next
                            transforming = true
                        }
                        if (transforming) event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }.pointerInput(media.id, active, width, height, aspect) {
                if (!active) return@pointerInput
                detectTapGestures(onTap = { tapCallback() }, onDoubleTap = { focal ->
                    animation?.cancel()
                    val fromScale = scale
                    val fromOffset = offset
                    val target = if (scale > 1.01f) 1f else 3f
                    val toOffset = clamp(focalOffset(offset, focal, center, target / scale, Offset.Zero), target)
                    animation = scope.launch {
                        animate(0f, 1f, animationSpec = tween(GalleryMotion.Zoom, easing = GalleryMotion.Easing)) { value, _ ->
                            scale = fromScale + (target - fromScale) * value
                            offset = clamp(fromOffset + (toOffset - fromOffset) * value, scale)
                        }
                    }
                })
            }) {
            Box(Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }) {
                if (cached != null) Image(cached.bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else AsyncImage(thumb, null, imageLoader = thumbnails, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                if (active) AsyncImage(full, media.filename, imageLoader = loader, contentScale = ContentScale.Fit,
                    onSuccess = {
                        loaded = true; failed = false
                        val size = it.painter.intrinsicSize
                        if (size.width > 0 && size.height > 0) decodedRatio = size.width / size.height
                    }, onError = { failed = true }, modifier = Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha.value })
            }
        }
        if (failed) Text(stringResource(R.string.media_unavailable), color = Color.White, modifier = Modifier.padding(24.dp))
    }
}
