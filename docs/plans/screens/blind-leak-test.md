# Blind leak test plan

`BlindModeLeakTest` renders every surface in Blind and asserts that no calorie
amount and no "N left" reaches the screen. It is the acceptance test the
handoff asks for and it runs in CI. This plan covers where it lives, how it is
built, the ported `SURFACES` list, and the contract every screen meets so it
can be listed.

## Source
- Handoff: `README.md` section 1 "Acceptance test"; `docs/design/handoff/Friendly Tracking.dc.html` lines 140 to 160 (`SURFACES`, `LEAK`, `TAGGED`, `scan`).
- System design section 12; review checklist "Visibility".
- Pattern: `libraries/navigation/src/androidUnitTest/.../FloatingWindowHostTest.kt`, its `build.gradle.kts` (Robolectric offline staging), `docs/practices/testing.md` "Composition tests".

## Where it lives

New module `:apps:leaktest`, plugin `opson.kotlin.multiplatform` with the
Android target, `commonMain` empty, everything in `androidUnitTest`, package
`com.opson.leaktest`. Same shape as `:apps:integration`.

| Why this module | |
|---|---|
| It must see every feature's `impl` | Only `:apps:*` may depend on `*:impl`. |
| Not `:apps:integration` | That module needs Docker for its other tests; the leak test must run anywhere `testDebugUnitTest` runs. |
| Not `:apps:compose` | Keeps Robolectric and the test rule off the application module. |

Copy from `libraries/navigation/build.gradle.kts`, wholesale:
`testOptions.unitTests.isIncludeAndroidResources = true`, the
`robolectricAndroidAll` configuration, `stageRobolectricJars`,
`robolectric.offline`, `maxHeapSize = "2g"`. Dependencies: every
`:features:*:impl`, `:libraries:nutrition:ui`, `:libraries:ui`,
`:libraries:resources`, compose ui-test junit4 and manifest, robolectric,
kotlin-test-junit.

## Shared type

```kotlin
// :libraries:nutrition:ui, com.opson.libraries.nutrition.ui.leak
class LeakSurface(
    val id: String,                            // stable, kebab case, matches the list below
    val label: String,                         // the prototype's label where one exists
    val showsCaloriesInFull: Boolean,          // the control: Full must produce a hit
    val content: @Composable (policy: VisibilityPolicy) -> Unit,
)
```

Each feature impl exports its list from `commonMain`, next to its fixtures:
`TodayLeakSurfaces.all`, `LoggerLeakSurfaces.all`, `FoodsLeakSurfaces.all`,
`TrendsLeakSurfaces.all`, `SettingsLeakSurfaces.all`, `OnboardingLeakSurfaces.all`.
`:apps:leaktest` concatenates them into `SURFACES`.

## The test

```kotlin
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h4000dp")
class BlindModeLeakTest(private val surface: LeakSurface, private val mode: LeakMode) {
    @get:Rule val compose = createComposeRule()

    @Test fun showsNoCalorieAmount() {
        compose.setContent { LeakHarness(mode.policy()) { surface.content(it) } }
        assertNoTaggedMetrics()   // metric:calories, metric:remaining, metric:energyOut, unmerged tree
        assertNoLeakText()        // LEAK regex over the joined text
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in {1}")
        fun cases() = MODES_UNDER_TEST.flatMap { m -> SURFACES.map { arrayOf(it, m) } }  // minus whole-surface reveals
    }
}
```

