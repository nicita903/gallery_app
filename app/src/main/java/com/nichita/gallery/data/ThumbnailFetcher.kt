package com.nichita.gallery.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Trace
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.request.ImageRequest
import coil3.size.Precision
import coil3.memory.MemoryCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

data class ThumbnailRequest(val uri: Uri, val version: String, val pixels: Int) {
    val cacheKey: String get() = "thumbnail:$uri:$version:$pixels"
}

// Small size buckets reuse thumbnails across nearby window sizes without decoding originals.
internal fun thumbnailPixels(displayPixels: Int): Int = ((displayPixels.coerceAtLeast(1) + 63) / 64 * 64).coerceAtMost(1024)

internal fun thumbnailImageRequest(context: Context, media: MediaItem, pixels: Int): ImageRequest {
    val data = ThumbnailRequest(media.contentUri, media.cacheVersion, thumbnailPixels(pixels))
    return ImageRequest.Builder(context.applicationContext).data(data).memoryCacheKey(data.cacheKey)
        .size(data.pixels).precision(Precision.INEXACT).build()
}

internal fun cachedThumbnail(loader: ImageLoader, media: MediaItem, pixels: Int): MemoryCache.Value? {
    val cache = loader.memoryCache ?: return null
    val size = thumbnailPixels(pixels)
    fun value(bucket: Int) = cache[MemoryCache.Key(ThumbnailRequest(media.contentUri, media.cacheVersion, bucket).cacheKey)]
    value(size)?.let { return it }
    // At most 16 direct lookups; never copy/scan all cache keys on a viewer tap.
    for (bucket in size - 64 downTo 64 step 64) value(bucket)?.let { return it }
    for (bucket in size + 64..1024 step 64) value(bucket)?.let { return it }
    return null
}

class ThumbnailFetcher private constructor(
    private val resolver: ContentResolver,
    private val data: ThumbnailRequest,
    private val permits: Semaphore
) : Fetcher {
    override suspend fun fetch(): ImageFetchResult = withContext(Dispatchers.IO) {
        permits.withPermit {
            val bitmap = suspendCancellableCoroutine { continuation ->
                val cancellation = CancellationSignal()
                continuation.invokeOnCancellation { cancellation.cancel() }
                Trace.beginSection("Gallery.loadThumbnail")
                try {
                    val result = resolver.loadThumbnail(data.uri, Size(data.pixels, data.pixels), cancellation)
                    continuation.resume(result, onCancellation = { _, value, _ -> value.recycle() })
                } catch (error: Exception) {
                    continuation.resumeWith(Result.failure(error))
                } finally {
                    Trace.endSection()
                }
            }
            // This model represents the complete thumbnail, not a sampled original. Its key
            // already contains the requested bucket, so a larger tile uses a different key.
            ImageFetchResult(bitmap.asImage(), isSampled = false, dataSource = DataSource.DISK)
        }
    }

    class Factory(private val resolver: ContentResolver) : Fetcher.Factory<ThumbnailRequest> {
        private val permits = Semaphore(2)
        override fun create(data: ThumbnailRequest, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbnailFetcher(resolver, data, permits)
    }
}
