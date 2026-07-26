package com.ishaan.myMedia.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ishaan.myMedia.data.model.Album
import com.ishaan.myMedia.data.model.MediaItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveCopySheet(
    item: MediaItem,
    albums: List<Album>,
    onDismiss: () -> Unit,
    onMove: (Album) -> Unit,
    onCopy: (Album) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "Organize Media",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = "Select Destination Album",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(Modifier.height(8.dp))
            
            val validAlbums = albums.filter { it.folderUri != null }
            
            if (validAlbums.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("No target albums available.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(validAlbums) { album ->
                        AlbumDestinationRow(
                            album = album,
                            onMove = { onMove(album); onDismiss() },
                            onCopy = { onCopy(album); onDismiss() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDestinationRow(
    album: Album,
    onMove: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(album.name, style = MaterialTheme.typography.bodyLarge)
                Text("${album.mediaCount} items", style = MaterialTheme.typography.labelSmall)
            }
        }
        
        Row {
            IconButton(onClick = onCopy) {
                Icon(Icons.Rounded.ContentCopy, "Copy to this album")
            }
            IconButton(onClick = onMove) {
                Icon(Icons.Rounded.MoveToInbox, "Move to this album")
            }
        }
    }
}
