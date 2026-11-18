package com.nichita.gallery.ui

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ContentDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.nichita.gallery.data.MediaItem
import com.nichita.gallery.R
import androidx.compose.ui.res.stringResource

@OptIn(UnstableApi::class)
@Composable
fun LocalVideoPlayer(
    media: MediaItem,
    controlsVisible: Boolean = true,
    onControlsVisibility: (Boolean) -> Unit = {},
    playbackEnabled: Boolean = true,
    onPlayerAvailable: (Player?) -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var failed by remember(media.id, media.cacheVersion) {
        mutableStateOf(false)
    }

    var playing by remember(media.id, media.cacheVersion) {
        mutableStateOf(false)
    }

    var resumePosition by rememberSaveable(media.id) {
        mutableLongStateOf(0L)
    }

    val playerCallback by rememberUpdatedState(onPlayerAvailable)
    val controlsCallback by rememberUpdatedState(onControlsVisibility)
    val controlsShown by rememberUpdatedState(controlsVisible)
    val canPlay by rememberUpdatedState(playbackEnabled)

    val player = remember(media.id, media.cacheVersion) {
        ExoPlayer.Builder(app).build().apply {

            val factory = DataSource.Factory {
                ContentDataSource(app)
            }

            setMediaSource(
                ProgressiveMediaSource.Factory(factory)
                    .createMediaSource(
                        PlayerMediaItem.fromUri(media.contentUri)
                    )
            )

            addListener(object : Player.Listener {
                override fun onPlayerError(
                    error: PlaybackException
                ) {
                    failed = true
                }

                override fun onIsPlayingChanged(
                    isPlaying: Boolean
                ) {
                    playing = isPlaying
                }
            })

        }
    }

    var resumeWhenEnabled by remember(player) { mutableStateOf(false) }
    LaunchedEffect(player) {
        player.seekTo(resumePosition)
        player.playWhenReady = canPlay && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        player.prepare()
    }
    LaunchedEffect(playbackEnabled, player) {
        if (!playbackEnabled) {
            resumeWhenEnabled = player.playWhenReady
            player.pause()
        } else if (resumeWhenEnabled && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            player.play()
            resumeWhenEnabled = false
        }
    }

    DisposableEffect(player, lifecycle) {

        playerCallback(player)

        val observer = LifecycleEventObserver { _, event ->
            if (
                event == Lifecycle.Event.ON_PAUSE ||
                event == Lifecycle.Event.ON_STOP
            ) {
                resumePosition = player.currentPosition
                resumeWhenEnabled = false
                player.pause()
            }
        }

        lifecycle.addObserver(observer)

        onDispose {
            resumePosition = player.currentPosition
            lifecycle.removeObserver(observer)
            playerCallback(null)
            player.release()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        // VIDEO SURFACE — NO BUILT-IN CONTROLS

        AndroidView(
            factory = {
                PlayerView(it).apply {
                    this.player = player

                    // Prevent duplicate control layers.
                    useController = false
                    setEnableComposeSurfaceSyncWorkaround(true)

                    keepScreenOn = playing
                }
            },

            update = {
                it.player = player
                it.useController = false
                it.keepScreenOn = playing
            },

            onRelease = {
                it.player = null
                it.keepScreenOn = false
            },

            modifier = Modifier.fillMaxSize()
        )

        // TAP ANYWHERE ON VIDEO TO TOGGLE CONTROLS

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(media.id) {
                    detectTapGestures(
                        onTap = {
                            controlsCallback(!controlsShown)
                        }
                    )
                }
        )

        // CENTER PLAYBACK CONTROLS

        AnimatedVisibility(
            visible = controlsVisible && !failed && playbackEnabled,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(150))
        ) {

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {

                VideoControlButton(
                    text = "−5",
                    onClick = {
                        player.seekTo(
                            (player.currentPosition - 5000L)
                                .coerceAtLeast(0L)
                        )
                    }
                )

                VideoControlButton(
                    text = if (playing) "Ⅱ" else "▶",
                    large = true,
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                        } else if (canPlay && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                            player.play()
                        }
                    }
                )

                VideoControlButton(
                    text = "+5",
                    onClick = {
                        val duration = player.duration

                        val target = player.currentPosition + 5000L

                        player.seekTo(
                            if (duration > 0L) {
                                target.coerceAtMost(duration)
                            } else {
                                target
                            }
                        )
                    }
                )
            }
        }

        if (failed) {
            Text(
                text = stringResource(R.string.video_unavailable),
                color = Color.White
            )
        }
    }
}

@Composable
private fun VideoControlButton(
    text: String,
    large: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        contentColor = Color.White,
        modifier = Modifier.size(
            if (large) 62.dp else 46.dp
        )
    ) {

        Box(
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = if (large) {
                    MaterialTheme.typography.headlineMedium
                } else {
                    MaterialTheme.typography.titleMedium
                }
            )
        }
    }
}
