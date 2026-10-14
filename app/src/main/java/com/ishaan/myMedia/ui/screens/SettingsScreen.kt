package com.ishaan.myMedia.ui.screens

import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ishaan.myMedia.data.model.AppTheme
import com.ishaan.myMedia.data.model.DeletedItem
import com.ishaan.myMedia.data.model.PrivacyPrefs
import com.ishaan.myMedia.ui.components.PinSetupDialog
import com.ishaan.myMedia.viewmodel.GalleryUiState
import java.util.*

@Composable
fun SettingsScreen(
    uiState: GalleryUiState,
    onThemeChange: (AppTheme) -> Unit,
    onRestore: (DeletedItem) -> Unit,
    onPermanentlyDelete: (DeletedItem) -> Unit,
    onSavePin: (String) -> Unit,
    onClearPin: () -> Unit,
    onSetBiometrics: (Boolean) -> Unit,
    onSetInstantLock: (Boolean) -> Unit,
    onOpenRecycleBin: () -> Unit,
    onOpenMediaFolders: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { SettingsSection("Appearance") }
        item {
            ThemeSelector(
                current  = uiState.theme,
                onChange = onThemeChange,
            )
        }

        item { SettingsSection("Media Folders") }
        item {
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenMediaFolders() }
                        .padding(horizontal = 16.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text("Manage Media Folders", style = MaterialTheme.typography.bodyLarge)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (uiState.deletedItems.isNotEmpty()) {
            item { SettingsSection("Recently Deleted") }
            item {
                RecentlyDeletedPreview(
                    items      = uiState.deletedItems,
                    onRestore  = onRestore,
                    onDelete   = onPermanentlyDelete,
                    onManage   = onOpenRecycleBin,
                )
            }
        }

        item { SettingsSection("Privacy & Security") }
        item {
            PrivacySettings(
                prefs           = uiState.privacyPrefs,
                onSavePin       = onSavePin,
                onClearPin      = onClearPin,
                onSetBiometrics = onSetBiometrics,
                onSetInstantLock = onSetInstantLock,
            )
        }

        item { SettingsSection("Storage") }
        item {
            StorageAnalytics(totalSize = uiState.totalStorageUsage)
        }
    }
}

@Composable
private fun SettingsSection(title: String) {
    Text(
        text  = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

// ── Theme selector ────────────────────────────────────────────────────────────
@Composable
private fun ThemeSelector(current: AppTheme, onChange: (AppTheme) -> Unit) {
    val options = listOf(
        AppTheme.SYSTEM to "Follow system",
        AppTheme.LIGHT  to "Light",
        AppTheme.DARK   to "Dark",
    )
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            options.forEachIndexed { idx, (theme, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(theme) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    RadioButton(
                        selected = current == theme,
                        onClick  = { onChange(theme) },
                        colors   = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                    )
                }
                if (idx < options.lastIndex)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
            }
        }
    }
}

// ── Privacy & Security ────────────────────────────────────────────────────────

@Composable
private fun PrivacySettings(
    prefs: PrivacyPrefs,
    onSavePin: (String) -> Unit,
    onClearPin: () -> Unit,
    onSetBiometrics: (Boolean) -> Unit,
    onSetInstantLock: (Boolean) -> Unit,
) {
    var showPinSetup by remember { mutableStateOf(false) }
    var showDisableConfirm by remember { mutableStateOf(false) }

    if (showPinSetup) {
        PinSetupDialog(
            onDismiss = { showPinSetup = false },
            onPinSet  = { pin -> onSavePin(pin); showPinSetup = false },
        )
    }

    if (showDisableConfirm) {
        AlertDialog(
            onDismissRequest = { showDisableConfirm = false },
            title = { Text("Disable PIN lock?") },
            text  = { Text("This will remove PIN and biometric protection from the app.") },
            confirmButton = {
                TextButton(onClick = { onClearPin(); showDisableConfirm = false }) {
                    Text("Disable", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisableConfirm = false }) { Text("Cancel") }
            },
        )
    }

    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (prefs.pinEnabled) showDisableConfirm = true else showPinSetup = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("PIN Lock", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (prefs.pinEnabled) "Tap to disable" else "Tap to set up a 4-digit PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked         = prefs.pinEnabled,
                    onCheckedChange = { if (it) showPinSetup = true else showDisableConfirm = true },
                )
            }

            AnimatedVisibility(visible = prefs.pinEnabled) {
                Column {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetBiometrics(!prefs.biometricsEnabled) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Fingerprint, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Biometric Unlock", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Use fingerprint or face alongside PIN",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked         = prefs.biometricsEnabled,
                            onCheckedChange = { onSetBiometrics(it) },
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetInstantLock(!prefs.instantLockEnabled) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Instant Lock", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Lock immediately when app is minimized",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked         = prefs.instantLockEnabled,
                            onCheckedChange = { onSetInstantLock(it) },
                        )
                    }
                }
            }
        }
    }
}

// ── Recently Deleted Preview ──────────────────────────────────────────────────
@Composable
private fun RecentlyDeletedPreview(
    items: List<DeletedItem>,
    onRestore: (DeletedItem) -> Unit,
    onDelete: (DeletedItem) -> Unit,
    onManage: () -> Unit,
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${items.size} items in recycle bin. They will be permanently deleted after 30 days.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items.take(4).forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(item.uri)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                if (items.size > 4) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "+${items.size - 4}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = {
                    items.firstOrNull()?.let(onRestore)
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Restore Last")
            }
            TextButton(
                onClick = onManage,
                modifier = Modifier.align(Alignment.End),
            ) {
                Text("Manage All")
            }
        }
    }
}

// ── Storage Analytics ─────────────────────────────────────────────────────────
@Composable
private fun StorageAnalytics(totalSize: Long) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storage, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("App Storage Usage", style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = formatBytes(totalSize),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Total size of all media managed by the app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000     -> "%.1f KB".format(bytes / 1_000.0)
    else               -> "$bytes B"
}

private fun uriToPath(uriString: String): String? {
    // e.g. content://com.android.externalstorage.documents/tree/primary%3ADCIM
    return try {
        val decoded = java.net.URLDecoder.decode(uriString, "UTF-8")
        val treeId  = decoded.substringAfterLast("/tree/", "").substringBefore(":")
        val subPath = decoded.substringAfterLast(":", "")
        val root    = if (treeId.equals("primary", ignoreCase = true))
            Environment.getExternalStorageDirectory().absolutePath
        else "/storage/$treeId"
        if (subPath.isBlank()) root else "$root/$subPath"
    } catch (e: Exception) {
        null
    }
}
