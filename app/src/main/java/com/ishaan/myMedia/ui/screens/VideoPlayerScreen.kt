package com.ishaan.myMedia.ui.screens

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Public entry-point ─────────────────────────────────────────────────────────
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    item: MediaItem,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    val recommendedTimeout = LocalAccessibilityManager.current
    val accessibility = remember(context) { context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager }
    var accessibilityEnabled by remember { mutableStateOf(accessibility.isEnabled) }

    // ── ExoPlayer ──────────────────────────────────────────────────────────
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(item.uri))
            repeatMode    = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }

    fun seekBy(delta: Long): Boolean {
        val duration = exoPlayer.duration
        if (duration <= 0L) return false
        exoPlayer.seekTo((exoPlayer.currentPosition + delta).coerceIn(0L, duration))
        return true
    }

    // ── Playback state ─────────────────────────────────────────────────────
    var isPlaying     by remember { mutableStateOf(true) }
    var currentMs     by remember { mutableLongStateOf(0L) }
    var durationMs    by remember { mutableLongStateOf(0L) }
    var isSeeking     by remember { mutableStateOf(false) }
    var seekMs        by remember { mutableLongStateOf(0L) }
    var repeatMode    by remember { mutableIntStateOf(Player.REPEAT_MODE_OFF) }
    var isMuted       by remember { mutableStateOf(false) }
    var speed         by remember { mutableFloatStateOf(1f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showOverlay   by remember { mutableStateOf(true) }
    var overlayJob    by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(accessibility) {
        val listener = android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener { enabled ->
            accessibilityEnabled = enabled
            if (enabled) { overlayJob?.cancel(); showOverlay = true }
        }
        accessibility.addAccessibilityStateChangeListener(listener)
        onDispose { accessibility.removeAccessibilityStateChangeListener(listener) }
    }

    // Seek-ripple feedback
    var seekRippleLeft  by remember { mutableStateOf(false) }
    var seekRippleRight by remember { mutableStateOf(false) }

    // ── Player listener ────────────────────────────────────────────────────
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) isPlaying = false
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Poll position every 200 ms
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentMs  = exoPlayer.currentPosition
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(200)
        }
    }

    // Auto-hide overlay helpers
    fun scheduleHide() {
        overlayJob?.cancel()
        if (accessibilityEnabled || showSpeedMenu || isSeeking) return
        val timeout = recommendedTimeout?.calculateRecommendedTimeoutMillis(3_000L, containsIcons = true, containsText = true, containsControls = true) ?: 3_000L
        overlayJob = scope.launch {
            delay(timeout)
            showOverlay = false
        }
    }
    fun showAndScheduleHide() {
        showOverlay = true
        scheduleHide()
    }

    LaunchedEffect(accessibilityEnabled, showSpeedMenu, isSeeking) {
        if (accessibilityEnabled || showSpeedMenu || isSeeking) showOverlay = true
        scheduleHide()
    }

    // ── Root box ───────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        if (accessibilityEnabled) return@detectTapGestures
                        if (showOverlay) { overlayJob?.cancel(); showOverlay = false }
                        else             { showAndScheduleHide() }
                    },
                    onDoubleTap = { offset ->
                        val isRight = offset.x > size.width / 2
                        val delta   = if (isRight) 10_000L else -10_000L
                        if (!seekBy(delta)) return@detectTapGestures
                        if (isRight) {
                            seekRippleRight = true
                            scope.launch { delay(400); seekRippleRight = false }
                        } else {
                            seekRippleLeft = true
                            scope.launch { delay(400); seekRippleLeft = false }
                        }
                        showAndScheduleHide()
                    },
                )
            },
    ) {
        // ── Video surface ──────────────────────────────────────────────────
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player        = exoPlayer
                    useController = false
                    resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams  = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        // ── Double-tap seek ripples ────────────────────────────────────────
        SeekRipple(visible = seekRippleLeft,  isRight = false)
        SeekRipple(visible = seekRippleRight, isRight = true)

        // ── Top bar ────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showOverlay,
            enter    = fadeIn() + slideInVertically { -it },
            exit     = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(0.75f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text     = item.name,
                    style    = MaterialTheme.typography.titleMedium,
                    color    = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                )
                // Speed chip
                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { showSpeedMenu = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text  = if (speed == 1f) "1× Speed" else "${speed}×",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                        )
                    }
                    DropdownMenu(
                        expanded         = showSpeedMenu,
                        onDismissRequest = { showSpeedMenu = false },
                    ) {
                        listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                            DropdownMenuItem(
                                text    = { Text("${s}×", color = if (s == speed) PrimaryAccent else Color.Unspecified) },
                                onClick = {
                                    speed = s
                                    exoPlayer.setPlaybackSpeed(s)
                                    showSpeedMenu = false
                                    showAndScheduleHide()
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── Centre controls ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showOverlay,
            enter    = fadeIn(tween(150)),
            exit     = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                // Rewind 10 s
                ControlButton(
                    size    = 48.dp,
                    onClick = {
                        seekBy(-10_000L)
                        showAndScheduleHide()
                    },
                ) {
                    Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                // Play / Pause
                ControlButton(
                    size     = 68.dp,
                    gradient = true,
                    onClick  = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        showAndScheduleHide()
                    },
                ) {
                    Icon(
                        imageVector        = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint               = AccentForeground,
                        modifier           = Modifier.size(36.dp),
                    )
                }
                // Forward 10 s
                ControlButton(
                    size    = 48.dp,
                    onClick = {
                        seekBy(10_000L)
                        showAndScheduleHide()
                    },
                ) {
                    Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        // ── Bottom controls ────────────────────────────────────────────────
        AnimatedVisibility(
            visible  = showOverlay,
            enter    = fadeIn() + slideInVertically { it },
            exit     = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.8f))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                // Time labels
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text  = formatMs(if (isSeeking) seekMs else currentMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                    Text(
                        text  = formatMs(durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(4.dp))

                // Seek bar
                Slider(
                    enabled = durationMs > 0L,
                    value    = if (isSeeking) seekMs.toFloat() else currentMs.toFloat(),
                    onValueChange = { v ->
                        isSeeking = true
                        seekMs    = v.toLong()
                        showAndScheduleHide()
                    },
                    onValueChangeFinished = {
                        exoPlayer.seekTo(seekMs)
                        isSeeking = false
                    },
                    valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                    colors     = SliderDefaults.colors(
                        thumbColor         = PrimaryAccent,
                        activeTrackColor   = PrimaryAccent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription = "Playback position"
                        stateDescription = "${formatMs(if (isSeeking) seekMs else currentMs)} of ${formatMs(durationMs)}"
                    },
                )

                Spacer(Modifier.height(4.dp))

                // Mute + Repeat row
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    // Mute toggle
                    IconButton(onClick = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                        showAndScheduleHide()
                    }) {
                        Icon(
                            imageVector        = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff
                            else         Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (isMuted) "Unmute" else "Mute",
                            tint               = Color.White,
                        )
                    }

                    // Repeat mode: OFF → ONE → ALL → OFF
                    IconButton(modifier = Modifier.semantics { stateDescription = when (repeatMode) {
                        Player.REPEAT_MODE_OFF -> "Repeat off"
                        Player.REPEAT_MODE_ONE -> "Repeat this video"
                        else -> "Repeat all videos"
                    } }, onClick = {
                        repeatMode = when (repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                            else                   -> Player.REPEAT_MODE_OFF
                        }
                        exoPlayer.repeatMode = repeatMode
                        showAndScheduleHide()
                    }) {
                        Icon(
                            imageVector        = when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                                else                   -> Icons.Rounded.Repeat
                            },
                            contentDescription = "Repeat",
                            tint               = if (repeatMode != Player.REPEAT_MODE_OFF) PrimaryAccent
                            else Color.White.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}

// ── Seek ripple ────────────────────────────────────────────────────────────────
@Composable
private fun BoxScope.SeekRipple(visible: Boolean, isRight: Boolean) {
    val alpha by animateFloatAsState(
        targetValue   = if (visible) 0.25f else 0f,
        animationSpec = tween(200),
        label         = "ripple_alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.5f)
            .align(if (isRight) Alignment.CenterEnd else Alignment.CenterStart)
            .background(Color.White.copy(alpha = alpha)),
        contentAlignment = Alignment.Center,
    ) {
        if (visible) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = if (isRight) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                    contentDescription = null,
                    tint               = Color.White,
                    modifier           = Modifier.size(36.dp),
                )
                Text(
                    text  = if (isRight) "+10s" else "-10s",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

// ── Control button ─────────────────────────────────────────────────────────────
@Composable
private fun ControlButton(
    size: Dp,
    gradient: Boolean = false,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .then(
                if (gradient) Modifier.background(Brush.linearGradient(listOf(PrimaryAccent, SoftPurple)))
                else          Modifier.background(Color.White.copy(alpha = 0.15f))
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
        content          = content,
    )
}

// ── Time formatter ─────────────────────────────────────────────────────────────
private fun formatMs(ms: Long): String {
    val s = ms / 1000L
    val m = s / 60L
    val h = m / 60L
    return if (h > 0L) "%d:%02d:%02d".format(h, m % 60L, s % 60L)
    else               "%02d:%02d".format(m, s % 60L)
}
