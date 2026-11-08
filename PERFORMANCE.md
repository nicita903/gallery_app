# Gallery 1.2: implementation and validation

## Scope

Updated the existing application, package, navigation, permissions, media model,
and Compose UI. Version is now 1.2 (version code 3). No dependencies or build-tool
versions were added or upgraded. The existing system-font, AMOLED/white themes
and local Media3 viewer/player are retained.

## Findings before edits

- Grid requests read original image/video content URIs through Coil's normal
  decoder path. Video tiles could extract frames from original videos. Increasing
  the bitmap cache alone did not remove cold decode work.
- The screen owned an ImageLoader without a shutdown path. Recreation could
  discard its cache while old requests/loaders were still being collected.
- Grid pinch changed column counts and wrote SharedPreferences during pointer
  movement, repeatedly changing grid layout. The old range was 1-15 columns.
- Preferences were first read during composition. The whole monthly grid was
  flattened into a second list on the UI thread when the library changed.
- The observer inserted an initial event followed by a 500 ms debounce. Refresh
  canceled and restarted queries, and unchanged foreground loads rebuilt metadata.
- Sorting/grouping was already off the main thread. Date labels were nevertheless
  formatted repeatedly per item, including when building album sections.
- There was no evidence justifying a Paging migration for the metadata-only list.

These are code findings and likely sources of work, not individual CPU-trace
attributions. ADB gfxinfo flagged slow UI-thread frames far more often than slow
GPU work in the baseline.

## Thumbnail pipeline

Grid models now use ContentResolver.loadThumbnail, available at the existing
API 29 minimum. Android's MediaProvider chooses/generates the appropriate image
or video thumbnail and can reuse its own on-device cache. Coil remains responsible
for request lifecycle, cancellation, memory caching, and AsyncImage presentation.
The grid does not fall back to opening/decoding originals if the provider fails;
it shows an unavailable tile. The viewer still loads the actual local media.

- Display-pixel sizing, rounded up in 64-pixel buckets, capped at 1024 pixels.
- Two concurrent provider operations; waiting requests and active provider work
  are cancellable. No bulk offscreen prefetch or synchronous composable loading.
- Lazy composition requests visible and normal nearby prefetched cells only.
- App-scoped loaders retain caches across Activity recreation and hold only the
  Application context. Thumbnails have a bounded 48 MiB memory cache, separate
  from the viewer's 32 MiB cache. The viewer loader is created only when needed.
- Explicit cache keys include content URI, media revision, and size bucket.
  Revision uses modification time, file size, and generation-modified on API 30+.
  Full-viewer keys also include the media revision.
- Inexact sizing accepts the provider's actual thumbnail aspect ratio/dimensions.
  The model describes a complete size-specific thumbnail, not a sampled original.
  Device tests verify a second request hits MEMORY_CACHE and a new revision does
  not reuse the previous entry.
- No app disk image cache or network transport. MediaProvider's thumbnail cache
  remains entirely local and is managed by Android.

This was selected over original-file Coil decoding because the grid needs small,
reusable images and video posters, not repeated original-image/video decoding.
The platform can still do expensive work for uncached HEIC/video thumbnails;
bounded concurrency keeps that work away from the UI thread.

## Refresh and Compose work

Loading starts on resume without waiting for the observer debounce. Observer
bursts are conflated/debounced by 250 ms. In-flight refreshes are coalesced rather
than continually restarted; a notification arriving during a query schedules a
follow-up check. Cancellation prevents abandoned loads from publishing state.
Provider queries use CancellationSignal and close undelivered/finished cursors.

For full grants on API 30+, a signature of mounted volumes, database versions,
generations, locale, time zone, and permissions reuses unchanged metadata. Partial
access and API 29 re-query on resume; selected-media access cannot safely be
inferred from an unchanged full-library generation. Explicit refresh forces a
query. Observers stop when the app stops. No polling was added.

