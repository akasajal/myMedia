package com.ishaan.myMedia.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.automirrored.rounded.RotateRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.ui.theme.PrimaryAccent
import com.ishaan.myMedia.ui.theme.SoftPurple
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

// ── Filter definitions ────────────────────────────────────────────────────────

enum class EditorTool { FILTERS, CROP, ROTATE }

data class PhotoFilter(
    val name: String,
    val colorFilter: ColorFilter?,
)

val photoFilters: List<PhotoFilter> = listOf(
    PhotoFilter("Original", null),
    PhotoFilter(
        "Grayscale",
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.33f, 0.59f, 0.11f, 0f, 0f,
                    0.33f, 0.59f, 0.11f, 0f, 0f,
                    0.33f, 0.59f, 0.11f, 0f, 0f,
                    0f,    0f,    0f,    1f, 0f,
                )
            )
        )
    ),
    PhotoFilter(
        "Warm",
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.2f, 0.05f, -0.05f, 0f, 10f,
                    0f,   1.05f,  0f,    0f, 5f,
                    0f,   0f,     0.85f, 0f, -10f,
                    0f,   0f,     0f,    1f, 0f,
                )
            )
        )
    ),
    PhotoFilter(
        "Cool",
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.85f, 0f,    0.05f, 0f, -10f,
                    0f,    1.0f,  0.05f, 0f, 5f,
                    0f,    0f,    1.2f,  0f, 15f,
                    0f,    0f,    0f,    1f, 0f,
                )
            )
        )
    ),
    PhotoFilter(
        "Fade",
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.8f, 0f,   0f,   0f, 40f,
                    0f,   0.8f, 0f,   0f, 40f,
                    0f,   0f,   0.8f, 0f, 40f,
                    0f,   0f,   0f,   1f, 0f,
                )
            )
        )
    ),
    PhotoFilter(
        "Vivid",
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    1.4f, -0.2f, -0.2f, 0f, 0f,
                    -0.2f, 1.4f, -0.2f, 0f, 0f,
                    -0.2f, -0.2f, 1.4f, 0f, 0f,
                    0f,    0f,    0f,   1f, 0f,
                )
            )
        )
    ),
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(
    item: MediaItem,
    onBack: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tool state
    var activeTool by remember { mutableStateOf(EditorTool.FILTERS) }

    // Filter state
    var selectedFilter by remember { mutableStateOf(photoFilters[0]) }

    // Rotate state — cumulative rotation in 90° steps
    var rotationDegrees by remember { mutableIntStateOf(0) }

    // Crop state (normalised 0..1 rect relative to displayed image bounds)
    var cropRect by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }
    var cropApplied by remember { mutableStateOf(false) } // tracks if user has moved handles

    // Saving
    var isSaving by remember { mutableStateOf(false) }

    // Load source bitmap once for save
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(item.uri) {
        withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context).data(item.uri).allowHardware(false).build()
                val result = coil.ImageLoader(context).execute(req)
                if (result is SuccessResult) {
                    sourceBitmap = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                }
            } catch (_: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Edit") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (isSaving) return@TextButton
                            scope.launch {
                                isSaving = true
                                val src = sourceBitmap
                                if (src != null) {
                                    val result = withContext(Dispatchers.Default) {
                                        applyEdits(src, rotationDegrees, cropRect, selectedFilter)
                                    }
                                    onSave(result)
                                }
                                isSaving = false
                            }
                        },
                        enabled = !isSaving,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Save", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        },
        containerColor = Color.Black,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // ── Preview area ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                if (activeTool == EditorTool.CROP) {
                    CroppablePreview(
                        item = item,
                        rotationDegrees = rotationDegrees,
                        colorFilter = selectedFilter.colorFilter,
                        cropRect = cropRect,
                        onCropRectChange = { cropRect = it; cropApplied = true },
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(item.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        colorFilter = selectedFilter.colorFilter,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { rotationZ = rotationDegrees.toFloat() },
                    )
                }
            }

            // ── Tool tab row ──────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111111))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                EditorToolTab(
                    icon = Icons.Rounded.AutoAwesome,
                    label = "Filters",
                    selected = activeTool == EditorTool.FILTERS,
                    onClick = { activeTool = EditorTool.FILTERS },
                )
                EditorToolTab(
                    icon = Icons.Rounded.Crop,
                    label = "Crop",
                    selected = activeTool == EditorTool.CROP,
                    onClick = { activeTool = EditorTool.CROP },
                )
                EditorToolTab(
                    icon = Icons.AutoMirrored.Rounded.RotateRight,
                    label = "Rotate",
                    selected = activeTool == EditorTool.ROTATE,
                    onClick = { activeTool = EditorTool.ROTATE },
                )
            }

            // ── Tool panel ────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center,
            ) {
                when (activeTool) {
                    EditorTool.FILTERS -> FilterPanel(
                        item = item,
                        filters = photoFilters,
                        selectedFilter = selectedFilter,
                        onFilterSelect = { selectedFilter = it },
                    )
                    EditorTool.CROP -> CropHintPanel(
                        onReset = { cropRect = Rect(0f, 0f, 1f, 1f); cropApplied = false }
                    )
                    EditorTool.ROTATE -> RotatePanel(
                        rotationDegrees = rotationDegrees,
                        onRotateCW = { rotationDegrees = (rotationDegrees + 90) % 360 },
                        onRotateCCW = { rotationDegrees = (rotationDegrees + 270) % 360 },
                        onReset = { rotationDegrees = 0 },
                    )
                }
            }
        }
    }
}

