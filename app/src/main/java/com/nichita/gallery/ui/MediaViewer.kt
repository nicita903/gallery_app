package com.nichita.gallery.ui

import android.content.ClipData
import android.content.Intent
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.ImageLoader
import com.nichita.gallery.R
import com.nichita.gallery.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.navigationBarsPadding



@Composable
fun MediaViewer(items: List<MediaItem>, initialId: String, loader: ImageLoader,
    onCurrentItem: (String) -> Unit, onClose: () -> Unit, onDelete: (MediaItem) -> Unit = {},
    thumbnails: ImageLoader = loader, deletionBusy: Boolean = false,
    favorites: Set<String> = emptySet(), toggleFavorite: (MediaItem) -> Unit = {}, previewPixels: Int = 384) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0), pageCount = { items.size })
    var zoomedId by remember { mutableStateOf<String?>(null) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val dismissOffset = remember { mutableFloatStateOf(0f) }
    val dismissScope = rememberCoroutineScope()

    var dismissJob by remember { mutableStateOf<Job?>(null) }
    var dismissDragging by remember { mutableStateOf(false) }
    var controlsBeforeDrag by remember { mutableStateOf(true) }
    val controlsShown by rememberUpdatedState(showControls)
    val topToolbarBounds = remember { mutableStateOf(Rect.Zero) }
    val bottomToolbarBounds = remember { mutableStateOf(Rect.Zero) }
    val dismissBusy by remember { derivedStateOf { dismissOffset.floatValue > 0f } }

    suspend fun animateDismiss(target: Float, spec: AnimationSpec<Float>) {
        animate(dismissOffset.floatValue, target, animationSpec = spec) { value, _ -> dismissOffset.floatValue = value }
    }

    val density = LocalDensity.current
    val dismissThreshold = with(density) { 140.dp.toPx() }

    val dismissProgress by remember(dismissThreshold) {
        derivedStateOf {
            (dismissOffset.floatValue / dismissThreshold).coerceIn(0f, 1f)
        }
    }
    val current = items.getOrNull(pager.currentPage) ?: items.last()
    var videoBinding by remember { mutableStateOf<Pair<String, Player>?>(null) }
    val currentTransform = rememberSaveable(current.id, saver = PhotoTransform.Saver) { PhotoTransform() }
    val context = LocalContext.current
    val repository = remember(context.applicationContext) { MediaMetadata(context.applicationContext.contentResolver) }
    val callback by rememberUpdatedState(onCurrentItem)
    val desiredId by rememberUpdatedState(initialId)
    val closeCallback by rememberUpdatedState(onClose)
    val progress = animateFloatAsState(if (visible) 1f else 0f,
        tween(if (closing) GalleryMotion.Close else GalleryMotion.Open, easing = GalleryMotion.Easing), label = "Viewer transition",
        finishedListener = { if (closing && it == 0f) closeCallback() })
    LaunchedEffect(Unit) { if (!closing) visible = true }
    LaunchedEffect(closing) { if (closing && progress.value == 0f) closeCallback() }
    LaunchedEffect(items) {
        if (pager.isScrollInProgress) snapshotFlow { pager.isScrollInProgress }.first { !it }
        val target = items.indexOfFirst { it.id == desiredId }.coerceAtLeast(0)
        if (pager.currentPage != target) pager.scrollToPage(target)
    }
    LaunchedEffect(pager, items) {
        snapshotFlow { pager.currentPage }.collect { page -> items.getOrNull(page)?.let { callback(it.id) } }
    }
    LaunchedEffect(current.id) { showInfo = false; deleteId = null }
    fun close() { if (!deletionBusy) { dismissJob?.cancel(); closing = true; visible = false } }
    Dialog(onDismissRequest = { if (showInfo) showInfo = false else close() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val controller = remember(window) { window?.let { WindowInsetsControllerCompat(it, it.decorView) } }
        DisposableEffect(window) {
            val lightStatus = controller?.isAppearanceLightStatusBars
            val lightNav = controller?.isAppearanceLightNavigationBars
            val wasVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.systemBars()) ?: true
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setWindowAnimations(0)
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            onDispose {
                controller?.isAppearanceLightStatusBars = lightStatus ?: false
                controller?.isAppearanceLightNavigationBars = lightNav ?: false
                if (wasVisible) controller?.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        SideEffect {
            if (showControls || closing) controller?.show(WindowInsetsCompat.Type.systemBars())
            else controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Color.Black.copy(
                            alpha = progress.value * (1f - dismissProgress * 0.85f)
                        )
                    )
                }
                .viewerDismissGesture(
                    mediaId = "${current.id}:$dismissThreshold",
                    enabled = !(current.type == MediaType.Image && zoomedId == current.id) &&
                        !showInfo && !deletionBusy && !closing,
                    excluded = { position -> controlsShown &&
                        (topToolbarBounds.value.contains(position) || bottomToolbarBounds.value.contains(position)) },
                    onStart = {
                        dismissJob?.cancel()
                        controlsBeforeDrag = controlsShown
                        dismissDragging = true
                        showControls = false
                    },
                    onDrag = { delta ->
                        val resistance = if (delta < 0f) 0.65f else 1f
                        dismissOffset.floatValue = (dismissOffset.floatValue + delta * resistance).coerceAtLeast(0f)
                    },
                    onCancel = {
                        dismissDragging = false
                        if (!closing) {
                            dismissJob = dismissScope.launch {
                                animateDismiss(0f, spring(dampingRatio = 0.9f, stiffness = 600f))
                                showControls = controlsBeforeDrag
                            }
                        }
                    },
                    onEnd = {
                        dismissDragging = false
                        dismissJob = dismissScope.launch {
                            if (dismissOffset.floatValue >= dismissThreshold) {
                                closing = true
                                animateDismiss(view.height.toFloat(), tween(180, easing = GalleryMotion.Easing))
                                closeCallback()
                            } else {
                                animateDismiss(0f, spring(dampingRatio = 0.85f, stiffness = 450f))
                                showControls = controlsBeforeDrag
                            }
                        }
                    }
                )
                .testTag("media_viewer")
        ) {
            HorizontalPager(state = pager, key = { items.getOrNull(it)?.id ?: "missing:$it" },
                userScrollEnabled =
                    zoomedId != current.id &&
                            !showInfo &&
                            !deletionBusy &&
                            !closing &&
                            !dismissDragging &&
                            !dismissBusy,                modifier = Modifier.fillMaxSize().testTag("viewer_pager")
                    .graphicsLayer {

                        alpha = progress.value

                        val dismissScale = 1f - dismissProgress * 0.12f

                        val transitionScale = 0.95f + 0.05f * progress.value

                        val finalScale = dismissScale * transitionScale

                        scaleX = finalScale
                        scaleY = finalScale

                        translationY = dismissOffset.floatValue
                    }
            ) { page ->
                val media = items.getOrNull(page) ?: return@HorizontalPager
                val active = page == pager.currentPage
                if (media.type == MediaType.Video && active)
                    LocalVideoPlayer(
                        media = media,
                        controlsVisible = showControls,
                        onControlsVisibility = {
                            showControls = it
                        },
                        playbackEnabled = !closing && !dismissDragging && !pager.isScrollInProgress,
                        onPlayerAvailable = { player ->
                            if (player != null) videoBinding = media.id to player
                            else if (videoBinding?.first == media.id) videoBinding = null
                        }
                    )                else ZoomablePhoto(media, loader, thumbnails, active && media.type == MediaType.Image,
                    transform = if (active) currentTransform else remember(media.id) { PhotoTransform() },
                    onZoomed = { zoomed -> if (zoomed) zoomedId = media.id else if (zoomedId == media.id) zoomedId = null },
                    onTap = { if (!closing && !deletionBusy) showControls = !showControls }, previewPixels = previewPixels)
            }
            val enter = fadeIn(tween(GalleryMotion.ShowControls, delayMillis = 40, easing = GalleryMotion.Easing))
            val exit = fadeOut(tween(GalleryMotion.HideControls, easing = GalleryMotion.Easing))
            AnimatedVisibility(showControls && visible, enter = enter + slideInVertically(tween(GalleryMotion.ShowControls, easing = GalleryMotion.Easing)) { -it / 8 },
                exit = exit + slideOutVertically(tween(GalleryMotion.HideControls, easing = GalleryMotion.Easing)) { -it / 8 }, modifier = Modifier.align(Alignment.TopCenter)) {
                Row(Modifier.fillMaxWidth().onGloballyPositioned { topToolbarBounds.value = it.boundsInRoot() }.background(Color.Black.copy(alpha = 0.5f))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::close, enabled = !deletionBusy) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.close), tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(current.filename, color = Color.White, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        Text("${pager.currentPage.coerceAtMost(items.lastIndex) + 1} / ${items.size}", color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.testTag("viewer_position"))
                    }
                    var overflow by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { overflow = true }) { Icon(painterResource(R.drawable.ic_more), stringResource(R.string.more_options), tint = Color.White) }
                        DropdownMenu(overflow, { overflow = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.information)) }, onClick = { overflow = false; showInfo = true })
                        }
                    }
                }
            }
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val shareError = stringResource(R.string.share_failed)
            val shareTitle = stringResource(R.string.share)
            AnimatedVisibility(
                visible = showControls && visible,

                enter = enter + slideInVertically(
                    animationSpec = tween(
                        GalleryMotion.ShowControls,
                        easing = GalleryMotion.Easing
                    )
                ) {
                    it / 8
                },

                exit = exit + slideOutVertically(
                    animationSpec = tween(
                        GalleryMotion.HideControls,
                        easing = GalleryMotion.Easing
                    )
                ) {
                    it / 8
                },

                modifier = Modifier.align(Alignment.BottomCenter)
            ) {

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .onGloballyPositioned { bottomToolbarBounds.value = it.boundsInRoot() }
                        .fillMaxWidth()
                        .background(Color.Black)
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp)
                        .padding(top = 4.dp, bottom = 4.dp)
                ) {

                    // VIDEO TIMELINE

                    if (current.type == MediaType.Video) {

                        VideoSeekRow(
                            player = videoBinding?.takeIf { it.first == current.id }?.second,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(
                            modifier = Modifier.height(2.dp)
                        )
                    }

                    // SHARED GALLERY ACTIONS

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        ViewerAction(
                            R.drawable.ic_share,
                            R.string.share,
                            !deletionBusy && !closing
                        ) {

                            try {
                                val intent = Intent(Intent.ACTION_SEND).apply {

                                    type = if (current.type == MediaType.Image) {
                                        "image/*"
                                    } else {
                                        "video/*"
                                    }

                                    putExtra(
                                        Intent.EXTRA_STREAM,
                                        current.contentUri
                                    )

                                    clipData = ClipData.newRawUri(
                                        current.filename,
                                        current.contentUri
                                    )

                                    addFlags(
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                }

                                context.startActivity(
                                    Intent.createChooser(intent, shareTitle)
                                )

                            } catch (_: Exception) {

                                scope.launch {
                                    snackbar.showSnackbar(shareError)
                                }
                            }
                        }

                        ViewerAction(
                            R.drawable.ic_info,
                            R.string.information,
                            !deletionBusy && !closing
                        ) {
                            showInfo = true
                        }

                        IconButton(
                            onClick = {
                                toggleFavorite(current)
                            },
                            enabled = !deletionBusy && !closing
                        ) {

                            val favorite = current.id in favorites

                            Icon(
                                painter = painterResource(
                                    if (favorite) {
                                        R.drawable.ic_favorite
                                    } else {
                                        R.drawable.ic_favorite_outline
                                    }
                                ),

                                contentDescription = stringResource(
                                    if (favorite) {
                                        R.string.remove_favorite
                                    } else {
                                        R.string.add_favorite
                                    }
                                ),

                                tint = if (favorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.White
                                }
                            )
                        }

                        ViewerAction(
                            R.drawable.ic_delete,
                            R.string.delete,
                            !deletionBusy && !closing
                        ) {
                            deleteId = current.id
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp))
        }
        val deleteItem = items.find { it.id == deleteId }
        if (deleteItem != null) AlertDialog(onDismissRequest = { deleteId = null },
            title = { Text(stringResource(R.string.delete_title)) }, text = { Text(stringResource(R.string.delete_message)) },
            confirmButton = { TextButton(onClick = { deleteId = null; onDelete(deleteItem) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text(stringResource(R.string.cancel)) } })
        if (showInfo && !closing) PhotoInformation(current, repository) { showInfo = false }
    }
}

