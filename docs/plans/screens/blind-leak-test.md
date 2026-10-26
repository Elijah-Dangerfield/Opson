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
class BlindModeLeakTest(private val surface: LeakSurface, private val preset: PresetId) {
    @get:Rule val compose = createComposeRule()

    @Test fun showsNoCalorieAmount() {
        compose.setContent { LeakHarness(policyFor(preset)) { surface.content(it) } }
        assertNoTaggedMetrics()   // metric:calories, metric:remaining, metric:energyOut, unmerged tree
        assertNoLeakText()        // LEAK regex over the joined text
    }

    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name = "{0} in {1}")
        fun cases() = SURFACES.flatMap { s -> HIDDEN_PRESETS.map { arrayOf(s, it) } }
    }
}
```

| Piece | Detail |
|---|---|
| `HIDDEN_PRESETS` | Blind, plus every preset whose calories, remaining and energyOut are all Hide: Nutrient, Rhythm, Explorer, Mindful. Blind is the requirement; the others cost a few seconds and cover the Rhythm and Explorer layouts. Fuel (calories Soft) and Delayed / Coast (Delay) are not in this set: a soft word legitimately carries the `metric:calories` tag. |
| `policyFor(preset)` | `VisibilityPolicy(NumbersSettings(preset), fixedClock, TimeZone.UTC)` with the clock at 2026-10-25 12:00, before the 9 pm reveal. |
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

## SURFACES, ported

From `Friendly Tracking.dc.html`, in its order, then the additions.

| # | Prototype label | Id | Owner | Status | Calories in Full |
|---|---|---|---|---|---|
| 1 | Onboarding · pricing | `onboarding-welcome` | onboarding | Pricing not built in 0.1; the welcome takes its place | no |
| 2 | Onboarding · mode preview | `onboarding-recommendation` | onboarding | ported | no |
| 3 | Today | `today` | today | ported | yes |
| 4 | Today · big day | `today-big-day` | today | ported | yes |
| 5 | Quick-add sheet | `logger` | logger | ported | yes |
| 6 | Food search | `logger-results` | logger | search lives in the Logger | yes |
| 7 | Add-food sheet | `amount-sheet` | logger | ported | yes |
| 8 | Photo estimate | none | | dropped, Photo is Later | |
| 9 | Describe a meal | `log-describe-parsed` | logger | ported | yes |
| 10 | Saved meals and copy yesterday | `log-saved` | logger | ported | yes |
| 11 | Day close · big day | `day-close-big-day` | today | ported | yes |
| 12 | Trends (week history) | `trends` | trends | ported | yes |
| 13 | Foods tab | `foods` | foods | ported | yes |
| 14 | Nutrients detail | `nutrients` | trends | ported | yes |
| 15 | Modes and numbers | `modes` | settings | ported | no |
| 16 | Reminders | none | | 0.2, add when built | |
| 17 | Weight | `weight` | trends | ported | no |
| 18 | Integrations | none | | 0.2, add when built (it shows energyOut) | |
| 19 | Support | `support` | settings | ported | no |
| 20 | More | `more` | settings | ported | yes |
| 21 | Widgets and watch | none | | Later | |
| 22 | Meal options | `meal-sheet` | today | ported | no |
| 23 | How it felt | `meal-feel` | today | ported | no |
| 24 | Water options | `water-sheet` | today | ported | no |
| 25 | Water settings | `water-settings` | settings | ported | no |
| 26 | Calendar | `calendar` | today | ported | no |
| 27 | Day menu | `today-day-menu` | today | ported | yes (Today under it) |
| 28 | Add menu | `today-add-menu` | today | ported | yes (Today under it) |
| 29 | Fasting | none | | 0.2, add when built | |
| 30 | Logger with picked foods | `logger-picked` | logger | ported | yes |
| 31 | Create a meal | `create-meal` | foods | ported (demo fixture) | yes |
| 32 | Custom food | `custom-food` | foods | ported, Energy field holds 190 | yes |
| 33 | Targets | `targets` | settings | ported | yes |
| new | | `today-groups-off`, `today-past`, `today-day-menu-armed` | today | | yes |
| new | | `today-future` | today | | no |
| new | | `day-close` | today | | yes |
| new | | `item-sheet` | today | | yes |
| new | | `day-note`, `meal-note` | today | | no |
| new | | `nutrients-week` | trends | | yes |
| new | | `trends-settings` | trends | | no |
| new | | `pause-sheet` | settings | | no |
| new | | `look-and-feel` | settings | | no |

`REQUIRED_SURFACES` is every id in this table. When a 0.2 screen ships
(Reminders, Integrations, Fasting, barcode result), its plan adds a row here and
an entry to its feature's list in the same PR.

## Tests about the test
- Break it on purpose once, in the PR that lands it: render `Display.Shown("1,245 kcal")` with the calories tag in one Blind fixture and confirm `BlindModeLeakTest` fails naming that surface; remove a surface id and confirm `SurfacesCoverageTest` fails; make one Full fixture empty and confirm `LeakTestControlTest` fails. Revert all three. Note the result in the PR description.
- Runtime budget: about 40 surfaces × 5 presets plus the control. If the sweep passes 2 minutes, split the parameter list across two classes so Gradle can run them in parallel forks.
