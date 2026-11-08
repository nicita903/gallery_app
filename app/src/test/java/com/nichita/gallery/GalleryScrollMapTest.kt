package com.nichita.gallery

import com.nichita.gallery.data.thumbnailPixels
import com.nichita.gallery.ui.GalleryScrollMap
import com.nichita.gallery.ui.pinchedColumns
import org.junit.Assert.*
import org.junit.Test

class GalleryScrollMapTest {
    @Test fun unequalMonthsUseTheirActualRowCounts() {
        val map = GalleryScrollMap(listOf(3, 300, 7), 3, 100f, 50f, 200f, 4f, 24f)
        assertArrayEquals(intArrayOf(1, 5, 306), map.headerIndexes)
        assertEquals(204f, map.offsetFor(1), 0.01f)
        assertEquals(362f, map.offsetFor(5), 0.01f)
        assertEquals(10816f, map.offsetFor(306), 0.01f)
        assertEquals(1, map.monthAt(5000f))
        assertEquals(2, map.monthAt(10816f))
    }

    @Test fun pixelOffsetsRoundTripAcrossAllColumnCounts() {
        for (columns in 2..8) {
            val map = GalleryScrollMap(listOf(1, 10003, 19), columns, 140f, 54f, 210f, 3f, 24f)
            for (index in listOf(0, 1, 2, 3, 4, 88, 5000, 10007, 10012)) {
                val offset = map.offsetFor(index)
                val target = map.target(offset + 10f)
                assertEquals("columns=$columns index=$index", offset + 10f,
                    map.offsetFor(target.first) + target.second, 0.01f)
            }
        }
    }

    @Test fun emptyMapAndBoundsAreSafe() {
        val map = GalleryScrollMap(emptyList(), 3, 100f, 50f, 200f, 4f, 24f)
        assertEquals(0f, map.offsetFor(100), 0f)
        assertEquals(0 to 0, map.target(-100f))
    }

    @Test fun pinchDirectionAndLimits() {
        assertEquals(3, pinchedColumns(3, 1f))
        assertEquals(6, pinchedColumns(3, 0.5f))
        assertEquals(2, pinchedColumns(3, 2f))
        assertEquals(8, pinchedColumns(3, 0.1f))
    }

    @Test fun thumbnailBucketsBoundAllocationAndReuseNearbySizes() {
        assertEquals(384, thumbnailPixels(353))
        assertEquals(thumbnailPixels(353), thumbnailPixels(375))
        assertEquals(1024, thumbnailPixels(2000))
    }
}
