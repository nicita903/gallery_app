package com.nichita.gallery.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class MediaType { Image, Video }

@Immutable
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
    val durationMillis: Long? = null,
    val cacheVersion: String = "0"
)

data class MediaSection(val month: String, val items: List<MediaItem>)
data class MediaAlbum(val id: String, val name: String, val items: List<MediaItem>, val sections: List<MediaSection>)
class MediaLibrary(
    val items: List<MediaItem> = emptyList(),
    val sections: List<MediaSection> = emptyList(),
    val albums: List<MediaAlbum> = emptyList()
)

internal fun creationDate(takenMillis: Long, addedSeconds: Long, modifiedSeconds: Long): Long =
    takenMillis.takeIf { it > 0 } ?: (addedSeconds.takeIf { it > 0 } ?: modifiedSeconds.coerceAtLeast(0)) * 1000

internal fun buildLibrary(items: List<MediaItem>): MediaLibrary {
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).withZone(ZoneId.systemDefault())
    val monthNames = HashMap<java.time.YearMonth, String>()
    fun sections(media: List<MediaItem>) = media.groupBy {
        val date = Instant.ofEpochMilli(it.creationDate).atZone(ZoneId.systemDefault())
        monthNames.getOrPut(java.time.YearMonth.from(date)) { formatter.format(date) }
    }
        .map { (month, entries) -> MediaSection(month, entries) }
    val sorted = items.sortedWith(compareByDescending<MediaItem> { it.creationDate }.thenBy { it.id })
    return MediaLibrary(sorted, sections(sorted), sorted.groupBy { it.albumId }.map { (id, entries) ->
        MediaAlbum(id, entries.first().albumName, entries, sections(entries))
    })
}
