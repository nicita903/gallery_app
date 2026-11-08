# Gallery 1.3

## Scope and safety

Upgraded the existing Gallery project, package com.nichita.gallery, to version
1.3 (version code 4). Existing SDK/build-tool versions, themes, permissions,
navigation, local thumbnails, and sharing were retained. Existing uncommitted
user edits were integrated, not reverted. No new runtime service or networking
dependency was introduced. The existing AndroidX EXIF catalog dependency is used;
the duplicate direct EXIF dependency was removed.

## Bugs corrected

- Removed the unsafe Function0-to-Function1 cast in detectTapGestures. Tap and
  double-tap now have correctly typed callbacks and use current callback state.
- Deleting no longer unconditionally closes the viewer. A missing selected item
  resolves to the next position, or the preceding item at the end. An empty
  library/album returns to the gallery safely. Pager keys and item lookups guard
  against transient out-of-range indexes during changes.
- The Android 30+ path uses MediaStore.createDeleteRequest and its real Activity
  result. A canceled system request does not remove anything from app state.
  Android 29 handles RecoverableSecurityException, asks for the system grant,
  and retries the authorized delete. The app confirmation precedes every request.
- Saved deletion stage, URI, and ID survive Activity recreation. Duplicate
  requests are blocked. Errors are visible and trigger a refresh, not a crash.
- Confirmed IDs are excluded from late-arriving snapshots. Photos, sections,
  album counts, and covers are rebuilt off main before a forced refresh.
- Missing albums no longer fall back to displaying unrelated library items.
  Selected media position, tab, grid states, and viewer transforms have bounded
  saved state; no full media list or bitmap is written to saved instance state.
- Zoom state was moved above the Dialog/pager's saveable boundary. Restoration
  and actual portrait/landscape rotation are covered by device tests.
- System-bar cleanup no longer runs on every toolbar toggle. Bar visibility and
  appearance are managed separately, and image bounds do not use disappearing
  bar insets. Native window animations are disabled only for this Dialog so they
  do not compete with the Compose transition.
- Sharing catches unavailable/unauthorized handlers and grants URI read access.
  ClipData.newRawUri avoids a MIME query on the UI thread.
- Video playback releases its player, pauses on backgrounding, saves position
  during recreation, and only keeps the screen awake while actually playing.
  Closing retains the last rendered frame while pausing through the exit fade;
  video poster pages never request the viewer's full-size frame decode.
- Album scrollbar progress no longer divides by a changing visible-item count.
  It uses measured row geometry, including variable-height label rows.
- Month geometry accounts for the extra full-span album heading. Its label uses
  the visible media's preformatted month rather than formatting dates per frame.
- Back to top has separate show/hide thresholds to avoid threshold flicker.

## Metadata

MediaMetadata uses androidx.exifinterface.media.ExifInterface on Dispatchers.IO.
It queries size, MIME format, and relative folder through ContentResolver, reads
EXIF through a read-only file descriptor, and never decodes image pixels.

Only available values are shown: filename, resolution, date/time, file size,
format, folder, manufacturer, camera model, ISO, aperture, shutter speed, and
focal length. Provider failures do not prevent independent EXIF loading, and
unsupported/missing EXIF does not suppress basic fields. Small files use B/KB
instead of misleading 0.00 MB. Non-reciprocal exposures such as 0.8 seconds are
not incorrectly rounded to 1/1 second.

The current item's fields are cached by ID, revision, locale, and time zone.
Closing the sheet/viewer or changing photos cancels its producer. The synchronous
AndroidX EXIF parser cannot itself be interrupted mid-parse; work stays on IO,
descriptors close, and cancellation prevents publishing abandoned results.

The Material bottom sheet opts into its experimental API locally. It has rounded
upper corners, a scrollable size-constrained body, separate camera fields, and
does not reserve a large empty sheet for a short field list.

## Animations and gestures

- Open: 280 ms backdrop fade and 0.95-to-1 graphics-layer expansion.
- Close: 250 ms reverse scale/fade, with dismissal after completion.
- This is the requested lightweight fallback, NOT a thumbnail-geometry shared
  element: the current architecture uses a separate Dialog window. Gallery scroll
  position remains intact, including when closing after swiping to another item.
- Toolbars: 200 ms show / 160 ms hide with opacity and small vertical movement.
  They overlay the image rather than changing its layout. Controls begin appearing
  after the viewer transition starts. Rapid targets replace earlier animations.
- Double tap: 220 ms zoom/reset, maintaining the focal point where bounds allow.
  Pinch uses centroid-aware offset math and clamps against the fitted image bounds.
  Panning/zoom consume transforming gestures; one-finger unzoomed drags reach the
  native HorizontalPager. Inactive pages reset transforms.
