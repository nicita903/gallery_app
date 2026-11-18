package com.nichita.gallery

import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import com.nichita.gallery.data.*
import com.nichita.gallery.ui.LocalVideoPlayer
import com.nichita.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class Gallery13VideoTest {
    @get:Rule val compose = createComposeRule()
    @Test fun localVideoPreparesSeeksAndDetachesWithoutAutoplay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val access = MediaPermissions.read(context)
        assumeTrue(access.canRead)
        val media = MediaRepository(context).load(access).items.firstOrNull { it.type == MediaType.Video }
        assumeTrue(media != null)
        requireNotNull(media)
        var root: View? = null
        var visible by mutableStateOf(true)
        var controlsVisible by mutableStateOf(true)
        compose.setContent {
            root = LocalView.current
            GalleryTheme { if (visible) LocalVideoPlayer(media, controlsVisible,
                onControlsVisibility = { controlsVisible = it }, playbackEnabled = false) }
        }
        var view: PlayerView? = null
        compose.waitUntil(15000) {
            var ready = false
            compose.runOnUiThread { view = root?.let(::findPlayer); ready = view?.player?.playbackState == Player.STATE_READY }
            ready
        }
        var target = 0L
        var originalPlayer: Player? = null
        compose.runOnUiThread {
            val player = requireNotNull(view?.player)
            originalPlayer = player
            assertTrue(player.duration > 0)
            assertFalse(player.playWhenReady)
            target = player.duration / 2
            player.seekTo(target)
        }
        compose.waitUntil(10000) {
            var seeked = false
            compose.runOnUiThread { seeked = view?.player?.let { it.playbackState == Player.STATE_READY && kotlin.math.abs(it.currentPosition - target) < 1000 } == true }
            seeked
        }
        repeat(2) { index ->
            compose.onRoot().performTouchInput { click(Offset(width * 0.2f, height * 0.2f)) }
            compose.runOnIdle {
                assertEquals(index == 1, controlsVisible)
                assertSame(originalPlayer, view?.player)
            }
        }
        compose.runOnIdle { visible = false }
        compose.waitForIdle()
        compose.runOnUiThread { assertNull(view?.player); assertFalse(view?.keepScreenOn ?: true) }
    }
    private fun findPlayer(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) findPlayer(view.getChildAt(index))?.let { return it }
        return null
    }
}
