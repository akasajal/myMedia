# myMedia

A native Android gallery built with Kotlin, Jetpack Compose, and Material 3. Browse photos and videos from selected device folders, organize media, and keep gallery preferences on your device.

See [CHANGELOG.md](CHANGELOG.md) for **v1.2** and earlier changes.

## Features

- **Media:** photos, videos, and GIF entries in a date-grouped grid. Sort by newest, oldest, name, or size; pinch to change the grid between two and six columns.
- **Albums:** folders containing visible photos or videos, with covers, item counts, pinning, and name/count/recent-date sorting. Create a folder inside a selected destination; it appears in Albums once media is added.
- **Favorites:** locally saved favorites, filtered by folder visibility.
- **Viewer:** swipe between media, pinch or double-tap to zoom, pan within image bounds, and tap to show actions. Includes a thumbnail strip, sharing, editing, favorites, move/copy, deletion, and file information.
- **Video:** Media3 playback with play/pause, seeking, ten-second skips, speed, mute, and repeat controls.
- **Editor:** filters, 90-degree rotation, crop handles, and labeled crop-edge sliders. Edits are saved as a new JPEG in `Pictures/myMedia`, preserving the original.
- **Folder management:** choose folders through Android's Storage Access Framework (SAF), browse their tree, and toggle visibility. Empty folders can still be move/copy destinations.
- **Recently deleted:** restore entries or permanently delete them. Deleting initially hides the item in the app's recycle bin; physical deletion happens on permanent deletion or an expiry cleanup attempt when the bin is loaded.
- **Appearance:** System / Dark / Light controls and six accent colors: Rose, Purple, Green, Teal, Gold, and Slate. Preferences persist across restarts.
- **Privacy:** four-digit PIN, optional strong biometrics, Instant Lock, and protected recent-app previews when PIN lock is enabled.
- **Photo widget:** a resizable home-screen widget showing one image or a slideshow from one or more folders, with fit/fill framing, shuffle, and separate settings for each widget.

## Photo widget

Open your launcher's widget picker, choose **My Media Photos**, and place the widget. Select a particular image, one folder, or multiple folders, then tap **Add widget**. You can also add a folder to the app from this configuration screen. The default size is 8 rows × 6 columns; actual sizing depends on the launcher and available grid space.

Folder slideshows include photos from descendants and combine overlapping selections without duplicate files. Choose a 15-second, 30-second, 1-minute, or 5-minute interval. Shuffle changes the order when the collection reloads. GIFs appear as still frames; videos are excluded. Tap a photo to open a zoomable view. To reconfigure, touch and hold the widget and choose the launcher's Edit/settings action. This uses Android's reconfigurable-widget support; the menu wording and availability depend on the launcher. The widget has no overlaid controls. Empty or locked placeholders can also be tapped to open configuration.

The launcher runs the slideshow through Android's native `AdapterViewFlipper`, without a persistent service or frequent alarms. It pauses when the widget's window is hidden, including screen-off on supported launchers. Returning starts a fresh interval; partial countdowns are not preserved. Launcher behavior must be verified on a device. The 30-minute widget update refreshes the collection, independently of the slide interval; SAF-only discoveries also depend on the app's folder scan cache being refreshed.

Hidden and recently deleted media are excluded. Adding a widget asks whether to allow photos on the home screen while app lock is enabled. **Allow** keeps that widget visible while locked; **Deny** hides its photos whenever app lock is enabled. Each widget stores its own choice, which can be changed in widget settings; existing widgets default to Deny. Allowing home-screen display does not bypass PIN/biometric authentication when opening a photo or widget settings. Removed or unavailable sources show a recovery message with access to configuration.

See [WIDGET_TESTING.md](WIDGET_TESTING.md) for verification status and device checks.

## Media access

Add root folders through **Settings > Manage Media Folders**. The gallery combines MediaStore entries under those roots with cached SAF scans and refreshes folder contents in the background. Entries from both sources are deduplicated by file identity.

On supported Android versions, library access can be granted for photos, videos, or selected media. If permission is denied, open Android's permission settings or continue with individual folder access. The app rechecks access and reloads media when returning to the foreground.

The Albums overview excludes folders containing only text, audio, or no visible media. Hiding a folder excludes its contents from Media, Albums, Favorites, and storage totals. Visibility is an app display preference; it does not encrypt or relocate the files.

## Privacy and accessibility

