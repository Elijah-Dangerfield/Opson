# Barcode scan plan (0.2)

Scan a packaged food from the logger and land on the amount sheet for it. A
miss is calm, never a dead end, and recorded so the owner can see coverage
gaps. Behind `release.barcode`, default off.

## Source
- Design: `docs/design/v3/logger.png`, `logger-picked.png` (the barcode button
  inside the empty search bar). No v3 frame draws the camera screen itself, so
  its layout is ours, built from existing components.
- Handoff: `docs/design/handoff/README.md` (Add menu and meal sheet "Scan a
  barcode", search field "with a barcode button"); `phone-logic.js` `onScan`
  opens the add sheet for the food.
- Older plan: `docs/plans/screens/logger.md` "Barcode (0.2)", waiting copy and
  the 400 ms rule. Its miss buttons are replaced here (see Miss below).
- Data: `docs/plans/food-data-import.md` "Scan-miss measurement (0.1)" only.
  Label photo and community foods are out of scope.

## Entry points
- **Built:** the barcode button in the empty logger search bar
  (`SearchField(trailing = ...)`), the only one drawn in `logger*.png`.
  Hidden while typing (the clear button takes the slot), hidden in Create a
  meal's pick mode (a scan logs into a meal, a meal being put together does
  not log), and not rendered at all with the flag off.
- **Not built:** the Add menu and meal sheet "Scan a barcode" rows
  (`shared.md` 0.1 scope gates). They live on Today, not in `logger*.png`,
  and can open `LoggerRoute` then the scanner later.

## Routes
- `BarcodeScanRoute(dayIso: String, slot: MealSlot)`, a full `screen<>` in
  `:features:logger:impl` (only the logger opens it, like `AmountSheetRoute`).
  A screen, not a sheet: the camera preview is a platform view, and a platform
  view inside a popup window is the least tested path on iOS. Pushed over the
  logger sheet, so Back lands on the logger with its basket intact.
- Hit: `router.batch { goBack(); navigate(AmountSheetRoute(foodId, isCustom = false, source = PickSource.Barcode, dayIso, slot)) }`.
  The amount sheet then sits over the logger exactly as a tapped search row
  does, and its "Add to {meal}" closes both.

## Modules
| Module | What it adds |
|---|---|
| `:libraries:foods` | `Gtin.normalize(code)` (pure, mirrors the importers), `BarcodeMissReporter` interface. |
| `:libraries:foods:impl` | `HttpBarcodeMissReporter` (`POST /v1/foods/barcode-misses`). `FoodCatalogImpl.barcode` caches a found food without marking it used, so the amount sheet can load it when the server cannot serve it again (offline, or a source we may not store). |
| `:libraries:scanner` (new, compose) | `ScannedCode`, `BarcodeFormat`, `CameraPermission`, `CameraPermissions` (status, open settings), `BarcodeCamera` (the composable viewfinder and the permission request). |
| `:libraries:scanner:impl` (new) | Android: CameraX + ML Kit bundled model. iOS: AVFoundation through Kotlin/Native's platform interop. No Swift. |
| `:features:logger:impl` | `BarcodeScanEnabled` flag, the button, `BarcodeScanRoute`, `BarcodeScanViewModel`, `BarcodeScanStateBuilder`, `BarcodeScanScreen`, `BarcodeTelemetry`, `PickSource.Barcode`. |

