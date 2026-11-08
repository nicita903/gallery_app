package com.nichita.gallery

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nichita.gallery.data.MediaPermissions
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GalleryScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun deniedAccessIsSafeOnBothTabs() {
        assumeFalse(MediaPermissions.read(compose.activity).canRead)
        val allow = compose.activity.getString(R.string.allow_access)
        val albums = compose.activity.getString(R.string.albums)
        compose.waitUntil(10000) {
            compose.onAllNodesWithText(allow).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(albums).performClick()
        compose.onAllNodesWithText(albums).assertCountEquals(2)
        compose.onAllNodesWithText(allow).assertCountEquals(1)
    }
}
