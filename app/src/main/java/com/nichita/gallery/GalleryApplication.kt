package com.nichita.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.memory.MemoryCache
import coil3.video.VideoFrameDecoder
import com.nichita.gallery.data.ThumbnailFetcher

class GalleryApplication : Application() {
    private val viewerDelegate = lazy { createViewer() }
    fun clearImageMemory() {
        thumbnails.memoryCache?.clear()
        if (viewerDelegate.isInitialized()) viewerDelegate.value.memoryCache?.clear()
    }
    val thumbnails: ImageLoader by lazy {
        ImageLoader.Builder(this)
            .components { add(ThumbnailFetcher.Factory(contentResolver)) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(48L * 1024 * 1024).build() }
            .diskCache(null)
            .build()
    }
    val viewerImages: ImageLoader by viewerDelegate
    private fun createViewer(): ImageLoader =
        ImageLoader.Builder(this)
            .components { add(VideoFrameDecoder.Factory()) }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(32L * 1024 * 1024).build() }
            .diskCache(null)
            .build()
}
