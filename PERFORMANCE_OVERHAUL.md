# Gallery Performance and Stability Audit

## Scope

Updated the existing application without changing its palette, typography,
navigation architecture, custom video controls/timeline, or animations. Existing
uncommitted changes in LocalVideoPlayer.kt and MediaViewer.kt were preserved.
No dependencies, permissions, services, cache capacities, or Gradle settings changed.

## Findings and Fixes

1. **Repeated video taps captured stale visibility.** The video pointer handler was
   keyed only by media ID but captured its original controlsVisible parameter.
   It now reads the latest value. The focused video test toggles twice and checks
   both the resulting state and unchanged player identity.
2. **Swipe-down launched a coroutine for every movement.** Dragging previously
   queued Animatable.snapTo jobs, and its raw offset was read in the viewer's
   composition. Movement now writes float state synchronously, read by draw/layer
   callbacks; pager locking observes a derived boolean. Only settling/dismissal
   starts an animation job. Horizontal, multi-touch, and toolbar gestures are
   excluded; cancellation preserves the prior immersive state.
3. **Pager movement tore down videos immediately.** The player composable was
   removed whenever isScrollInProgress became true, including a canceled swipe.
   The active page now retains its player/surface and pauses during the gesture.
   Player callbacks are tagged by media ID so outgoing callbacks cannot clear a
   different video's timeline binding. Paused videos stay paused; ended playback
   can restart from zero.
4. **Viewer previews used a different thumbnail bucket.** A fixed 512 px request
   duplicated grid work. The selected grid bucket is now saved and reused in the
   viewer and adjacent previews. Cached previews use at most 16 direct key lookups
   instead of copying/scanning the cache's complete key set. URI, revision, and
   bucket identity remain intact; a test rejects a different revision.
5. **Viewer sizing/cache policy was implicit.** Full-photo requests now explicitly
   use FIT/INEXACT sizing, retaining the existing 2560 px bound. Zoom and toolbar
   changes still do not alter the request. No originals are decoded for grid cells.
6. **Repeated allocations during scrolling.** Album scrollbar measurement used
   groupBy and temporary row lists for every layout update. A single ordered pass
   now computes the same row maxima. Photos no longer format unused video duration
   strings. Default presentation returns the source library rather than rebuilding
   an identical wrapper/album list.
7. **Duplicated date processing.** Library grouping resolved timestamps separately
   for Photos and Albums. It now resolves each item's month once and shares the
   labels while building both indexes off the main thread. This is a structural
   reduction in duplicated work, not a demonstrated cold-start latency improvement.
8. **Independent settings publication could invalidate initial tile sizes.** One
   IO read now loads settings and grid density atomically. Media discovery runs
   concurrently, and its first publication waits for the real settings read, not
   an artificial delay. Preference failures fall back safely. Transient refresh
   failures retain the last authorized library state instead of dropping the
   viewer's items; access changes still clear inaccessible state.
9. **Unnecessary playback polling.** Timeline polling remains local to its row and
   now runs only while STARTED, with slower polling when paused. Player events
   update seeks/duration promptly. Time labels are formatted only when their
   displayed second changes. Preparation starts in an effect after composition,
   rather than inside player construction.

## Rendering and Memory

The active SurfaceView is no longer destroyed just because a pager drag begins.
The existing Media3 1.5.1 Compose surface synchronization workaround is enabled;
its implementation specifically applies on Android 14, not the S25's Android 16.
No verified GPU-driver defect was found, and no general software rendering,
TextureView replacement, or forced bitmap configuration was introduced.

Existing 48 MiB thumbnail / 32 MiB viewer caches, two-provider-request concurrency,
cancellation signals, size/revision keys, stable lazy item IDs, lazy loading,
MediaStore generation reuse, and player release remain in place. Coil already
prepares decoded images for drawing and handles memory-pressure callbacks, so
those mechanisms were not duplicated. Android's provider thumbnail disk cache
remains the disk backing; an extra private disk cache was not added without evidence.

## Verification

- Final offline build succeeded: `:app:assembleDebug :app:assembleDebugAndroidTest`.
  The extra task compiled only the focused device checks in the same invocation.
- The first attempt stopped before compilation because the default Gradle cache
  lacked the existing Foojay plugin. Retrying with the already-populated alternate
  cache avoided downloads. A missing test-only click import was then corrected;
  production compilation succeeded before that correction.
- One focused S25 / Android 16 run: **14 passed, zero failures or skips**.
- Covered: image/video provider cache reuse and revision invalidation, unchanged
  MediaStore snapshot reuse, library sorting/grouping/favorites/search helpers,
  downward versus horizontal/toolbar/multi-touch gestures, photo zoom/pager/info
  interactions, deletion-neighbor selection through a synthetic callback, rapid
  viewer reopening/restoration, paused video preparation/seeking/detachment, and
  repeated video control visibility toggles without recreating the player.
- No clean build, dependency downloads, unrelated suite, repeated successful
  build, release build, or full lint/unit-test suite was run.
