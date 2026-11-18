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
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()).withZone(zone)
    val monthNames = HashMap<java.time.YearMonth, String>()
    val months = LinkedHashMap<String, MutableList<MediaItem>>()
    val albumItems = LinkedHashMap<String, MutableList<MediaItem>>()
    val albumMonths = HashMap<String, LinkedHashMap<String, MutableList<MediaItem>>>()
    val sorted = items.sortedWith(compareByDescending<MediaItem> { it.creationDate }.thenBy { it.id })
    for (item in sorted) {
        // Resolve each timestamp once, sharing the month label between Photos and Albums.
        val date = Instant.ofEpochMilli(item.creationDate).atZone(zone)
        val month = monthNames.getOrPut(java.time.YearMonth.from(date)) { formatter.format(date) }
        months.getOrPut(month) { ArrayList() }.add(item)
        albumItems.getOrPut(item.albumId) { ArrayList() }.add(item)
        albumMonths.getOrPut(item.albumId) { LinkedHashMap() }.getOrPut(month) { ArrayList() }.add(item)
    }
    fun sections(groups: Map<String, List<MediaItem>>) = groups.map { (month, entries) -> MediaSection(month, entries) }
    return MediaLibrary(sorted, sections(months), albumItems.map { (id, entries) ->
        MediaAlbum(id, entries.first().albumName, entries, sections(albumMonths.getValue(id)))
    })
}