Sorting, grouping, and month formatting stay off the main thread. Month labels
are formatted once per distinct month; the library uses identity equality so a
StateFlow publication does not deeply compare thousands of records on main.
Media items are immutable. The extra flattened UI list was removed; lazy sections
retain stable media IDs and separate header/media content types. Thumbnail shape
and dimensions remain fixed while requests load. Duration strings are remembered.

## Controls and visual refinements

- Pinch supports 2-8 columns, default 3. A small whole-grid layer preview does not
  recompose/layout individual tiles on pointer movement. The column change is
  committed on release, anchored to a visible media index. Two-finger events are
  consumed before thumbnail click handlers. Preference reads/writes run off main;
  writes are serialized and conflated once per committed change.
- The month scrollbar uses prefix geometry based on actual section counts,
  column-dependent row counts, cell height, full-span header height, and measured
  page-heading height. Binary searches map offsets to section/item indexes. It
  does not assume equal month sizes. Dragging requests immediate index scrolling
  and shows the month at the target viewport position.
- Normal thumb movement is drawn from grid state in the scrollbar child, not
  fed back into the gallery list's composition. Only the narrow right-edge strip
  captures scrollbar gestures. An accessibility progress action is included.
- Scroll-to-top appears beyond roughly six rows. Nearby jumps animate; jumps
  beyond thirty rows go directly to the beginning.
- Spacing is 3 dp, grid corners 6 dp, and full-span month headers have predictable
  height that respects font scale. Album covers remain square, with consistent
  labels/counts. Existing Material navigation handles gesture-system insets.
- Photos and Albums, viewer swipe/pinch/double-tap, local video controls, and
  return-to-grid position remain supported. No shared-element or per-tile
  animations were introduced. Dark/light fixture screenshots were inspected.

## Build and tests

Successful tasks: :app:assembleDebug, :app:testDebugUnitTest, :app:lintDebug,
and :app:assembleDebugAndroidTest. Used the existing Android Studio Gradle 9.6
installation/cache and bundled JDK, without changing wrapper/AGP/Kotlin/SDK values.
No Kotlin compiler warnings were emitted. Lint has 30 warnings and no errors,
primarily existing dependency/version/resource suggestions plus an obsolete
Lifecycle custom lint-check warning. The new plural-string and preference-edit
warnings were resolved; dependencies were not churned just to silence lint.

- 9 unit tests passed. Coverage includes unequal month sizes, offset/index round
  trips across every column count, a section with 10,003 items, pinch limits,
  thumbnail buckets, date fallbacks, permissions, and duration formatting.
- Device: connected SM-S9310, Android 16. Final instrumentation run had 12 cases:
  11 passed and one assumption-skip. Denied-access testing was skipped because
  the installed app currently has permission; grants were not revoked for testing.
- Passed real-image AND real-video provider thumbnail requests, memory hits,
  revision invalidation, unchanged metadata reuse, forced refresh, pinch with no
  accidental viewer opening, month drag label, fast scroll, top button, album
  navigation, viewer return position, and forbidden-permission checks.
- A 600-item synthetic UI fixture exercises controls in both themes. A separate
  test stalls thumbnail fetches for 30 seconds and verifies fast-scroll/top controls
  still respond. Fixture loaders are disposed/canceled; the test does not wait
  thirty seconds or alter the user's original media.
- Saved only synthetic UI screenshots for visual QA, not personal photos.

Instrumentation was run through adb am instrument after installing the app/test
APKs with -r, preserving existing permissions and settings. Unlike a managed test
runner cleanup, this leaves the upgraded app installed. Raw results are in
build/gallery-1.2-device-tests.txt.

## Rendering observations, not an FPS guarantee

Same connected phone and saved seven-column setting; eight 350 ms upward swipes
then eight downward swipes, using x=500 and y=550/1850. Counters were reset before
the swipe sequence. These are debug-build observations, not release benchmarks.

| Metric | Baseline | Final 1.2 sample |
| --- | --- | --- |
| Frames rendered | 622 | 815 |
| Janky frames, current gfxinfo metric | 187 (30.06%) | 46 (5.64%) |
| Janky frames, legacy metric | 125 (20.10%) | 170 (20.86%) |
| 50th-percentile frame | 10 ms | 7 ms |
| 95th-percentile frame | 40 ms | 16 ms |
| 99th-percentile frame | 57 ms | 23 ms |
| Slow UI-thread frames | 184 | 46 |

