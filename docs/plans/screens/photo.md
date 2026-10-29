# Photo estimate plan (0.2)

Take or pick a photo of a meal and get an editable list of the foods it
probably holds, each matched to the catalog with an estimated amount. The
person fixes what is wrong and adds it to the meal. Every line is an estimate
and says so. Behind `release.photo`, default off.

## Source
- Design: `docs/design/v3/logger-photo.png` (3.7, the review), `logger.png`
  (the Photo chip, first in the row). v3 wins over the handoff. No v3 frame
  draws the steps before the review (choose a photo, estimating, unavailable),
  so those are built from existing components in the same sheet.
- Manifest note for 3.7: "Each line item can be edited. The AI never gets the
  last word: 'About' and 'Estimate' stick to the entry. In Blind the kcal
  column and the total drop out."
- Pipeline: `POST /v1/describe` and `DescribedItem` (`apps/server/.../domain/Describe.kt`).

## Not built, on purpose
- **The Plus badge.** Plus pricing does not exist yet and 0.2 has no paywall.
  The badge is the only Plus mark in the frame, and drawing it would promise a
  price. A paywall would gate in one place: `PhotoEstimateViewModel` before it
  sends `LaunchCamera` / `LaunchLibrary` (client), with the server's per-user
  daily limit (`PhotoEstimateLimiter`) as the entitlement check. Both are
  marked `// Plus gate` in code.
- Photo from the Add menu or the meal sheet (`shared.md` 0.1 scope gates). The
  logger chip is the only entry point drawn in v3.
- Photo from search, a barcode miss or Describe. Search's "Photograph it" was
  removed on 2026-10-27: packaged foods go through Add a packaged food (photos
  of the label, `submit-food.md`), which is far more accurate, and the meal
  estimate stays on the + sheet's tile alone (docs/decisions.md). Since
  2026-10-28 that tile reads "Estimate a meal" and the screen's title the same,
  and its first line says "we'll estimate what's on it", so it never reads as
  the way to add a food; the pack's own tile, "Packaged food", sits beside the
  scanner.

## Flow
1. Logger, Photo chip (flag on, not picking for Create a meal) opens
   `PhotoEstimateRoute(dayIso, slot)`, a full-height sheet stacked on the
   logger, so Back lands on the logger with its basket.
2. **Start**: an optional note ("Your note", what a photo cannot show: oil,
   butter, cooking), then **Take a photo** (hidden where there is no camera,
   such as the simulator) and **Choose from library**. One line says the photo
   goes to our AI provider and is not saved.
3. The system camera or the system photo picker. The result is downscaled on
   the device to 1568 px on the long edge, JPEG, and held in memory only.
4. **Estimating**: the thumbnail and "Looking at your photo".
5. **Review** (the v3 frame): "What we think is there", one row per item with
   the food, the amount and its energy, and an X. Tap the food to swap it (a
   search inside the sheet), tap the amount to open a stepper with unit chips
   under the row, X removes. "Add something it missed" opens the same search
   and adds a row. The banner: photos miss oil and cooking method, saved as an
   estimate. Footer: "N items · Estimate", "About 379 kcal", **Add to
   {meal}**.
6. Add: the rows join the basket as `PickSource.Photo` and `BasketCommitter`
   commits the whole basket, exactly as the amount sheet's Add does. Both
   sheets close; How it felt follows when asked.

Calm ends, never dead ends:
- **Unavailable** (server has no key, 503): "Photo estimates aren't available
  right now", with Describe it instead and Go back.
- **Failed** (offline, timeout, provider error): Try again (same photo, kept in
  memory), Describe it instead.
- **Too many** (429, the per-user limit): try later, or describe it.
- **Nothing found** (an empty list): Try another photo, Describe it instead.
- **Camera off**: Allow camera (when it can still be asked) or Open Settings,
  and Choose from library (needs no permission).

"Describe it instead" closes this sheet and reopens the logger on Describe
(`goBack`, `goBack`, `LoggerRoute(start = Describe)`); the basket survives.

## Routes
`PhotoEstimateRoute(dayIso: String, slot: MealSlot)`, `:features:logger:impl`,
`bottomSheet<>` with `FullHeightFraction`, typeMap for `MealSlot`. Only the
logger opens it.

