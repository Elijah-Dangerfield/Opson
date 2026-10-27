# Widgets plan (0.2)

Home screen widgets on Android (Jetpack Glance) and iOS (WidgetKit), the More
row that v3 draws, and a small Widgets page behind it. The More row is behind
`release.widgets` (default off); the snapshot is written whatever it says,
because the iOS extension is in the widget gallery in every build. Read `shared.md`, `settings.md` and
`blind-leak-test.md` first.

## Source
- Design v3: `widgets.png` (14.1) and `more.png` (the "Widgets and watch face"
  row). Manifest note: "Widgets follow the same policy as the app. Tapping + on
  a widget opens the logger."
- Handoff (older, v3 wins): `Phone.dc.html` `is.widgets` (820 to 840),
  `phone-logic.js` `wg` (433 to 441): numbers when calories are on and not
  softened, the meal list otherwise.

## Where v3 and the handoff disagree, and what this builds

| Handoff | v3 | Built |
|---|---|---|
| Widget shows eaten and "N left" | Eaten and a bar with the range box, no "left" (the watch note: a "left" number is the evening nudge we promised not to send) | Eaten, bar and range box. No remaining. |
| Meals as pills with a tick | Four meal circles, tick when done, a dashed ring when not | Circles, as v3. |
| Watch face on the same page | Wear OS faces on the board | Not here. The watch is its own workstream; the More row says "Widgets" until it lands, then becomes v3's "Widgets and watch face". |

## Architecture

The visibility policy is applied in shared Kotlin and nowhere else. The
widgets render a `WidgetSnapshot` and nothing else: display-ready strings,
fractions and colours. A hidden number is absent from the snapshot, not
masked, so no platform code can show what the policy hid.

```
DiaryRepository.observeDay(today) ┐
SettingsRepository (known only)   ├─ RealWidgetSnapshotSource ─ WidgetSnapshotBuilder.build(...)
VisibilityPolicySource.policy     │      (+ WidgetCopy, resolved strings)
date ticks (midnight, foreground) ┘                │
                                                   ├─ WidgetRefresher (AutoInit, ungated) ─ WidgetPublisher
                                                   │       Android: SharedPreferences + GlanceAppWidget.updateAll
                                                   │       iOS:     WidgetBridge (Swift) → App Group UserDefaults + WidgetCenter.reloadAllTimelines
                                                   └─ WidgetsViewModel (the Widgets page preview)
```

### Modules
- `:libraries:widgets` (compose multiplatform): `WidgetSnapshot` (serializable),
  `WidgetSnapshotBuilder` (pure), `WidgetInputs`, `WidgetCopy`, `WidgetsEnabled`
  (the flag), `WidgetLinks` (the tap URL), `nextMeal`, `WidgetSnapshotSource`,
  `WidgetPublisher`, `WidgetOpens`, `WidgetStrings`, `WidgetCard` (the Compose
  lookalike of the widget, for the page, design sync and the leak test),
  `WidgetFixtures`. iOS: `WidgetBridge`, implemented in Swift.
- `:libraries:widgets:impl`: `RealWidgetSnapshotSource`, `WidgetRefresher`,
  `WidgetOpener`, `ComposeWidgetStrings`; Android `AndroidWidgetPublisher`,
  `OpsonWidget` (Glance UI), `OpsonWidgetReceiver`; iOS `IosWidgetPublisher`.
- iOS extension target `OpsonWidgets` in `apps/ios/OpsonWidgets/`, SwiftUI only.
  It does not link the Kotlin framework: it decodes the snapshot JSON.

### Snapshot

```kotlin
@Serializable data class WidgetSnapshot(
    val version: Int,
    val days: List<WidgetDay>,     // today, then tomorrow as an empty day
    val logLabel: String,          // "Log food", the + button's label
    val logLink: String,           // "opson://widget/log"
    val colors: WidgetColors,      // light and dark swatches from the app's theme
)
@Serializable data class WidgetDay(
    val day: String,               // ISO date this entry is for
    val title: String,             // "Today"
    val energy: WidgetEnergy?,     // only when calories are effectively Show on Surface.Widget
    val meals: List<WidgetMeal>,   // only when energy is null, meal groups are on and the day is tracked
    val note: String?,             // "Not tracking today" / "Tap + to log" when neither
)
@Serializable data class WidgetEnergy(
    val amount: String, val unit: String, val label: String,   // "1,245", "kcal", "1,245 kcal"
    val fraction: Float, val rangeStart: Float, val rangeEnd: Float,
)
@Serializable data class WidgetMeal(val slot: String, val label: String, val done: Boolean, val description: String)
```

`days` carries tomorrow so the widget turns over at midnight on its own (iOS
timeline entry at midnight, Android re-renders every 30 minutes and picks the
entry for the local date) even when the app is not running.

### Visibility surfaces

One number on a widget: calories eaten, on a new `Surface.Widget`.

| Calories level | Widget |
|---|---|
| Show | Numbers card: amount over unit, bar to 120% of the range top, the range box. Amount and unit from `ringValue`, fraction from `resolve`. |
| Soften (Fuel) | Meals card. The handoff shows meals for soft levels; a soft word on a home screen says nothing a meal tick doesn't. |
| Delay (Delayed, Coast) | Meals card, all day. **`Surface.Widget` never reveals.** A home screen is seen by whoever picks up the phone, and the reveal is a moment in the app (day close or 9 pm on Today). This also means the snapshot written at 3 pm can't be wrong at 9 pm. |
| Hide | Meals card. |

`ModePreset.today` plays no part: the handoff picks the card by the calories
level alone. No widget code asks which mode is on.

