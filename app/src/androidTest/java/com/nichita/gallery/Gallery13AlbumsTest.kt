package com.nichita.gallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
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
import org.junit.Rule
import org.junit.Test

class Gallery13AlbumsTest {
    @get:Rule val compose = createComposeRule()
    @Test fun variableHeightAlbumsScrollAndRestoreTheirListPosition() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        val loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<ThumbnailRequest> {
                override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader) = object : Fetcher {
                    override suspend fun fetch() = ImageFetchResult(bitmap.asImage(), false, DataSource.MEMORY)
                }
            })
        }.build()
        val library = buildLibrary(List(180) { index -> MediaItem("album:$index", Uri.parse("content://album-test/$index"), "Album media $index",
            MediaType.Image, 1700000000000L, "album:$index", if (index % 2 == 0) "Album $index" else "A longer album name with two lines $index", 32, 32) })
        compose.setContent {
            var state by remember { mutableStateOf(GalleryUiState(tab = GalleryTab.Albums, library = library, access = MediaAccess(true, true), loading = false)) }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            GalleryTheme { GalleryContent(state, loader, { loader }, { state = state.copy(tab = it) }, {}, {}, {}, {}) }
        }
        compose.onNodeWithTag("albums_scrollbar").performSemanticsAction(SemanticsActions.SetProgress) { it(0.7f) }
        compose.onNodeWithTag("scroll_to_top").assertIsDisplayed()
        val cover = compose.onAllNodes(hasClickAction() and hasContentDescription("Album media", substring = true))
            .fetchSemanticsNodes().first().config[SemanticsProperties.ContentDescription].first()
        compose.onNodeWithContentDescription(cover).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.all_albums)).performClick()
        compose.onNodeWithContentDescription(cover).assertIsDisplayed()
        compose.onNodeWithTag("scroll_to_top").performClick()
        compose.onNodeWithContentDescription("Album media 0").assertIsDisplayed()
    }
}
