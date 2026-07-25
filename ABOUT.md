# About myPhotos

---

## Navigation

The app has four main tabs accessible from the bottom navigation bar:

- **Media** — all photos and videos in a scrollable grid
- **Albums** — media organised by folder
- **Favorites** — starred items collected in one place
- **Settings** — appearance, folders, privacy, and storage

---

## Media Grid

- Displays photos, videos, and GIFs in a configurable column grid (default: 4 columns)
- Media is grouped by date with sticky section headers
- Video items show a play icon overlay and duration badge
- GIF items are labelled distinctly
- Sort options: Date (Newest), Date (Oldest), Name (A–Z), Size (Largest)
- Grid column count is adjustable from Settings

---

## Media Viewer

Tap any item to open the full-screen viewer.

- Pinch-to-zoom and pan on images
- Swipe left/right to navigate between items
- Swipe down to dismiss
- Action bar with:
    - **Share** — share the current item via any app
    - **Edit** — open in the image editor
    - **Favorite** — toggle star
    - **Move / Copy** — move or copy to another folder
    - **Delete** — send to Recycle Bin
    - **Info** — file name, size, dimensions, date

---

## Video Player

- Full-screen playback with ExoPlayer
- Playback controls: play/pause, seek bar, skip forward/back
- Supports standard video formats

---

## Image Editor

Non-destructive editing workflow — the original file is preserved; edits are saved as a new copy.

**Filters** — one-tap colour filters with live preview thumbnails:
- Original, Grayscale, Warm, Cool, Fade, Vivid

**Crop** — drag handles on each corner and edge to define a crop region. Reset to original bounds at any time.

**Rotate** — rotate 90° clockwise or counter-clockwise in steps.

---

## Albums

- Albums are derived automatically from folder structure
- Each album shows a cover thumbnail and item count
- Tap an album to browse its contents in a grid
- Supports nested sub-albums mirroring the folder hierarchy

---

## Favorites

- Tap the heart icon on any item in the viewer to favorite it
- Favorites persist across app restarts
- Dedicated Favorites tab for quick access

---

## Folder Management

Found in **Settings → Manage Media Folders**.

- Add any folder from internal storage or external storage via the system folder picker
- Folders are displayed as an expandable tree — tap the chevron to expand a root folder and see its subfolders
- Toggle visibility on any folder or subfolder with the eye icon — hidden folders and their contents are excluded from the grid and albums, but remain in the tree so they can be re-enabled at any time
- Remove a root folder entirely with the remove button
- Changes take effect immediately without restarting the app

---

## Recycle Bin

Found in **Settings → Recycle Bin**.

- Deleted items are moved to the Recycle Bin instead of being permanently deleted
- Each item shows its name, size, type, and when it was deleted
- **Restore** — returns the item to its original folder
- **Delete permanently** — removes the file from the device for good
- **Empty Bin** — permanently deletes all items at once

---

## Privacy & Security

Found in **Settings → Privacy**.

**PIN Lock**
- Set a 4-digit PIN to lock the app
- The app locks automatically on every exit when Instant Lock is enabled
- PIN is required to view any content after locking

**Biometric Unlock**
- Use fingerprint or face unlock instead of (or alongside) the PIN
- Falls back to PIN entry if biometric fails

**Instant Lock**
- When enabled, the app locks as soon as it goes to the background
- Suitable for keeping media private on a shared device

---

## Appearance

Found in **Settings → Appearance**.

- **System** — follows the device light/dark mode setting
- **Light** — always light theme
- **Dark** — always dark theme

The app uses a purple/lavender colour palette throughout with a transparent status bar and edge-to-edge layout.

---

## Storage Info

Found in **Settings**.

- Shows total storage used by all media currently loaded in the app
- Calculated from file sizes of all visible media items

---

## Move & Copy

- Trigger from the viewer action bar
- A bottom sheet lets you pick a destination album from your existing folders
- **Move** — relocates the file and updates the gallery
- **Copy** — duplicates the file into the chosen folder

---

## Offline & Private

- No internet permission required
- No analytics, telemetry, or crash reporting
- No cloud sync
- All data (favorites, PIN, folder preferences) is stored locally in SharedPreferences