### Taps
- **+** opens the Logger. The link is `opson://widget/log` with no slot: the app
  picks the slot at tap time (`nextMeal`: the first meal not done from the
  time of day onward, else the Logger's own default), because a slot baked in
  at 9 am is wrong at 7 pm. iOS small widget: the whole widget is the link.
- The rest of the widget opens the app where it was.
- iOS: `onOpenURL` → `DeepLinkBridge` → `App.kt` asks `WidgetOpens.opened(url)`
  before the nav graph. Android: `MainActivity` hands `intent.dataString` to
  `WidgetOpens.opened` and clears it. `WidgetOpener` reads today's day and
  emits `LoggerRoute(slot, dayIso = today)`, which `App.kt` routes through the
  router (held until the NavHost binds, so a cold start lands).

### Refresh
`WidgetRefresher` is `AutoInit`. It does not read `release.widgets`: the iOS
extension ships in every build, so a person can place the widget with the flag
off, and a widget the app never wrote for says "Open Opson" for good. Once the
settings are known it collects the snapshot source and publishes each distinct
snapshot. Inputs that move it: today's day (any log, ate-no-details,
untracked), settings (mode, targets, meal groups), the policy, the date
(midnight timer and every foreground). When the settings go unknown (a user
change) it clears what it published. On Android, until the receiver ships, a
publish stores the JSON and the redraw finds no widget.

## Route (the Widgets page)
`WidgetsRoute`, `screen<>` in `:features:settings:impl`. Registered whatever
the flag says (a stale back stack lands rather than crashes); only the More
row links to it.

## State
```kotlin
data class WidgetsState(
    val snapshot: WidgetSnapshot? = null,   // null while loading
    val today: LocalDate? = null,           // the snapshot's own first day, which the preview draws
)
```
No `Display` in state: the snapshot is already policy output, built by
`WidgetSnapshotBuilder`.

## Actions
- `SnapshotArrived(snapshot)`: the source emitted.
- `BackTapped`: close.

## Events
`Close`.

## Data
`WidgetSnapshotSource.observe()`. Local only, works offline.

## Components
`SettingsPage`, `SettingsHeader`, `SectionLabel`, `Text` from `:libraries:ui`;
`WidgetCard` from `:libraries:widgets` (new, previewed there).

## More
`MoreRow.Widgets`, in the extras card after Fasting, only with
`release.widgets` on. Icon `AppIcons.Widgets`, no subtitle, no trailing text,
as v3.

## Copy (`strings_widgets.xml`)
| Key | Text |
|---|---|
| `widgets_title` | Widgets |
| `widgets_intro` | Widgets follow your mode, same as the app. (handoff) |
| `widgets_home_screen` | Home screen (handoff) |
| `widgets_how_to_add` | Touch and hold an empty spot on your home screen, then add Opson from the widget list. |
| `widgets_today` | Today |
| `widgets_log` | Log food |
| `widgets_meal_done` | %1$s, done |
| `widgets_meal_not_yet` | %1$s, not yet |
| `widgets_note_log` | Tap + to log |
| `widgets_note_untracked` | Not tracking today |

Android picker description and the no-snapshot fallback ("Open Opson to
start.") are Android string resources in `:libraries:widgets:impl`; iOS keeps
the same two lines in the extension.

## Edge cases
- No snapshot yet (flag never on, or cleared): "Open Opson to start." and a +.
- Meal groups off: no circles, "Tap + to log".
- Untracked day: no energy, no circles, "Not tracking today".
- Settings not known yet (first launch, before the store reads): nothing is
  published, as reminders do.
- Large text: the widget uses fixed sizes; the page preview follows the app.
- App not opened for days: the widget shows the last entry (an empty day).

## Platform changes (held back until 0.2, see `docs/plans/0.2/release-checklist.md`)
- Android: the `<receiver>` in `libraries/widgets/impl/src/androidMain/AndroidManifest.xml`
  and `res/xml/opson_widget_info.xml` plus the picker strings. A receiver
  shows in the launcher's picker even with the flag off, so these commits are
  held. The Glance UI and plumbing are inert without them.
- iOS: the `OpsonWidgets` extension target (bundle id `com.opson.Opson.Widgets`),
  `com.apple.security.application-groups` = `group.com.opson.Opson` on the app
  and the extension, the shared scheme, and the Fastfile's second profile.
  The Swift `IOSWidgetBridge` in the app target is main-safe: it needs no
  entitlement and only runs with the flag on.

## Tests
- `WidgetSnapshotBuilderTest`: Full shows the numbers card with the design's
  1,245 kcal and range; every preset that doesn't show calories gets no energy,
  and the JSON of its snapshot has no digit near "kcal" and no "N left";
  Fuel and Delayed after 9 pm still get meals; tomorrow is an empty day;
  groups off, untracked, colours are Slate's.
- `NextMealTest` (with the link parsing).
- `VisibilityPolicyRevealTest`: `Surface.Widget` never reveals a delayed number.
- `WidgetRefresherTest`: flag off publishes nothing and reads nothing; on
  publishes; a change republishes; on to off clears; unknown settings wait.
- `WidgetSourceAndOpenerTest`: the source rebuilds on a log, a mode or meal groups change and a new day, drops equal snapshots, waits for known settings; the log link emits a Logger open for the next meal.
- `WidgetsViewModelTest`, `MoreTest` (the row with the flag on and off).
- Leak test: `widget` (the snapshot through `WidgetCard`, both days, both
  sizes) and `widgets` (the page) in `SettingsLeakSurfaces`, both
  `showsCaloriesInFull`.
- Previews: `WidgetCard` Full, Blind, groups off, no snapshot, dark; the
  Widgets page light, dark, Blind; More with widgets; Glance previews on
  Android; SwiftUI `#Preview` for small and medium, Full and Blind.
