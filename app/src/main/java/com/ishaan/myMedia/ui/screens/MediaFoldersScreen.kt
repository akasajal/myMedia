package com.ishaan.myMedia.ui.screens

import androidx.compose.foundation.clickable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateSet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.ishaan.myMedia.data.model.FolderNode
import com.ishaan.myMedia.ui.theme.AccentForeground
import com.ishaan.myMedia.ui.theme.PrimaryAccent
import com.ishaan.myMedia.ui.theme.SoftPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaFoldersScreen(
    folderTree: List<FolderNode>,
    onAddFolder: (android.net.Uri) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onRemoveRoot: (String) -> Unit,
    onBack: () -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onAddFolder(uri)
        }
    }

    val expandedRoots = remember { mutableStateSetOf<String>() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Media Folders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Add Folder Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(Brush.horizontalGradient(listOf(PrimaryAccent, SoftPurple)))
                    .clickable { folderPicker.launch(null) }
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = AccentForeground)
                    Text(
                        "Add new root folder",
                        style = MaterialTheme.typography.labelLarge,
                        color = AccentForeground,
                    )
                }
            }

            if (folderTree.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No folders added yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    folderTree.forEach { rootNode ->
                        folderTreeItems(rootNode, 0, onToggleVisibility, onRemoveRoot, expandedRoots)
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.folderTreeItems(
    node: FolderNode,
    depth: Int,
    onToggleVisibility: (String) -> Unit,
    onRemoveRoot: (String) -> Unit,
    expandedRoots: SnapshotStateSet<String>,
) {
    val isRoot = depth == 0

    item(key = node.path) {
        val hasChildren = node.subFolders.isNotEmpty()
        val isExpanded = expandedRoots.contains(node.path)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (depth * 24).dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse chevron — only shown on root nodes that have subfolders
            if (isRoot && hasChildren) {
                IconButton(onClick = {
                    if (isExpanded) expandedRoots.remove(node.path)
                    else expandedRoots.add(node.path)
                }) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.KeyboardArrowDown else Icons.Rounded.KeyboardArrowRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isRoot) {
                // Placeholder spacer so other rows align
                Spacer(Modifier.size(48.dp))
            }

            IconButton(onClick = { onToggleVisibility(node.path) }) {
                Icon(
                    imageVector = if (node.isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                    contentDescription = "Toggle Visibility",
                    tint = if (node.isVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (node.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                if (isRoot) {
                    Text(
                        text = node.path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        maxLines = 1
                    )
                }
            }

            if (isRoot) {
                IconButton(onClick = { onRemoveRoot(node.path) }) {
                    Icon(Icons.Rounded.RemoveCircleOutline, "Remove Root", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    // Subfolders only rendered when the root is expanded
    if (!isRoot || expandedRoots.contains(node.path)) {
        node.subFolders.forEach { subNode ->
            folderTreeItems(subNode, depth + 1, onToggleVisibility, onRemoveRoot, expandedRoots)
        }
    }
}