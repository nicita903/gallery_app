package com.nichita.gallery

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.nichita.gallery.ui.viewerDismissGesture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ViewerDismissTest {
    @get:Rule val compose = createComposeRule()
    private var starts = 0
    private var ends = 0
    private var cancels = 0
    private var distance = 0f
    private fun content() {
        compose.setContent {
            val repaint = remember { mutableIntStateOf(0) }
            Box(Modifier.fillMaxSize().testTag("dismiss_fixture").viewerDismissGesture("fixture", true,
                excluded = { it.y > 1200f }, onStart = { starts++ },
                onDrag = { distance += it; repaint.intValue++ }, onCancel = { cancels++ }, onEnd = { ends++ }))
        }
    }
    @Test fun downwardSwipeTracksFingerAndEndsOnce() {
        content()
        compose.onNodeWithTag("dismiss_fixture").performTouchInput { swipe(Offset(300f, 300f), Offset(300f, 900f), 300) }
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, ends); assertEquals(0, cancels); assertTrue(distance > 500f) }
    }
    @Test fun horizontalSwipeAndToolbarDragNeverDismiss() {
        content()
        compose.onNodeWithTag("dismiss_fixture").performTouchInput { swipe(Offset(200f, 400f), Offset(800f, 400f), 300) }
        compose.onNodeWithTag("dismiss_fixture").performTouchInput { swipe(Offset(300f, 1300f), Offset(300f, 1800f), 300) }
        compose.runOnIdle { assertEquals(0, starts); assertEquals(0, ends) }
    }
    @Test fun addingSecondFingerCancelsInsteadOfClosing() {
        content()
        compose.onNodeWithTag("dismiss_fixture").performTouchInput {
            down(0, Offset(300f, 300f)); moveTo(0, Offset(300f, 600f), 150)
            down(1, Offset(600f, 600f)); moveTo(0, Offset(300f, 700f), 100)
            up(1); up(0)
        }
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, cancels); assertEquals(0, ends) }
    }
}
