package com.ishaan.myMedia.widget

import org.junit.Assert.*
import org.junit.Test

class PhotoWidgetTest {
    @Test fun folderSelectionIncludesDescendantsButNotSimilarNames() {
        val folders = setOf("/storage/emulated/0/Pictures/Trip")
        assertTrue(widgetFolderMatches("/storage/emulated/0/Pictures/Trip", folders))
        assertTrue(widgetFolderMatches("/storage/emulated/0/Pictures/Trip/Day 1", folders))
        assertFalse(widgetFolderMatches("/storage/emulated/0/Pictures/Trips", folders))
        assertFalse(widgetFolderMatches("/storage/emulated/0/Pictures", folders))
        assertFalse(widgetFolderMatches("/storage/emulated/0/Pictures/Trip", emptySet()))
    }

    @Test fun overlappingFolderSelectionsDoNotRequireMultipleMatches() {
        val folders = setOf("/Photos", "/Photos/Trip")
        val albums = listOf("/Photos/Trip", "/Photos/Trip/Day 1", "/Videos", "/PhotosOther")
        assertEquals(listOf("/Photos/Trip", "/Photos/Trip/Day 1"), albums.filter { widgetFolderMatches(it, folders) })
    }

    @Test fun bitmapSizePreservesAspectRatioAndStaysWithinBinderBudget() {
        for ((width, height) in listOf(300 to 150, 500 to 80, 80 to 500, 1 to 1, 2000 to 3000)) {
            val (w, h) = widgetBitmapSize(width, height)
            assertTrue(w > 0 && h > 0)
            assertTrue("$w x $h exceeds budget", w.toLong() * h * 4 <= 720_000)
            assertEquals(width.toDouble() / height, w.toDouble() / h, 0.03)
        }
    }

    @Test fun bitmapSizeHandlesMissingOrInvalidLauncherDimensions() {
        for ((w, h) in listOf(0 to 0, -1 to 100, Int.MAX_VALUE to 1)) {
            val (width, height) = widgetBitmapSize(w, h)
            assertTrue(width > 0 && height > 0)
            assertTrue(width.toLong() * height * 4 <= 720_000)
        }
    }
}
