package com.nichita.gallery

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil3.request.SuccessResult
import com.nichita.gallery.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceThumbnailTest {
    @Test fun cachedPreviewReusesGridBucketButNeverAnotherRevision() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as GalleryApplication
        val access = MediaPermissions.read(app)
        assumeTrue(access.canRead)
        val item = MediaRepository(app).load(access).items.firstOrNull { it.type == MediaType.Image }
        assumeTrue(item != null)
        requireNotNull(item)
        val result = app.thumbnails.execute(thumbnailImageRequest(app, item, 320)) as coil3.request.SuccessResult
        assertSame(result.image, cachedThumbnail(app.thumbnails, item, 320)?.image)
        assertNotNull(cachedThumbnail(app.thumbnails, item, 512))
        assertNull(cachedThumbnail(app.thumbnails, item.copy(cacheVersion = "not-the-same-revision"), 320))
    }
    @Test fun unchangedFullAccessReusesMetadataSnapshot() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val access = MediaPermissions.read(context)
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 30 && access.isFull)
        val repository = MediaRepository(context)
        val first = repository.load(access)
        assertSame(first, repository.load(access))
        assertNotSame(first, repository.load(access, force = true))
    }
    @Test fun providerLoadsImageThumbnails() = runBlocking { verify(MediaType.Image) }
    @Test fun providerLoadsVideoThumbnails() = runBlocking { verify(MediaType.Video) }

    private suspend fun verify(type: MediaType) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as GalleryApplication
        val access = MediaPermissions.read(app)
        assumeTrue(access.canRead)
        val library = MediaRepository(app).load(access)
        assumeTrue(library.items.isNotEmpty())
            val item = library.items.firstOrNull { it.type == type }
            assumeTrue("No accessible $type fixture on this device", item != null)
            requireNotNull(item)
            val request = thumbnailImageRequest(app, item, 256)
            val result = app.thumbnails.execute(request)
            assertTrue("Provider failed for $type", result is SuccessResult)
            val again = app.thumbnails.execute(request)
            assertTrue(again is SuccessResult)
            assertEquals(coil3.decode.DataSource.MEMORY_CACHE, (again as SuccessResult).dataSource)
            val changed = app.thumbnails.execute(thumbnailImageRequest(app, item.copy(cacheVersion = "test-change"), 256))
            assertTrue(changed is SuccessResult)
            assertEquals(coil3.decode.DataSource.DISK, (changed as SuccessResult).dataSource)
    }
}