- The pager retains native finger tracking/fling behavior. List synchronization
  waits for an active swipe to end; selection follows currentPage. Inactive pages
  request provider thumbnails instead of full-resolution adjacent photos.
- Cached thumbnail bitmaps are reused underneath the full image during loading.
  The full image has a short readiness fade, also making replacement after
  confirmed deletion subtle without an unnecessary empty black frame.
- Album navigation uses a 240 ms layer-opacity transition only on navigation,
  not on initial startup. No per-tile or shared-element layout animations.
- Both Back to top controls are wide arrow-and-label pills with 240 ms slide/fade.
  Far jumps go near the start immediately and animate only the final short part.
- Scrollbars fade when idle and emphasize dragging; month indicators disappear
  after release. Common application curves/timings are in GalleryMotion. Native
  Material/pager components retain their supported motion defaults.
- Compose animation clocks honor the platform's animation-duration scale. No
  custom infinite animations or independent manual animation timers were added.

## Scrolling and battery work

The existing 1.2 thumbnail path remains in place: loadThumbnail on IO, cancellable
requests, two concurrent provider operations, 64-pixel size buckets, revision-
aware keys, and separate bounded 48 MiB thumbnail / 32 MiB viewer caches. No
original-image grid decoding or batch offscreen image loading was introduced.

Stable lazy item keys/content types, lifecycle-aware collection, coalesced
MediaStore refreshes, generation-based unchanged-library reuse, and off-main
sorting/grouping are preserved. Album switches do not issue a media query.
Animation values for the photo and backdrop are read in graphics/draw layers,
not used to recompose the large gallery for every animation frame.

Scrollbar width is derived from layout state without observing every scroll pixel
in its parent. Album row heights are recorded only when measurements change;
prefix geometry is reused, and a drag freezes its mapping to avoid feedback loops.
Unvisited variable-height rows necessarily use a measured-row average until seen;
this is not an exact premeasurement of every uncomposed album name. Known rows
and boundaries are taken from real layout information. No Paging/extra framework
was added for this metadata-only workload.

## Validation

The complete :app:build and :app:assembleDebugAndroidTest tasks were run with the
installed Gradle 9.6/JDK/SDK. Both debug and R8-processed release APKs are produced;
release remains unsigned as in the existing configuration.

The Windows host ran out of paging memory during a parallel verification attempt
(a test JVM could not allocate its default heap). One confirmed-idle Gradle daemon
was stopped; Android Studio and other Java processes were not closed. Verification
was retried with one worker, a single-use 1 GiB SerialGC daemon, and a temporary
build/verify-memory.gradle init script setting a 128 MiB test heap. No normal
project JVM settings were changed. Generated VM error logs were retained under
build rather than left in app source directories.

Unit coverage includes focal-point invariance, fitted-image pan bounds, variable
album row targets, album-heading offsets, exposure formatting, and the existing
date/permission/thumbnail/month/pinch tests.

Device coverage on the connected SM-S9310 running Android 16 includes:

- Real local image/video thumbnails, memory reuse, invalidation, metadata reuse.
- A foreign-owned disposable test JPEG: real system cancellation preserves it;
  real confirmation removes it through Gallery's actual viewer/dialog/result/
  ViewModel flow and updates album contents. No original user media is deleted
  by tests.
- A separate Android-test-only helper Activity runs under the test APK UID to
  create this fixture. Its pure SDK Java code avoids relying on the target APK's
  Kotlin runtime. Cleanup accepts only its privately recorded, self-created URI.
  This helper and its manifest are NOT present in the Gallery APK.
- EXIF maker/model/exposure, MIME data, cache reuse, missing-file safety.
- Neighbor selection on confirmed deletion, cancel, and deletion through to empty.
- Single/double tap, zoom reset, swiping disabled while zoomed, information while
  zoomed, controls toggling without a transform reset, and normal paging afterward.
- Rapid open/close, saved-state restoration, real Activity recreation and physical
  landscape/portrait transitions while preserving the selected photo and zoom.
- Month dragging/label, top controls while thumbnails are deliberately stalled,
  variable-height album scrolling, returning to the same album-list position.
- Forbidden-permission checks in the installed merged application manifest.
- A real accessible local video prepares, seeks while paused (no autoplay/audio),
  and detaches with its keep-screen-on flag cleared.

Synthetic screenshots under build/gallery13-immersive.png and
build/gallery13-information.png were inspected. They contain test artwork only,
not original photos. Immersive capture shows no status/nav bars and no toolbar.
The information capture verifies field spacing and overlay presentation.