@Composable
private fun ViewerAction(icon: Int, label: Int, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icon), stringResource(label), tint = if (enabled) Color.White else Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoSeekRow(
    player: Player?,
    modifier: Modifier = Modifier
) {
    var position by remember(player) {
        mutableLongStateOf(0L)
    }

    var duration by remember(player) {
        mutableLongStateOf(0L)
    }

    var isDragging by remember(player) {
        mutableStateOf(false)
    }

    var dragValue by remember(player) {
        mutableFloatStateOf(0f)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    fun readPosition() {
        if (player == null) return
        duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        if (!isDragging) position = player.currentPosition.coerceAtLeast(0L)
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { readPosition() }
        }
        player?.addListener(listener)
        readPosition()
        onDispose { player?.removeListener(listener) }
    }
    LaunchedEffect(player, lifecycle) {
        if (player == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                readPosition()
                delay(if (player.isPlaying) 100L else 500L)
            }
        }
    }
    val displayedSeconds = (if (isDragging) (dragValue * duration).toLong() else position) / 1000L
    val elapsedText = remember(displayedSeconds) { formatPlaybackTime(displayedSeconds * 1000L) }
    val durationSeconds = duration / 1000L
    val durationText = remember(durationSeconds) { formatPlaybackTime(durationSeconds * 1000L) }

    val progress = if (isDragging) {
        dragValue
    } else if (duration > 0L) {
        (position.toFloat() / duration).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {

        Slider(
            value = progress,

            onValueChange = {
                if (duration > 0L) {
                    isDragging = true
                    dragValue = it
                }
            },

            onValueChangeFinished = {
                if (duration > 0L) {
                    player?.seekTo(
                        (dragValue * duration).toLong()
                    )
                }

                isDragging = false
            },

            enabled = player != null && duration > 0L,

            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .offset(y = 5.dp),

            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            ),

            thumb = {
                Box(
                    modifier = Modifier
                        .size(
                            if (isDragging) 15.dp else 10.dp
                        )
                        .background(
                            Color.White,
                            CircleShape
                        )
                )
            },

            track = { sliderState ->

                SliderDefaults.Track(
                    sliderState = sliderState,

                    modifier = Modifier.height(
                        if (isDragging) 5.dp else 3.dp
                    ),

                    colors = SliderDefaults.colors(
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(
                            alpha = 0.25f
                        )
                    ),

                    thumbTrackGapSize = 0.dp,
                    trackInsideCornerSize = 0.dp
                )
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Text(
                text = elapsedText,
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                text = durationText,
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatPlaybackTime(timeMs: Long): String {
    val totalSeconds = timeMs.coerceAtLeast(0L) / 1000L

    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L

    return if (hours > 0L) {
        String.format(
            java.util.Locale.US,
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds
        )
    } else {
        String.format(
            java.util.Locale.US,
            "%02d:%02d",
            minutes,
            seconds
        )
    }
}
