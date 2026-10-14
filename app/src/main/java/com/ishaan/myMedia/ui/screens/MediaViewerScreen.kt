package com.ishaan.myMedia.ui.screens

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.data.model.MediaType
import com.ishaan.myMedia.ui.components.MediaThumbnail
import com.ishaan.myMedia.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    items: List<MediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onDelete: (MediaItem) -> Unit,
    onShare: (MediaItem) -> Unit,
    onMove: (MediaItem) -> Unit,
    onPlayVideo: (MediaItem) -> Unit,
    onEdit: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState    = rememberPagerState(initialPage = initialIndex) { items.size }
    val stripState    = rememberLazyListState()
    val scope         = rememberCoroutineScope()
    var showOverlay   by remember { mutableStateOf(false) }
    var showInfo      by remember { mutableStateOf(false) }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: return
    var isImageZoomed by remember(currentItem.uri) { mutableStateOf(false) }

    // Hoist ExoPlayer above the pager so it never lives inside a disposable page slot.
    // Keyed to the current page URI — rebuilds only when you land on a different video.
    val context = LocalContext.current
    val currentVideoUri = if (currentItem.mediaType == MediaType.VIDEO) currentItem.uri else null
    val exoPlayer = remember(currentVideoUri) {
        currentVideoUri?.let { uri ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(ExoMediaItem.fromUri(uri))
                repeatMode    = Player.REPEAT_MODE_ONE
                playWhenReady = false
                prepare()
            }
        }
    }
    DisposableEffect(currentVideoUri) {
        onDispose { exoPlayer?.release() }
    }

    // Sync strip when pager changes
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        val stripCenter = maxOf(0, page - 3)
        stripState.animateScrollToItem(stripCenter)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // ── Pager ─────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isImageZoomed,
            key = { items[it].uri.toString() },
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]

            if (item.mediaType == MediaType.VIDEO) {
                InlineVideoPlayer(
                    exoPlayer   = exoPlayer,
                    onTap       = { showOverlay = !showOverlay },
                    onPlayClick = { onPlayVideo(item) }
                )
            } else {
                ZoomableImage(
                    item        = item,
                    onSingleTap = { showOverlay = !showOverlay },
                    onZoomChanged = { if (pagerState.currentPage == page) isImageZoomed = it },
                )
            }
        }

        // ── Header overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showOverlay,
            enter   = fadeIn() + slideInVertically { -it },
            exit    = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent))
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White)
                }
                Text(
                    text = currentItem.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                )
                IconButton(
                    onClick = { showInfo = true },
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Icon(Icons.Rounded.MoreVert, "More", tint = Color.White)
                }
            }
        }

        // ── Footer overlay ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showOverlay,
            enter   = fadeIn() + slideInVertically { it },
            exit    = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.75f)))
                    )
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                // Thumbnail strip — the signature element
                ThumbnailStrip(
                    items        = items,
                    currentIndex = pagerState.currentPage,
                    listState    = stripState,
                    onThumbnailClick = { idx ->
                        scope.launch { pagerState.animateScrollToPage(idx) }
                    },
                )
                Spacer(Modifier.height(16.dp))
                // Action row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ViewerAction(
                        icon    = if (currentItem.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        label   = if (currentItem.isFavorite) "Unfavorite" else "Favorite",
                        tint    = if (currentItem.isFavorite) PrimaryAccent else Color.White,
                        onClick = { onToggleFavorite(currentItem) },
                    )
                    ViewerAction(Icons.Rounded.Share, "Share", onClick = { onShare(currentItem) })
                    if (currentItem.mediaType != MediaType.VIDEO) {
                        ViewerAction(Icons.Rounded.Edit, "Edit", onClick = { onEdit(currentItem) })
                    }
                    ViewerAction(Icons.Rounded.FolderCopy, "Move", onClick = { onMove(currentItem) })
                    ViewerAction(
                        icon    = Icons.Rounded.DeleteOutline,
                        label   = "Delete",
                        tint    = Color(0xFFFF6B6B),
                        onClick = { onDelete(currentItem) },
                    )
                }
            }
        }
    }

    // ── Info bottom sheet ──────────────────────────────────────────────────
    if (showInfo) {
        MediaInfoSheet(
            item      = currentItem,
            onDismiss = { showInfo = false },
        )
    }
}

