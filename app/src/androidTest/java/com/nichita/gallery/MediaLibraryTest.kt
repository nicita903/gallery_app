package com.nichita.gallery

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nichita.gallery.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class MediaLibraryTest {
    @Test fun librarySortsAndKeepsSameNamedBucketsSeparate() {
        fun item(id: String, date: String, bucket: String) = MediaItem(id,
            Uri.parse("content://media/external_primary/images/media/$id"), "$id.jpg", MediaType.Image,
            Instant.parse(date).toEpochMilli(), bucket, "Camera", 100, 100)
        val library = buildLibrary(listOf(
            item("1", "2025-01-15T12:00:00Z", "primary:1"),
            item("2", "2025-02-15T12:00:00Z", "sdcard:1"),
            item("3", "2025-02-16T12:00:00Z", "primary:1")
        ))
        assertEquals(listOf("3", "2", "1"), library.items.map { it.id })
        assertEquals(2, library.sections.size)
        assertEquals(listOf(2, 1), library.sections.map { it.items.size })
        assertEquals(2, library.albums.size)
        assertEquals(2, library.albums.first().items.size)
    }

    @Suppress("DEPRECATION")
    @Test fun packagedAppHasNoNetworkOrBroadStoragePermission() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager.getPackageInfo(context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertFalse(permissions.contains("android.permission.INTERNET"))
        assertFalse(permissions.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertFalse(permissions.contains("android.permission.MANAGE_EXTERNAL_STORAGE"))
    }
}
