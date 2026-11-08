# Gallery Terminal Redesign

## Implementation

Updated the existing Compose application; no second project, dependency changes,
network permissions, or media-pipeline replacement.

- Black/red palette with dark, light, and system appearance preferences.
- Compact terminal header, prompt titles, month/year counts, and four bottom destinations.
- Lazy vertical album rows with measured-row fast scrolling.
- Separate retained Photos, Albums, album-content, Search, and Settings scroll states.
- Offline filename, album, localized date, ISO date, and image/video search. Input is
  debounced 220 ms, the index is built only after a nonempty query, and indexing,
  filtering, ordering, and grouping run on Dispatchers.Default.
- Persistent app-local favorites, available from the Photos overflow menu. These
  do not modify Android's system favorite flag or fabricate MediaStore folders.
- Real settings for appearance, photo/album ordering, phone grid density, media
  permissions, memory-cache clearing, and application version.
- Viewer filename/back/overflow controls, persistent favorite action, and existing
  Share, Information, Delete, video, zoom, and immersive controls retained.
- Circular red back-to-top button with 210 ms fade/slide, understated scrollbar
  feedback, and existing pager, zoom, viewer, toolbar, and album transitions.
- Coherent library/presentation publication prevents a completed refresh from
  momentarily presenting the old empty grid and dismissing a restored viewer.
- Album navigation survives tab changes; favorites filtering does not restrict
  the complete device-folder list.

The existing stable media keys, bounded thumbnail concurrency, size-specific
Android thumbnails, Coil memory caches, MediaStore observer/query reuse,
lifecycle collection, cancellation, authorized deletion, and metadata readers remain.
Cache clearing releases app image memory only; it does not remove original media
or Android's provider-managed thumbnail disk cache.

No reference screenshot accompanied this request. The textual specification was
used as the visual target. No unimplemented Edit action was added.

## Files

Modified:

- `.gitignore`: ignore generated Kotlin session files.
- `app/src/main/java/com/nichita/gallery/GalleryApplication.kt`: explicit image-memory cache clearing without eagerly creating the viewer loader.
- `app/src/main/java/com/nichita/gallery/MainActivity.kt`: selected theme and matching system-bar appearance.
- `app/src/main/java/com/nichita/gallery/ui/GalleryScreen.kt`: four destinations, album rows, responsive grids, search, month counts, retained state, and action wiring.
- `app/src/main/java/com/nichita/gallery/ui/GalleryViewModel.kt`: local preferences, favorites, background presentation/search, coherent snapshots, and saved navigation/search state.
- `app/src/main/java/com/nichita/gallery/ui/GalleryScrollControls.kt`: circular action and scrollbar styling.
- `app/src/main/java/com/nichita/gallery/ui/MediaViewer.kt`: filename/overflow header and favorite action.
- `app/src/main/java/com/nichita/gallery/ui/PhotoInformation.kt`: monospace storage paths.
- `app/src/main/java/com/nichita/gallery/ui/theme/Theme.kt`: black/red and light palettes.
- `app/src/main/res/values/strings.xml`: new destination/settings/action text and plural album counts.

Created:

- `app/src/main/java/com/nichita/gallery/ui/TerminalComponents.kt`: shared header and bottom navigation.
- `app/src/main/java/com/nichita/gallery/ui/GallerySettings.kt`: functional settings.
- `app/src/main/java/com/nichita/gallery/ui/LibraryPresentation.kt`: background ordering/filtering and offline search index.
- `app/src/main/res/drawable/ic_search.xml`, `ic_more.xml`, `ic_chevron.xml`, `ic_favorite.xml`, `ic_favorite_outline.xml`.
- `app/src/androidTest/java/com/nichita/gallery/LibraryPresentationTest.kt`: ordering, available favorites, and metadata search.
- `app/src/androidTest/java/com/nichita/gallery/TerminalRedesignTest.kt`: new UI, favorite actions, settings, and retained Photos position.
- `app/src/androidTest/java/com/nichita/gallery/RedesignPerformanceTest.kt`: real-library bidirectional browsing and search snapshot reuse.
- This report.

Removed the obsolete `app/src/main/res/drawable/ic_close.xml`; the viewer now uses the existing back icon.

## Verification

- Complete `:app:build :app:assembleDebugAndroidTest`: successful, including debug
  and optimized release builds.
- Unit tests: 14 passed, zero failures/errors.
- Samsung Galaxy S25 (SM-S9310), Android 16: 27 device cases completed,
  26 passed and one assumption-skipped. The skipped case requires denied
  permissions; the phone already has media access, which was not revoked.
