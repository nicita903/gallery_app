package com.nichita.gallery

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.lifecycle.ViewModelProvider
import com.nichita.gallery.ui.GalleryViewModel
import com.nichita.gallery.ui.GalleryTab
import android.content.pm.ActivityInfo
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nichita.gallery.data.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Gallery13MediaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver
    private var owned: Uri? = null
    private var foreign: Uri? = null
    private suspend fun helper(cleanup: Uri? = null): Uri? {
        val result = Channel<Uri?>(1)
        lateinit var launcher: ActivityResultLauncher<Intent>
        compose.runOnUiThread {
            launcher = compose.activity.activityResultRegistry.register("helper-${UUID.randomUUID()}", ActivityResultContracts.StartActivityForResult()) {
                result.trySend(it.data?.data)
            }
            val intent = Intent().setClassName("com.nichita.gallery.test", "com.nichita.gallery.ForeignMediaFixtureActivity")
            if (cleanup != null) { intent.action = "com.nichita.gallery.test.CLEANUP"; intent.data = cleanup }
            launcher.launch(intent)
        }
        return try { withTimeout(15000) { result.receive() } } finally { compose.runOnUiThread { launcher.unregister() } }
    }
    private fun fixture(): Uri {
        val uri = requireNotNull(resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Gallery13-test-${UUID.randomUUID()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Gallery13Tests")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }))
        owned = uri
        resolver.openOutputStream(uri)!!.use { stream ->
            val bitmap = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream); bitmap.recycle()
        }
        resolver.openFileDescriptor(uri, "rw")!!.use {
            ExifInterface(it.fileDescriptor).apply {
                setAttribute(ExifInterface.TAG_MAKE, "Gallery test maker")
                setAttribute(ExifInterface.TAG_MODEL, "Test camera")
                setAttribute(ExifInterface.TAG_F_NUMBER, "2.8")
                setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "0.008")
                saveAttributes()
            }
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }
    @Test fun metadataReadsExifWithoutImageDecodeAndHandlesMissingFiles() = runBlocking {
        val uri = fixture()
        val item = MediaItem("metadata-fixture", uri, "Metadata fixture.jpg", MediaType.Image, 1700000000000L, "test", "Tests", 64, 32)
        val repository = MediaMetadata(resolver)
        val details = repository.load(item)
        val fields = details.associate { it.label to it.value }
        assertEquals("Test camera", fields[R.string.camera_model])
        assertEquals("Gallery test maker", fields[R.string.manufacturer])
        assertEquals("1/125 s", fields[R.string.shutter_speed])
        assertEquals("image/jpeg", fields[R.string.format])
        assertSame(details, repository.load(item))
        val missing = repository.load(item.copy(id = "missing", contentUri = Uri.parse("content://media/external_primary/images/media/9223372036854775806")))
        assertTrue(missing.any { it.label == R.string.filename })
    }
    @Test fun activityRecreationAndRotationPreserveCurrentPhotoAndZoom() {
        val uri = fixture()
        val key = "external_primary:Image:${uri.lastPathSegment}"
        lateinit var model: GalleryViewModel
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity)[GalleryViewModel::class.java]
            model.selectTab(GalleryTab.Photos)
            model.refresh(force = true)
        }
        compose.waitUntil(10000) { !model.state.value.loading && model.state.value.library.items.any { it.id == key } }
        val filename = model.state.value.library.items.first { it.id == key }.filename
        compose.onNodeWithTag("media_grid").performScrollToKey(key)
        compose.onNodeWithContentDescription(filename).performClick()
        compose.onNodeWithTag("viewer_photo").performTouchInput { doubleClick(center) }
        val zoom = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, context.getString(R.string.zoom_level, 300))
        compose.onNodeWithTag("viewer_photo").assert(zoom)
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("viewer_photo").assert(zoom)
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.onNodeWithTag("viewer_photo").assert(zoom)
        compose.runOnUiThread { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
        compose.onNodeWithContentDescription(filename).assertIsDisplayed()
    }
    @androidx.test.filters.SdkSuppress(minSdkVersion = 30)
    @Test fun systemDeletionCancellationThenConfirmationTouchesOnlyOwnedFixture() = runBlocking {
        val uri = requireNotNull(helper()).also { foreign = it }
        val key = "external_primary:Image:${uri.lastPathSegment}"
        lateinit var model: GalleryViewModel
        compose.runOnUiThread {
            model = ViewModelProvider(compose.activity)[GalleryViewModel::class.java]
            model.selectTab(GalleryTab.Photos)
            model.refresh(force = true)
        }
        compose.waitUntil(10000) { !model.state.value.loading && model.state.value.library.items.any { it.id == key } }
        val name = model.state.value.library.items.first { it.id == key }.filename
        compose.onNodeWithTag("media_grid").performScrollToKey(key)
        compose.onNodeWithContentDescription(name).performClick()
        compose.onNodeWithContentDescription(context.getString(R.string.delete)).performClick()
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        clickSystemButton("Cancel")
        compose.waitUntil(5000) {
            compose.onAllNodesWithContentDescription(context.getString(R.string.delete)).fetchSemanticsNodes()
                .any { !it.config.contains(SemanticsProperties.Disabled) }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.delete)).assertIsEnabled()
        assertTrue(exists(uri))
        assertTrue(model.state.value.library.items.any { it.id == key })
        compose.onNodeWithContentDescription(context.getString(R.string.delete)).performClick()
        compose.onNodeWithText(context.getString(R.string.delete)).performClick()
        clickSystemButton("Delete")
        compose.waitUntil(10000) { !model.state.value.loading && model.state.value.library.items.none { it.id == key } }
        assertFalse(exists(uri))
        assertTrue(model.state.value.library.albums.none { album -> album.items.any { it.id == key } })
    }
    private fun exists(uri: Uri) = resolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)!!.use { it.moveToFirst() }
    private fun clickSystemButton(text: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(15000) {
            val root = automation.rootInActiveWindow
            var clicked = false
            if (root != null && root.packageName?.toString() != context.packageName) {
                val alternatives = if (text == "Cancel") listOf("Cancel", "Don't allow", "Deny") else listOf("Delete", "Allow")
                val node = alternatives.flatMap { label -> root.findAccessibilityNodeInfosByText(label) }
                    .firstOrNull { node -> alternatives.any { it.equals(node.text?.toString(), ignoreCase = true) } }
                if (node != null) {
                    var button: AccessibilityNodeInfo? = node
                    while (button != null && !button.isClickable) button = button.parent
                    clicked = button?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                }
            }
            clicked
        }
    }
    @After fun cleanup() = runBlocking {
        owned?.let { runCatching { resolver.delete(it, null, null) } }; owned = null
        foreign?.let { helper(it) }; foreign = null
    }
}
