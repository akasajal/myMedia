package com.ishaan.myMedia.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.ui.components.DateGroupHeader
import com.ishaan.myMedia.ui.components.MediaGridRow
import com.ishaan.myMedia.viewmodel.GalleryUiState

@Composable
fun FavoritesScreen(
    uiState: GalleryUiState,
    onMediaClick: (MediaItem, List<MediaItem>, Int) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val flatFavs = uiState.favorites

    if (flatFavs.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "♡",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No favorites yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Tap ♡ on any photo or video to save it here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.7f),
                )
            }
        }
        return
    }

    val favoriteGroups = uiState.mediaGroups.mapNotNull { group ->
        val filtered = group.items.filter { groupItem ->
            flatFavs.any { fav -> fav.id == groupItem.id }
        }
        if (filtered.isEmpty()) null else group.copy(items = filtered)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .pinchToAdjustColumns(columns, onColumnsChange),
        contentPadding = PaddingValues(bottom = 80.dp),
    ) {
        favoriteGroups.forEach { group ->
            item(key = "fav_header_${group.label}") { DateGroupHeader(group.label) }
            val rows = group.items.chunked(columns)
            itemsIndexed(rows, key = { i, _ -> "fav_row_${group.label}_$i" }) { _, row ->
                MediaGridRow(
                    items   = row,
                    columns = columns,
                    onItemClick = { item ->
                        val idx = flatFavs.indexOfFirst { it.id == item.id }
                        onMediaClick(item, flatFavs, idx.coerceAtLeast(0))
                    },
                )
            }
        }
    }
}
