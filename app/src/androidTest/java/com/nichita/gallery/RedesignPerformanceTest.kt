package com.nichita.gallery

import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.nichita.gallery.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RedesignPerformanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun realLibraryScrollsBothDirectionsAndSearchReusesItsSnapshot() {
        lateinit var model: GalleryViewModel
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity)[GalleryViewModel::class.java]
            model.selectTab(GalleryTab.Photos)
        }
        compose.waitUntil(20000) { !model.state.value.loading && model.state.value.displayLibrary != null }
        val before = model.state.value.library
        org.junit.Assume.assumeTrue(before.items.isNotEmpty() && !model.state.value.favoritesOnly)
        val start = SystemClock.elapsedRealtime()
        repeat(8) { compose.onNodeWithTag("media_grid").performTouchInput { swipeUp(durationMillis = 240) } }
        repeat(8) { compose.onNodeWithTag("media_grid").performTouchInput { swipeDown(durationMillis = 240) } }
        val item = before.items.first()
        compose.runOnUiThread {
            model.selectTab(GalleryTab.Search)
            model.search(item.filename)
        }
        compose.waitUntil(10000) { model.state.value.searchLibrary.items.any { it.id == item.id } }
        assertSame(before, model.state.value.library)
        compose.onNodeWithTag("media_grid").performScrollToKey(item.id)
        compose.onNodeWithContentDescription(item.filename).performClick()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.close)).performClick()
        compose.runOnUiThread { model.search(""); model.selectTab(GalleryTab.Photos) }
        Log.i("GalleryRedesignTest", "Bidirectional scripted browsing: ${SystemClock.elapsedRealtime() - start} ms; PSS: ${Debug.getPss()} KiB; metadata items: ${before.items.size}")
    }
}