An earlier intermediate 1.2 sample rendered only 257 frames, with a 15 ms p95
and 5.84% current-metric jank. This variation is one reason not to overstate the
numbers. Final raw statistics: build/gallery-1.2-gfxinfo-final.txt.

Frame counts, adaptive refresh, system cache warmth, thermal conditions, and fling
trajectories were not controlled. The baseline was measured after unlocking the
phone; the final measurement waited two seconds after process launch. Legacy
jank percentage did NOT improve. Observed cold Activity launch time varied from
400 ms baseline to 506 ms in an intermediate build and 361 ms final; these single
samples do not establish a startup speedup. No guaranteed 60/120 FPS, complete
elimination of jank, or causal attribution to a single fix is claimed.

For reliable performance certification, repeat a release/profileable Macrobenchmark
with fixed refresh, identical media/window/columns, cold and warm provider caches,
and Perfetto FrameTimeline/CPU/GC traces. Trace sections Gallery.loadThumbnail,
Gallery.queryMedia, and Gallery.groupMedia are available for that analysis.

## Remaining limits and privacy

Actual multi-minute 120 Hz behavior, extreme library sizes, every image codec,
corrupt files, SD-card hot removal, full video seek/playback regressions, and
airplane-mode operation were not exhaustively exercised in this run. Android's
provider/codec support determines which JPEG/PNG/HEIC/WebP/video thumbnails can
be decoded. Thumbnail failures do not trigger a costly full-resolution fallback.
The metadata list remains in memory; no Paging library was added without evidence
that it would improve this workload.

The merged app manifest has no INTERNET, ACCESS_NETWORK_STATE, or
MANAGE_EXTERNAL_STORAGE permission. Backup is disabled so the saved UI preference
is not opted into Android cloud backup. No HTTP, cloud, analytics, ads, telemetry,
or runtime external services were added. Original files remain untouched.

## Created or modified source files

- app/build.gradle.kts
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/nichita/gallery/GalleryApplication.kt (new)
- app/src/main/java/com/nichita/gallery/data/ThumbnailFetcher.kt (new)
- app/src/main/java/com/nichita/gallery/data/MediaItem.kt
- app/src/main/java/com/nichita/gallery/data/MediaRepository.kt
- app/src/main/java/com/nichita/gallery/ui/GalleryScreen.kt
- app/src/main/java/com/nichita/gallery/ui/GalleryViewModel.kt
- app/src/main/java/com/nichita/gallery/ui/MediaThumbnail.kt (new)
- app/src/main/java/com/nichita/gallery/ui/GridPinch.kt (new)
- app/src/main/java/com/nichita/gallery/ui/GalleryScrollMap.kt (new)
- app/src/main/java/com/nichita/gallery/ui/GalleryScrollControls.kt (new)
- app/src/main/java/com/nichita/gallery/ui/MediaViewer.kt
- app/src/main/res/drawable/ic_up.xml (new)
- app/src/main/res/values/strings.xml
- app/src/test/java/com/nichita/gallery/GalleryScrollMapTest.kt (new)
- app/src/androidTest/java/com/nichita/gallery/GalleryControlsTest.kt (new)
- app/src/androidTest/java/com/nichita/gallery/DeviceThumbnailTest.kt (new)
- TESTING.md
- PERFORMANCE.md (new)

## API references used

- [ContentResolver.loadThumbnail](https://developer.android.com/reference/android/content/ContentResolver#loadThumbnail(android.net.Uri,%20android.util.Size,%20android.os.CancellationSignal))
- [Coil's extensible pipeline](https://coil-kt.github.io/coil/image_pipeline/)
- [Compose performance practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Compose touch injection](https://developer.android.com/reference/kotlin/androidx/compose/ui/test/TouchInjectionScope)
