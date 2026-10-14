# UI, UX, and accessibility audit

Reviewed source at commit `e863a8c`. This is a source review, not a device or TalkBack certification. No application behavior was changed during this audit.

P1 means a broken or inaccessible core flow. P2 means a significant usability or accessibility defect.

## Coverage

| Area | Reviewed |
| --- | --- |
| Entry and permissions | Permission gate, splash, lock lifecycle, Recents protection |
| Media and Favorites | Sorting, thumbnail mapping, selection, scroll state, empty/error states |
| Albums | Overview, creation, sorting, pinning, album detail |
| Viewer | Paging, overlays, double-tap zoom, thumbnail strip, actions |
| Video | Playback controls, seeking, lifecycle, control timeout |
| Editor | Loading, saving, filters, rotation, crop preview and gestures |
| Settings | Appearance, privacy switches, recently deleted preview, storage |
| Folders and Recycle Bin | Tree controls, visibility, restore/delete, layouts |
| Shared UI | Theme, typography, thumbnails, grid rows, PIN setup/keypad, move/copy sheet |

## High-priority findings

### 1. P1 — Sorting can open the wrong media item

[MediaScreen.kt:95](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MediaScreen.kt:95), [MainScreen.kt:421](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MainScreen.kt:421).

The grid computes an index in `mediaGroups.flatMap { it.items }`, but the viewer receives `allMedia`. Grouping by date changes the order when files sorted by name or size are interleaved across dates. Tapping the third item can therefore open another file. Use the tapped item's identity to resolve the index in the exact viewer list, or pass the grid's flattened list.

### 2. P1 — Move/copy can lose all destination albums

[MediaRepository.kt:226](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/data/repository/MediaRepository.kt:226), [MediaRepository.kt:611](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/data/repository/MediaRepository.kt:611), [MoveCopySheet.kt:59](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/components/MoveCopySheet.kt:59).

Deduplication keeps the MediaStore entry, whose `parentUri` is null. Album construction derives `folderUri` only from media items with a `parentUri`; the destination sheet then excludes albums without one. An entirely indexed album can disappear from destinations even though its folder has SAF access. Resolve album folder URIs independently from retained media entries and include writable empty folders.

### 3. P1 — Seeking can crash while a video is loading

[VideoPlayerScreen.kt:135](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/VideoPlayerScreen.kt:135).

Double-tap seeking calls `coerceIn(0L, exoPlayer.duration)` before checking whether duration is known. ExoPlayer can report a negative unknown-duration sentinel before preparation; that makes the range invalid. The forward button also uses this raw duration. Disable seeking until duration is valid and handle loading/playback failures explicitly.

### 4. P1 — Image viewer controls cannot be revealed through an accessibility action

[MediaViewerScreen.kt:79](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MediaViewerScreen.kt:79), [MediaViewerScreen.kt:403](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MediaViewerScreen.kt:403).

The action overlays start hidden. Revealing them and zooming depend on raw `pointerInput` gestures; those handlers expose no semantic click or zoom actions. The image's file-name description does not supply these actions. Add an accessible Show controls action, explicit zoom/reset actions, and accessible navigation between media. Keep controls available for assistive technology.

### 5. P1 — Cropping has no accessible alternative

[ImageEditorScreen.kt:475](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/ImageEditorScreen.kt:475), [ImageEditorScreen.kt:522](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/ImageEditorScreen.kt:522).

Crop handles exist only as Canvas drawings and drag hit tests. There are no focusable handles, adjustment actions, presets, or numeric controls for TalkBack, Switch Access, or keyboard users. Hit tolerance is also fixed in pixels: the 80px-wide corner region is only about 27dp at 3x density. Provide semantic adjustment actions/presets and density-aware touch targets.

### 6. P1 — Permission denial can leave users stuck

[MainActivity.kt:129](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/MainActivity.kt:129), [GalleryViewModel.kt:70](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/viewmodel/GalleryViewModel.kt:70).

The gate requires every requested media permission. Granting photos but denying videos blocks the whole app, including folders that already have SAF permission. A permanently denied permission has only a request-again button, with no Settings recovery. The ViewModel starts loading before permission approval, and the grant callback does not reload it. Support each granted source, recheck permissions on return, and reload after approval. Android 14 selected-media access also needs an explicit reselection and lifecycle strategy.

## Additional findings

### 7. P2 — A viewer can leave its album/Favorites collection during updates

[MainScreen.kt:63](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MainScreen.kt:63), [MediaViewerScreen.kt:76](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MediaViewerScreen.kt:76).

Every `allMedia` update replaces the viewer's list with the whole visible library, even when opened from an album or Favorites. `ViewerState.index` also stays at the opening index when the pager moves, and `initialPage` does not reposition an existing pager. Preserve collection scope and report the currently viewed media ID before reconciling updates.

