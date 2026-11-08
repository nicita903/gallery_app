package com.nichita.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.nichita.gallery.R
import com.nichita.gallery.data.MediaItem

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LocalVideoPlayer(media: MediaItem, controlsVisible: Boolean = true, onControlsVisibility: (Boolean) -> Unit = {}, playbackEnabled: Boolean = true) {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(media.id) { mutableStateOf(false) }
    var playing by remember(media.id) { mutableStateOf(false) }
    var resumePosition by rememberSaveable(media.id) { mutableLongStateOf(0L) }
    val controlsCallback by rememberUpdatedState(onControlsVisibility)
    val player = remember(media.id, media.cacheVersion) {
        // ContentDataSource cannot open network URLs; playback is strictly local.
        ExoPlayer.Builder(app).build().apply {
            val factory = DataSource.Factory { ContentDataSource(app) }
            setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(PlayerMediaItem.fromUri(media.contentUri)))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { failed = true }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            })
            seekTo(resumePosition)
            prepare()
            playWhenReady = playbackEnabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
    }
    LaunchedEffect(playbackEnabled) { if (!playbackEnabled) player.pause() }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                resumePosition = player.currentPosition
                player.pause()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { resumePosition = player.currentPosition; lifecycle.removeObserver(observer); player.release() }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { PlayerView(it).apply {
            this.player = player; useController = true
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility -> controlsCallback(visibility == android.view.View.VISIBLE) })
        } },
            update = {
                it.player = player; it.keepScreenOn = playing; it.useController = playbackEnabled
                if (controlsVisible && playbackEnabled) it.showController() else it.hideController()
            },
            onRelease = { it.setControllerVisibilityListener(null as PlayerView.ControllerVisibilityListener?); it.player = null; it.keepScreenOn = false },
            modifier = Modifier.fillMaxSize())
        if (failed) Text(stringResource(R.string.video_unavailable), color = Color.White, modifier = Modifier.padding(24.dp))
    }
}