// ── Inline video player (used inside the pager) ────────────────────────────────
@OptIn(UnstableApi::class)
@Composable
fun InlineVideoPlayer(
    exoPlayer: ExoPlayer?,
    onTap: () -> Unit,
    onPlayClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTap() })
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player             = exoPlayer
                    useController      = false
                    resizeMode         = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams       = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            update = { view -> view.player = exoPlayer },
            modifier = Modifier.fillMaxSize(),
        )

        // Play button overlay
        IconButton(
            onClick = onPlayClick,
            modifier = Modifier
                .size(72.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Play Video",
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

// ── Thumbnail strip ────────────────────────────────────────────────────────────
@Composable
fun ThumbnailStrip(
    items: List<MediaItem>,
    currentIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onThumbnailClick: (Int) -> Unit,
) {
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items) { idx, item ->
            val isCurrent = idx == currentIndex
            val scale     = if (isCurrent) 1.15f else 0.85f
            val border    = if (isCurrent)
                BorderStroke(2.dp, Brush.linearGradient(listOf(PrimaryAccent, SoftPurple)))
            else null

            Box(
                modifier = Modifier
                    .size(if (isCurrent) 56.dp else 48.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(RoundedCornerShape(6.dp))
                    .then(if (border != null) Modifier.border(border, RoundedCornerShape(6.dp)) else Modifier)
                    .clickable { onThumbnailClick(idx) }
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .crossfade(false)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (!isCurrent) Modifier.blur(1.5.dp) else Modifier),
                )
                // Video badge
                if (item.mediaType == MediaType.VIDEO) {
                    Icon(
                        imageVector = Icons.Rounded.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.Center),
                    )
                }
            }
        }
    }
}

// ── Zoomable image ────────────────────────────────────────────────────────────
private data class ImageTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero)

private fun imagePanBounds(viewport: Size, image: Size, scale: Float): Offset {
    val fitted = if (image.width > 0f && image.height > 0f) {
        val fit = min(viewport.width / image.width, viewport.height / image.height)
        Size(image.width * fit, image.height * fit)
    } else viewport
    return Offset(
        ((fitted.width * scale - viewport.width) / 2f).coerceAtLeast(0f),
        ((fitted.height * scale - viewport.height) / 2f).coerceAtLeast(0f),
    )
}

private fun Offset.clampTo(bounds: Offset): Offset = Offset(
    x.coerceIn(-bounds.x, bounds.x),
    y.coerceIn(-bounds.y, bounds.y),
)