### Scanner choices
- **Android: CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`
  1.5.3) with ML Kit `barcode-scanning` 17.3.0, the bundled model.** The model
  ships in the APK, so a scan works on the first launch, offline, and on phones
  without Google Play services. The unbundled
  `play-services-mlkit-barcode-scanning` and Google Code Scanner both download
  a module from Play at first use, which the brief rules out. ZXing is in
  maintenance mode and weaker on damaged 1D codes. The analyzer asks only for
  EAN-13, EAN-8, UPC-A and UPC-E, which is faster and cuts false reads.
- **iOS: `AVCaptureSession` + `AVCaptureMetadataOutput` in Kotlin/Native**
  (`platform.AVFoundation`), shown through `UIKitView`. Kept in Kotlin so the
  logic and the Android side share one module, the iOS Gradle compile checks
  it, and `IosAppComponent.create(...)` and `iOSApp.swift` (which every 0.2
  workstream touches) stay unchanged. Metadata types: EAN-13, EAN-8, UPC-E.
  iOS reports a UPC-A as an EAN-13 with a leading 0, which normalises to the
  same GTIN.
- **Permission.** `CameraPermission { Granted, NotAsked, Denied(canAskAgain) }`.
  The request is composition-bound on Android (an activity result launcher),
  so the view model asks for it with an event and the answer comes back as an
  action. iOS uses `AVCaptureDevice.requestAccessForMediaType`. A second
  denial on Android, or any denial on iOS, can only be undone in Settings.

## State
```kotlin
data class BarcodeScanState(
    val slot: MealSlot,
    val stage: ScanStage = ScanStage.Starting,
)

