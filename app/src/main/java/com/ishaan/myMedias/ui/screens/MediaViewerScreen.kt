package com.ishaan.myMedias.ui.screens

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ishaan.myMedias.data.model.MediaItem
import com.ishaan.myMedias.data.model.MediaType
import com.ishaan.myMedias.ui.components.MediaThumbnail
import com.ishaan.myMedias.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

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
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = items[page]

            if (item.mediaType == MediaType.VIDEO) {
                InlineVideoPlayer(
                    item      = item,
                    onTap     = { showOverlay = !showOverlay },
                    onPlayClick = { onPlayVideo(item) }
                )
            } else {
                ZoomableImage(
                    item        = item,
                    onSingleTap = { showOverlay = !showOverlay },
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
    item: MediaItem,
    onTap: () -> Unit,
    onPlayClick: () -> Unit,
) {
    val context = LocalContext.current

    val exoPlayer = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            repeatMode    = Player.REPEAT_MODE_ONE
            playWhenReady = false
            prepare()
        }
    }

    // Pause player when this screen is no longer active or disposed
    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

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
                    useController      = false   // hide default controls; overlay handles UI
                    resizeMode         = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams       = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
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
@Composable
fun ZoomableImage(
    item: MediaItem,
    onSingleTap: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val offset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val velocityTracker = VelocityTracker()
                val flingSpec = exponentialDecay<Offset>()
                var lastTapTime = 0L

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false)
                    val now = System.currentTimeMillis()
                    val isDoubleTap = now - lastTapTime < 300L
                    lastTapTime = now

                    if (isDoubleTap) {
                        // Handle Double Tap Zoom
                        scope.launch {
                            if (scale.value > 1f) {
                                launch { scale.animateTo(1f) }
                                launch { offset.animateTo(Offset.Zero) }
                            } else {
                                val targetScale = 2.5f
                                val boundsX = (size.width * (targetScale - 1) / 2)
                                val boundsY = (size.height * (targetScale - 1) / 2)

                                val tapOffset = firstDown.position
                                val targetX = ((size.width / 2) - tapOffset.x) * (targetScale - 1)
                                val targetY = ((size.height / 2) - tapOffset.y) * (targetScale - 1)

                                launch { scale.animateTo(targetScale) }
                                launch { offset.animateTo(Offset(targetX.coerceIn(-boundsX, boundsX), targetY.coerceIn(-boundsY, boundsY))) }
                            }
                        }
                    } else {
                        // Single tap might be detected at the end if no drag happened
                    }

                    var currentScale = scale.value
                    var currentOffset = offset.value
                    var hasMoved = false

                    do {
                        val event = awaitPointerEvent()
                        val dragChange = event.changes.firstOrNull { it.id == firstDown.id }

                        if (event.changes.size > 1) {
                            // Multi-finger: Zoom & Pan
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()

                            currentScale = (currentScale * zoomChange).coerceIn(1f, 5f)

                            val boundsX = (size.width * (currentScale - 1) / 2)
                            val boundsY = (size.height * (currentScale - 1) / 2)

                            currentOffset = Offset(
                                x = (currentOffset.x + panChange.x).coerceIn(-boundsX, boundsX),
                                y = (currentOffset.y + panChange.y).coerceIn(-boundsY, boundsY)
                            )

                            scope.launch {
                                scale.snapTo(currentScale)
                                offset.snapTo(currentOffset)
                            }
                            event.changes.forEach { it.consume() }
                            hasMoved = true
                        } else if (dragChange != null) {
                            val pan = dragChange.position - dragChange.previousPosition
                            // Always track movement so pager swipes don't fire onSingleTap
                            if (pan.getDistance() > 8f) hasMoved = true

                            if (currentScale > 1f && pan.getDistance() > 1f) {
                                val boundsX = (size.width * (currentScale - 1) / 2)
                                val boundsY = (size.height * (currentScale - 1) / 2)

                                currentOffset = Offset(
                                    x = (currentOffset.x + pan.x).coerceIn(-boundsX, boundsX),
                                    y = (currentOffset.y + pan.y).coerceIn(-boundsY, boundsY)
                                )

                                velocityTracker.addPosition(dragChange.uptimeMillis, dragChange.position)

                                scope.launch { offset.snapTo(currentOffset) }
                                dragChange.consume()
                                hasMoved = true
                            }
                        }
                    } while (event.changes.fastAny { it.pressed })

                    // Gesture ended - handle Fling or Single Tap
                    if (currentScale > 1f && hasMoved) {
                        val velocity = velocityTracker.calculateVelocity()
                        scope.launch {
                            val boundsX = (size.width * (currentScale - 1) / 2)
                            val boundsY = (size.height * (currentScale - 1) / 2)

                            offset.animateDecay(
                                initialVelocity = Offset(velocity.x, velocity.y),
                                animationSpec = flingSpec
                            ) {
                                val constrainedX = value.x.coerceIn(-boundsX, boundsX)
                                val constrainedY = value.y.coerceIn(-boundsY, boundsY)
                                if (constrainedX != value.x || constrainedY != value.y) {
                                    scope.launch { offset.snapTo(Offset(constrainedX, constrainedY)) }
                                }
                            }
                        }
                    } else if (!hasMoved && !isDoubleTap) {
                        onSingleTap()
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
            contentDescription = item.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offset.value.x
                    translationY = offset.value.y
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