| Piece | Detail |
|---|---|
| `MODES_UNDER_TEST` | Every preset whose calories level is not Show, derived from `Presets.all` (design sync 4), No numbers first: No numbers (Hide: nothing at all), Nutrients first (Soften: the calories tag may hold a word, never a digit), and Later each day and weekly (Delay, at noon before the reveal and before the weekly check: nothing apart from `REVEALED_BY_DESIGN`). `SurfacesCoverageTest` pins the set. |
| `REVEALED_BY_DESIGN` | What a delayed or soft mode shows on purpose, keyed by mode, each with its reason: Day close, a past day and the week views in Later (the whole surface is skipped in that mode only); Later weekly's last weekly check; the label typed into Add a food (the exact value only, so anything else on the surface still fails). Never anything in No numbers. A copy-from-yesterday offer resolves as the day it would be added to, so it is never on this list. |
| `LeakMode.policy()` | `VisibilityPolicy(settings, fixedClock, TimeZone.UTC)` with the clock at 2026-10-25 12:00, before the 9 pm reveal. |
| `NoNumbersLeakTest` | No numbers alone, every surface: no node tagged with any metric shows a digit, so not protein, weight, water or a share either. Amounts of food carry no tag and stay. |
| `LeakHarness` | `AppThemeProvider` (Pine, light, normal text) plus `CompositionLocalProvider(LocalVisibilityPolicy provides policy)`. If Compose resources fail to resolve under Robolectric, add `PreviewContextConfigurationEffect()` here; verify on the first run. |
| `assertNoTaggedMetrics` | `onAllNodes(hasTestTag(...), useUnmergedTree = true).assertCountEquals(0)` for each of the three tags. |
| `assertNoLeakText` | Walk the unmerged semantics tree depth first; collect `Text`, `EditableText` and `ContentDescription`; join with single spaces in tree order (so `Energy` `190` `kcal` reads `Energy 190 kcal`, like the prototype's `textContent` plus input values). Match `Regex("""\d[\d,.]*\s*(kcal|cal\b|calories)|\d[\d,]*\s*left\b""", IGNORE_CASE)`. The failure message names the surface, the preset, the matches and their nodes. |
| Tall window | `h4000dp` so lazy lists compose every fixture row. A leak below the fold is still a leak. |

Two companion tests in the same module:

- `LeakTestControlTest`: every surface with `showsCaloriesInFull = true`, rendered in **Full**, must produce at least one tagged node or regex hit. Proves the scan is not passing because it finds nothing anywhere (the prototype runs Full beside Blind for the same reason). Mutate one surface to render nothing and watch this go red before trusting it, per `testing.md`.
- `SurfacesCoverageTest`: `SURFACES.map { it.id }` contains every id in `REQUIRED_SURFACES` (the list below) and has no duplicates. Deleting a surface from a feature list fails the build instead of silently shrinking the test.

## Contract every listed screen meets

1. **Stateless screen.** `XScreen(state, onAction, modifier)` (or `XSheetContent` for a sheet, `XMenu` for a popover) is public in the impl's `commonMain` and needs nothing but its parameters: no ViewModel, no DI, no `NavController`, no repository.
2. **State from the real builder.** The surface's `content(policy)` builds its state with `XStateBuilder.build(XFixtures.inputs(), policy, ...)`. The same builder the ViewModel calls, so the test exercises the real `Display` mapping, not a hand-written one.
3. **Fixtures never construct a `Display`.** Enforced by the detekt rule `HardcodedDisplay`: any `Display.Shown(`, `Display.Soft(`, `RingValue(` or `MacroPart(` in a main source set outside `:libraries:nutrition` fails the build (test source sets may build expected values). The ring and macro types are in it because a hand-built `RingValue("1,245", "kcal")` leaks exactly like a hand-built `Display`. A fixture that hardcodes `Display.Shown("1,245 kcal")` would leak in Blind and make the test fail for the wrong reason, or a fixture with `Display.Hidden` would pass it for the wrong reason.
4. **Fixtures are realistic.** Inputs carry real calorie values (the prototype's `MEALS`, `BIG_DINNER`, `SAVED`, `FOODS` mocks are the source), so Full has something to show and the control test means something.
5. **Every number renders through `NutrientText`, `NutrientBar`, `NutrientRingLabel` or `MacroLine`**, which tag it. Text inputs expose their value as `EditableText` semantics.
6. **Everything composes at once.** Sheets render their content without the `ModalBottomSheet` popup. Pagers that hold numbers set `beyondViewportPageCount` so every page composes (the Today summary pager uses 2). Menus render their content directly.
7. **No infinite animation under `LocalInspectionMode`** (AGENTS.md landmine), or the rule waits forever for idle.
8. **A widget's snapshot is a surface too.** Widgets render a `WidgetSnapshot` built in shared
   Kotlin and nothing else, so a hidden number is absent from it rather than masked. `widget`
   renders that snapshot through `WidgetCard`, the Compose lookalike laid out as the Glance and
   SwiftUI widgets are, and `WidgetSnapshotBuilderTest` scans the snapshot's JSON in every preset
   that hides calories. `Surface.Widget` never reveals a delayed number, so Later, each day or
   weekly, needs no `REVEALED_BY_DESIGN` entry for it.
9. **Notification text is a surface too.** A notification never reaches a composable, so its
   words are listed through a lookalike: `reminder-notification` renders every reminder key in
   both tones from `ReminderCopy`, the same resources the platform posts. Notification copy
   takes no format arguments, so no number can reach it in any mode; one that ever needs a number
   resolves it through `VisibilityPolicy` and stays in this surface. Proved by breaking it once:
   a meal body changed to "About 640 kcal left" failed `reminder-notification` in all eight
   presets under test (2026-10-26; four modes since design sync 4).
10. **Vitamins and minerals hide with their own level.** Calories hidden is not the only case:
   micros show in Full numbers and hide in every other mode, and a person can turn them off
   in Full. `MicrosHiddenLeakTest` renders every surface in `MICROS_SURFACES` in each mode that
   hides micros and in Full with micros off, and asserts no node tagged `metric:micros`; its
   Full case is the control. `LeakTestControlTest` fails on a surface that draws micros in Full
   without being listed. Proved by breaking it once: the amount sheet's rows built without the
   policy failed `logger-amount-nutrients` in all eight presets and in Full with micros off
   (2026-10-27).

## SURFACES, ported

From `Friendly Tracking.dc.html`, in its order, then the additions.

| # | Prototype label | Id | Owner | Status | Calories in Full |
|---|---|---|---|---|---|
| 1 | Onboarding · pricing | `onboarding-welcome` | onboarding | Welcome and the plan (v3); Plus is "Coming later" with no price in 0.1 | no |
| 2 | Onboarding · mode preview | `onboarding-recommendation` | onboarding | ported; v3 adds `onboarding-statement` and `onboarding-account` next to it | no |
| 3 | Today | `today` | today | ported | yes |
| 4 | Today · big day | `today-big-day` | today | ported | yes |
| 5 | Quick-add sheet | `logger` | logger | ported | yes |
| 6 | Food search | `logger-search` | logger | search lives in the Logger | yes |
| 7 | Add-food sheet | `logger-amount` | logger | ported | yes |
| 8 | Photo estimate | `logger-photo` | logger | 0.2, `photo.md`; the review with the stepper open | yes |
| 9 | Describe a meal | `logger-describe` | logger | a pane of the Logger in v3 | yes |
| 10 | Saved meals | `logger-saved` | logger | a pane of the Logger in v3; copy yesterday is a chip | yes |
| 11 | Day close · big day | `day-close-big-day` | today | ported | yes |
| 12 | Trends (week history) | `trends` | trends | ported; with the watch's Expended card, so it shows energyOut | yes |
| 13 | Foods tab | `foods` | foods | ported | yes |
| 14 | Nutrients detail | `nutrients` | trends | ported | yes |
| 15 | Modes and numbers | `modes` | settings | ported | no |
| new | | `modes-end-after`, `modes-each-number` | settings | design sync 4, 13.5a; End after a set time open, and the Each number page | no |
| 16 | Reminders | `reminders` | settings | 0.2, behind `release.reminders` | no |
| 17 | Weight | `weight` | trends | ported | no |
| 18 | Integrations | `integrations` | settings | 0.2, `integrations.md`; shows energyOut | yes |
| 19 | Support | `support` | settings | ported | no |
| 20 | More | `more` | settings | ported | yes |
| 21 | Widgets and watch | `widget` | settings | 0.2, `widgets.md`, behind `release.widgets`: the home screen widget drawn from its snapshot (today and tomorrow, both sizes). The watch is still Later. | yes |
| 22 | Meal options | `meal-sheet` | today | ported | no |
| 23 | How it felt | none | | merged into the meal's Note (`meal-note`) | |
| 24 | Water options | `water-sheet` | today | ported | no |
| 25 | Water settings | `water-settings` | settings | ported | no |
| 26 | Calendar | `calendar` | today | ported | no |
| 27 | Day menu | `today-day-menu` | today | ported | yes (Today under it) |
| 28 | Add menu | none | | dropped in design v3 (no header +) | |
| 29 | Fasting | `fasting` | settings | 0.2, `fasting.md`; only Full numbers offers it, so No numbers renders "not part of this mode" | no |
| 30 | Logger with picked foods | `logger-picked` | logger | ported | yes |
| 31 | Create a meal | `foods-create-meal` | foods | ported (demo fixture) | yes |
| 32 | Custom food | `foods-add-food` | foods | v3 "Add a food", Calories field holds 120 | yes |
| 33 | Targets | `targets` | settings | ported | yes |
| new | | `today-groups-off`, `today-past`, `today-day-menu-armed` | today | | yes |
| new | | `today-future` | today | | yes (a planned day shows planned energy) |
| new | | `today-added`, `today-cards` | today | | `today-added` yes, `today-cards` no |
| new | | `day-close` | today | | yes |
| new | | `item-sheet` | today | | yes |
| new | | `day-note`, `meal-note` | today | | no |
| new | | `logger-search-empty` | logger | | no |
| new | | `nutrients-week` | trends | | yes |
| new | | `trends-settings` | trends | | no |
| new | | `trends-blind`, `trends-coast` | trends | design v3 | yes |
| new | | `patterns`, `patterns-blind` | trends | design v3 | `patterns` yes, `patterns-blind` no |
| new | | `pause-sheet` | settings | | no |
| new | | `look-and-feel` | settings | | no |
| new | | `foods-add-food-blind` | foods | v3 11.4; Add a food with no number fields | no |
| new | | `reminder-notification` | settings | 0.2, v3 7.2; every reminder's text, plain and warm, as the system shows it | no |
| new | | `today-timer-ended`, `today-timer-waiting` | today | design sync 4; End after a set time ran out, said once or offered behind the pause (mode names, never an amount) | yes |
| new | | `today-expended` | today | 0.2, `integrations.md`; the Expended so far line | yes |
| new | | `today-exercise` | today | 0.2, `integrations.md`; exercise in the range (All of it), energy out drawn | yes |
| new | | `today-burned` | today | `release.exercise`; Burned's parts and the day's exercise, left out where energy out is hidden | yes |
| new | | `today-burned-health` | today | `release.exercise`; the same with a health app, an entry the watch saw counting nothing | yes |
| new | | `today-summary`, `today-macros`, `today-sticky`, `today-collapsed`, `today-weight` | today | design sync 2, `release.todaySummary`; the stats row, the macros page, the sticky bar for every page, the collapsed line, the weight row | yes |
| new | | `today-select`, `today-select-remove`, `today-swipe-delete`, `today-delete-confirm` | today | design sync 2, `release.todaySelect`; the select panel's totals and its confirmation, a row swiped open and its confirmation | yes |
| new | | `today-cards-summary` | today | design sync 2; Edit Today's cards with the summary section | no |
| new | | `today-calendar`, `today-select-v4`, `today-drag-move`, `today-moved` | today | design sync 4, `today-actions.md`; the calendar over Today, select mode's bottom bar, foods mid-drag over dinner, the Undo after a move | yes |
| new | | `food-actions`, `food-actions-amount` | today | design sync 4, `release.foodActions`; a food's ⋮ over Today (its energy through the policy), and Change amount | `food-actions` yes |
| new | | `edit-a-copy` | foods | design sync 4, 13.3e; only numbers the mode shows in full, the Calories field and its "Was" tagged | yes |
| new | | `day-close-check-in` | today | design sync 2, `release.checkIn`; the check-in and the watch's energy | yes |
| new | | `integrations-leave-out` | settings | 0.2, `integrations.md`; the Leave out a day sheet, dates only | no |
| new | | `logger-describe-voice` | logger | 0.2, `voice.md`; Describe it while listening, over lines already read | yes |
| new | | `logger-photo-swap` | logger | 0.2, `photo.md`; swapping a line's food | yes |
| new | | `logger-uncategorized` | logger | design sync 2, 3.1b; the nav + with no usual meal, "Usual around" | yes |
| new | | `logger-amount-units` | logger | design sync 2, 3.3b; the unit menu open over the amount sheet | yes |
| new | | `logger-scan-amount` | logger | 0.2, barcode; the amount sheet after a scan | yes |
| new | | `logger-amount-nutrients` | logger | the amount sheet with All nutrients open, vitamins and minerals as a percent of a day | yes |
| new | | `logger-scan-miss` | logger | 0.2, barcode; a scan the catalog doesn't know | yes |
| new | | `logger-photo-start` | logger | 0.2, `photo.md`; the note and the two ways to a photo | no |
| new | | `submit-food-photos`, `submit-food-by-hand` | logger | 0.2, `release.submitFood`; Add this food's two photos, and the form typed in when the label could not be read | no |
| new | | `submit-food-review` | logger | 0.2, `release.submitFood`; the label as read, editable, with the energy check. Numbers and checks only where the mode shows calories; elsewhere a sentence says whether they add up | yes |
| new | | `exercise-sheet` | logger | `release.exercise`; the estimate is energy out | yes |
| new | | `exercise-sheet-other` | logger | `release.exercise`; Other's typed number, only where energy out shows | yes |
| new | | `exercise-sheet-pick` | logger | `release.exercise`; the activity list, no numbers | no |
| new | | `widgets` | settings | 0.2, `widgets.md`; the Widgets page under More, which previews the person's own widget | yes |
| new | | `logger-search-tabs` | logger | 0.2, `search-options.md`; tabs, the options button and source badges over results | yes |
| new | | `logger-search-options` | logger | 0.2, `search-options.md`; the options sheet: sort, database, tabs | no |
| new | | `targets-exact` | settings | 0.2, `release.goalsAndEnergy`; one number and its stepper | yes |
| new | | `targets-surplus` | settings | 0.2, `release.goalsAndEnergy`; the surplus, the burn and the goal it comes to | yes |
| new | | `targets-by-day-shapes` | settings | 0.2, `release.goalsAndEnergy`; a day group as one number, as a surplus, and as a deficit | yes |
| new | | `targets-deficit-estimated` | settings | 0.2, `release.goalsAndEnergy`; a deficit against Burned with no health app (resting plus everyday activity), its choices open | yes |
| new | | `targets-macros` | settings | 0.2; the macro editor in percents and in grams, presets, and what doesn't add up | yes |
| new | | `resting-energy` | settings | design sync 4, `release.goalsAndEnergy`; resting energy from each source, energy out only | no |
| new | | `energy-today` | settings | design sync 4, `release.goalsAndEnergy`; eaten with its ±, Burned and the deficit so far | yes |
| new | | `today-summary-in-out` | today | design sync 4; the card's default preset with a set deficit's target line | yes |
| new | | `today-summary-range-bar` | today | design sync 4; one bar, Burned as a tick, meals count on | yes |
| new | | `today-summary-rings` | today | design sync 4; one number as rings | yes |
| new | | `today-summary-equation` | today | design sync 4; Eaten − Burned = Deficit | yes |
| new | | `today-screen-settings` | settings | design sync 4; More › Today screen and its main number sheet, no amounts | no |
| new | | `onboarding-goal` | onboarding | `onboarding-v2.md`, `release.onboardingSetup`; goal styles and the range on a stepper | yes |
| new | | `onboarding-resting-energy` | onboarding | `onboarding-v2.md`; resting energy's details and the estimate they give, energy out | yes |
| new | | `onboarding-meal-times` | onboarding | `onboarding-v2.md`; when meals usually happen, no amounts | no |

`REQUIRED_SURFACES` is every id in this table. When a 0.2 screen ships
(Reminders, Integrations, Fasting, barcode result), its plan adds a row here and
an entry to its feature's list in the same PR.

## Tests about the test
- Break it on purpose once, in the PR that lands it: render `Display.Shown("1,245 kcal")` with the calories tag in one Blind fixture and confirm `BlindModeLeakTest` fails naming that surface; remove a surface id and confirm `SurfacesCoverageTest` fails; make one Full fixture empty and confirm `LeakTestControlTest` fails. Revert all three. Note the result in the PR description.
- Runtime budget: about 90 surfaces × 4 modes, No numbers again for every tag, plus the control. If the sweep passes 2 minutes, split the parameter list across two classes so Gradle can run them in parallel forks.