- Passed device coverage includes provider image/video thumbnails, snapshot reuse,
  variable album rows/position restoration, system deletion cancellation then
  confirmation using disposable test-owned media, metadata, real rotation and
  recreation with zoom, video lifecycle, immersive controls, zoom/pager conflicts,
  rapid open/close, deletion down to an empty library, fast month scrolling,
  back-to-top, pending thumbnails, light-theme pinch density changes, search,
  favorite toggling, settings, and the new layouts.
- A separate real-library browsing/search test also passed on 3,157 media items.
- Fixture-only Photos, Albums, and Settings screenshots were visually inspected.
  Gray tiles in these screenshots are synthetic test images, not production
  placeholders. No personal media screenshots were exported.
- Final lint: zero errors, 31 existing warnings (dependency/catalog suggestions,
  existing unused resources, and existing template/configuration warnings).
- Packaged permissions test passed: no INTERNET, ACCESS_NETWORK_STATE, or
  MANAGE_EXTERNAL_STORAGE permission. Manifest, Gradle versions, and SDK settings
  were not changed.

### Rendering Sample

Used Android `dumpsys gfxinfo`, `framestats`, and `meminfo` with the actual
foreground app. The phone was confirmed unlocked and Gallery focused. Eight
250 ms upward swipes followed by eight downward swipes were sent with ADB.

The optimized release APK was signed locally with the existing development
keystore for installation; no signing configuration or private key was changed.

| Build | Frames | Deadline misses | p50 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Debug | 586 | 174 (29.69%) | 11 ms | 24 ms | 31 ms |
| Optimized release | 717 | 13 (1.81%) | 5 ms | 10 ms | 11 ms |

The debug frame-stage sample showed p95 layout traversal 0.08 ms, draw recording
3.08 ms, main-frame work 13.05 ms, and scheduling delay 8.58 ms. These measurements
do not isolate a single cause and include runtime/build-variant effects.

Optimized cold activity launch was 192 ms in one measured run; standalone debug
launches measured 506-518 ms. Release PSS after scrolling was 252,729 KiB, including
graphics allocations; this is a snapshot, not a memory-leak or long-session test.
Debug/release sampling was sequential, not a controlled benchmark, so cache
warmth, compilation, scheduling, and thermal state may differ. The result is not
a sustained 120 Hz guarantee or a before/after redesign comparison.

Generated evidence is in ignored `build/`: `terminal-device-tests.txt`,
`terminal-scroll-sample.txt`, `terminal-standalone-gfxinfo.txt`,
`terminal-release-gfxinfo.txt`, `terminal-frame-stages.txt`, and memory reports.
Lint reports are under `app/build/reports/`.

## Running

The locally development-signed optimized build is installed on the S25. Launch
**Gallery** normally. The separate instrumentation-test APK was removed after
verification. The optimized installable APK is
`D:\projects\gallery\build\Gallery-terminal-profile.apk`; it is development
signed, not a store-distribution release. The debug APK remains at
`app/build/outputs/apk/debug/app-debug.apk`.

In Android Studio, open `D:\projects\gallery`, select the `app` run configuration
and the phone, then Run. Android Studio Run will install the debug variant.

Verification used the existing local Gradle installation and JDK:

```powershell
$env:JAVA_HOME = 'E:\programs\android studio\jbr'
$env:GRADLE_USER_HOME = 'E:\programs\gradle\gradle-9.6.0-bin\gradle-9.6.0'
& 'E:\programs\gradle\gradle-9.6.0-bin\gradle-9.6.0\bin\gradle.bat' --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xms64m -Xmx1024m -XX:+UseSerialGC' -I build/verify-memory.gradle :app:build :app:assembleDebugAndroidTest
```

The ignored verification init script caps test JVM memory at 128 MiB with one
fork; no project-wide Gradle memory settings were changed.

## Limits

- Actual denied/partial permission transitions were not exercised this run.
- Airplane mode was not toggled, and external Share delivery was not tested.
  Sharing still uses the existing Android chooser and explicit URI grant.
- No new tablet, very large-font, or prolonged thermal/battery test was performed.
- Favorites are local to Gallery, not synchronized with other gallery apps.
- The optimized variant received launch/bidirectional-scroll smoke checks; the
  complete instrumentation suite ran against the debug variant.
- No crash was reported by the device suite; the optimized process crash buffer
  was empty after the scrolling sample. This does not prove absence of all bugs.
