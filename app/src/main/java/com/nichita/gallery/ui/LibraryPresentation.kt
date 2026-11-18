package com.nichita.gallery.ui

import com.nichita.gallery.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun presentLibrary(source: MediaLibrary, oldest: Boolean, albumNames: Boolean,
    favoritesOnly: Boolean, favorites: Set<String>): MediaLibrary {
    val base = if (favoritesOnly) buildLibrary(source.items.filter { it.id in favorites }) else source
    if (!oldest && !albumNames) return base
    fun sections(entries: List<MediaSection>) = if (oldest)
        entries.asReversed().map { it.copy(items = it.items.asReversed()) } else entries
    val albums = base.albums.map { album -> if (oldest) album.copy(
        items = album.items.asReversed(), sections = sections(album.sections)) else album }
    return MediaLibrary(if (oldest) base.items.asReversed() else base.items, sections(base.sections),
        if (albumNames) albums.sortedBy { it.name.lowercase(Locale.getDefault()) } else albums)
}

internal class LocalSearchIndex(library: MediaLibrary) {
    private val entries: List<Pair<MediaItem, String>>
    init {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd MMMM yyyy", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
        entries = library.items.map { item -> item to
            "${item.filename} ${item.albumName} ${formatter.format(Instant.ofEpochMilli(item.creationDate))} ${if (item.type == MediaType.Video) "video" else "image photo"}"
                .lowercase(Locale.getDefault()) }
    }
    fun search(query: String): MediaLibrary {
        val terms = query.trim().lowercase(Locale.getDefault()).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (terms.isEmpty()) MediaLibrary() else buildLibrary(entries.asSequence()
            .filter { (_, text) -> terms.all { it in text } }.map { it.first }.toList())
    }
}
