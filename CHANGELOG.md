# Changelog

Changes prepared for v1.1. Entries describe application behavior; the audit records the individual implementation commits.

## 1.1

### Added

- Segmented System / Dark / Light appearance controls and six persistent accent colors: Rose, Purple, Green, Teal, Gold, and Slate.
- Album folder creation with a destination picker and name validation.
- Labeled crop-edge sliders as an alternative to dragging crop handles, with density-aware handle targets.
- PIN progress semantics, a labeled backspace action, and incorrect-PIN feedback.
- Permission recovery through Android settings or individual folder access, including photo-only, video-only, and selected-media grants.
- Loading retries, move/copy progress and result messages, and visible editor load/save errors.
- Recent-app preview protection while PIN lock is enabled. Android controls whether the replacement card is blank or obscured; the older Android fallback also blocks screenshots.

### Changed

- Albums include only folders containing visible images or videos. Text-only, audio-only, and empty folders are excluded from the overview, while empty folders remain available as move/copy destinations.
- Recent album sorting uses the latest media date.
- Theme and accent choices persist across restarts. Pastel highlights are retained, with contrasting text and icons on light surfaces and custom controls.
- Tab, album, viewer, secondary-screen, selection, and editor state are restored after activity recreation. Album sorting survives navigation into an album and back.
- Thumbnail, selection, pinning, settings, folder, and playback controls expose clearer accessibility labels and state.
- Video controls respect accessibility timeouts and remain visible during accessibility-service use, seeking, and speed-menu interaction.
- Status-bar and navigation-bar icons follow the selected app theme and the dark backgrounds used by media screens.

### Fixed

- Reduced scrolling overhead by moving refresh processing off the UI thread, coalescing overlapping loads, preserving the grid during refreshes, caching folder lookups and row data, and simplifying thumbnail loading.
- Album contents, Favorites, the Albums overview, and Settings losing their scroll position after navigating away and returning.
- Rapid double-tap zoom animations drifting images out of frame, and interrupted double-taps choosing the wrong zoom direction.
- Duplicate listings of the same file from MediaStore and SAF, including background refreshes and overlapping selected folders.
- Name/size sorting opening a different file from the tapped thumbnail.
- Album and Favorites viewers switching to the entire library or losing the current item during media updates.
- Hidden-folder media remaining reachable through a stale Favorites viewer list, and outdated storage totals after visibility changes.
- Move/copy destination albums disappearing after deduplication.
- Seeking against an unknown video duration and invalid seek bounds.
- Crop handles using letterboxed container coordinates instead of the fitted, rotated image bounds, and subsequent drags using stale crop state.
- Create Album doing nothing and Recent sorting duplicating item-count sorting.
- Save being available before an image was ready, save feedback being covered by screen content, and album action counts appearing as literal interpolation text.
- File-operation results reporting success when an original could not be removed. Failed deletion retains the recycle-bin entry; failed image writes attempt to remove the incomplete output.
- Thumbnail-strip buttons lacking names, selection state being ambiguous, and settings switches becoming separate unlabeled focus targets.

### Validation

- Kotlin compilation passed after each requested fix; changes were committed individually with P1 fixes before P2.
- Six local regression tests pass for video seek bounds and fitted/rotated crop geometry.
- The viewer's gesture-only control reveal remains intentional.
- Video and keyboard/RTL device checks remain pending. Android lint encountered an internal detector-loading failure.

For the original findings and resolution commits, see [UI_UX_ACCESSIBILITY_AUDIT.md](UI_UX_ACCESSIBILITY_AUDIT.md).
