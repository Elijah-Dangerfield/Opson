# Findings: test apps

`apps/integration/`, `apps/leaktest/` and `apps/screenshots/`. The integration bugs found
earlier are in [integration.md](integration.md) and not repeated. Each finding names the
place, the rule from [PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. Every §7 journey has an end-to-end test — not a problem

PRACTICES §7 lists eight journeys. Each runs the real client stack against the in-process
server and Postgres:

| Journey | Test |
|---|---|
| Onboard and sync | `GuestOnboardingSyncTest` |
| Log and pull on a second device | `TwoDeviceSyncTest` |
| Settings merge across old and new clients | `SettingsMergeAcrossVersionsTest` |
| Search and barcode | `FoodSearchAndBarcodeTest` (extended, see 2) |
| Delete everything | `DeleteEverythingTest` |
| Fasting sync | `FastingSyncTest` |
| Waitlist | `WaitlistTest` (raw HTTP, because its client is the website) |
| Photo with a fake provider | `DescribeAndPhotoTest` |

## 2. Search and barcode never proved a scan meets the importers' stored form — fixed

- **Where:** `apps/integration/.../foods/FoodSearchAndBarcodeTest.kt`, `helpers/FakeUpstreams.kt`.
- **Rule:** §7 "every user journey that crosses the wire gets one".
- **Failure:** both importers store codes as 14-digit GTINs; scanners read 12 (UPC-A),
  13 (EAN) or 8 (UPC-E) digits. The barcode test seeded and scanned the same string, and
  the Open Food Facts stand-in only ever said no, so the live-source path (served, stored
  in `foods`, the next scan a catalog hit with the same id) had no journey.
- **Fix:** `test(integration): a barcode is found in every form it can be scanned in, and an Open Food Facts answer is cached for the next scan`.
  Both pass today; they guard `Barcodes.equivalents` and `CatalogBarcodeLookup` end to end.

## 3. Two leak-test surfaces were covered but not pinned — fixed

- **Where:** `apps/leaktest/.../SurfacesCoverageTest.kt`, `REQUIRED_SURFACES` and
  `SHOWS_CALORIES_IN_FULL`.
- **Rule:** §7 "the Blind leak test covers every surface".
- **Failure:** `logger-scan-amount` and `logger-scan-miss` (the barcode flow) were run but
  not pinned, so deleting either, or unmarking `showsCaloriesInFull`, would not fail the
  build. The pin relied on whoever added a surface remembering it.
- **Fix:** `test(leaktest): every listed surface has to be pinned, and every one that draws energy out is checked with it hidden`.
  `everyListedSurfaceIsPinned` fails on any listed surface missing from either list.
- **Deferred (cross-slice):** add both ids to the table in
  `docs/plans/screens/blind-leak-test.md`.

## 4. Seven surfaces that draw energy out were never checked with it hidden — fixed

- **Where:** `EnergyOutHiddenLeakTest.ENERGY_OUT_SURFACES`, a hand list.
- **Rule:** §7; the visibility policy is the only thing that may hide a number.
- **Failure:** `today-macros`, `today-weight`, `today-select`, `today-select-remove`,
  `today-swipe-delete`, `today-delete-confirm` and `targets-deficit` draw energy out in
  Full, but were not in the list, so a person who hides energy out was never checked on
  them.
- **Fix:** same commit. `LeakTestControlTest` already renders every calorie surface in
  Full; it now fails on one that shows an energy-out tag without being listed. All seven
  pass with energy out hidden or softened, so this was a coverage gap, not a leak.

## 5. Notifications and widgets in the leak test — not a problem

- **Notifications:** reminders are the only ones (`LocalNotifications`), and every word
  comes from `ReminderCopy`, fixed resources with no format arguments.
  `reminder-notification` renders that copy for every meal and water key in both tones.
  Android's channel names carry no numbers.
- **Widgets:** the Glance widget and the SwiftUI widget draw only `WidgetSnapshot`, which
  `WidgetSnapshotBuilder` builds through the policy. The `widget` surface renders
  `WidgetCard` from the same builder, and `WidgetCard` draws every text field the
  snapshot has, content descriptions included.
- **Deferred (cross-slice, platform-libraries):** `libraries/widgets/.../WidgetSnapshot.kt`
  KDoc cites `apps/ios/OpsonWidgets/WidgetSnapshot.swift`, which is not on main (it is on
  `hold/0.2-widgets-native`). Say so in the KDoc, or drop the path until the branch lands.

## 6. A preview that throws passes the screenshot run — not a problem

`PreviewScreenshotTest.render` records the failure in the screen's result file and
returns, on purpose: one broken preview should not cost the designer every other
screen. Roborazzi's own verify failure is still rethrown.

## Not problems

- The integration harness has no fixed sleeps: `awaitUntil` polls with a timeout and the
  journeys wait on flows.
- `SQLException.kt` and `isReturnDefaultValues` are explained in `integration.md`'s
  harness notes.

## Product notes

None from this slice.
