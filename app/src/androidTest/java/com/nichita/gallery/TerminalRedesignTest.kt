package com.nichita.gallery

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
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
import java.io.File
import java.io.FileOutputStream

class TerminalRedesignTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var loader: ImageLoader

    private fun content() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.DKGRAY) }
        loader = ImageLoader.Builder(context).components {
            add(object : Fetcher.Factory<ThumbnailRequest> {
                override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader) = object : Fetcher {
                    override suspend fun fetch() = ImageFetchResult(bitmap.asImage(), false, DataSource.MEMORY)
                }
            })
        }.build()
        val library = buildLibrary(List(120) { i -> MediaItem("fixture:$i", Uri.parse("content://terminal/$i"),
            "Holiday $i.jpg", MediaType.Image, 1800000000000L - i * 86400000L, "camera", "Camera", 32, 32) })
        compose.setContent {
            var state by remember { mutableStateOf(GalleryUiState(library = library, access = MediaAccess(true, true), loading = false)) }
            DisposableEffect(loader) { onDispose { loader.shutdown() } }
            GalleryTheme(state.theme != ThemeMode.Light) {
                GalleryContent(state, loader, { loader }, { state = state.copy(tab = it) }, {},
                    { state = state.copy(columns = it) }, {}, {},
                    search = { state = state.copy(query = it, searchLibrary = LocalSearchIndex(library).search(it)) },
                    setTheme = { state = state.copy(theme = it) },
                    setOldest = { state = state.copy(oldestFirst = it) },
                    setAlbumNames = { state = state.copy(albumNames = it) },
                    toggleFavorite = { media -> state = state.copy(favorites = if (media.id in state.favorites)
                        state.favorites - media.id else state.favorites + media.id) })
            }
        }
    }
    private fun tab(label: Int) = compose.onAllNodesWithText(context.getString(label)).onLast().performClick()
    private fun preview(name: String) {
        FileOutputStream(File(context.externalCacheDir, "$name.png")).use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun terminalHeadersAndAlbumRowsRender() {
        content()
        preview("terminal-photos")
        tab(R.string.albums)
        compose.onNodeWithText("Camera").assertIsDisplayed()
        compose.onNodeWithText(context.resources.getQuantityString(R.plurals.item_count, 120, 120)).assertIsDisplayed()
        preview("terminal-albums")
        tab(R.string.settings)
        preview("terminal-settings")
    }

    @Test fun searchOpensMatchingMediaAndFavoriteToggleWorks() {
        content()
        tab(R.string.search)
        compose.onNodeWithTag("search_input").performTextInput("holiday 119")
        compose.onNodeWithContentDescription("Holiday 119.jpg").performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.add_favorite)).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.remove_favorite)).assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.add_favorite)).assertIsDisplayed()
        val screen = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        FileOutputStream(File(context.externalCacheDir, "terminal-viewer.png")).use { screen.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screen.recycle()
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.onNodeWithTag("search_input").assertTextContains("holiday 119")
    }

    @Test fun settingsChangesThemeGridAndOrderAndPhotosKeepTheirScroll() {
        content()
        compose.onNodeWithTag("media_grid").performScrollToIndex(45)
        tab(R.string.settings)
        compose.onNodeWithText(context.getString(R.string.theme)).performClick()
        compose.onNodeWithText(context.getString(R.string.theme_light)).performClick()
        compose.onNodeWithText(context.getString(R.string.theme_light)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.grid_layout)).performClick()
        compose.onNodeWithText(context.getString(R.string.grid_columns, 4)).performClick()
        compose.onNodeWithText(context.getString(R.string.sort_order)).performClick()
        compose.onNodeWithText(context.getString(R.string.oldest)).performClick()
        compose.onNodeWithText(context.getString(R.string.oldest)).assertIsDisplayed()
        tab(R.string.photos)
        compose.onNodeWithTag("scroll_to_top").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.grid_columns, 4)).assertExists()
    }
}
