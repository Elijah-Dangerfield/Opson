# Progress photos plan

New in design sync 2, behind the release flag `release.progressPhotos`
(default off). Opened from the Weight page. Read `trends.md` (Weight) and
`shared.md` first.

## Source
- Design: `progress-photos-screen__progress-photos` (8.4) and
  `progress-photos-screen__progress-photos-compare` (8.5) in
  `/Users/elijahdangerfield/Workspace/Opson/design-sync/design/`, and their
  manifest notes ("Section behavior (PROGRESS PHOTOS)").
- Weight's row: `weight-screen__weight` (8.1).

## The rules this is built around

Privacy is the feature. Every line here is load-bearing.

| Rule | How |
|---|---|
| Only on this phone, in app-private storage | Android `Context.noBackupFilesDir/progress_photos`; iOS `Application Support/progress_photos`. Never the photo gallery, never synced, never uploaded. No network code touches them. |
| Out of backups | Android: `noBackupFilesDir` is excluded from Auto Backup and device transfer by the platform, so no backup rules are needed. iOS: the folder carries `NSURLIsExcludedFromBackupKey`, and every file is written with `NSFileProtectionComplete`. |
| Locked each time the screen opens | The grid asks for the phone's lock every time it is pushed: BiometricPrompt with device-credential fallback on Android (`BIOMETRIC_WEAK or DEVICE_CREDENTIAL` on 30+, `setDeviceCredentialAllowed` on 28 and 29, `KeyguardManager.createConfirmDeviceCredentialIntent` below 28); `LAContext.evaluatePolicy(.deviceOwnerAuthentication)` on iOS (Face ID or Touch ID, passcode fallback; `NSFaceIDUsageDescription`). Leaving the app locks again; coming back asks again. |
| No phone lock, no photos | If the phone has no passcode, PIN, pattern or biometric, the page says so and shows nothing. |
| Deleted by "Delete everything" | `ProgressPhotosClearer` is a `UserScopedClearer`: deleting the account, signing out or switching accounts deletes every photo and the index. |
| No numbers on photos | No weight, date overlay inside the image, or any figure. The grid's date tag is a label beside the image, not burned in. |
| Nothing auto-generated | No before-and-after, collage or share card. Compare is on demand only; the page opens to the grid. |
| Saving a comparison | A ⋮ action on Compare. It saves the pair (two photo ids) in app storage; saved pairs list under the grid. Nothing is rendered or exported. |
| Reminder | Optional, off by default, at most every 2 weeks. Not built in this pass: `ReminderKey` has no slot for it. `TODO(design-sync-2)`; nothing about a reminder is shown until it is. |

## Modules
- `:libraries:progressphotos`: `ProgressPhoto`, `Pose`, `SavedComparison`,
  `ProgressPhotoRepository`, `ProgressPhotoFileStore`, `DeviceLock`,
  `ProgressPhotosEnabled` (the flag).
- `:libraries:progressphotos:impl`: `ProgressPhotoRepositoryImpl` (common,
  a JSON index plus one JPEG and one thumbnail per photo, all through the file
  store), `AndroidProgressPhotoFileStore`, `IosProgressPhotoFileStore`,
  `AndroidDeviceLock`, `IosDeviceLock`, `ProgressPhotosClearer`.
- `:libraries:scanner` gains `StillCamera`: a live viewfinder with a
  shutter, front or back camera, returning an upright JPEG and a thumbnail.
  CameraX `ImageCapture` on Android, `AVCapturePhotoOutput` on iOS. In-app on
  purpose: the system camera app can save its own copy to the gallery and
  cannot draw the outline.
