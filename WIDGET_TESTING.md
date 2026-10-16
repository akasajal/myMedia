# Photo widget verification

## Automated checks

`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --offline --no-configuration-cache`

Kotlin/resource compilation and all 12 unit tests passed during implementation. Six widget tests cover per-widget app-lock permission, descendant folder matching, hidden ancestors, path boundaries, overlapping selections, bitmap aspect ratios, and the 720 KB per-image memory ceiling. The remaining six are existing seek/crop regression tests.

No APK was assembled or installed as part of these checks. The following launcher checks remain unverified.

## Device checklist

- Add a widget, cancel configuration, and confirm the launcher removes the unconfigured instance.
- Configure a single image; verify preview, fit/fill framing, image opening, zoom, and back navigation.
- Edit an existing widget without changing its interval: choose a different image, switch between image/folder/multiple-folder sources, change fit/fill and shuffle, and save. Verify each save immediately replaces the displayed collection and framing. Repeat saves to exercise both root layouts. Change the interval separately and verify the new timing applies; cancel an edit and verify its changes do not apply.
- Configure one folder, then multiple overlapping folders; verify images occur once, descendants are included, and videos are excluded.
- Verify 15-second, 30-second, 1-minute, and 5-minute intervals. Ensure the launcher does not override them with its own automatic-advance interval.
- Turn the screen off midway through an interval, wait longer than several intervals, then return. Verify the slideshow did not advance while hidden and starts a fresh interval. Native flippers do not preserve the remainder of an interrupted interval.
- Switch launcher pages and open another app; check visibility-dependent pausing and return behavior on the device's launcher.
- Add two widgets with different sources and intervals; verify long-press Edit/settings and photo-opening actions affect the correct widget. Check that no next/menu buttons overlay photos or placeholders. Confirm long-press opens the launcher's widget menu and its Edit/settings action opens configuration; wording and support vary by launcher.
- Resize horizontally and vertically; verify cropping, fit framing, controls, and image clarity. Also check landscape and launcher recreation.
- Add widgets with app lock both enabled and disabled. Verify each setup asks Allow/Deny before saving; dismissing the dialog must not save a new widget. Rotate the device while the warning is open and confirm the pending choice survives.
- Allow one widget and deny another, then enable PIN lock with photos already displayed. Verify the allowed widget continues displaying/rotating, while the denied widget becomes a placeholder. Revoke and grant permission in widget settings and verify immediate updates and persistence after restart. Existing pre-consent widget configurations must default to Deny.
- Test PIN and biometric access when opening a photo or configuration from an allowed widget. Home-screen display permission must not bypass authentication or expose hidden-folder media.
- Hide/remove a selected root, hide a child folder, recycle/restore an image, and revoke folder access. Verify old photos disappear and configuration remains reachable.
- Delete a file outside the app, refresh the gallery, and verify missing/unreadable files show recovery feedback without crashing. For SAF-only sources, refresh the app's scan cache before expecting discoveries in the widget.
- Test reboot, process termination, widget deletion/re-addition, reconfiguration, and launcher backup/restore ID remapping.
- Test TalkBack, Switch Access, keyboard focus, large text, and RTL in configuration, photo view, and widget controls.

## Timing contract

The slideshow is hosted by the launcher using `AdapterViewFlipper` with automatic flipping enabled. The widget does not ask the launcher to auto-advance the collection, so the chosen layout interval controls normal flipping. Screen-off/visibility behavior depends on the platform and launcher. Ordinary widget updates refresh the collection at Android's minimum 30-minute period and after relevant in-app changes; they are separate from slideshow advancement.

An interrupted partial countdown restarts when the widget becomes visible. Exact accumulation of screen-on seconds across launcher/process restarts is not implemented.
