package com.nichita.gallery

import com.nichita.gallery.data.MediaAccess
import com.nichita.gallery.data.creationDate
import com.nichita.gallery.ui.formatDuration
import org.junit.Assert.*
import org.junit.Test

class MediaHelpersTest {
    @Test fun creationDatePrefersCaptureTimeAndNormalizesFallbackSeconds() {
        assertEquals(123L, creationDate(123, 456, 789))
        assertEquals(456000L, creationDate(0, 456, 789))
        assertEquals(789000L, creationDate(0, 0, 789))
        assertEquals(0L, creationDate(0, 0, -1))
    }

    @Test fun accessDistinguishesFullSelectedAndSingleType() {
        assertFalse(MediaAccess().canRead)
        assertTrue(MediaAccess(selected = true).canRead)
        assertFalse(MediaAccess(selected = true).isFull)
        assertFalse(MediaAccess(images = true).isFull)
        assertTrue(MediaAccess(images = true, videos = true).isFull)
    }

    @Test fun durationFormatsShortAndLongVideos() {
        assertEquals("0:00", formatDuration(0))
        assertEquals("1:01", formatDuration(61000))
        assertEquals("1:01:01", formatDuration(3661000))
        assertEquals("0:00", formatDuration(-1))
    }
}
