package com.nichita.gallery

import android.net.Uri
import com.nichita.gallery.data.*
import com.nichita.gallery.ui.*
import org.junit.Assert.*
import org.junit.Test

class LibraryPresentationTest {
    private fun item(id: String, date: Long, album: String, type: MediaType = MediaType.Image) =
        MediaItem(id, Uri.parse("content://fixture/$id"), "$id.jpg", type, date, album, album, 100, 100)

    @Test fun sortingReversesMonthsAndTheirItemsWithoutChangingTheSource() {
        val source = buildLibrary(listOf(item("a", 1700000000000L, "Zebra"), item("b", 1800000000000L, "Alpha")))
        val result = presentLibrary(source, true, true, false, emptySet())
        assertEquals(listOf("a", "b"), result.items.map { it.id })
        assertEquals(listOf("Alpha", "Zebra"), result.albums.map { it.name })
        assertEquals(listOf("a", "b"), result.sections.flatMap { it.items }.map { it.id })
        assertEquals(listOf("b", "a"), source.items.map { it.id })
    }

    @Test fun favoritesIncludeOnlyAvailableMediaAndUpdateCoversAndCounts() {
        val source = buildLibrary(listOf(item("a", 1700000000000L, "Camera"), item("b", 1800000000000L, "Camera")))
        val result = presentLibrary(source, false, false, true, setOf("a", "missing"))
        assertEquals(listOf("a"), result.items.map { it.id })
        assertEquals(1, result.albums.single().items.size)
        assertEquals("a", result.albums.single().items.first().id)
    }

    @Test fun searchMatchesFilenameAlbumDateTypeAndAllTerms() {
        val source = buildLibrary(listOf(item("Holiday", 1700000000000L, "Camera"),
            item("Clip", 1700000000000L, "Downloads", MediaType.Video)))
        val index = LocalSearchIndex(source)
        assertEquals("Holiday", index.search("holiday CAMERA 2023 photo").items.single().id)
        assertEquals("Clip", index.search("video downloads").items.single().id)
        assertTrue(index.search("holiday video").items.isEmpty())
        assertTrue(index.search("  ").items.isEmpty())
    }
}