Final results: full debug/release build successful, all 14 unit tests passed,
and 20 device cases completed with 19 passes and one assumption skip (the existing
denied-permission test, because access is already granted). Android's actual
confirmation/cancellation and the app's integration flow both passed. Lint has no
errors and 31 remaining existing dependency/template/custom-lint warnings. The
new resource-lookup and window-sizing warnings were resolved. The test-only APK
was uninstalled afterward; Gallery 1.3 remains installed and open.

## Rendering observation

One final debug-build gfxinfo sample on the connected phone used its saved
seven-column grid, a two-second wait after launch, then eight 350 ms upward
swipes and eight downward swipes (x=500, y=1850/550). Counters were reset before
the swipes. Results: 865 rendered frames, 50 current-metric janky frames (5.78%),
160 legacy-metric janky frames (18.50%), p50 7 ms, p95 15 ms, p99 22 ms. The
single Activity cold-launch observation was 497 ms. Raw statistics are in
build/gallery-1.3-gfxinfo.txt and test results in build/gallery-1.3-device-tests.txt.

This is not a controlled comparison with 1.2: cache warmth, adaptive refresh,
fling paths, thermal conditions, and background activity were not fixed. It
does not prove a new FPS gain or certify sustained 120 Hz. The existing trace
sections remain available for a release/profileable Perfetto/Macrobenchmark run.

## Remaining limitations

- No claim that every possible bug is eliminated or that every frame meets 120 Hz.
  Debug gfxinfo samples are observations, not controlled release benchmarks.
- Android 29 deletion recovery is implemented/compiled but not exercised on an
  Android 10 device in this run; system-confirmation tests target Android 16.
- The denied-permission test skips when the phone has access already granted.
  The test suite does not revoke the user's existing permissions to force it.
- Broad partial-access/reselection matrices, SD-card removal, every codec, long
  video playback/seek sessions, extended thermal/battery tests, and manually
  disabling system animations are not exhaustively tested here.
- Album rows that have never been composed are estimated from measured rows.
  Exact global geometry for arbitrary unseen text heights would require either
  premeasurement or fixed album row heights; neither was introduced as a hidden
  main-thread startup cost.

The app remains offline: no INTERNET, network-state, or all-files permission,
no uploads, cloud, telemetry, ads, analytics, or runtime remote dependencies.
Sharing is an explicit user-initiated handoff to the Android chooser, not an app
upload or synchronization service. Backup stays disabled.

## Files changed or created for 1.3

- app/build.gradle.kts
- app/src/main/java/com/nichita/gallery/ui/GalleryScreen.kt
- app/src/main/java/com/nichita/gallery/ui/GalleryViewModel.kt
- app/src/main/java/com/nichita/gallery/ui/GalleryScrollControls.kt
- app/src/main/java/com/nichita/gallery/ui/GalleryScrollMap.kt
- app/src/main/java/com/nichita/gallery/ui/MediaViewer.kt
- app/src/main/java/com/nichita/gallery/ui/LocalVideoPlayer.kt
- app/src/main/java/com/nichita/gallery/ui/ZoomablePhoto.kt (new)
- app/src/main/java/com/nichita/gallery/ui/GalleryMotion.kt (new)
- app/src/main/java/com/nichita/gallery/ui/AlbumRowGeometry.kt (new)
- app/src/main/java/com/nichita/gallery/ui/MediaDeleteAction.kt (new)
- app/src/main/java/com/nichita/gallery/ui/PhotoInformation.kt (new)
- app/src/main/java/com/nichita/gallery/data/MediaDeletion.kt (new)
- app/src/main/java/com/nichita/gallery/data/MediaMetadata.kt (new)
- app/src/main/res/values/strings.xml
- app/src/main/res/drawable/ic_delete.xml (new)
- app/src/main/res/drawable/ic_info.xml (new)
- app/src/main/res/drawable/ic_share.xml (new)
- app/src/test/java/com/nichita/gallery/Gallery13MathTest.kt (new)
- app/src/androidTest/AndroidManifest.xml (new, test only)
- app/src/androidTest/java/com/nichita/gallery/ForeignMediaFixtureActivity.java (new)
- app/src/androidTest/java/com/nichita/gallery/Gallery13MediaTest.kt (new)
- app/src/androidTest/java/com/nichita/gallery/Gallery13ViewerTest.kt (new)
- app/src/androidTest/java/com/nichita/gallery/Gallery13AlbumsTest.kt (new)
- app/src/androidTest/java/com/nichita/gallery/Gallery13VideoTest.kt (new)
- GALLERY_1_3.md (new)
- TESTING.md

The already-modified EXIF version catalog was retained as supplied, not replaced.

## Platform references

- [MediaStore removal and user approval](https://developer.android.com/training/data-storage/shared/media#remove-item)
- [AndroidX ExifInterface](https://developer.android.com/reference/androidx/exifinterface/media/ExifInterface)
- [Compose multitouch](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/multi-touch)
