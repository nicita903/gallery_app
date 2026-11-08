package com.nichita.gallery.data

import android.net.Uri
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MediaType { Image, Video }

data class MediaItem(
    val id: String,
    val contentUri: Uri,
    val filename: String,
    val type: MediaType,
    val creationDate: Long,
    val albumId: String,
    val albumName: String,
    val width: Int,
    val height: Int,
    val durationMillis: Long? = null
)

data class MediaSection(val month: String, val items: List<MediaItem>)
data class MediaAlbum(val id: String, val name: String, val items: List<MediaItem>, val sections: List<MediaSection>)
data class MediaLibrary(
    val items: List<MediaItem> = emptyList(),
    val sections: List<MediaSection> = emptyList(),
    val albums: List<MediaAlbum> = emptyList()
)

internal fun creationDate(takenMillis: Long, addedSeconds: Long, modifiedSeconds: Long): Long =
    takenMillis.takeIf { it > 0 } ?: (addedSeconds.takeIf { it > 0 } ?: modifiedSeconds.coerceAtLeast(0)) * 1000

internal fun buildLibrary(items: List<MediaItem>): MediaLibrary {
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).withZone(ZoneId.systemDefault())
    fun sections(media: List<MediaItem>) = media.groupBy { formatter.format(Instant.ofEpochMilli(it.creationDate)) }
        .map { (month, entries) -> MediaSection(month, entries) }
    val sorted = items.sortedWith(compareByDescending<MediaItem> { it.creationDate }.thenBy { it.id })
    return MediaLibrary(sorted, sections(sorted), sorted.groupBy { it.albumId }.map { (id, entries) ->
        MediaAlbum(id, entries.first().albumName, entries, sections(entries))
    })
}
