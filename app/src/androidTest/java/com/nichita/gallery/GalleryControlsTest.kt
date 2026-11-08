package com.nichita.gallery

import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.nichita.gallery.data.*
import com.nichita.gallery.ui.*
import com.nichita.gallery.ui.theme.GalleryTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class GalleryControlsTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun content(dark: Boolean = true, pending: Boolean = false) {
        val fixture = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<ThumbnailRequest> {
                override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher = object : Fetcher {
                    override suspend fun fetch(): ImageFetchResult {
                        if (pending) kotlinx.coroutines.delay(30000)
                        return ImageFetchResult(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).asImage(), false, DataSource.MEMORY)
                    }
                }
            })
        }.build()
        val library = buildLibrary(List(600) { index ->
            MediaItem("fixture:$index", Uri.parse("content://gallery-test/$index"), "Fixture $index", MediaType.Image,
                Instant.parse(if (index < 5) "2026-11-15T12:00:00Z" else if (index < 520) "2026-10-15T12:00:00Z" else "2026-09-15T12:00:00Z").toEpochMilli(),
                "fixture", "Test album", 32, 32)
        })
        compose.setContent {
            var state by remember { mutableStateOf(GalleryUiState(library = library, access = MediaAccess(true, true), loading = false)) }
            DisposableEffect(fixture) { onDispose { fixture.shutdown() } }
            GalleryTheme(dark) {
                GalleryContent(state, fixture, { fixture }, { state = state.copy(tab = it) }, {}, { state = state.copy(columns = it) }, {}, {})
            }
        }
    }

    @Test fun fastScrollTopAndAlbums() {
        content()
        screenshot("gallery-dark")
        compose.onNodeWithTag("month_scrollbar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("scroll_to_top").assertIsDisplayed().performClick()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.albums)).performClick()
        compose.onNodeWithText("Test album").assertIsDisplayed()
        compose.onNodeWithContentDescription("Fixture 0").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.all_albums)).assertIsDisplayed().performClick()
    }

    @Test fun pinchChangesColumnsWithoutOpeningViewerInLightTheme() {
        content(dark = false)
        compose.onNodeWithTag("media_grid").performTouchInput {
            pinch(start0 = Offset(width * 0.15f, height * 0.55f), end0 = Offset(width * 0.35f, height * 0.55f),
                start1 = Offset(width * 0.85f, height * 0.55f), end1 = Offset(width * 0.65f, height * 0.55f), durationMillis = 400)
        }
        compose.onNodeWithContentDescription(context.getString(R.string.grid_columns, 7)).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertDoesNotExist()
        screenshot("gallery-light")
    }

    @Test fun draggingShowsAnAccurateMonthLabel() {
        content()
        compose.onNodeWithTag("month_scrollbar").performTouchInput {
            down(Offset(width - 6f, height * 0.4f))
            moveTo(Offset(width - 6f, height * 0.5f), 300)
        }
        compose.onNodeWithTag("month_indicator").assertTextEquals("October 2026")
        screenshot("gallery-month-drag")
        compose.onNodeWithTag("month_scrollbar").performTouchInput { up() }
    }

    @Test fun viewerReturnsToTheSameVisiblePhoto() {
        content()
        compose.onNodeWithTag("media_grid").performScrollToIndex(100)
        val media = compose.onAllNodes(hasClickAction() and hasContentDescription("Fixture", substring = true))
            .fetchSemanticsNodes().first().config[SemanticsProperties.ContentDescription].first()
        compose.onNodeWithContentDescription(media).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(media).assertIsDisplayed()
    }

    @Test fun controlsRespondWhileThumbnailsArePending() {
        content(pending = true)
        compose.onNodeWithTag("month_scrollbar").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(1f) }
        compose.onNodeWithTag("scroll_to_top").assertIsDisplayed().performClick()
        compose.onNodeWithText("November 2026").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        FileOutputStream(File(context.externalCacheDir, "$name.png")).use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