### 8. P2 — Light-mode accent text and custom button labels have insufficient contrast

[Theme.kt:26](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/theme/Theme.kt:26), [SharedComponents.kt:233](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/components/SharedComponents.kt:233), [MediaFoldersScreen.kt:73](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MediaFoldersScreen.kt:73).

Modeled from the source's Oklab interpolation, approximate primary-text contrast against `#FAF8FB` is:

| Accent | Primary color | Contrast |
| --- | --- | --- |
| Rose | #DAA8BC | 1.92:1 |
| Purple | #BDB2F7 | 1.82:1 |
| Green | #A8D5A9 | 1.56:1 |
| Teal | #99D4D4 | 1.56:1 |
| Gold | #F6D892 | 1.31:1 |
| Slate | #9EABC5 | 2.19:1 |

These values fall below the 4.5:1 recommendation for normal small text. Date headers, Appearance headings, and selected navigation labels use that color. White labels on the raw pastel accents also range from approximately 1.68:1 to 3.97:1, before the gradient becomes still lighter. Keep the requested pastel highlights, but use a contrasting text/icon color when they appear on light surfaces. Confirm the final rendered colors on a device.

### 9. P2 — Crop preview coordinates do not match the saved image

[ImageEditorScreen.kt:514](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/ImageEditorScreen.kt:514), [ImageEditorScreen.kt:528](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/ImageEditorScreen.kt:528), [ImageEditorScreen.kt:702](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/ImageEditorScreen.kt:702).

`onSizeChanged` measures the entire fitted-image container, including letterboxing. The crop overlay uses those bounds, but saving maps the normalized crop directly to the rotated bitmap. Rotation further changes the visible bounds without changing the crop coordinate system. The gesture handler also captures `cropRect` with only `imageSize` as its restart key, so later drags can start from stale bounds. Use the fitted, rotated image rectangle and read current crop state inside the gesture handler.

### 10. P2 — Album controls promise behavior they do not implement

[AlbumsScreen.kt:52](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/AlbumsScreen.kt:52), [AlbumsScreen.kt:77](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/AlbumsScreen.kt:77), [MainScreen.kt:446](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MainScreen.kt:446).

Create Album invokes a default empty callback because the caller supplies no implementation. The dialog dismisses without creating anything. Recent sorting orders by item count, exactly like Most items. Implement these actions or remove them until supported.

### 11. P2 — PIN entry lacks essential feedback

[PinSetupDialog.kt:141](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/components/PinSetupDialog.kt:141), [LockScreen.kt:81](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/components/LockScreen.kt:81).

The backspace button has no accessible label. PIN progress is represented only by colored dots, and an incorrect unlock PIN silently clears the input. Add a Delete digit label, a non-sensitive digit-count announcement, and an announced incorrect-PIN error. Do not announce the PIN itself.

### 12. P2 — Navigation and album sorting reset on recreation

[MainScreen.kt:44](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MainScreen.kt:44), [AlbumsScreen.kt:58](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/AlbumsScreen.kt:58).

Tab, active album, viewer, editing and selection state use plain `remember`, so activity recreation resets the route. Album sort order is local and resets even when returning from an album. The overview scroll state survives that normal return, but its items can silently reorder beneath the saved position. Save appropriate route IDs and hoist album sort state; preserve edit state or warn before discarding it.

### 13. P1 — Hidden media remains reachable through the Favorites viewer

[GalleryViewModel.kt:166](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/viewmodel/GalleryViewModel.kt:166).

Visibility toggles update `allMedia`, date groups and albums, but do not rebuild `favorites` or `totalStorageUsage`. Favorites filters its grid through the updated date groups, yet passes the entire stale favorites list to the viewer. After hiding a folder with a favorite, opening a remaining visible favorite can therefore let the user swipe to the hidden item. If all favorites are hidden, the screen also renders a blank list instead of its empty state. Recompute all derived lists/counts from the same visible media set.

### 14. P2 — Loading and file-operation failures have no useful recovery

[GalleryViewModel.kt:103](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/viewmodel/GalleryViewModel.kt:103), [GalleryViewModel.kt:237](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/viewmodel/GalleryViewModel.kt:237), [ImageEditorScreen.kt:191](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/ImageEditorScreen.kt:191).

`errorMessage` is never rendered by the screens, so failed loads look like an empty gallery. Move/copy dismiss their sheet without progress, success or failure feedback. Editor Save is enabled before the source bitmap exists and silently does nothing when loading failed. Provide error states with retry, operation result feedback, and a Save state tied to loading and completion.

### 15. P2 — Save feedback can be obscured and album counts print as code

[MainScreen.kt:144](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MainScreen.kt:144), [MainScreen.kt:247](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MainScreen.kt:247).

Album bulk-action strings escape the dollar sign, displaying `${albumSelection.count}` and `${uris.size}` literally. The save snackbar is emitted before the screen/scaffold that is drawn afterward, so it can be covered. Use actual interpolation and a SnackbarHost in the visible screen scaffold.