## Modules
| Module | Adds |
|---|---|
| `:libraries:scanner` | `MealPhoto(bytes, mediaType)`, `MealPhotoPicker` (composable launchers for the camera and the library, `canTakePhoto`). |
| `:libraries:scanner:impl` | Android: `ActivityResultContracts.TakePicture` into a cache file behind its own `FileProvider` (deleted as soon as it is read), `PickVisualMedia` (the system photo picker, no storage permission), downscale with `BitmapFactory` + EXIF rotation. iOS: `UIImagePickerController` (camera) and `PHPickerViewController` (library, no photo-library permission), downscale with `UIGraphicsImageRenderer`. Kotlin/Native, no Swift, like the barcode camera. |
| `:libraries:foods` | `MealPhotoEstimator` and `PhotoEstimate` (Items, Unavailable, TooMany, Failed(offline)). |
| `:libraries:foods:impl` | `HttpMealPhotoEstimator`, `POST /v1/describe/photo`, no retry (each call costs money; Try again is the person's). |
| `:features:logger:impl` | `PhotoEstimateEnabled` flag, `LoggerMethod.Photo`, `PhotoEstimateRoute`, `PhotoEstimateViewModel`, `PhotoEstimateStateBuilder`, `PhotoEstimateSheetContent`, `PhotoTelemetry`, `PickSource.Photo`, `LogVia.Photo`. |

### Why the system camera and picker, not a viewfinder
The scanner's live viewfinder is a platform view, which is why the scanner is a
full screen and not a sheet. A still photo needs none of it: the OS camera
already does focus, flash and HDR, and the system picker needs no library
permission on either platform. The scanner's `CameraPermissions` and its
permission request are reused for the camera; the library path asks for
nothing.

## State
```kotlin
data class PhotoEstimateState(
    val slot: MealSlot,
    val stage: PhotoStage = PhotoStage.Start,
    val note: String = "",
    val photo: MealPhoto? = null,          // the thumbnail, in memory only
    val canTakePhoto: Boolean = true,
)
sealed interface PhotoStage {
    data object Start; data object Estimating
    data class Review(val rows: List<PhotoRowUi>, val editor: PhotoAmountEditor?, val usedNote: Boolean,
        val footer: PhotoFooter?)                     // null when there is nothing to add
    data class Swap(val replacing: Boolean, val query: String, val results: List<SwapResultUi>?)  // null: searching
    data object NothingFound; data object Unavailable; data object TooMany
    data class Failed(val offline: Boolean)
    data class CameraOff(val canAskAgain: Boolean)
}
data class PhotoRowUi(key, name, amount: String /* "120 g", "1 tbsp" */, isWeight: Boolean /* reads "about 120 g" */,
    calories: Display /* bare */, matched: Boolean)
data class PhotoAmountEditor(key, amount: String, units: List<String>, unitIndex: Int, canLess: Boolean)
data class SwapResultUi(id, name, subtitle: String?, amount: String, calories: Display /* bare */)
data class PhotoFooter(count: Int, calories: Display /* "380 kcal" */)  // counts the basket's other picks too
```
Built by `PhotoEstimateStateBuilder.build(model: PhotoModel, inputs: PhotoInputs?, policy)`,
pure. `PhotoModel` is what the view model holds (step, note, photo, `PhotoRow`s,
the open editor, the swap search); `PhotoRows` holds the amount rules. Every
energy value goes through `VisibilityPolicy.resolve(Calories, …, Surface.Logger)`
with the day's context.

## Amounts
- Each matched item's food is loaded with `FoodCatalog.detail` before the
  review shows, so the amount can be edited in that food's own units
  (`Food.amountUnits()`, `PortionAmount`). An item the catalog did not match
  keeps the server's name and amount, has no energy and no stepper, and can be
  swapped.
- Start unit: the portion the server matched (`portionLabel`), counted to the
  nearest half; else grams, rounded.
- **Household units when calories are hidden.** With calories at Hide on the
  Logger surface, a food with any household portion starts in that portion,
  and the stepper does not offer grams. Weights are a number too. This reads
  the calories level, never the mode.
- A weight reads "about 120 g"; a household amount reads "1 tbsp" or "2 cups", as drawn. Both go through `LogDraft.amountLabel` (`HouseholdAmount`), like every other row.

## Actions
| Action | Does |
|---|---|
| `InputsArrived(inputs)` | The day (for the policy context) and the basket. Rebuild. |
| `PolicyArrived(policy)` | Rebuild; with calories now hidden, weighed lines move to their household measure. |
| `NoteTyped(text)` | Up to 300 characters. |
| `TakePhotoTapped` | Camera permission: Granted sends `LaunchCamera`; NotAsked sends `RequestCameraPermission`; Denied shows `CameraOff`. Plus gate. |
| `ChooseFromLibraryTapped` | `LaunchLibrary` (no permission). Plus gate. |
| `CameraPermissionAnswered(p)` | Granted launches the camera, else `CameraOff`. |
| `AllowCameraTapped` / `OpenSettingsTapped` | Ask again / `CameraPermissions.openSettings()`. |
| `PhotoAnswered(result, fromCamera)` | Picked: Estimating, `photo.started`, the estimate. Cancelled: nothing. Failed: `Failed`. |
| `EstimateArrived(estimate, foods)` | The answer plus the catalog foods its lines name (loaded in parallel before it is sent). Items: Review and `photo.estimated`; empty: `NothingFound`; the rest: the calm stages and `photo.failed`. Dropped unless still estimating. |
| `RowAmountTapped(key)` | Opens or closes the stepper under that line (matched lines only; an unmatched line's amount opens the swap). |
| `LessTapped(key)`, `MoreTapped(key)`, `UnitPicked(key, index)` | `PortionAmount` steps and conversions. |
| `RowRemoveTapped(key)` | Drops the line. An empty list says so and keeps "Add something it missed". |
| `RowNameTapped(key)` / `AddMissedTapped` | The swap search, from the line's short name (searched at once) or empty. |
| `SwapTyped(text)` | Debounced 250 ms `FoodCatalog.search(q, 15)`. |
| `SwapResultsArrived(query, foods)` | Dropped unless still the current query. |
| `SwapPicked(id)` | Replaces the line (keeping its weight) or adds one at its first unit; back to Review. |
| `TryAgainTapped` | Same photo and note, estimate again. |
| `AnotherPhotoTapped` | Start, photo and lines dropped. |
| `DescribeInsteadTapped` | `OpenDescribe(day, slot)`. |
| `BackTapped` | Swap back to Review (system back too); otherwise `Dismiss`. |
| `AddToMealTapped` | Lines into the basket as `PickSource.Photo`, `BasketCommitter.commit`, `photo.added`, then `Logged`. |

## Events
`LaunchCamera`, `LaunchLibrary`, `RequestCameraPermission`,
`OpenDescribe(day, slot)`, `Logged(day, committed)`, `Dismiss`.

## Logging the items
`PickSource.Photo` logs with `LogMethod.Describe` and precision `Estimate`. A
new `photo` method would need a server migration and would break a 0.1 phone
pulling the item from the same account (its enum has no `photo`). Telemetry
still tells them apart (`logger.method_picked` `method=photo`).

## Server
`POST /v1/describe/photo`, JWT required, new route only.

- Body JSON `{image: base64, note?: string}`. The type is read from the bytes
  (JPEG, PNG, WebP), never trusted. Limits: body 6 MB (`Content-Length`
  checked before reading, and the read itself bounded), decoded image 4 MB,
  note 300 characters. 400 `invalid_body` / `invalid_image` /
  `invalid_note`, 413 `image_too_large`.
- Rate limits: per user (from the JWT, after auth) 10 per 10 minutes and 40 per
  day, in memory (`PhotoEstimateLimiter`), 429 `rate_limited`; plus a named
  per-IP bucket (30/min) in front of auth. One Fly machine today; a second
  machine would double the effective limit, noted in the limiter.
- `EstimateProvider` (domain) returns `Estimated(foods)`, `NotConfigured` or
  `Failed`. `EstimatedFood(name, quantity, unit, grams)`.
  - `ConfiguredEstimateProvider` (data) delegates to
    `AnthropicEstimateProvider` when `ANTHROPIC_API_KEY` is set
    (`FoodSourcesConfig.anthropic`), else answers NotConfigured. 503 `photo_estimate_unavailable` with no
    key; 502 `photo_estimate_failed` when the provider fails or refuses.
  - `FakeEstimateProvider` for tests.
- `PhotoDescriber` (data) turns each `EstimatedFood` into a `ParsedPhrase`
  (household unit or size when the model gave one the parser knows, else
  grams; the model's weight rides along as `gramsHint` for a household unit
  the catalog has no portion for) and runs it through
  `CatalogFoodDescriber.describePhrases`, the same
  matching `/v1/describe` uses. The response is `DescribeResponse`, so photo
  and text return the same row shape.
- **Never stored, never logged.** The image lives in the request's memory and
  is dropped when it returns. The route logs `items`, `latency_ms` and the
  outcome only; no note, no names, no amounts. The span carries the same.

### Model call
- Anthropic Messages API through the official Java SDK
  (`com.anthropic:anthropic-java`), one request: the image as a base64 image
  block, the note as text, a fixed system prompt, and structured output
  (`output_config.format`, a JSON schema: `foods[]` of `name`,
  `quantity`, `unit`, `grams`). Max 2,000 output tokens, 30 s timeout, one SDK
  retry.
- **Model: `claude-opus-5-5`, effort `low`**, overridable with
  `ANTHROPIC_PHOTO_MODEL`. Cost: the client sends at most 1568 px on the long
  edge, about 2,500 image tokens, plus about 600 for the prompt; output is a
  few hundred tokens. At $4 / $20 per million that is about 2 cents a photo,
  so 40 a day per person caps one person near 80 cents a day. Latency: low
  effort keeps thinking short; a single image call answers in a few seconds,
  inside the 30 s budget. Opus over Haiku because portion size is the hard
  part of this job and the reason someone would trust it; Haiku 5.5 is the
  lever if cost matters more (about a twentieth of the price), set by env
  without a release. Server-side refusal `fallbacks: "default"` is on, as the
  API guidance recommends for Opus 5.5.

## Components
`SheetHeader(onBack, overline)`, `ButtonPrimary`, `ButtonGhost`, `TextArea`,
`SearchField`, `Stepper`, `ChoiceChip`, `Banner`, `CircularProgressIndicator`,
`ListRow`, `RoundIconButton`, `NutrientText`, `ActionChip` (the chip, with
`AppIcons.PhotoCamera`), `FoodRowsCard`.

Added to `:libraries:ui`: `PhotoThumbnail` (decodes the in-memory bytes with
`decodeToImageBitmap`; with none, a quiet camera tile, so previews need no
photo), `TapField` (a filled field that opens an editor when tapped, for a
line's food and amount), and `AppIcons.PhotoLibrary`. The iOS camera is
presented over full screen so the Compose host never leaves the window
(AGENTS.md, the UIKit presentation landmine).

## Visibility surfaces (surface `Logger`)
| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Row energy | calories | `198` | hidden (an item has no goal) | hidden | hidden |
| Footer energy | calories | `About 379 kcal` | hidden | hidden | hidden, with "About" |
| Swap result energy | calories | `89` | hidden | hidden | hidden |
| Row amount | (amount, not a nutrient) | `about 120 g` | same | same | household unit where the food has one |

No mode check: Blind is the calories level at Hide.

## Copy (`strings_photo.xml`, `photo_` prefix)
| Key | String |
|---|---|
| `photo_method` | Photo |
| `photo_overline` | Photo for %1$s |
| `photo_title` | Estimate |
| `photo_start_body` | Take a photo of your plate and we'll suggest what's on it. You check every line before anything is saved. |
| `photo_note_label` | Your note |
| `photo_note_placeholder` | Anything a photo can't show, like oil, butter or how it was cooked |
| `photo_take` | Take a photo |
| `photo_choose` | Choose from library |
| `photo_privacy` | The photo goes to our AI provider to make the estimate. It isn't saved. |
| `photo_estimating` | Looking at your photo |
| `photo_list_title` | What we think is there |
| `photo_tap_to_edit` | Tap to edit |
| `photo_about` | about %1$s |
| `photo_not_matched` | Not in the food list yet |
| `photo_add_missed` | Add something it missed |
| `photo_banner_note` | We used your note too. This is saved as an estimate. |
| `photo_banner` | Photos can miss oil and how it was cooked. This is saved as an estimate. |
| `photo_footer_count` | %1$d items · Estimate (plural, 1 item) |
| `photo_footer_about` | About |
| `photo_remove_cd` | Remove %1$s |
| `photo_swap_title` / `photo_add_title` | Swap the food / Add a food |
| `photo_swap_placeholder` | Search for the right food |
| `photo_empty_title` / `_body` | We couldn't make out any food / Try another photo, or describe it in your own words. |
| `photo_unavailable_title` / `_body` | Photo estimates aren't available right now / You can still describe the meal in your own words, or search for each food. |
| `photo_failed_title` / `_body` / `_offline` | Couldn't read that photo / Something went wrong on our side. Try again in a moment. / Photo estimates need a connection. |
| `photo_too_many_title` / `_body` | That's a lot of photos for now / Try again in a little while, or describe this one in your own words. |
| `photo_camera_title` / `_body` | The camera is off for Opson / You can turn it on in Settings, or choose a photo from your library. |
| `photo_camera_body_ask` | Opson only uses the camera when you take a meal photo. You can also choose one from your library. |
| `photo_try_again`, `photo_another`, `photo_describe_instead`, `photo_back_to_search`, `photo_open_settings`, `photo_allow_camera` | Try again / Try another photo / Describe it instead / Go back / Open Settings / Allow camera |
| `photo_list_empty` | Nothing left on the list. Add what you ate, or go back. |
| `photo_swap_searching` / `photo_swap_none` | Searching / Nothing by that name. Try another word. |
| `photo_thumbnail_cd`, `photo_food_cd`, `photo_amount_cd` | Your meal photo / Swap %1$s / Change the amount of %1$s |

iOS `NSCameraUsageDescription` (changed): "Opson uses the camera to read
barcodes on food packs, or to photograph a meal when you ask for an estimate.
Photos are never saved." No `NSPhotoLibraryUsageDescription`: PHPicker runs
out of process and needs none. Android: no new permission (`CAMERA` is
already declared by the scanner; the photo picker needs none); a new
`FileProvider` for the camera's output file.

## Telemetry (`docs/practices/app-events.md`)
From `PhotoEstimateViewModel` through `PhotoTelemetry`. No image, no food
name, no amount, no note.
| Event | Attributes | Fires |
|---|---|---|
| `photo.started` | `source` (`camera`, `library`) | A photo came back and the estimate was asked for. |
| `photo.estimated` | `items` (count), `matched` (count) | The review shows. |
| `photo.added` | `items` (count) | Add to {meal} wrote the rows. |
| `photo.failed` | `reason` (`unavailable`, `too_many`, `offline`, `error`, `nothing_found`) | A calm end instead of a review. |
`logger.method_picked` gains `method=photo`.

## Privacy (the photo goes to a third party)
Draft for `legal/privacy.md`, added at the 0.2 release, not before (also in
`docs/plans/0.2/release-checklist.md`):

- Under "What else the app sends": "**Meal photos**, only when you use Photo
  estimate, are sent with your note to [Anthropic](https://www.anthropic.com/legal/privacy),
  which reads them and returns a list of foods. We don't save the photo or
  keep a copy on our server, and it is not linked to your account. Anthropic
  does not use it to train its models."
- "What the app never does" already promises camera and photos only for the
  feature that needs them; keep it.

App Privacy label change: **Photos or Videos**, used for **App Functionality**,
**not linked** to the user, **not used for tracking**.

Owner checks before the release: the Anthropic org's data retention and
training settings match the sentence above (API inputs are not used for
training by default; retention is set per org).

## Edge cases
- **Flag off**: no chip, no route opened, nothing at launch. The picker and
  estimator are only constructed when the sheet opens.
- **Simulator / no camera**: Take a photo is not shown.
- **Android process death while the camera app is up**: the sheet comes back on
  Start; the photo is lost, by design (it is never written anywhere we keep).
- **Big photos, HEIC, panoramas**: the device decodes and re-encodes to JPEG
  under 1568 px, so the server only ever sees a small JPEG.
- **Picking for Create a meal**: no Photo chip (that mode logs nothing).
- **Offline**: Failed(offline) with Describe it instead (which is also online)
  and Go back.
- **Large text**: rows wrap; the footer stays pinned.

## Tests
- Server: `PhotoRoutesTest` (401, 400s, 413, 429 per user, 503 unconfigured,
  502 provider failure, 200 with the Describe row shape, nothing logged but
  counts), `PhotoDescriberTest` (unit mapping, grams fallback, unmatched),
  `PhotoEstimateLimiterTest`, `AnthropicEstimateProviderTest` (request shape
  and JSON parsing against a mock server, refusal and errors as `Failed`),
  `ImageTypesTest`.
- Client: `HttpMealPhotoEstimatorTest` (status mapping), `PhotoEstimateStateBuilderTest`
  (every stage, Blind drops energy and footer, household units under Hide,
  "about" on weights, unmatched rows), `PhotoEstimateViewModelTest` (every
  action, permission paths, commit with `PickSource.Photo`, telemetry), 
  `LoggerStateBuilderTest` / `LoggerViewModelTest` (Photo chip with the flag on
  and off, pick mode, `PhotoTapped`).
- Leak test: `logger-photo` (review, calories in Full), `logger-photo-swap`
  (calories in Full), `logger-photo-start` (none), added to
  `LoggerLeakSurfaces` and `REQUIRED_SURFACES`.
- Previews (`PhotoEstimatePreviews.kt`): start (capturing), estimating, review,
  review with the stepper open, swap, review Blind, review dark, unavailable,
  nothing found, failed, camera off; logger with the Photo chip.
