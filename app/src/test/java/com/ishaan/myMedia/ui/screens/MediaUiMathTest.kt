package com.ishaan.myMedia.ui.screens

import org.junit.Assert.*
import org.junit.Test

class MediaUiMathTest {
    @Test fun seekingBeforeDurationIsKnownIsIgnored() {
        assertNull(boundedSeekPosition(0L, 10_000L, Long.MIN_VALUE + 1L))
        assertNull(boundedSeekPosition(0L, -10_000L, 0L))
    }

    @Test fun seekingStopsAtBothEndsOfTheVideo() {
        assertEquals(12_000L, boundedSeekPosition(8_000L, 10_000L, 12_000L))
        assertEquals(0L, boundedSeekPosition(2_000L, -10_000L, 12_000L))
        assertEquals(8_000L, boundedSeekPosition(3_000L, 5_000L, 12_000L))
    }

    @Test fun extremeSeekValuesCannotOverflow() {
        assertEquals(Long.MAX_VALUE, boundedSeekPosition(Long.MAX_VALUE - 1L, 10_000L, Long.MAX_VALUE))
        assertEquals(0L, boundedSeekPosition(1L, Long.MIN_VALUE, Long.MAX_VALUE))
    }

    @Test fun landscapeImageUsesImageBoundsInsteadOfPortraitLetterboxing() {
        val (width, height) = fittedImageDimensions(1920, 1080, 1080, 2340)
        assertEquals(1080f, width, 0.01f)
        assertEquals(607.5f, height, 0.01f)
    }

    @Test fun rotationChangesFittedBounds() {
        val original = fittedImageDimensions(1200, 800, 1000, 600)
        val rotated = fittedImageDimensions(800, 1200, 1000, 600)
        assertEquals(900f, original.first, 0.01f)
        assertEquals(400f, rotated.first, 0.01f)
        assertEquals(600f, rotated.second, 0.01f)
    }

    @Test fun unmeasuredPreviewHasNoCropBounds() {
        assertEquals(0f to 0f, fittedImageDimensions(1200, 800, 0, 0))
    }
}