sealed interface ScanStage {
    data object Starting : ScanStage            // permission not known yet
    data object Scanning : ScanStage            // viewfinder live, hint shown
    data class LookingUp(val slow: Boolean) : ScanStage   // slow after 400 ms: first-lookup copy
    data class Miss(val code: String, val name: String) : ScanStage { val canSave: Boolean }
    data object Unavailable : ScanStage         // 503, or offline with nothing cached
    data class NoCamera(val canAskAgain: Boolean) : ScanStage
}
```
No nutrition number is ever in this state: a hit leaves the screen before
anything about the food is drawn. The amount sheet it opens is the existing
one, with its existing policy handling.

`LoggerState` gains `canScan: Boolean` (flag on, not picking for a meal),
built by `LoggerStateBuilder` from `LoggerChoices.scanAvailable`.

## Actions
Scanner (`BarcodeScanAction`):
- `PermissionChecked(permission)`: arrived at open. Granted starts scanning;
  NotAsked sends `RequestPermission`; Denied shows `NoCamera`.
- `PermissionAnswered(permission)`: the system prompt's answer.
- `CodeRead(code)`: the camera read something. Ignored unless scanning, or
  when `Gtin.normalize` rejects it (a misread). Starts the lookup.
- `LookupSlow`: 400 ms passed with no answer.
- `FoodFound(food)`, `FoodNotFound`, `LookupFailed`: the lookup's answer.
- `MissNameTyped(text)`, `SaveNameTapped`, `SearchInsteadTapped`,
  `ScanAgainTapped`, `AllowCameraTapped`, `OpenSettingsTapped`, `CloseTapped`.
- `Resumed`: back from Settings. Starts scanning only if the camera was
  refused and is now allowed; it never asks on its own, because the system
  prompt itself pauses and resumes the screen on Android.

Logger: `BarcodeTapped` sends `OpenScanner(day, slot)`.

## Events
`RequestPermission`, `OpenAmount(foodId, day, slot)`, `Close` (back to the
logger, after "Save the name only", "Search instead" or the close button).
Settings opens through `CameraPermissions.openSettings()`, which is not
navigation.

## Data
- Lookup: `FoodCatalog.barcode(code)`, unchanged. The server tries every
  equivalent form (UPC-A, EAN-13, GTIN-14, UPC-E), then the live outside
  sources. `off_foods` is not involved.
- A miss (`Catching.success(null)`, the server's 404) calls
  `BarcodeMissReporter.report(code)` once, fire and forget, with a
  client-generated id so a retry is idempotent. A failed lookup (503 or
  offline) is not a miss and is not reported.
- "Save the name only" uses the same helper as the logger's: a name-only custom
  food (reused when one by that name exists), picked into the basket as
  `PickSource.Name`, then back to the logger, which shows it in the footer.
- A hit picks with `PickSource.Barcode`, logged with `LogMethod.Barcode`.

## Pick a match (2026-10-29)
A code can be in more than one database: USDA Branded, the Open Food Facts
export, OPDB. The scanner asks for every match
(`FoodCatalog.barcodeMatches`, `GET /v1/foods/barcode/{code}?matches=all`)
and gets the server's single answer first, then the other catalog rows. A
server without the list ignores the param, and offline the cached food
answers alone, so both read as one match. Typed codes go the same way.

- One match, or several the same within rounding (energy within 1 kcal or 2%
  per 100 g, protein, carbs and fat within 0.5 g or 2%): the detail opens as
  before (`BarcodeMatches.groups`).
- Several that differ: "Pick a match" in place of the camera window. One row
  per group, the suggested one first, outlined and labelled. Each row has the
  name, brand, one serving, its energy and macros through the person's mode,
  the databases it stands for ("OPDB · Open Food Facts"), and "Verified",
  "Corrected" (a reviewer's fix) or "Numbers look off" (the server's energy
  check doubts the source). The last two only where calories show. Search
  instead and Scan again under the list, and Open Food Facts' credit when a
  row is theirs.
- A tap opens that food's detail, replacing the scanner as a single hit
  does. Only the suggested food is kept on the device.
- Live Open Food Facts and Nutritionix are still only asked on a catalog
  miss, so a list only ever holds catalog rows.

## Miss
Calm, and offers in this order:
1. A name field ("What is it?") and **Save the name only** (primary, enabled
   once there is a name). First, as the queued plan asks, because it is the
   recovery path.
2. **Search instead**: back to the logger's search bar. (Not with the
   keyboard up: the logger is the screen underneath, so there is no route
   to pass that through, and a tap on the bar is one step.)
3. **Scan again**.

The scanned code is shown under the title ("Barcode 0012345678905") so the
person can see what was read. Unavailable (503 or offline) offers Scan again
and Search instead, with the existing `barcode_unavailable` copy.

## Follow-ups after review (simulator)
- **Camera unavailable.** Allowed but unable to run (no back camera, as on
  the iOS simulator; a failed start; another app holding it, or an iOS
  interruption) is its own calm state, `ScanStage.CameraUnavailable`:
  "Type the code" first, then "Search instead", then "Try the camera again".
  `BarcodeCamera.Viewfinder` gained `onUnavailable`: Android reports no back
  camera, a bind failure, or an error on the camera's state; iOS reports no
  device or input, a session that does not start, and
  `AVCaptureSessionRuntimeError` / `WasInterrupted`.
- **Ways out while scanning.** Under the hint: "Type the code" and "Search
  instead". Typing takes 8 to 14 digits (the numbers under the barcode) and
  runs the same lookup as a scan, hit or miss; a failed check digit asks to
  check the numbers rather than looking anything up. "Type the code" is also
  offered when the camera is refused. A lookup in progress or answered now
  wins over the camera's state in the builder, since a typed code needs no
  camera.
- Telemetry: `typed` on `barcode.hit` and `barcode.miss`, and
  `barcode.camera_unavailable`.

## Follow-ups after device testing (iPhone Pro)
Scans took a long, still hold to catch. The view model was never the cause (it
takes the first read whose check digit passes, with no debounce); the camera
setup was.
- **iOS camera.** `defaultDeviceWithMediaType` gave the plain wide camera,
  which on a Pro cannot focus closer than about 20 cm, so a pack held where it
  fills the frame stayed blurred. The scanner now prefers the triple or dual
  wide virtual camera, which hands over to the ultra wide for close focus, and
  opens at a zoom (`ScanZoom.kt`, after Apple's AVCamBarcode sample) at which a
  small code fills the frame from a distance the lens can focus.
- **iOS focus and reading.** Continuous autofocus and exposure, autofocus
  restricted to the near range, smooth autofocus off (it slows focus down on
  purpose, for video), a 1080p preset, `rectOfInterest` set to the frame with
  a margin (converted through the preview layer once the session runs),
  metadata delivered on its own queue and hopped to main, and the whole
  session configured on the capture queue so opening the screen never waits on
  the camera. The start and stop ordering from the review stays: everything
  still runs on the one serial `captureQueue`.
- **Android.** ImageAnalysis ran at CameraX's default 640x480, too few pixels
  per bar; it now asks for 1280x720 (`ResolutionSelector`, since
  `setTargetResolution` is deprecated). The format filter was already there.
- **Tap to focus.** A tap on the preview focuses and meters there (iOS keeps
  focusing there continuously; CameraX for a few seconds, then back to
  continuous) and draws a ring where it landed.
- **Caught it.** The first accepted read buzzes (`BarcodeScanEvent.Scanned`,
  `HapticFeedbackType.Confirm`), and the frame snaps tight in the accent colour
  with a tick until the lookup has been slow for 400 ms, when the spinner takes
  over. A typed code does not buzz.
- **Light.** A torch button over the camera when it has one. It stays on
  through a lookup and "Scan again" until it is turned off.
- **One camera session per scan.** The viewfinder is called from one place
  for both scanning and the lookup under the scrim, so a read no longer stops
  and restarts the camera.
- Needs a phone to check: the camera choice and zoom, focus speed, the ring
  landing where the finger did, the torch, and the haptic. Unit tests cover
  the zoom maths, the accepted-read rule, the haptic event and the torch state.

## Server: miss logging
- `V13__barcode_misses.sql`, additive and idempotent (`IF NOT EXISTS`,
  `CREATE OR REPLACE VIEW`):
  `barcode_misses(id uuid pk, created_at, barcode, gtin null, country null, platform, app_version null)`,
  RLS on, index on `created_at` and `gtin`.
- `POST /v1/foods/barcode-misses {id, barcode}`: new route, JWT required,
  204, idempotent (`ON CONFLICT (id) DO NOTHING`). 400 `invalid_id`,
  `invalid_barcode`. Its own rate limit (60/min per IP). Country, platform and
  app version come from the request headers every client already sends
  (`ClientContext`), never from the body. GTIN is computed by the server
  (`Barcodes.gtin14`), never trusted from the client.
- Views (`analytics`): `barcode_hit_rate_daily` (from `barcode_lookups`),
  `top_missed_barcodes` (30 days), `barcode_misses_by_country`.

### Privacy, against `legal/privacy.md`
- **No user id, install id or session id** on a miss. Any of them would make
  rows joinable into one person's shopping list. The route reads the JWT only
  to require one.
- **Country is the phone's region setting** (`X-Country-Code`), not location.
  The policy promises "No location tracking"; a region setting the person
  chose is not tracking, and it is coarse (two letters).
- **A barcode names a product, not a person.** Rows hold only the code, its
  GTIN, region, platform, app version and time. The views aggregate by day
  and product, so no view can show one person's scans.
- Policy text to add with the 0.2 release, not before (main must stay a valid
  0.1.x hotfix, and the legal sync publishes on merge):
  - Under "What else the app sends": "**Barcodes we can't find** are recorded
    without your account attached, with your phone's region setting and app
    version, so we can see which products are missing."
  - Under "What the app never does", add camera: "No contacts, camera, photos
    or microphone access unless you choose a feature that needs it, and then
    only for that feature." The camera is used only while the scanner is open,
    and no frame is stored.

## Components
`SearchField(trailing)`, `IconTile` with `AppIcons.BarcodeScanner`,
`SheetHeader`, `Screen`, `ButtonPrimary` (Filled, Tonal), `ButtonGhost`,
`SingleLineField`, `Text`, `CircularProgressIndicator`, `AppTheme` tokens.
Added to `:libraries:ui`: `ScanFrame` (corner brackets over the camera, with a
preview), and `CircularProgressIndicator` now draws still under
`LocalInspectionMode` so a preview with a spinner can be screenshotted. The viewfinder is a slot
(`viewfinder: @Composable (ViewfinderRequest, Modifier) -> Unit`), so previews
pass a plain dark box and never touch a camera.

## Visibility surfaces
| Surface | Numbers | Notes |
|---|---|---|
| Scanner (all stages) | none | No food data is drawn. Identical in every mode. |
| Amount sheet after a hit | Energy, protein, carbs, fat tiles | Existing `AmountStateBuilder`, `Surface.Logger`. Previews for a scanned branded food in Full and Blind. |

Leak test: `logger-scan-miss` and `logger-scan-amount` added to
`LoggerLeakSurfaces`.

## Copy (`strings_barcode.xml`, `barcode_` prefix)
| Key | String |
|---|---|
| `barcode_scan_cd` | Scan a barcode |
| `barcode_title` | Scan a barcode |
| `barcode_close_cd` | Close |
| `barcode_hint` | Hold the barcode inside the frame. |
| `barcode_looking_up` | This one is new to Opson. Looking it up now. |
| `barcode_not_found` | We couldn't find this barcode. |
| `barcode_code` | Barcode %1$s |
| `barcode_miss_body` | You can still log it by name, without numbers. |
| `barcode_name_placeholder` | What is it? |
| `barcode_save_name` | Save the name only |
| `barcode_search_instead` | Search instead |
| `barcode_light_on` / `barcode_light_off` | Turn the light on / Turn the light off (the torch button, for screen readers) |
| `barcode_scan_again` | Scan again |
| `barcode_unavailable` | Couldn't look that up right now. Try again in a moment. |
| `barcode_camera_title` | The camera is off for Opson |
| `barcode_camera_body` | Opson only uses the camera to read barcodes. You can turn it on in Settings, or search by name instead. |
| `barcode_camera_body_ask` | Opson only uses the camera to read barcodes. Nothing is recorded. |
| `barcode_allow_camera` | Allow camera |
| `barcode_open_settings` | Open Settings |

iOS `NSCameraUsageDescription`: "Opson uses the camera to read barcodes on
food packs. No photos are taken or saved."

## Telemetry (`docs/practices/app-events.md`)
Never the barcode, a food name or an amount.
| Event | Attributes | Fires |
|---|---|---|
| `barcode.scan_started` | `permission` (`granted`, `asked`) | The viewfinder goes live. |
| `barcode.hit` | `source` (`usda_branded`, `off`, …), `slow` | A lookup found a food. |
| `barcode.miss` | | The server had no food for the code. |
| `barcode.permission_denied` | `can_ask_again` | The camera answer came back denied, or the scanner opened with it already off. |
`logger.method_picked` gains `method=barcode` for a basket item picked by scan.

## Grafana
Food catalog board (`ops/grafana/generate_db_dashboards.py`): barcode hit rate
(7-day stat and daily series), most-missed barcodes (table), misses by country
(bars), and a Loki panel of the four client events. Generated JSON only; the
orchestrator pushes it.

## Edge cases
- **Repeated reads**: the camera reports the same code many times a second;
  only `Scanning` accepts one.
- **Misreads**: a code that fails its check digit is ignored and scanning
  continues.
- **Offline**: a code scanned before comes from the device cache; otherwise
  Unavailable. Nothing is reported.
- **Flag off**: no button, so no route is ever opened. The route stays
  registered (harmless, unreachable).
- **Back from Settings**: `Resumed` re-checks, so turning the camera on there
  starts scanning without reopening.
- **Large text**: the bottom card scrolls.

## Tests
- `GtinTest` (client) and `BarcodesTest.gtin14` (server): UPC-A, EAN-13,
  EAN-8, UPC-E, GTIN-14, bad check digits, placeholders.
- `BarcodeScanViewModelTest`: one per action, flag-independent.
- `BarcodeScanStateBuilderTest`: every stage, miss `canSave`.
- `LoggerViewModelTest` / `LoggerStateBuilderTest`: flag on and off, pick mode,
  `BarcodeTapped`.
- `FoodCatalogImplTest`: a found code is cached unused; `HttpBarcodeMissReporter`.
- Server: `BarcodeMissRoutesTest` (204, 400s, 401, headers), 
  `PostgresBarcodeMissRepositoryTest` (insert, duplicate id ignored),
  `DatabaseSchemaTest` (projection, RLS, views, V13 re-run).
- Previews: scanning, looking up, miss, miss with a name, unavailable, camera
  denied (light and dark), and the amount sheet for a scanned food in Full and
  Blind. Logger with the flag on and off.

## Also read the label (`release.builders`)

An active Builder sees an "Also read the label" switch under the camera. On, a
code that finds a food opens Check against the pack instead of the food detail,
where the person photographs the panel themselves. Nothing is read from the
scanner's own frames (`docs/plans/feedback-2026-10-29/passive-label-capture.md`,
option D). A miss goes to Add a packaged food, in Builder mode.


More › Builders' "Check a food against its pack" opens the scanner the same way
(`CheckPackRoute`) with the switch already on. There a miss adds the food
without logging it, and "Search instead" goes back to the Builders page.