// ── Tool tab ──────────────────────────────────────────────────────────────────

@Composable
private fun EditorToolTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tint by animateColorAsState(
        if (selected) PrimaryAccent else Color.Gray, label = "tint"
    )
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        Text(label, fontSize = 11.sp, color = tint)
    }
}

// ── Filter panel ──────────────────────────────────────────────────────────────

@Composable
private fun FilterPanel(
    item: MediaItem,
    filters: List<PhotoFilter>,
    selectedFilter: PhotoFilter,
    onFilterSelect: (PhotoFilter) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(filters) { filter ->
            val isSelected = filter.name == selectedFilter.name
            val borderWidth: Dp by animateDpAsState(if (isSelected) 2.5.dp else 0.dp, label = "border")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onFilterSelect(filter) }
                    .padding(4.dp),
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.uri)
                        .crossfade(false)
                        .size(80, 80)
                        .build(),
                    contentDescription = filter.name,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    colorFilter = filter.colorFilter,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) Modifier.border(
                                borderWidth,
                                Brush.linearGradient(listOf(PrimaryAccent, SoftPurple)),
                                RoundedCornerShape(8.dp)
                            ) else Modifier
                        ),
                )
                Text(
                    text = filter.name,
                    fontSize = 10.sp,
                    color = if (isSelected) PrimaryAccent else Color.Gray,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Rotate panel ──────────────────────────────────────────────────────────────

@Composable
private fun RotatePanel(
    rotationDegrees: Int,
    onRotateCCW: () -> Unit,
    onRotateCW: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onRotateCCW,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF2A2A2A), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Rounded.RotateLeft, "Rotate CCW", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text("90° Left", fontSize = 11.sp, color = Color.Gray)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF2A2A2A), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${rotationDegrees}°",
                    color = PrimaryAccent,
                    fontSize = 14.sp,
                )
            }
            TextButton(onClick = onReset, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                Text("Reset", fontSize = 11.sp, color = Color.Gray)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(
                onClick = onRotateCW,
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF2A2A2A), CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Rounded.RotateRight, "Rotate CW", tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Text("90° Right", fontSize = 11.sp, color = Color.Gray)
        }
    }
}

// ── Crop hint panel ───────────────────────────────────────────────────────────