### 16. P2 — Thumbnail and selection semantics are incomplete

[MediaViewerScreen.kt:329](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MediaViewerScreen.kt:329), [SharedComponents.kt:90](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/components/SharedComponents.kt:90), [AlbumsScreen.kt:193](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/AlbumsScreen.kt:193).

Viewer-strip thumbnails are clickable but their images have no descriptions, leaving unnamed navigation targets. Selection and pinning have visual indicators but no consistent selected/state semantics or descriptive action labels. CombinedClickable does expose generic click/long-click actions already; improve their labels and state rather than replacing them with raw gestures. Announce media name/type, selection state, and pin/unpin actions.

### 17. P2 — Settings and folder controls lack context when focused individually

[SettingsScreen.kt:290](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/SettingsScreen.kt:290), [MediaFoldersScreen.kt:141](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MediaFoldersScreen.kt:141), [VideoPlayerScreen.kt:372](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/VideoPlayerScreen.kt:372).

Clickable preference rows contain separately interactive switches without their own setting labels, producing ambiguous extra focus targets. Folder buttons repeat Toggle Visibility without naming the folder or announcing hidden/visible state. Repeat cycles through three modes but has a constant Repeat label. Use a single labeled toggle per preference and contextual action/state descriptions elsewhere.

### 18. P2 — Timed controls ignore accessibility timeouts

[VideoPlayerScreen.kt:110](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/VideoPlayerScreen.kt:110), [MainScreen.kt:249](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/screens/MainScreen.kt:249).

Video controls disappear after three seconds regardless of accessibility focus or an open speed menu. Save messages disappear after 2.5 seconds. Respect the user's recommended accessibility timeout and keep focused controls/menu interactions visible.

### 19. P2 — Navigation-bar icon appearance does not follow the app theme

[Theme.kt:88](E:/PROJECTS/androidProjects/myMedia/app/src/main/java/com/ishaan/myMedia/ui/theme/Theme.kt:88).

The theme changes status-bar icon appearance but never updates navigation-bar icon appearance. `enableEdgeToEdge()` uses system theme defaults, so forcing an opposite theme in the app can leave the three-button navigation controls with unsuitable contrast. Update both system bars from the selected theme and test gesture and three-button navigation.

## Device checks still required

- TalkBack and Switch Access: complete unlock, select media, reveal viewer actions, zoom/reset, crop, restore and delete; verify actual focus order and announcements.
- Large text and landscape: the lock column has no scrolling and a fixed 64dp keypad; the editor panel is fixed at 140dp; viewer actions and selection toolbars use dense unweighted rows. Check these at 200% font scaling and on narrow/short windows.
- Settings recently-deleted preview: four 60dp thumbnails plus a 60dp overflow badge require 332dp before outer/card padding. Check clipping on 320–360dp screens and make the row adaptive or scrollable.
- Appearance: six weighted swatches can be spaced less than 48dp apart. Compose can expand touch targets automatically, so nominal size alone does not prove a failure; test neighboring hit areas and adapt the layout where necessary.
- Recycle Bin: two action buttons share each narrow grid card. Test expanded text, long names, and individual contextual button announcements.
- Video: check behavior on Home/Recents with Instant Lock disabled. The player is released on disposal but has no background-pause observer.
- Keyboard and RTL: confirm focus traversal and directional controls. Visible English strings are hardcoded rather than localized string resources.
- Privacy: check Recents after enabling/disabling lock, and return from the system picker and share chooser without unexpected route loss.
- Regression: repeated rapid double-taps, scroll restoration, source deduplication, and opening media after background refresh.

## Verification and references

Android lint was attempted offline, then with dependency resolution and configuration caching disabled. It reached analysis but failed inside the lint detector migration/loading code with `NegativeArraySizeException`; this audit therefore makes no lint-clean claim. Kotlin compilation was successful in the preceding fix, and lint reused that compiled output. No APK was assembled for this review.

Accessibility recommendations follow [Android's accessibility guidance](https://developer.android.com/guide/topics/ui/accessibility/apps), [Compose semantics](https://developer.android.com/develop/ui/compose/accessibility/semantics), and [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults). Permission recovery should account for [Android selected-media access](https://developer.android.com/about/versions/14/changes/partial-photo-video-access). Color estimates model [Compose's Oklab interpolation](https://android.googlesource.com/platform/frameworks/support/+/c7d2a9b0f7f27c85a187b05044b5fb8f1281a191/compose/ui/ui-graphics/src/commonMain/kotlin/androidx/compose/ui/graphics/Color.kt).

Recommended implementation order: incorrect media mapping and missing destinations; video seeking and permission recovery; accessible viewer/crop/PIN controls; contrast and feedback; navigation/state and adaptive layouts.
