package com.ishaan.myMedia.widget

import org.junit.Assert.*
import org.junit.Test

class PhotoWidgetTest {
    @Test fun appLockRequiresExplicitPermissionForEachWidget() {
        val denied = PhotoWidgetConfig()
        val allowed = denied.copy(allowWhileLocked = true)
        assertFalse(widgetPhotosAllowed(true, denied))
        assertTrue(widgetPhotosAllowed(true, allowed))
        assertTrue(widgetPhotosAllowed(false, denied))
        assertTrue(widgetPhotosAllowed(false, allowed))
        assertFalse(widgetPhotosAllowed(true, null))
        assertFalse(widgetPhotosAllowed(false, null))
        // Allowing one widget never changes another widget's default.
        assertFalse(widgetPhotosAllowed(true, PhotoWidgetConfig()))
    }
    @Test fun hiddenAncestorsExcludeDescendantsWithoutHidingSimilarNames() {
        val hidden = setOf("/Photos/Private")
        assertFalse(widgetFolderVisible("/Photos/Private") { it !in hidden })
        assertFalse(widgetFolderVisible("/Photos/Private/Trip/Day 1") { it !in hidden })
        assertTrue(widgetFolderVisible("/Photos/PrivateOther/Trip") { it !in hidden })
        assertTrue(widgetFolderVisible("/Photos/Public") { it !in hidden })
    }
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

    @Test fun bitmapSizePreservesAspectRatioAndStaysWithinMemoryBudget() {
        for ((width, height) in listOf(300 to 150, 500 to 80, 80 to 500, 1 to 1, 2000 to 3000)) {
            val (w, h) = widgetBitmapSize(width, height)
            assertTrue(w > 0 && h > 0)
            assertTrue("$w x $h exceeds budget", w.toLong() * h <= MAX_WIDGET_PIXELS)
            assertEquals(width.toDouble() / height, w.toDouble() / h, 0.03)
        }
    }

    @Test fun bitmapSizeHandlesMissingOrInvalidLauncherDimensions() {
        for ((w, h) in listOf(0 to 0, -1 to 100, Int.MAX_VALUE to 1)) {
            val (width, height) = widgetBitmapSize(w, h)
            assertTrue(width > 0 && height > 0)
            assertTrue(width.toLong() * height <= MAX_WIDGET_PIXELS)
        }
    }

    @Test fun bitmapSizeUsesPhysicalPixelsForLargeHighDensityWidgets() {
        // A typical tall phone widget must no longer be reduced to roughly 350 x 500 pixels.
        assertEquals(1041 to 1482, widgetBitmapSize(347, 494, density = 3f))
        assertEquals(320 to 160, widgetBitmapSize(160, 80, density = 2f))
        // Small widgets should not be enlarged simply to exhaust the available memory.
        assertEquals(160 to 80, widgetBitmapSize(160, 80))
    }

    @Test fun bitmapSizeHonorsScreenBudgetAndInvalidDensityFallback() {
        val (w, h) = widgetBitmapSize(600, 800, density = 4f, maxPixels = 480_000)
        assertTrue(w.toLong() * h <= 480_000)
        assertEquals(0.75, w.toDouble() / h, 0.01)
        for (density in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -2f)) {
            assertEquals(300 to 150, widgetBitmapSize(300, 150, density))
        }
    }
}