@Composable
private fun CropHintPanel(onReset: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Drag the corner and edge handles in the preview to crop",
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        OutlinedButton(
            onClick = onReset,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray),
            shape = RoundedCornerShape(8.dp),
        ) {
            Text("Reset Crop", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// ── Crop preview with interactive handles ─────────────────────────────────────

private const val HANDLE_PX = 40f  // touch target size in px

private enum class DragHandle {
    NONE,
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT,
    INTERIOR, // pan the whole rect
}

@Composable
private fun CroppablePreview(
    item: MediaItem,
    rotationDegrees: Int,
    colorFilter: ColorFilter?,
    cropRect: Rect,
    onCropRectChange: (Rect) -> Unit,
) {
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var activeDrag by remember { mutableStateOf(DragHandle.NONE) }
    var dragStart by remember { mutableStateOf(Offset.Zero) }
    var rectAtDragStart by remember { mutableStateOf(Rect(0f, 0f, 1f, 1f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(item.uri)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { rotationZ = rotationDegrees.toFloat() }
                .onSizeChanged { imageSize = it },
        )

        // Overlay: dim outside crop rect + draw handles
        if (imageSize != IntSize.Zero) {
            val w = imageSize.width.toFloat()
            val h = imageSize.height.toFloat()

            Canvas(
                modifier = Modifier
                    .size(
                        width = with(androidx.compose.ui.platform.LocalDensity.current) { w.toDp() },
                        height = with(androidx.compose.ui.platform.LocalDensity.current) { h.toDp() },
                    )
                    .pointerInput(imageSize) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val px = pos.x
                                val py = pos.y
                                val r = Rect(
                                    cropRect.left * w, cropRect.top * h,
                                    cropRect.right * w, cropRect.bottom * h
                                )
                                activeDrag = hitTestHandle(px, py, r, HANDLE_PX)
                                dragStart = pos
                                rectAtDragStart = cropRect
                            },
                            onDragEnd = { activeDrag = DragHandle.NONE },
                            onDragCancel = { activeDrag = DragHandle.NONE },
                            onDrag = { change, _ ->
                                change.consume()
                                val dx = (change.position.x - dragStart.x) / w
                                val dy = (change.position.y - dragStart.y) / h
                                val minSize = 0.05f
                                val r = rectAtDragStart
                                val newRect = when (activeDrag) {
                                    DragHandle.TOP_LEFT -> Rect(
                                        left   = (r.left + dx).coerceIn(0f, r.right - minSize),
                                        top    = (r.top + dy).coerceIn(0f, r.bottom - minSize),
                                        right  = r.right,
                                        bottom = r.bottom,
                                    )
                                    DragHandle.TOP_RIGHT -> Rect(
                                        left   = r.left,
                                        top    = (r.top + dy).coerceIn(0f, r.bottom - minSize),
                                        right  = (r.right + dx).coerceIn(r.left + minSize, 1f),
                                        bottom = r.bottom,
                                    )
                                    DragHandle.BOTTOM_LEFT -> Rect(
                                        left   = (r.left + dx).coerceIn(0f, r.right - minSize),
                                        top    = r.top,
                                        right  = r.right,
                                        bottom = (r.bottom + dy).coerceIn(r.top + minSize, 1f),
                                    )
                                    DragHandle.BOTTOM_RIGHT -> Rect(
                                        left   = r.left,
                                        top    = r.top,
                                        right  = (r.right + dx).coerceIn(r.left + minSize, 1f),
                                        bottom = (r.bottom + dy).coerceIn(r.top + minSize, 1f),
                                    )
                                    DragHandle.TOP -> Rect(
                                        r.left,
                                        (r.top + dy).coerceIn(0f, r.bottom - minSize),
                                        r.right, r.bottom,
                                    )
                                    DragHandle.BOTTOM -> Rect(
                                        r.left, r.top, r.right,
                                        (r.bottom + dy).coerceIn(r.top + minSize, 1f),
                                    )
                                    DragHandle.LEFT -> Rect(
                                        (r.left + dx).coerceIn(0f, r.right - minSize),
                                        r.top, r.right, r.bottom,
                                    )
                                    DragHandle.RIGHT -> Rect(
                                        r.left, r.top,
                                        (r.right + dx).coerceIn(r.left + minSize, 1f),
                                        r.bottom,
                                    )
                                    DragHandle.INTERIOR -> {
                                        val clampedDx = dx.coerceIn(-r.left, 1f - r.right)
                                        val clampedDy = dy.coerceIn(-r.top, 1f - r.bottom)
                                        Rect(r.left + clampedDx, r.top + clampedDy, r.right + clampedDx, r.bottom + clampedDy)
                                    }
                                    DragHandle.NONE -> r
                                }
                                if (activeDrag != DragHandle.NONE) onCropRectChange(newRect)
                            }
                        )
                    }
            ) {
                drawCropOverlay(
                    cropRect = cropRect,
                    canvasW = w,
                    canvasH = h,
                )
            }
        }
    }
}

