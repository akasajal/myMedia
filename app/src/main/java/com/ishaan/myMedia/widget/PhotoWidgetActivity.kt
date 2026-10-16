package com.ishaan.myMedia.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ishaan.myMedia.R
import com.ishaan.myMedia.data.model.AppTheme
import com.ishaan.myMedia.data.model.MediaItem
import com.ishaan.myMedia.data.model.MediaType
import com.ishaan.myMedia.data.repository.MediaRepository
import com.ishaan.myMedia.ui.components.LockScreen
import com.ishaan.myMedia.ui.screens.ZoomableImage
import com.ishaan.myMedia.ui.theme.GalleryTheme
import com.ishaan.myMedia.viewmodel.GalleryUiState
import com.ishaan.myMedia.viewmodel.GalleryViewModel

class PhotoWidgetActivity : FragmentActivity() {
    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setResult(RESULT_CANCELED)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID ||
            AppWidgetManager.getInstance(this).getAppWidgetInfo(id)?.provider?.packageName != packageName) {
            finish(); return
        }
        val saved = PhotoWidgetStore(this).read(id)
        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val dark = when (state.theme) {
                AppTheme.DARK -> true
                AppTheme.LIGHT -> false
                AppTheme.SYSTEM -> isSystemInDarkTheme()
            }
            SideEffect {
                if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!state.privacyPrefs.pinEnabled)
                else if (state.privacyPrefs.pinEnabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            GalleryTheme(darkTheme = dark, accentColor = state.accentColor) {
                if (state.isLocked) {
                    LockScreen(state.privacyPrefs.biometricsEnabled, ::authenticate,
                        viewModel::checkPin, viewModel::unlock)
                } else if (intent.getBooleanExtra(EXTRA_VIEW, false) && saved != null) {
                    val requested = intent.getStringExtra(EXTRA_URI)
                    val item = state.allMedia.firstOrNull {
                        it.uri.toString() == requested && it.mediaType != MediaType.VIDEO &&
                            widgetFolderVisible(it.albumId, MediaRepository(this)::isFolderVisible) &&
                            (if (saved.imageUri != null) requested == saved.imageUri else widgetFolderMatches(it.albumId, saved.folders))
                    }
                    PhotoFromWidget(item, state.isLoading, onBack = ::finish)
                } else {
                    WidgetConfiguration(state, saved, viewModel::addFolder, onBack = ::finish) { config ->
                        PhotoWidgetStore(this).save(id, config)
                        PhotoWidgetProvider.update(this, id)
                        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                        finish()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshMediaOnResume()
    }

    override fun onStop() {
        super.onStop()
        if (viewModel.uiState.value.privacyPrefs.pinEnabled) viewModel.lock()
    }

    private fun authenticate() {
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { viewModel.unlock() }
        }).authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.widget_unlock))
            .setNegativeButtonText(getString(R.string.widget_use_pin)).build())
    }

    companion object {
        const val EXTRA_VIEW = "widget_view"
        const val EXTRA_URI = "widget_image_uri"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoFromWidget(item: MediaItem?, loading: Boolean, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(item?.name ?: stringResource(R.string.widget_name), maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.widget_back)) } })
    }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(Color.Black), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            else if (item != null) ZoomableImage(item, onSingleTap = {})
            else Text(stringResource(R.string.widget_no_images), color = Color.White, modifier = Modifier.padding(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetConfiguration(
    state: GalleryUiState,
    saved: PhotoWidgetConfig?,
    onAddFolder: (android.net.Uri) -> Unit,
    onBack: () -> Unit,
    onSave: (PhotoWidgetConfig) -> Unit,
) {
    var mode by rememberSaveable { mutableIntStateOf(if (saved == null || saved.imageUri != null) 0 else if (saved.folders.size == 1) 1 else 2) }
    var image by rememberSaveable { mutableStateOf(saved?.imageUri) }
    var folders by rememberSaveable { mutableStateOf(saved?.folders?.toList() ?: emptyList<String>()) }
    var interval by rememberSaveable { mutableIntStateOf(saved?.intervalSeconds ?: 15) }
    var shuffle by rememberSaveable { mutableStateOf(saved?.shuffle ?: false) }
    var fit by rememberSaveable { mutableStateOf(saved?.fit ?: false) }
    // Ask for every new widget, even if app lock is currently disabled.
    var allowWhileLocked by rememberSaveable { mutableStateOf<Boolean?>(saved?.allowWhileLocked) }
    var showPrivacyWarning by rememberSaveable { mutableStateOf(false) }
    var saveAfterPrivacyChoice by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val repo = remember(context) { MediaRepository(context) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) onAddFolder(uri) }
    val images = remember(state.allMedia, state.folderTree) {
        val visibility = mutableMapOf<String, Boolean>()
        state.allMedia.filter { it.mediaType != MediaType.VIDEO &&
            visibility.getOrPut(it.albumId) { widgetFolderVisible(it.albumId, repo::isFolderVisible) } }
    }
    val availableFolders = remember(images, state.chosenFolders) {
        (images.map { it.albumId } + state.chosenFolders.filterValues { it }.keys)
            .distinct().filter { folder -> images.any { widgetFolderMatches(it.albumId, setOf(folder)) } }.sorted()
    }
    val validFolders = folders.filter { it in availableFolders }.toSet()
    val candidates = remember(images, image, mode, validFolders) {
        images.filter { if (mode == 0) it.uri.toString() == image else widgetFolderMatches(it.albumId, validFolders) }
    }
    val canSave = !state.isLoading && candidates.isNotEmpty() && (mode != 1 || validFolders.size == 1)
    fun saveConfiguration(allow: Boolean) {
        onSave(PhotoWidgetConfig(
            imageUri = if (mode == 0) image else null,
            folders = if (mode == 0) emptySet() else validFolders,
            intervalSeconds = interval,
            shuffle = shuffle,
            fit = fit,
            allowWhileLocked = allow,
        ))
    }
    fun choosePrivacy(allow: Boolean) {
        allowWhileLocked = allow
        showPrivacyWarning = false
        if (saveAfterPrivacyChoice && canSave) saveConfiguration(allow)
        saveAfterPrivacyChoice = false
    }
    if (showPrivacyWarning) {
        AlertDialog(
            onDismissRequest = { showPrivacyWarning = false; saveAfterPrivacyChoice = false },
            title = { Text(stringResource(R.string.widget_privacy_warning_title)) },
            text = { Text(stringResource(R.string.widget_privacy_warning)) },
            confirmButton = { TextButton(onClick = { choosePrivacy(true) }) { Text(stringResource(R.string.widget_allow)) } },
            dismissButton = { TextButton(onClick = { choosePrivacy(false) }) { Text(stringResource(R.string.widget_deny)) } },
        )
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.widget_configure)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.widget_back)) }
        })
    }, bottomBar = {
        Surface(tonalElevation = 3.dp) {
            Button(
                onClick = {
                    val allowed = allowWhileLocked
                    if (allowed == null) { saveAfterPrivacyChoice = true; showPrivacyWarning = true }
                    else saveConfiguration(allowed)
                },
                enabled = canSave,
                modifier = Modifier.navigationBarsPadding().padding(16.dp).fillMaxWidth(),
            ) { Text(stringResource(if (saved == null) R.string.widget_add else R.string.widget_save)) }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (candidates.isNotEmpty()) {
                item(key = "preview") {
                    AsyncImage(candidates.first().uri, stringResource(R.string.widget_preview),
                        modifier = Modifier.fillMaxWidth().aspectRatio(2f).clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = if (fit) ContentScale.Fit else ContentScale.Crop)
                }
            }
            item {
                WidgetToggle(stringResource(R.string.widget_show_while_locked), allowWhileLocked == true) { allow ->
                    if (allow) { saveAfterPrivacyChoice = false; showPrivacyWarning = true }
                    else allowWhileLocked = false
                }
                Text(stringResource(R.string.widget_privacy_note), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
            item { Text(stringResource(R.string.widget_source), style = MaterialTheme.typography.titleMedium) }
            items(listOf(R.string.widget_single_image, R.string.widget_one_folder, R.string.widget_multiple_folders)) { label ->
                val index = when (label) { R.string.widget_single_image -> 0; R.string.widget_one_folder -> 1; else -> 2 }
                Row(Modifier.fillMaxWidth().selectable(mode == index, role = Role.RadioButton, onClick = {
                    mode = index
                    if (index == 1 && folders.size > 1) folders = folders.take(1)
                }).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mode == index, onClick = null)
                    Spacer(Modifier.width(12.dp)); Text(stringResource(label))
                }
            }
            item {
                WidgetToggle(stringResource(R.string.widget_fit), fit) { fit = it }
            }
            if (mode != 0) {
                item { Text(stringResource(R.string.widget_interval), style = MaterialTheme.typography.titleMedium) }
                items(widgetIntervals) { seconds ->
                    val label = when (seconds) { 15 -> R.string.widget_15s; 30 -> R.string.widget_30s; 60 -> R.string.widget_1m; else -> R.string.widget_5m }
                    Row(Modifier.fillMaxWidth().selectable(interval == seconds, role = Role.RadioButton, onClick = { interval = seconds }), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(interval == seconds, onClick = null); Spacer(Modifier.width(12.dp)); Text(stringResource(label))
                    }
                }
                item { WidgetToggle(stringResource(R.string.widget_shuffle), shuffle) { shuffle = it } }
                item { Text(stringResource(R.string.widget_timing_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            item {
                OutlinedButton(onClick = { picker.launch(null) }) { Text(stringResource(R.string.widget_add_folder)) }
            }
            if (state.isLoading) item { CircularProgressIndicator() }
            state.errorMessage?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
            if (!state.isLoading && images.isEmpty()) item { Text(stringResource(R.string.widget_no_library)) }
            if (mode == 0) {
                items(images.chunked(3), key = { it.first().uri.toString() }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { media ->
                            val selected = image == media.uri.toString()
                            Column(Modifier.weight(1f).selectable(selected, role = Role.RadioButton, onClick = { image = media.uri.toString() })) {
                                Box {
                                    AsyncImage(media.uri, media.name, Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Crop)
                                    if (selected) Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                                        Icon(Icons.Rounded.CheckCircle, null, Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                Text(media.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            } else {
                items(availableFolders, key = { it }) { folder ->
                    val selected = folder in folders
                    val choose: () -> Unit = { folders = if (mode == 1) listOf(folder) else if (selected) folders - folder else folders + folder }
                    Row(Modifier.fillMaxWidth().then(if (mode == 1) Modifier.selectable(selected, role = Role.RadioButton, onClick = choose)
                        else Modifier.toggleable(selected, role = Role.Checkbox, onValueChange = { choose() })).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (mode == 1) RadioButton(selected, onClick = null) else Checkbox(selected, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(folder.substringAfterLast('/'), style = MaterialTheme.typography.bodyLarge)
                            Text(folder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f).padding(end = 16.dp))
        Switch(checked, onCheckedChange = null)
    }
}
