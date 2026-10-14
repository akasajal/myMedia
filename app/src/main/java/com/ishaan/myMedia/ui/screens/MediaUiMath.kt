package com.ishaan.myMedia.ui.screens

import kotlin.math.min

internal fun boundedSeekPosition(position: Long, delta: Long, duration: Long): Long? {
    if (duration <= 0L) return null
    val current = position.coerceIn(0L, duration)
    return current + delta.coerceIn(-current, duration - current)
}

internal fun fittedImageDimensions(imageWidth: Int, imageHeight: Int, viewportWidth: Int, viewportHeight: Int): Pair<Float, Float> {
    if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) return 0f to 0f
    val fit = min(viewportWidth.toFloat() / imageWidth, viewportHeight.toFloat() / imageHeight)
    return imageWidth * fit to imageHeight * fit
}