private fun hitTestHandle(px: Float, py: Float, r: Rect, t: Float): DragHandle {
    // Corners first (highest priority)
    if (near(px, r.left, t) && near(py, r.top, t))    return DragHandle.TOP_LEFT
    if (near(px, r.right, t) && near(py, r.top, t))   return DragHandle.TOP_RIGHT
    if (near(px, r.left, t) && near(py, r.bottom, t)) return DragHandle.BOTTOM_LEFT
    if (near(px, r.right, t) && near(py, r.bottom, t))return DragHandle.BOTTOM_RIGHT
    // Edges
    val insideX = px in r.left..r.right
    val insideY = py in r.top..r.bottom
    if (insideX && near(py, r.top, t))    return DragHandle.TOP
    if (insideX && near(py, r.bottom, t)) return DragHandle.BOTTOM
    if (insideY && near(px, r.left, t))   return DragHandle.LEFT
    if (insideY && near(px, r.right, t))  return DragHandle.RIGHT
    // Interior
    if (insideX && insideY) return DragHandle.INTERIOR
    return DragHandle.NONE
}

private fun near(a: Float, b: Float, tolerance: Float) = kotlin.math.abs(a - b) <= tolerance

private fun DrawScope.drawCropOverlay(cropRect: Rect, canvasW: Float, canvasH: Float) {
    val l = cropRect.left * canvasW
    val t = cropRect.top * canvasH
    val r = cropRect.right * canvasW
    val b = cropRect.bottom * canvasH
    val rect = Rect(l, t, r, b)

    // Dim outside
    val dimColor = Color.Black.copy(alpha = 0.55f)
    drawRect(dimColor, topLeft = Offset(0f, 0f), size = Size(canvasW, t))           // top
    drawRect(dimColor, topLeft = Offset(0f, b), size = Size(canvasW, canvasH - b)) // bottom
    drawRect(dimColor, topLeft = Offset(0f, t), size = Size(l, b - t))             // left
    drawRect(dimColor, topLeft = Offset(r, t), size = Size(canvasW - r, b - t))    // right

    // Border
    drawRect(Color.White, topLeft = Offset(l, t), size = Size(r - l, b - t), style = Stroke(2f))

    // Rule-of-thirds grid
    val thirdW = (r - l) / 3f
    val thirdH = (b - t) / 3f
    val gridColor = Color.White.copy(alpha = 0.3f)
    for (i in 1..2) {
        drawLine(gridColor, Offset(l + i * thirdW, t), Offset(l + i * thirdW, b), strokeWidth = 1f)
        drawLine(gridColor, Offset(l, t + i * thirdH), Offset(r, t + i * thirdH), strokeWidth = 1f)
    }

    // Corner handles
    val hLen = 24f
    val hW = 3f
    val hColor = Color.White
    // TL
    drawLine(hColor, Offset(l, t), Offset(l + hLen, t), hW)
    drawLine(hColor, Offset(l, t), Offset(l, t + hLen), hW)
    // TR
    drawLine(hColor, Offset(r, t), Offset(r - hLen, t), hW)
    drawLine(hColor, Offset(r, t), Offset(r, t + hLen), hW)
    // BL
    drawLine(hColor, Offset(l, b), Offset(l + hLen, b), hW)
    drawLine(hColor, Offset(l, b), Offset(l, b - hLen), hW)
    // BR
    drawLine(hColor, Offset(r, b), Offset(r - hLen, b), hW)
    drawLine(hColor, Offset(r, b), Offset(r, b - hLen), hW)
    // Mid-edge handles
    val midX = (l + r) / 2f
    val midY = (t + b) / 2f
    drawLine(hColor, Offset(midX - hLen / 2, t), Offset(midX + hLen / 2, t), hW)
    drawLine(hColor, Offset(midX - hLen / 2, b), Offset(midX + hLen / 2, b), hW)
    drawLine(hColor, Offset(l, midY - hLen / 2), Offset(l, midY + hLen / 2), hW)
    drawLine(hColor, Offset(r, midY - hLen / 2), Offset(r, midY + hLen / 2), hW)
}

