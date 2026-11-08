# Gallery: local media

## Gallery 1.2 quick checks

Gallery 1.2 is installed on the connected Galaxy S25. Open Gallery and keep your
existing full/selected media choice. Scroll through uncached photos in both
directions, including video-heavy and HEIC sections. Verify you can continue
scrolling and selecting items before thumbnails finish loading.

Pinch inward to increase columns and outward to decrease them (2-8). Release to
commit the change. The nearby visible photo should remain the scroll anchor.
Close/reopen Gallery and confirm the selected column count is restored.

Drag the thin right-edge scrollbar and check its month/year label against the
visible month. Test months with very different item counts at 2, 3, and 8 columns.
After scrolling down, tap the arrow button above the navigation bar to return to
the beginning. Also check album covers/counts, image swipe/zoom, local video
play/pause/seek, and returning to the same grid position from the viewer.

Repeat in light/dark mode and larger system text. Enable airplane mode manually
and check media access/playback again; this can disconnect wireless ADB. Do not
infer 120 Hz performance from the debug APK alone. Measurements, limitations,
complete changed-file list, and regression coverage are in PERFORMANCE.md.

## Build and install

Open this folder in Android Studio, sync Gradle, select the app configuration,
and run on a Samsung Galaxy phone running Android 10 or later. Enable Developer
options and USB debugging on the phone, connect USB, and accept the debugging
authorization prompt. Select the connected phone as the deployment target.

From PowerShell with a JDK available:

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest lintDebug
.\gradlew.bat installDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.
Use `adb install -r app/build/outputs/apk/debug/app-debug.apk` to install it
manually. Instrumentation tests require a connected device or emulator:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

If the terminal cannot find Java, set JAVA_HOME to Android Studio's bundled
`jbr` directory for that terminal session. Keep the project's existing SDK,
Gradle, AGP, and Kotlin versions. Android Studio may have a separate Gradle
user-home/cache configured; use that same installation/cache when needed.

## Media access

- Android 10-12L: allow read access to shared storage.
- Android 13: allow both photos and videos. Either permission can be denied
  independently; only granted media types are queried.
- Android 14+: allow all photos and videos or choose selected items. Limited
  access is indicated in the page header. Tap Manage media access to reselect
  items. The settings icon always opens the application's system settings,
  including when repeated denial prevents another permission prompt.
- Access is checked again on resume; grants are never saved as permanent truth.
  MediaStore filters the results to the currently allowed items.
- Only locally mounted shared-storage media indexed by MediaStore is included.
  Secure Folder, other Android profiles, app-private files, hidden/unindexed
  files, cloud-only assets, pending writes, and trash are not accessed.
  Media must be downloaded outside Gallery before it can appear locally.
- Capture date is used when supplied by MediaStore, with date-added and
  date-modified fallbacks. Missing original metadata cannot be reconstructed.
- Playback depends on the phone's supported codecs. Unsupported, deleted, or
  newly restricted items display an unavailable state instead of crashing.

## Samsung device checklist

1. With existing media in DCIM, Pictures, Screenshots, Downloads, and an indexed
   third-party folder, grant full access. Check image/video totals and month
   order. Include an SD card if the phone supports one. Same-named folders on
   separate volumes must remain separate albums.
2. Scroll deeply, open an image, pinch and double-tap to zoom, pan while zoomed,
   zoom out, and swipe to adjacent items. Close using the close icon or system
   Back; confirm the original grid scroll position is preserved.
3. Open a video. Check its grid duration, play/pause, seek, and aspect ratio.
   Background the app and confirm audio stops. Swipe away or close the viewer
   and confirm playback stops and resources are released.
4. Open Albums and several real folders. Verify covers, counts, and contents.
   Use Back to return to Albums. Remove or unmount a folder's volume and retry.
5. In system App settings, deny both media permissions. Return to Gallery and
   check the access screen. Re-enable just images, then just videos, and verify
   that the other type is not exposed.
6. On Android 14+, choose selected photos/videos only. Verify only that subset
   appears, change the selection with Manage media access, and revoke access
   from settings while a viewer is open. No inaccessible media should crash it.
7. Take a photo, screenshot, or video in another app and return to Gallery.
   Verify automatic refresh. Delete media externally while Gallery is visible
   (for example in split screen) and verify the observer refreshes the list.
8. Test empty storage/empty selection, a corrupt file, large libraries, portrait
   and landscape rotation, large system font, light/dark modes, and airplane
   mode. No network connection is used for media access or playback.

## Privacy checks

The source manifest declares only the four version-specific read permissions.
There is no INTERNET, ACCESS_NETWORK_STATE, or MANAGE_EXTERNAL_STORAGE permission
in the merged app manifest. A manifest merger rule removes the unneeded
ACCESS_NETWORK_STATE permission contributed by a dependency. Coil has no network
transport dependency and no disk cache configured. Video playback uses only
ContentDataSource, not a URL/network data-source factory. MediaStore is queried
read-only; original files are never changed, copied, uploaded, or moved.

MediaLibraryTest also checks forbidden permissions in the installed merged
manifest. Build dependencies still require a development-time download when
not already cached; this does not add networking to the application.
