package com.nichita.gallery

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.nichita.gallery.data.*
import com.nichita.gallery.ui.*
import com.nichita.gallery.ui.theme.GalleryTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Gallery13ViewerTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()
    private var imageLoader: ImageLoader? = null
    private var deleted = 0
    private fun content(restoration: StateRestorationTester? = null) {
        val bitmap = Bitmap.createBitmap(96, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(54, 126, 102)) }
        val items = List(3) { index ->
            val file = File.createTempFile("gallery13-viewer-", ".jpg", context.cacheDir).also { files += it }
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
            MediaItem("viewer:$index", Uri.fromFile(file), "Test photo $index", MediaType.Image, 1700000000000L,
                "test", "Viewer test", 96, 64)
        }
        val loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<ThumbnailRequest> {
                override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader) = object : Fetcher {
                    override suspend fun fetch() = ImageFetchResult(bitmap.asImage(), false, DataSource.MEMORY)
                }
            })
        }.build().also { imageLoader = it }
        val render: @Composable () -> Unit = {
            var state by remember { mutableStateOf(GalleryUiState(library = buildLibrary(items), access = MediaAccess(true, true), loading = false)) }
            GalleryTheme {
                GalleryContent(state, loader, { loader }, { state = state.copy(tab = it) }, {}, {}, {}, {},
                    onDelete = { item -> deleted++; state = state.copy(library = buildLibrary(state.library.items.filterNot { it.id == item.id })) })
            }
        }
        if (restoration != null) restoration.setContent(render) else compose.setContent(render)
    }
    private fun open() { compose.onNodeWithContentDescription("Test photo 0").performClick() }
    private fun assertZoom(percent: Int) {
        compose.onNodeWithTag("viewer_photo").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.zoom_level, percent)))
    }
    @Test fun zoomControlsAndSheetDoNotCompete() {
        content(); open()
        compose.onNodeWithTag("viewer_photo").performTouchInput { doubleClick(center) }
        assertZoom(300)
        compose.onNodeWithTag("viewer_photo").performTouchInput { click(center) }
        compose.waitUntil(3000) { compose.onAllNodesWithContentDescription(context.getString(R.string.close)).fetchSemanticsNodes().isEmpty() }
        screenshot("gallery13-immersive")
        assertZoom(300)
        compose.onNodeWithTag("viewer_photo").performTouchInput { click(center) }
        compose.waitUntil(3000) { compose.onAllNodesWithContentDescription(context.getString(R.string.information)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription(context.getString(R.string.information)).performClick()
        compose.onNodeWithTag("photo_information").assertIsDisplayed()
        screenshot("gallery13-information")
        Espresso.pressBack()
        assertZoom(300)
        compose.onNodeWithTag("viewer_photo").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("viewer_position").assertTextEquals("1 / 3")
        compose.onNodeWithTag("viewer_photo").performTouchInput { doubleClick(center) }
        assertZoom(100)
        compose.onNodeWithTag("viewer_pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("viewer_position").assertTextEquals("2 / 3")
    }
    @Test fun confirmedDeletionSelectsANeighbourAndClosesOnlyWhenEmpty() {
        content(); open()
        compose.onNodeWithContentDescription(context.getString(R.string.delete)).performClick()
        compose.onNodeWithText(context.getString(R.string.cancel)).performClick()
        assertEquals(0, deleted)
        compose.onNodeWithTag("viewer_position").assertTextEquals("1 / 3")
        repeat(3) { index ->
            compose.onNodeWithContentDescription(context.getString(R.string.delete)).performClick()
            compose.onNodeWithText(context.getString(R.string.delete)).performClick()
            if (index < 2) compose.onNodeWithTag("viewer_position").assertTextEquals("1 / ${2 - index}")
        }
        compose.onNodeWithTag("media_viewer").assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.empty_title)).assertIsDisplayed()
    }
    @Test fun rapidOpenCloseAndSavedViewerState() {
        val restore = StateRestorationTester(compose)
        content(restore)
        repeat(4) { open(); compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick() }
        open()
        compose.onNodeWithTag("viewer_photo").performTouchInput { doubleClick(center) }
        assertZoom(300)
        restore.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("media_viewer").assertIsDisplayed()
        compose.onNodeWithTag("viewer_position").assertTextEquals("1 / 3")
        assertZoom(300)
    }
    @After fun cleanup() { imageLoader?.shutdown(); files.forEach { it.delete() } }

    private fun screenshot(name: String) {
        Thread.sleep(300)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        FileOutputStream(File(context.externalCacheDir, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