// ── Edit application (runs off main thread) ───────────────────────────────────

private fun applyEdits(
    source: Bitmap,
    rotationDegrees: Int,
    cropRect: Rect,
    filter: PhotoFilter,
): Bitmap {
    // 1. Rotate
    val rotated = if (rotationDegrees == 0) {
        source
    } else {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    // 2. Crop
    val cx = (cropRect.left * rotated.width).toInt().coerceIn(0, rotated.width - 1)
    val cy = (cropRect.top * rotated.height).toInt().coerceIn(0, rotated.height - 1)
    val cw = ((cropRect.right - cropRect.left) * rotated.width).toInt()
        .coerceIn(1, rotated.width - cx)
    val ch = ((cropRect.bottom - cropRect.top) * rotated.height).toInt()
        .coerceIn(1, rotated.height - cy)
    val cropped = Bitmap.createBitmap(rotated, cx, cy, cw, ch)

    // 3. Apply filter via ColorMatrix paint
    if (filter.colorFilter == null) return cropped

    // Extract the float array from the compose ColorFilter to apply it as an Android Paint
    // We reconstruct the matrix from the known filter definitions
    val filterIndex = photoFilters.indexOfFirst { it.name == filter.name }
    val output = cropped.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(output)
    val paint = android.graphics.Paint()

    val matrix: android.graphics.ColorMatrix? = when (filterIndex) {
        1 -> android.graphics.ColorMatrix(floatArrayOf(
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0.33f, 0.59f, 0.11f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        2 -> android.graphics.ColorMatrix(floatArrayOf(
            1.2f, 0.05f, -0.05f, 0f, 10f,
            0f, 1.05f, 0f, 0f, 5f,
            0f, 0f, 0.85f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f,
        ))
        3 -> android.graphics.ColorMatrix(floatArrayOf(
            0.85f, 0f, 0.05f, 0f, -10f,
            0f, 1.0f, 0.05f, 0f, 5f,
            0f, 0f, 1.2f, 0f, 15f,
            0f, 0f, 0f, 1f, 0f,
        ))
        4 -> android.graphics.ColorMatrix(floatArrayOf(
            0.8f, 0f, 0f, 0f, 40f,
            0f, 0.8f, 0f, 0f, 40f,
            0f, 0f, 0.8f, 0f, 40f,
            0f, 0f, 0f, 1f, 0f,
        ))
        5 -> android.graphics.ColorMatrix(floatArrayOf(
            1.4f, -0.2f, -0.2f, 0f, 0f,
            -0.2f, 1.4f, -0.2f, 0f, 0f,
            -0.2f, -0.2f, 1.4f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        else -> null
    }

    if (matrix != null) {
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(matrix)
    }
    canvas.drawBitmap(cropped, 0f, 0f, paint)
    return output
}