- Installed the new debug APK on the S25. Removed the test-only APK afterward.
- Merged debug manifest contains no INTERNET, ACCESS_NETWORK_STATE, or
  MANAGE_EXTERNAL_STORAGE permission. The sampled app crash buffer was empty.

Build used the existing alternate cache:

```powershell
$env:GRADLE_USER_HOME = 'E:\programs\gradle\gradle-9.6.0-bin\gradle-9.6.0'
$env:JAVA_HOME = 'E:\programs\android studio\jbr'
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --max-workers=1 --console=plain
```

APK: `D:\projects\gallery\app\build\outputs\apk\debug\app-debug.apk`.

## Diagnostic Measurements, Not Release Benchmarks

Used Android atrace (app markers, gfx/view/scheduling), gfxinfo, and meminfo around
launch, four downward swipes, a two-second pause, and four upward swipes. The
installed baseline and modified APK were debuggable. These are single diagnostic
runs with different JIT/cache/thermal/input conditions, not a controlled release-
like/profileable comparison. They cannot establish sustained refresh-rate performance.

| Observation | Before | After |
| --- | ---: | ---: |
| Cold activity launch | 471 ms | 470 ms |
| Frames / deadline misses | 479 / 36 (7.52%) | 490 / 29 (5.92%) |
| Frame p95 / p99 | 20 / 42 ms | 15 / 48 ms |
| Provider thumbnail calls | 127 | 120 |
| Provider call median / p95 | 4.34 / 8.15 ms | 3.46 / 5.74 ms |
| Background grouping wall time | 25.00 ms | 60.78 ms |
| First provider thumbnail ready after query start | 205.65 ms | 280.62 ms |

**Startup latency improvement is not established:** launch time was essentially
unchanged, and grouping/first-provider-result wall times were worse in the cold
post-install sample. Provider-result readiness is not pixel-verified first-visible-
thumbnail time. Some frame misses remained, and p99 was also worse. Do not treat
the lower miss percentage as proof the reported startup/scrolling problems are gone.

Both runs show provider thumbnail work off the main thread, and the provider calls
in this sampled route did not individually take seconds. This does not exclude
slow media or cache misses elsewhere in the library. Post-scroll PSS was 220,959 KiB;
this is one snapshot, not a leak or memory-improvement result. No slow bitmap upload
was reported by gfxinfo in these samples; that does not rule out intermittent artifacts.

Evidence is retained under ignored `build/`: `gallery-before.trace`,
`gallery-after.trace`, `performance-before-frames.txt`, `performance-after-frames.txt`,
`performance-after-memory.txt`, and `performance-focused-tests.txt`.
Temporary trace files were removed from the phone. No personal media was deleted
or uploaded, and no personal media screenshots were exported.

## Remaining Limits

- The reported 3-5-second startup lag and intermittent texture artifacts are not
  proven eliminated; release-like first-thumbnail, CPU, GPU, and long-session
  measurements remain necessary. No 120 FPS claim is made.
- Actual system deletion confirmation, permission changes, physical rotation,
  full timeline touch interaction, and all drawer/album paths were not retested
  in this focused run. Existing callbacks/navigation were preserved.
- Surface teardown risk and Android 14 interoperability were addressed; no new
  Android 16 GPU-specific workaround was justified by the available evidence.
- No new Baseline Profile dependency or benchmark infrastructure was introduced.

## Files Changed

Paths below are relative to `D:\projects\gallery`.

- `app/src/main/java/com/nichita/gallery/data/MediaItem.kt`
- `app/src/main/java/com/nichita/gallery/data/ThumbnailFetcher.kt`
- `app/src/main/java/com/nichita/gallery/ui/GalleryScreen.kt`
- `app/src/main/java/com/nichita/gallery/ui/GalleryScrollControls.kt`
- `app/src/main/java/com/nichita/gallery/ui/GalleryViewModel.kt`
- `app/src/main/java/com/nichita/gallery/ui/LibraryPresentation.kt`
- `app/src/main/java/com/nichita/gallery/ui/LocalVideoPlayer.kt` (preserved existing edits)
- `app/src/main/java/com/nichita/gallery/ui/MediaThumbnail.kt`
- `app/src/main/java/com/nichita/gallery/ui/MediaViewer.kt` (preserved existing edits)
- `app/src/main/java/com/nichita/gallery/ui/ZoomablePhoto.kt`
- `app/src/main/java/com/nichita/gallery/ui/ViewerDismissGesture.kt` (new)
- `app/src/androidTest/java/com/nichita/gallery/DeviceThumbnailTest.kt`
- `app/src/androidTest/java/com/nichita/gallery/Gallery13VideoTest.kt`
- `app/src/androidTest/java/com/nichita/gallery/ViewerDismissTest.kt` (new)
- `PERFORMANCE_OVERHAUL.md` (new)

## References Checked

- [Compose performance guidance](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Coil shared loaders and caches](https://coil-kt.github.io/coil/image_loaders/)
- [Media3 surface guidance](https://developer.android.com/media/media3/ui/surface)
- Local Coil 3.3.0 and Media3 1.5.1 source archives were inspected to verify the
  installed libraries' cache validation, prepare-to-draw behavior, and surface API.
