package com.ishaan.myMedias.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ishaan.myMedias.data.model.Album
import com.ishaan.myMedias.data.model.MediaGroup
import com.ishaan.myMedias.data.model.MediaItem
import com.ishaan.myMedias.ui.components.DateGroupHeader
import com.ishaan.myMedias.ui.components.MediaGridRow
import com.ishaan.myMedias.ui.theme.PrimaryAccent
import com.ishaan.myMedias.ui.theme.SoftPurple
import com.ishaan.myMedias.viewmodel.GalleryUiState

@Composable
fun AlbumsScreen(
    uiState: GalleryUiState,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.albums.isEmpty()) {
        EmptyState("No albums found.\nChoose folders in Settings.", modifier)
        return
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(uiState.albums.size, key = { uiState.albums[it].id }) { idx ->
            AlbumCard(
                album   = uiState.albums[idx],
                onClick = { onAlbumClick(uiState.albums[idx]) },
            )
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        if (album.coverUri != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(album.coverUri)
                    .crossfade(true)
                    .build(),
                contentDescription = album.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(listOf(PrimaryAccent.copy(0.4f), SoftPurple.copy(0.3f)))
                    )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f)))
                )
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text  = album.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text  = "${album.mediaCount} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(0.75f),
                )
            }
        }
    }
}

// ── Album detail (date-grouped grid) ─────────────────────────────────────────
@Composable
fun AlbumDetailScreen(
    album: Album,
    groups: List<MediaGroup>,
    onBack: () -> Unit,
    onMediaClick: (MediaItem, List<MediaItem>, Int) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val flatItems = groups.flatMap { it.items }

    Column(modifier.fillMaxSize()) {
        // FIX: add statusBarsPadding so the back button doesn't crowd the status bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    "Back",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text  = album.name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        if (groups.isEmpty()) {
            EmptyState("No media in this album.")
            return@Column
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .pinchToAdjustColumns(columns, onColumnsChange),
            contentPadding = PaddingValues(bottom = 80.dp),
        ) {
            groups.forEach { group ->
                item(key = "header_${group.label}") { DateGroupHeader(group.label) }
                val rows = group.items.chunked(columns)
                itemsIndexed(rows, key = { i, _ -> "row_${group.label}_$i" }) { _, row ->
                    MediaGridRow(
                        items   = row,
                        columns = columns,
                        onItemClick = { item ->
                            val idx = flatItems.indexOfFirst { it.id == item.id }
                            onMediaClick(item, flatItems, idx.coerceAtLeast(0))
                        },
                    )
                }
            }
        }
    }
}