PINs are stored as SHA-256 hashes in local SharedPreferences. When enabled, Instant Lock locks the gallery as the activity stops. PIN entry provides digit-count semantics, a labeled backspace action, and an incorrect-PIN message.

With PIN lock enabled, Android 13+ disables recent-app snapshots. Earlier versions use a secure-window flag, which also blocks foreground screenshots. Android controls the replacement preview; it may be blank rather than blurred.

Crop sliders provide an alternative to dragging. Thumbnail, selection, pinning, folder, settings, and playback controls expose descriptive labels or state. Video controls respect accessibility timing and remain visible while accessibility services are active. Light surfaces use contrasting text colors while pastel highlights remain available.

The viewer's tap-to-reveal controls are intentionally retained. Video and keyboard/RTL device checks remain pending; implementation and validation details are recorded in [UI_UX_ACCESSIBILITY_AUDIT.md](UI_UX_ACCESSIBILITY_AUDIT.md).

## Tech stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM, AndroidViewModel, StateFlow, coroutines |
| Images and thumbnails | Coil + VideoFrameDecoder |
| Video | AndroidX Media3 ExoPlayer |
| Media access | MediaStore, SAF, DocumentFile, DocumentsContract |
| Authentication | AndroidX BiometricPrompt |
| Persistence | Local SharedPreferences; no database |
| Android support | Minimum API 26; compile/target API 35 |
| JVM | Gradle daemon JDK 21; app bytecode targets JVM 17 |

The app requests no internet permission and has no cloud sync, analytics, or external API keys. Gradle needs access to repositories when build dependencies are not already cached.

## Project structure

```text
app/src/main/java/com/ishaan/myMedia/
|-- MainActivity.kt                 # Entry, permissions, lock lifecycle, Recents protection
|-- GalleryApplication.kt           # Shared Coil image loader and caches
|-- data/
|   |-- model/Models.kt             # Media, album, folder, privacy, and appearance models
|   `-- repository/
|       |-- MediaRepository.kt      # Queries, scans, deduplication, folders, file operations
|       `-- PrivacyRepository.kt    # PIN hash and privacy preferences
|-- viewmodel/GalleryViewModel.kt   # Gallery state, loading, operations, and feedback
`-- ui/
    |-- screens/                   # Tabs, album details, viewer, player, editor, folders, bin
    |   |-- MainScreen.kt          # Routing, saved navigation state, snackbar host
    |   |-- SelectionState.kt      # Media selection model
    |   `-- MediaUiMath.kt         # Seek bounds and fitted image geometry
    |-- components/                # Thumbnails, grids, PIN keypad/dialogs, move/copy sheet
    `-- theme/                     # Material colors, system bars, typography

app/src/test/java/com/ishaan/myMedia/ui/screens/
`-- MediaUiMathTest.kt              # Local seeking and crop-geometry regression tests

gradle/libs.versions.toml           # Dependency and plugin versions
CHANGELOG.md                       # Version release notes
UI_UX_ACCESSIBILITY_AUDIT.md        # Audit findings, resolution commits, validation status
```

## Architecture

`GalleryViewModel` coordinates `MediaRepository` and `PrivacyRepository`. `MainScreen` collects its `GalleryUiState` with lifecycle awareness and passes data and callbacks to the screens.

```text
MediaRepository ----\
                     > GalleryViewModel -> GalleryUiState -> Compose screens
PrivacyRepository --/
```

State includes unfiltered and visible media, date groups, albums, destination folders, favorites, recycle-bin entries, folder visibility, appearance, privacy, and operation feedback. Saved navigation uses route IDs rather than serializing the full library. Viewer updates preserve their source collection and current item; editor state survives activity recreation.

## Build and checks

Use the included Gradle wrapper with an Android SDK installation containing API 35 and a JDK 21 runtime for the Gradle daemon. Configure the SDK path through Android Studio or `local.properties`.

PowerShell:

```powershell
# Compile Kotlin without assembling an APK
.\gradlew.bat :app:compileDebugKotlin

# Run local regression tests
.\gradlew.bat :app:testDebugUnitTest --no-configuration-cache

# Build APKs when needed
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

On macOS/Linux, use `./gradlew` instead of `.\gradlew.bat`. Add `--offline` only after dependencies are cached.

The six local regression tests cover unknown video duration, seek limits/overflow, landscape letterboxing, rotated image dimensions, and an unmeasured preview. Kotlin compilation and these tests passed for the v1.1 changes. Android lint encountered an internal detector-loading failure; see the audit for details.
