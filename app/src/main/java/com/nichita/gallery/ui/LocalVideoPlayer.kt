package com.nichita.gallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
fun LocalVideoPlayer(media: MediaItem) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by remember(media.id) { mutableStateOf(false) }
    val player = remember(media.id) {
        // ContentDataSource cannot open network URLs; playback is strictly local.
        ExoPlayer.Builder(context).build().apply {
            val factory = DataSource.Factory { ContentDataSource(context) }
            setMediaSource(ProgressiveMediaSource.Factory(factory).createMediaSource(PlayerMediaItem.fromUri(media.contentUri)))
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) { failed = true }
            })
            prepare()
            playWhenReady = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); player.release() }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true; keepScreenOn = true } },
            update = { it.player = player },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize())
        if (failed) Text(stringResource(R.string.video_unavailable), color = Color.White, modifier = Modifier.padding(24.dp))
    }
}