@Composable
fun ZoomableImage(
    item: MediaItem,
    onSingleTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var transform by remember(item.uri) { mutableStateOf(ImageTransform()) }
    var zoomTarget by remember(item.uri) { mutableFloatStateOf(1f) }
    var motionJob by remember(item.uri) { mutableStateOf<Job?>(null) }
    var viewport by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember(item.uri) { mutableStateOf(Size.Zero) }
    val singleTap by rememberUpdatedState(onSingleTap)
    val zoomChanged by rememberUpdatedState(onZoomChanged)

    fun updateTransform(value: ImageTransform) {
        val scale = value.scale.coerceIn(1f, 5f)
        transform = ImageTransform(
            scale,
            if (scale == 1f) Offset.Zero else value.offset.clampTo(imagePanBounds(viewport, imageSize, scale)),
        )
        zoomChanged(scale > 1f)
    }

    DisposableEffect(item.uri) {
        onDispose { motionJob?.cancel() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewport = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(item.uri, viewport, imageSize) {
                detectTapGestures(
                    onTap = { singleTap() },
                    onDoubleTap = { position ->
                        motionJob?.cancel()
                        val start = transform
                        // Reverse the requested zoom even if the previous animation was interrupted.
                        val targetScale = if (zoomTarget > 1f) 1f else 2.5f
                        zoomTarget = targetScale
                        val ratio = targetScale / start.scale
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        val targetOffset = if (targetScale == 1f) Offset.Zero else
                            (start.offset * ratio + (center - position) * (ratio - 1f))
                                .clampTo(imagePanBounds(viewport, imageSize, targetScale))
                        motionJob = scope.launch {
                            // Scale and translation advance together and are bounded on every frame.
                            animate(0f, 1f, animationSpec = tween(220)) { fraction, _ ->
                                updateTransform(ImageTransform(
                                    start.scale + (targetScale - start.scale) * fraction,
                                    start.offset + (targetOffset - start.offset) * fraction,
                                ))
                            }
                        }
                    },
                )
            }
            .pointerInput(item.uri, viewport, imageSize) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // A new gesture owns the transform; older zoom/fling jobs cannot overwrite it.
                    motionJob?.cancel()
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, down.position)
                    var usedMultiplePointers = false
                    var wasPanning = false
                    var accumulatedPan = Offset.Zero
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val pointerCount = event.changes.count { it.pressed }
                        if (pointerCount > 1) {
                            usedMultiplePointers = true
                            val old = transform
                            val newScale = (old.scale * event.calculateZoom()).coerceIn(1f, 5f)
                            zoomTarget = newScale
                            val ratio = newScale / old.scale
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val focalPoint = event.calculateCentroid(useCurrent = false) - center
                            updateTransform(ImageTransform(
                                newScale,
                                old.offset * ratio + focalPoint * (1f - ratio) + event.calculatePan(),
                            ))
                            event.changes.forEach { it.consume() }
                        } else if (transform.scale > 1f) {
                            val pan = event.calculatePan()
                            accumulatedPan += pan
                            if (wasPanning || accumulatedPan.getDistance() > viewConfiguration.touchSlop) {
                                val delta = if (wasPanning) pan else accumulatedPan
                                wasPanning = true
                                updateTransform(transform.copy(offset = transform.offset + delta))
                                event.changes.forEach { it.consume() }
                            }
                            event.changes.firstOrNull { it.pressed }?.let {
                                tracker.addPosition(it.uptimeMillis, it.position)
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    // Pinching must not reuse a one-finger pan velocity for a fling.
                    if (wasPanning && !usedMultiplePointers && transform.scale > 1f) {
                        val velocity = tracker.calculateVelocity()
                        val start = transform
                        val bounds = imagePanBounds(viewport, imageSize, start.scale)
                        motionJob = scope.launch {
                            val pan = Animatable(start.offset, Offset.VectorConverter)
                            pan.updateBounds(lowerBound = -bounds, upperBound = bounds)
                            pan.animateDecay(Offset(velocity.x, velocity.y), exponentialDecay()) {
                                updateTransform(start.copy(offset = value))
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            onSuccess = { result ->
                val intrinsicSize = result.painter.intrinsicSize
                if (intrinsicSize.width > 0f && intrinsicSize.height > 0f) imageSize = intrinsicSize
            },
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val bounded = transform.offset.clampTo(imagePanBounds(viewport, imageSize, transform.scale))
                    scaleX = transform.scale
                    scaleY = transform.scale
                    translationX = bounded.x
                    translationY = bounded.y
                },
        )
    }
}

// ── Viewer action button ───────────────────────────────────────────────────────
@Composable
fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint.copy(alpha = 0.85f))
    }
}

// ── Media info sheet ──────────────────────────────────────────────────────────
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(item: MediaItem, onDismiss: () -> Unit) {
    val dateFmt = SimpleDateFormat("EEE, d MMM yyyy · HH:mm", Locale.getDefault())

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("File info", style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            InfoRow("Name", item.name)
            InfoRow("Date", dateFmt.format(Date(item.dateTaken)))
            if (item.width > 0 && item.height > 0)
                InfoRow("Resolution", "${item.width} × ${item.height}")
            InfoRow("Size", formatBytes(item.size))
            InfoRow("Type", item.mimeType)
            if (item.duration > 0)
                InfoRow("Duration", formatDuration(item.duration))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000     -> "%.1f KB".format(bytes / 1_000.0)
    else               -> "$bytes B"
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000; val m = s / 60; val h = m / 60
    return if (h > 0) "%d:%02d:%02d".format(h, m % 60, s % 60)
    else "%d:%02d".format(m, s % 60)
}
