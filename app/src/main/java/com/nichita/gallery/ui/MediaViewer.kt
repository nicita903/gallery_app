package com.nichita.gallery.ui

import android.content.ClipData
import android.content.Intent
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import coil3.ImageLoader
import com.nichita.gallery.R
import com.nichita.gallery.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Composable
fun MediaViewer(items: List<MediaItem>, initialId: String, loader: ImageLoader,
    onCurrentItem: (String) -> Unit, onClose: () -> Unit, onDelete: (MediaItem) -> Unit = {},
    thumbnails: ImageLoader = loader, deletionBusy: Boolean = false) {
    if (items.isEmpty()) return
    val pager = rememberPagerState(initialPage = items.indexOfFirst { it.id == initialId }.coerceAtLeast(0), pageCount = { items.size })
    var zoomedId by remember { mutableStateOf<String?>(null) }
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showInfo by rememberSaveable { mutableStateOf(false) }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val current = items.getOrNull(pager.currentPage) ?: items.last()
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
    fun close() { if (!deletionBusy) { closing = true; visible = false } }
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
        Box(Modifier.fillMaxSize().drawBehind { drawRect(Color.Black.copy(alpha = progress.value)) }.testTag("media_viewer")) {
            HorizontalPager(state = pager, key = { items.getOrNull(it)?.id ?: "missing:$it" },
                userScrollEnabled = zoomedId != current.id && !showInfo && !deletionBusy && !closing,
                modifier = Modifier.fillMaxSize().testTag("viewer_pager").graphicsLayer {
                    alpha = progress.value
                    val scale = 0.95f + 0.05f * progress.value
                    scaleX = scale; scaleY = scale
                }) { page ->
                val media = items.getOrNull(page) ?: return@HorizontalPager
                val active = page == pager.currentPage
                if (media.type == MediaType.Video && active && !pager.isScrollInProgress)
                    LocalVideoPlayer(media, showControls, onControlsVisibility = { showControls = it }, playbackEnabled = !closing)
                else ZoomablePhoto(media, loader, thumbnails, active && media.type == MediaType.Image,
                    transform = if (active) currentTransform else remember(media.id) { PhotoTransform() },
                    onZoomed = { zoomed -> if (zoomed) zoomedId = media.id else if (zoomedId == media.id) zoomedId = null },
                    onTap = { if (!closing && !deletionBusy) showControls = !showControls })
            }
            val enter = fadeIn(tween(GalleryMotion.ShowControls, delayMillis = 40, easing = GalleryMotion.Easing))
            val exit = fadeOut(tween(GalleryMotion.HideControls, easing = GalleryMotion.Easing))
            AnimatedVisibility(showControls && visible, enter = enter + slideInVertically(tween(GalleryMotion.ShowControls, easing = GalleryMotion.Easing)) { -it / 8 },
                exit = exit + slideOutVertically(tween(GalleryMotion.HideControls, easing = GalleryMotion.Easing)) { -it / 8 }, modifier = Modifier.align(Alignment.TopCenter)) {
                Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.5f))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::close, enabled = !deletionBusy) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close), tint = Color.White) }
                    val date = remember(current.creationDate) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(current.creationDate)) }
                    Text(date, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${pager.currentPage.coerceAtMost(items.lastIndex) + 1} / ${items.size}", color = Color.LightGray, modifier = Modifier.padding(end = 16.dp).testTag("viewer_position"))
                }
            }
            val snackbar = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            val shareError = stringResource(R.string.share_failed)
            val shareTitle = stringResource(R.string.share)
            AnimatedVisibility(showControls && visible, enter = enter + slideInVertically(tween(GalleryMotion.ShowControls, easing = GalleryMotion.Easing)) { it / 8 },
                exit = exit + slideOutVertically(tween(GalleryMotion.HideControls, easing = GalleryMotion.Easing)) { it / 8 }, modifier = Modifier.align(Alignment.BottomCenter)) {
                Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.58f))
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    ViewerAction(R.drawable.ic_share, R.string.share, !deletionBusy && !closing) {
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = if (current.type == MediaType.Image) "image/*" else "video/*"
                                putExtra(Intent.EXTRA_STREAM, current.contentUri)
                                clipData = ClipData.newRawUri(current.filename, current.contentUri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, shareTitle))
                        } catch (_: Exception) { scope.launch { snackbar.showSnackbar(shareError) } }
                    }
                    ViewerAction(R.drawable.ic_info, R.string.information, !deletionBusy && !closing) { showInfo = true }
                    ViewerAction(R.drawable.ic_delete, R.string.delete, !deletionBusy && !closing) { deleteId = current.id }
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
