# myMedia

A native Android gallery app built with Jetpack Compose and Material 3.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM (ViewModel + StateFlow) |
| Image Loading | Coil (with `VideoFrameDecoder` for thumbnails) |
| Video Playback | Media3 ExoPlayer |
| Storage Access | MediaStore API + Storage Access Framework (SAF) |
| Biometrics | AndroidX Biometric (`BiometricPrompt`) |
| Persistence | SharedPreferences |
| Database | None — media is sourced live from MediaStore and SAF cache |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 35 |

---

## Project Structure

```
app/src/main/java/com/ishaan/myMedia/
├── data/
│   ├── model/
│   │   └── Models.kt               # Data classes and enums (MediaItem, Album, FolderNode, etc.)
│   └── repository/
│       ├── MediaRepository.kt      # MediaStore queries, SAF handling, folder visibility, cache
│       └── PrivacyRepository.kt    # PIN hashing, biometric prefs, lock state
├── ui/
│   ├── components/
│   │   ├── LockScreen.kt           # PIN entry + biometric unlock UI
│   │   ├── MediaGridRow.kt         # Reusable grid row composable
│   │   ├── MoveCopySheet.kt        # Bottom sheet for move/copy operations
│   │   ├── PinSetupDialog.kt       # PIN creation and confirmation dialog
│   │   └── SharedComponents.kt     # MediaThumbnail and other shared composables
│   ├── screens/
│   │   ├── MainScreen.kt           # Root scaffold, bottom nav, screen routing
│   │   ├── MediaScreen.kt          # Main media grid (photos + videos)
│   │   ├── AlbumsScreen.kt         # Album grid view
│   │   ├── FavoritesScreen.kt      # Favorites grid
│   │   ├── MediaViewerScreen.kt    # Full-screen image/gif viewer with actions
│   │   ├── VideoPlayerScreen.kt    # Full-screen video player
│   │   ├── ImageEditorScreen.kt    # Image editor (filters, crop, rotate)
│   │   ├── MediaFoldersScreen.kt   # Folder tree management
│   │   ├── RecycleBinScreen.kt     # Deleted items with restore/purge
│   │   └── SettingsScreen.kt       # App settings
│   └── theme/
│       ├── Theme.kt                # Color scheme (light + dark), GalleryTheme
│       └── Type.kt                 # Typography
├── viewmodel/
│   └── GalleryViewModel.kt         # Single ViewModel holding GalleryUiState
├── GalleryApplication.kt           # Application class, Coil ImageLoader setup
└── MainActivity.kt                 # Entry point, splash screen, permission gate, biometric prompt
```

---

## Architecture

The app uses a single-ViewModel MVVM pattern. `GalleryViewModel` exposes one `GalleryUiState` data class via `StateFlow`, and all screens read from it via `collectAsStateWithLifecycle()`.

```
MediaRepository  ──►  GalleryViewModel  ──►  GalleryUiState (StateFlow)
PrivacyRepository ──►        │                      │
                             │               Compose screens
                             │               (collect + render)
                        viewModelScope
                        (coroutines)
```

### State

`GalleryUiState` holds:
- `rawMedia` — all media items, unfiltered by visibility (used for re-applying visibility in-memory)
- `allMedia` — visibility-filtered media for display
- `allFolderPaths` — set of all folder paths that contain media, regardless of visibility (used to keep hidden folders in the tree)
- `folderTree` — hierarchical `FolderNode` tree for the Manage Folders screen
- `mediaGroups` — media grouped by date for the grid header
- `albums`, `favorites`, `deletedItems`, `chosenFolders`
- `theme`, `gridColumns`, `sortOrder`, `privacyPrefs`, `isLocked`

---

## Media Sources

The app reads media from two sources:

**MediaStore** — standard gallery folders (`DCIM`, `Pictures`, `Movies`, etc.) queried via `ContentResolver`.

**SAF (Storage Access Framework)** — user-picked folders outside standard paths. URIs are persisted with `takePersistableUriPermission` so they survive app restarts. SAF media is cached in SharedPreferences and refreshed in the background after each load.

---

## Folder Visibility

Each folder path has a `folder_visible_<path>` boolean in SharedPreferences (default `true`). Hiding a parent folder implicitly hides all its subfolders via `isFolderVisible()`, which walks up the path tree. Toggling visibility is instant — it rebuilds the folder tree and refilters `rawMedia` in-memory without any disk access.

---

## Privacy & Security

- PIN is SHA-256 hashed before storage in SharedPreferences
- Biometric authentication uses `BiometricPrompt` with `BIOMETRIC_STRONG`
- Instant lock re-locks the app on every `onStop()`
- The lock screen intercepts navigation before any media is shown

---

## Theming

The app uses a custom purple palette (`RichPlum`, `DeepGrape`, `MediumViolet`, `LavenderMist`) applied via Material 3 `lightColorScheme` / `darkColorScheme`. Theme follows system by default and can be overridden to Light or Dark in Settings. Status bar is transparent with edge-to-edge enabled.

---

## Dependencies

```kotlin
// Core
androidx.core.splashscreen:1.0.1
androidx.biometric:biometric:1.2.0-alpha05
androidx.documentfile:documentfile:1.1.0

// Compose
androidx.compose (BOM)
androidx.compose.material3
androidx.compose.material.icons.extended
androidx.lifecycle.viewmodel.compose
androidx.lifecycle.runtime.compose
androidx.activity.compose
androidx.compose.foundation (HorizontalPager)

// Media
coil:coil-compose
coil:coil-video
androidx.media3:media3-exoplayer:1.3.1
androidx.media3:media3-ui:1.3.1
```

---

## Build

```bash
./gradlew assembleDebug
./gradlew assembleRelease
```

Requires Android Studio Hedgehog or later. No API keys or external services needed.