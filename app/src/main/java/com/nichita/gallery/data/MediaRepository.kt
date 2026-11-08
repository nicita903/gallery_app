package com.nichita.gallery.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

class MediaRepository(context: Context) {
    private val context = context.applicationContext
    private val resolver = this.context.contentResolver

    @OptIn(ExperimentalCoroutinesApi::class)
    fun changes() = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        resolver.registerContentObserver(MediaStore.AUTHORITY_URI, true, observer)
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    suspend fun load(access: MediaAccess): MediaLibrary = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        for (volume in MediaStore.getExternalVolumeNames(context)) {
            for (type in MediaType.entries) {
                currentCoroutineContext().ensureActive()
                if (type == MediaType.Image && !access.images && !access.selected) continue
                if (type == MediaType.Video && !access.videos && !access.selected) continue
                val collection = if (type == MediaType.Image) MediaStore.Images.Media.getContentUri(volume)
                    else MediaStore.Video.Media.getContentUri(volume)
                val projection = arrayOf(
                    MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.WIDTH,
                    MediaStore.MediaColumns.HEIGHT, MediaStore.Images.Media.BUCKET_ID,
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH
                ) + if (type == MediaType.Video) arrayOf(MediaStore.MediaColumns.DURATION) else emptyArray()
                val selection = buildString {
                    append("${MediaStore.MediaColumns.IS_PENDING} = 0")
                    if (Build.VERSION.SDK_INT >= 30) append(" AND ${MediaStore.MediaColumns.IS_TRASHED} = 0")
                }
                val cursor = resolver.query(collection, projection, selection, null,
                    "${MediaStore.MediaColumns.DATE_ADDED} DESC") ?: throw IOException("Media provider unavailable")
                cursor.use {
                    val columns = projection.associateWith { column -> it.getColumnIndexOrThrow(column) }
                    fun number(column: String) = it.getLong(columns.getValue(column))
                    fun text(column: String) = it.getString(columns.getValue(column))
                    while (it.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val id = number(MediaStore.MediaColumns._ID)
                        val folder = text(MediaStore.MediaColumns.RELATIVE_PATH).orEmpty()
                        val bucket = text(MediaStore.Images.Media.BUCKET_ID) ?: folder
                        items += MediaItem(
                            id = "$volume:$type:$id", contentUri = ContentUris.withAppendedId(collection, id),
                            filename = text(MediaStore.MediaColumns.DISPLAY_NAME) ?: id.toString(), type = type,
                            creationDate = creationDate(number(MediaStore.MediaColumns.DATE_TAKEN),
                                number(MediaStore.MediaColumns.DATE_ADDED), number(MediaStore.MediaColumns.DATE_MODIFIED)),
                            albumId = "$volume:$bucket", albumName = text(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                                ?: folder.trimEnd('/').substringAfterLast('/').ifEmpty { volume },
                            width = number(MediaStore.MediaColumns.WIDTH).toInt(), height = number(MediaStore.MediaColumns.HEIGHT).toInt(),
                            durationMillis = if (type == MediaType.Video) number(MediaStore.MediaColumns.DURATION).coerceAtLeast(0) else null
                        )
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        buildLibrary(items)
    }
}
