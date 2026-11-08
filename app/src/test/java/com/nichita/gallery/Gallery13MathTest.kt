package com.nichita.gallery

import androidx.compose.ui.geometry.Offset
import com.nichita.gallery.data.formatExposure
import com.nichita.gallery.ui.*
import org.junit.Assert.*
import org.junit.Test

class Gallery13MathTest {
    @Test fun focalPointRemainsUnderTheSameFinger() {
        val center = Offset(500f, 500f)
        val focal = Offset(600f, 700f)
        val offset = focalOffset(Offset.Zero, focal, center, 3f, Offset.Zero)
        assertEquals(Offset(-200f, -400f), offset)
        assertEquals(focal - center, (focal - center) * 3f + offset)
    }
    @Test fun panningIsClampedToTheActualFittedImage() {
        assertEquals(Offset.Zero, clampPhotoOffset(Offset(99f, 99f), 1f, 100f, 200f, 1f))
        assertEquals(Offset(100f, 50f), clampPhotoOffset(Offset(999f, 999f), 3f, 100f, 200f, 1f))
    }
    @Test fun variableAlbumRowsProduceCorrectTargetsAfterMeasurement() {
        val geometry = AlbumRowGeometry(8, 3, 4f, 200f)
        geometry.measure(1, 100f); geometry.measure(2, 150f); geometry.measure(3, 120f); geometry.rebuild()
        val model = geometry.snapshot()
        assertEquals(204f, model.offset(1), 0f)
        assertEquals(308f, model.offset(2), 0f)
        assertEquals(462f, model.offset(3), 0f)
        assertEquals(4 to 25, model.target(333f))
    }
    @Test fun albumContentHeadingIsIncludedInMonthGeometry() {
        val map = GalleryScrollMap(listOf(3), 3, 100f, 50f, 200f, 4f, 24f, 48f)
        assertEquals(256f, map.offsetFor(2), 0f)
        assertEquals(1 to 10, map.target(214f))
        assertEquals(2 to 0, map.target(256f))
    }
    @Test fun shutterExposureIsReadable() {
        assertEquals("1/125 s", formatExposure(0.008))
        assertEquals(java.text.NumberFormat.getNumberInstance().format(0.8) + " s", formatExposure(0.8))
    }
}