- Screens and routes live in `:features:trends` and `:features:trends:impl`
  (Weight's neighbours): `ProgressPhotosRoute()`,
  `ProgressPhotoCaptureRoute()`, `ProgressPhotosCompareRoute(leftId, rightId)`,
  `ProgressPhotoRoute(id)`, all `screen<>`.

## Lock flow (`PhotoLock`, `:features:trends:impl`, app scope)

```kotlin
enum class PhotoLockState { Locked, Unlocking, Unlocked, NoDeviceLock }
class PhotoLock(deviceLock: DeviceLock, appLifecycle: AppLifecycle) {
    val state: StateFlow<PhotoLockState>
    suspend fun unlock()   // NoDeviceLock when none is set; ignored while Unlocking or Unlocked
    fun lock()             // ignored while Unlocking: the prompt itself can stop the activity
}
```

Every photo screen's state carries `lock: PhotoLockState` and renders only a
lock panel (`Unlock` button) unless it is `Unlocked`. The grid's view model
locks and asks on creation (each time the screen opens). `PhotoLock` locks
itself when the app goes to the background (`AppLifecycle`, the app-wide
signal; a back stack entry's own `ON_STOP` also fires on a forward
navigation, which would lock the grid when Compare opens). Every photo
screen sends `Foregrounded` on `ON_START`, which asks again if locked.
`DeviceLock.authenticate()` owns its prompt title, so no copy rides through
the view models.

## Grid (`ProgressPhotosRoute`)

Top app bar `Progress photos` with ⋮ (`Delete all photos`). The privacy card:
lock icon, `Stored only on this phone, behind your phone's lock. Never shared
or backed up.` Then month sections (`October`), three portrait tiles a row,
each with a date tag (`Oct 25`). Saved comparisons, when any, under the
months. Pinned at the bottom: `Compare` (two or more photos) and `Take
photos`. Empty: `No photos yet. Take the first set whenever you like.`

Kept on purpose: the design's card says "behind your fingerprint" and "unless
you turn that on"; the app says "your phone's lock" (Face ID, a fingerprint or
the passcode all qualify) and drops "unless you turn that on", because there
is nothing to turn on (nothing advertised that isn't built). The design's
small `Front · Side` note beside the month is left out; each tile is one
pose and the tag says which in its spoken label.

```kotlin
data class ProgressPhotosState(
    val lock: PhotoLockState = PhotoLockState.Locked,
    val loading: Boolean = true,
    val months: List<PhotoMonth> = emptyList(),      // newest first
    val saved: List<SavedPair> = emptyList(),
    val canCompare: Boolean = false,
    val menuOpen: Boolean = false,
    val confirmDeleteAll: Boolean = false,
)
data class PhotoMonth(val month: YearMonth, val tiles: List<PhotoTile>)
data class PhotoTile(val id: String, val date: LocalDate, val pose: Pose, val thumbnail: PhotoBytes?)
data class SavedPair(val id: String, val left: PhotoTile, val right: PhotoTile)
```

| Action | Does |
|---|---|
| `PhotosArrived(photos, saved)` | Groups by month, loads thumbnails. |
| `LockChanged(state)` | |
| `UnlockTapped`, `Foregrounded` | Ask for the lock. |
| `TileTapped(id)` | Event `OpenPhoto(id)`. |
| `CompareTapped` | Event `OpenCompare(null, null)`. |
| `SavedPairTapped(id)` | Event `OpenCompare(left, right)`. |
| `TakePhotosTapped` | Event `OpenCapture`. |
| `MenuTapped` / `MenuDismissed` | |
| `DeleteAllTapped` | Opens the confirm. |
| `DeleteAllConfirmed` / `DeleteAllCancelled` | `repository.deleteAll()`. |
| `BackTapped` | `Close`. |

## Capture (`ProgressPhotoCaptureRoute`)

Full-bleed `StillCamera` viewfinder; over it the last photo of the same pose
at low opacity, so poses line up (the outline). `Front` then `Side`, a shutter,
a flip-camera button, `Done` after the first. Camera permission as in the
scanner (`CameraPermissions`, `rememberPermissionRequest`), with the same
denied panel. Each shot is saved at once (`repository.add(jpeg, thumbnail,
pose, today)`).

Kept on purpose: the "outline" is the previous photo as a faint ghost, not an
edge-traced outline. `TODO(design-sync-2)` if a traced outline is wanted.

Actions: `LockChanged`, `PhotosArrived`, `PermissionChecked`,
`PermissionAnswered`, `AllowCameraTapped`, `OpenSettingsTapped`, `Resumed`,
`ShutterTapped`, `Captured(result)`, `FlipTapped`, `CameraUnavailable`,
`UnlockTapped`, `Foregrounded`, `DoneTapped`, `BackTapped`. A set closes on
its own once the side is saved.

## Compare (`ProgressPhotosCompareRoute`)

Top app bar `Compare` with ⋮ (`Save comparison` / `Remove saved comparison`).
Two photos side by side with their date tags, or one frame in Slider mode
where a handle wipes between them. Under them a date picker per side (a menu
of every photo, `Oct 25 · Front`), the `Side by side` / `Slider` control, and
`Note on Oct 25 · optional` for the right-hand photo. Defaults when opened
without a pair: the earliest and the latest photo of the latest's pose.

```kotlin
data class CompareState(
    val lock: PhotoLockState,
    val choices: List<PhotoChoice>,          // every photo, newest first
    val left: ComparePhoto?, val right: ComparePhoto?,
    val mode: CompareMode,                   // SideBySide | Slider
    val sliderAt: Float,                     // 0..1
    val note: String,
    val savedId: String?,                    // this pair is saved
    val pickerOpen: CompareSide?, val menuOpen: Boolean,
)
```

Actions: `PhotosArrived`, `LockChanged`, `PickerTapped(side)`,
`PickerDismissed`, `PhotoPicked(side, id)`, `ModePicked(mode)`,
`SliderMoved(at)`, `NoteTyped(text)` (saved to the right photo as typed),
`MenuTapped`, `MenuDismissed`, `SaveComparisonTapped`,
`RemoveComparisonTapped`, `Foregrounded`, `UnlockTapped`, `BackTapped`.
Events: `Close`. A saved pair shows under the grid and the menu then offers
to remove it, so there is no toast.

## Photo (`ProgressPhotoRoute`)

The photo large with its date tag, title the date, ⋮ `Delete this photo` with
a confirm. Actions: `PhotosArrived`, `LockChanged`, `MenuTapped`,
`MenuDismissed`, `DeleteTapped`, `DeleteConfirmed`, `DeleteCancelled`,
`Foregrounded`, `UnlockTapped`, `BackTapped`. Closes when the photo is gone.

## Visibility surfaces

No numbers at all: dates are labels, notes are the person's own words. The
leak test lists `progress-photos` and `progress-photos-compare` anyway, so a
number slipped in later fails.

## Copy (`strings_progress_photos.xml`, prefix `photos_`)

`Progress photos`; privacy card; `October` (month name from `DateCopy`);
`Compare`; `Take photos`; `Delete all photos`; confirm `Delete all progress
photos?` / `They're only on this phone, so they can't be brought back.` /
`Delete` / `Cancel`; empty; lock panel `Progress photos are locked` / `Unlock
with your phone's lock to see them.` / `Unlock`; no lock `Your phone has no
lock` / `Set a passcode, fingerprint or face unlock in your phone's settings
to keep these private.`; prompt reason `Unlock your progress photos`;
capture `Front` / `Side` / `Take photo` / `Switch camera` / `Done` / the
camera panel copy; compare `Compare`, `Side by side`, `Slider`, `Note on %1$s
· optional`, `Save comparison`, `Remove saved comparison`; photo `Delete this photo`, `Delete this photo?` / `It's only on this
phone, so it can't be brought back.`; Weight row `Last taken %1$s · private`,
`Private, on this phone only`.

## Edge cases
- One photo: no Compare button. Compare with one side missing shows the
  picker only.
- A photo deleted while Compare holds it: that side falls back to the
  default.
- A thumbnail that fails to decode: the quiet placeholder tile.
- Simulator or no camera: the camera panel says the camera isn't available.
- Flag off: no Weight row, no route reachable, nothing runs at launch (the
  clearer only runs on a user change, and only deletes files).

## Tests
- `ProgressPhotoRepositoryImplTest` (fake file store): add writes the JPEG,
  thumbnail and index; observe orders newest first; note and delete;
  deleteAll removes every file; a broken index reads as empty; saved
  comparisons drop with their photos.
- `PhotoLockTest` (fake `DeviceLock`): unlock, cancel, no lock set, lock
  while unlocking is ignored, relock.
- View models: every action above, with the fake repository and lock.
- `ProgressPhotosClearerTest`.
- Leak surfaces `progress-photos`, `progress-photos-compare`.
- Previews: `progress-photos-screen__progress-photos`,
  `progress-photos-screen__progress-photos-compare`, plus slider, empty,
  locked, no lock, photo, capture, dark.

## Platform changes
- iOS `Info.plist`: `NSFaceIDUsageDescription` added; `NSCameraUsageDescription`
  reworded (it said photos are never saved). No entitlement.
- Android: `USE_BIOMETRIC` in `:libraries:progressphotos:impl`'s manifest.
  CAMERA is already declared by the scanner.
