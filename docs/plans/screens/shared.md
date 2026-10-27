# Shared decisions for the screen plans

Every screen plan in this folder leans on the pieces below. Build these first,
or alongside the first screen that needs them. Each one lands with its tests,
and anything that changes `docs/system-design.md` updates it in the same
commit.

## Module map changes

| Change | Why |
|---|---|
| New `:libraries:diary:ui` (compose.multiplatform), package `com.opson.libraries.diary.ui` | Today, Logger, Foods and Trends all draw meal slots, item rows and food rows. Feature impls cannot share code, so it goes in a library. Depends on `:libraries:diary`, `:libraries:nutrition:ui`, `:libraries:ui`. |
| New `:apps:leaktest` (kotlin.multiplatform, Android library, `androidUnitTest` only) | Hosts `BlindModeLeakTest`. Only `:apps:*` may depend on `*:impl`, and keeping it out of `:apps:integration` means the leak test never needs Docker. See `blind-leak-test.md`. |
| Shell lives in `:apps:compose` (`com.opson.shell`), not `:features:home` | The bottom bar and the root `LocalVisibilityPolicy` wrap the `NavHost`, which only `App.kt` owns. `:features:home` keeps Feedback and Bug report and loses `HomeRoute`/`HomeScreen`. Update system design section 3 row for `:features:home`. |
| Feature api modules: `:features:today`, `:features:logger`, `:features:foods`, `:features:trends`, `:features:settings` (+ `impl`) | As system design section 3. Impls depend on other features' api modules for routes (for example today impl depends on logger, foods, trends and settings api). |

## Conventions every plan follows

- **Route args are primitives or `@Serializable` enums.** A day is `dayIso: String`
  (`LocalDate.toString()`, parsed in the entry point). `MealSlot` is a
  `@Serializable` enum, so every registration that takes one passes
  `typeMap = mapOf(typeOf<MealSlot>() to serializableType<MealSlot>())`. Same for
  any other enum arg. Routes are `class`, never `data object`.
- **State is built by a pure function.** Each screen has `XStateBuilder.build(inputs, policy, ...)`
  in its impl `commonMain`. The ViewModel collects inputs, sends one
  `XInputsArrived(inputs)` action, and the handler calls the builder. Fixtures for
  previews and the leak test call the same builder with fake inputs. This is what
  makes `BlindModeLeakTest` test the real mapping.
- **Nutrition numbers in state are `Display`** (or `MacroPart` / `RingValue`, below),
  resolved in the builder with the right `Surface` and `FormatContext(day, dayClosed)`.
  Fill fractions come from `Display.fraction`. Composables never do arithmetic on
  a nutrient.
- **Toasts are events.** `ShowToast(message: StringResource, args: List<Any> = emptyList())`,
  rendered by the entry point with `showSnackBar`.
- **Tone is resolved in the builder.** `Settings.tone` picks between a plain and a
  warm `StringResource`; state carries the chosen one. Warm keys end in `_warm`.
- **Strings live in `:libraries:resources`**, prefixed by feature: `today_`,
  `logger_`, `foods_`, `trends_`, `settings_`, `onboarding_`, `shell_`.
- **Meal names in sentences are lower case**, as the prototype does
  (`Added to dinner.`). `MealSlot.sentenceName` resource per slot.
- **Sheet bodies are their own composable.** `XSheetContent(state, onAction)` is
  separate from the `BottomSheet` wrapper so previews and the leak test render it
  without a popup window.
- **Telemetry never carries amounts, answers or free text.** Ids, counts, preset
  keys and method names only.

## Domain additions, `:libraries:nutrition` (pure, unit tested)

Built. Everything that turns a number into text is an extension on
`VisibilityPolicy` (`policy.ringValue(...)`), so it reads the same level and
formatter as `resolve` and needs `import com.opson.libraries.nutrition.<name>`.

| Addition | Shape | Used by |
|---|---|---|
| `NutrientAmounts` | `data class NutrientAmounts(kcal: Double?, protein, carbs, fat, fiber)` (macros default 0), `plus`, `times(factor)`, `of(metric)`, `Iterable<NutrientAmounts>.total()`, `NutrientAmounts.None`. A null `kcal` (name-only item) adds nothing, and a total is null only when nothing in it had energy, so a meal of name-only items resolves Hidden rather than "0 kcal". | Every screen with a total |
| `GoalStyle` on `FormatContext` | `FormatContext(goal = GoalStyle.X)`, `enum class GoalStyle { Default, None, Of, Slash }`. `Default` keeps today's behaviour (macros print "of" when `of` is set, energy does not). `None`: `47 g`. `Of`: `47 of 120 g`, `1,245 kcal of 2,305`. `Slash`: `47 / 120 g`, `1,245 / 2,305`. `of` still drives soften words and `fraction` in every style. Water and micros ignore it. | Today grid, logger strip, nutrients detail |
| `resolveRange` | `policy.resolveRange(metric, min, max, surface, context): Display`, energy metrics only. Shown only at effective `Show`: `1,900 to 2,300` bare, `1,900 to 2,300 kcal` with unit. Hidden at every other level (a range has no soft word). | Day close, Targets, Modes |
| `macroLine` | `policy.macroLine(amounts, surface, context, style): List<MacroPart>`, `data class MacroPart(metric, text)`, `enum MacroLineStyle { Long, Compact }`. Protein, carbs, fat in that order (fiber is never in it, as the prototype). Only macros at effective `Show` appear. `Long`: `47 g protein`. `Compact`: `1P`, `27C`, `0F`. | Meal cards, food rows, Create a meal |
| `ringValue` | `policy.ringValue(metric, value, surface, context): RingValue?`, `data class RingValue(value, unit)`. Calories, energy out, remaining and the macros. Null unless effective `Show`. Calories `1,245`/`kcal`. Remaining: `1,060`/`kcal` below range, `On`/`target` in range, `Past`/`range` above, never `0` or negative even with no range in the context. `policy.balanceRingValue(eaten, energyOut, ...)` gives `−950`/`kcal` and carries `TODO(clinician)` (0.2). | Today rings |
| `share` | `policy.share(metric, percent, surface, context): Display`, macros only. `30%`, shown only when the macro is at effective `Show`. | Targets |
| `SummaryRingMode` | `@Serializable enum { Target, Remaining, Balance }`; `policy.availableRingModes(surface, hasEnergyOut): List<SummaryRingMode>`: Target always, Remaining when `level(Remaining) == Show`, Balance when energy out is effectively `Show` and `hasEnergyOut` (0.1 passes false). `mode.orTargetIn(available)` for a saved mode that is no longer offered, `mode.nextIn(available)` for the cycle button. | Today |
| `NumbersChange` | `@Serializable sealed interface NumbersChange { PickPreset(id), Override(metric, level), ClearOverride(metric), TurnOffPause }`, `change.applyTo(settings, at)` (stamps `changedAt`, TurnOffPause included), `change.needsPause(current)`: only with the pause on, and then for any raise or for `TurnOffPause`. | Modes, water toggles, weight, nutrients, Today scores |
| `Targets.raise/lower(scaleMacros)` | Ratio mode scales protein, carbs and fat with the energy target (prototype `GOAL[k] * baseT / GOAL.target`); gram mode leaves them; fiber never scales. Scaling runs from `Targets.ratioBasis: MacroSplit?`, a fixed point, because scaling each step from the last step's rounded grams drifts (3 g of fat in ten taps). Any non-scaling change clears it. Floor rule unchanged. | Targets |
| `Micronutrient` and `NutritionScores` | Enum of the 22 micros (the prototype `MICROS` table's 13, plus the B vitamins, choline, copper, manganese, phosphorus and selenium the catalogs carry, at FDA Daily Values) with `group` (`MicroGroup.Vitamins/Minerals`), `reference`, `unit` (`MicroUnit.Mg/Ug`), `isLimit` (sodium) and `percentOf(amount)`. `formatter.microReference(micro)`: `700 µg`, `2.4 µg`, `2,300 mg limit`. `NutritionScores.percents(entryMicros)` sums and divides by the reference; `NutritionScores.compute(percentByMicro): Map<NutritionScore, Double>` gives the six scores in display order (`NutritionScore.AllTargets, Vitamins, Minerals, BoneHealth, ImmuneSupport, EnergyAndMetabolism`), each the mean of percents capped at 100, missing counting as 0. Resolve as `Metric.Micros` with `of = 100`. | Today page 3, Nutrients detail |
| `WaterProgress` | `WaterProgress.glasses(ml, glassMl, goalGlasses): WaterGlasses(filled, total)`, filled capped at the goal. `WaterProgress.glassSize(ml, unit)` (`250 ml`, `2,000 ml`, `8 fl oz`) over `formatter.glassSize`. | Today water row, water sheet |

Surface mapping from the prototype's surface strings: `today` to `Today`;
`search`, `quickSheet`, `addSheet`, `quick`, `saved`, `photo` to `Logger`;
`dayclose` to `DayClose`; `week` to `Week`; `nutrients` to `Nutrients`;
`foods`, `builder` to `Foods`; `modes`, `targets`, `integrations` to `Settings`;
`weight` to `Weight`.

## `:libraries:diary` additions

| Addition | Shape |
|---|---|
| `MealSlot` | `@Serializable enum { Breakfast, Lunch, Snack, Dinner, Uncategorized }`, `meals` (the four), `defaultFor(time: LocalTime)`: before 10:30 Breakfast, before 14:30 Lunch, before 17:00 Snack, else Dinner. With meal groups off the answer is always `Uncategorized`. |
| `Day` helpers | `items(slot)`, `isMealDone(slot)` (items or ate-no-details), `doneMealCount`, `totals(slot?)`, `isClosed(today)` (closed_at set or day in the past), `formatContext(of, bare, goal)`. |
| `Settings` | The persisted object, fields below. `SettingsRepository.observe(): Flow<Settings>`, `update(transform)`, `isKnown: StateFlow<Boolean>`. It is a `UserScopedClearer`: a user change resets it to defaults and sets `isKnown` false until the new user's first pull lands (a fresh install that has never signed in is known from the start). |
| `VisibilityPolicySource` | `val policy: StateFlow<VisibilityPolicy>`. Rebuilds on settings change and re-emits at 21:00 and at midnight local, so a delayed number opens at 9 pm without a settings change. Emits a Blind policy while `SettingsRepository.isKnown` is false (signed into an existing account and the first pull has not landed), so a new phone never flashes Full numbers before the user's own mode arrives. |
| `NumbersChanger` | `suspend fun request(change): NumbersOutcome` with `Applied(fewer: Boolean)` or `NeedsPause`; `suspend fun confirm(change)` for the pause sheet; `turnOnPause()`; `pauseShown(change)` / `pauseKept(change)` for the sheet's telemetry. Lowering always applies at once; any raise with the pause on, and turning the pause off, returns `NeedsPause` and writes nothing. Stamps `changedAt`, sets `sprintStartedOn` when Sprint is picked, emits `mode.changed`, `friction.shown` and `friction.completed`. A screen that gets `NeedsPause` opens `PauseBeforeMoreRoute` via `change.toRoute()` from the settings api. |
| `SelectedDay` | `StateFlow<LocalDate>` plus `select(day)`. Today writes it; the center + reads it when `LoggerRoute.dayIso` is null. Snaps back to the new date at midnight if it was on the old today. |

### `Settings` fields

| Field | Type | Default | Notes |
|---|---|---|---|
| `numbers` | `NumbersSettings` | Full, no overrides, pause off, `changedAt` null | Onboarding writes the preset with `changedAt` null, so Modes says "Set by you during setup." |
| `sprintStartedOn` | `LocalDate?` | null | Set when Sprint is picked. Drives "day N of 14". |
| `targets` | `Targets` | `Targets.Default` | |
| `macroMode` | `MacroMode { Ratios, Grams }` | Ratios | |
| `exercise` | `ExerciseAdjustment` | Some | No effect in 0.1 (no watch data). |
| `ringMode` | `SummaryRingMode` | Target | |
| `trendsRange` | `TrendsRange { Week, FourWeeks, ThreeMonths }` | Week | |
| `trendsCards` | `TrendsCards(intake, recap, nutrients, expended, weight)` | all true | |
| `weekStart` | `DayOfWeek` (Mon, Sat or Sun) | Monday | |
| `waterGlasses` / `glassMl` / `waterUnit` | `Int` / `Int` / `WaterUnit` | 8 / 250 / Ml | |
| `weightUnit` | `WeightUnit` | Kg | |
| `mealGroups` | `Boolean` | true | Global, as the prototype. `day_records.meal_groups` stays null in 0.1. |
| `askTags` | `Boolean` | true | Hunger and fullness chips on the amount sheet. |
| `tone` | `Tone { Plain, Warm }` | Plain | |
| `bigDayEvent` | `EventLevel { Off, Subtle, Full }` | Full | No UI in 0.1 (Reminders is 0.2), so the default holds. |
| `theme` / `textSize` | `ThemeMode { System, Light, Dark }` / `TextSize` | System / Normal | `theme` is Appearance. 0.1 also wrote `palette`; 0.2 no longer reads or writes it (design sync 4, Slate only). |
| `onboarding` | `OnboardingAnswers(why, feel, notice, hard)` | empty | The setup answers as option keys, written with the preset when onboarding finishes. Never in telemetry. |
| 0.2 | `fasting`, `fastToday`, `fastPlan`, `healthToday`, `healthRows`, `healthOpen`, `quiet`, `mealReminders`, `waterReminders`, other `events` | as prototype `DEFAULTS` | Declared now so the serializer default exists, no UI. |

## `:libraries:nutrition:ui` additions

Built.

- `NutrientRingLabel(metric, value: RingValue?, modifier, valueColor, unitColor)`: value over unit on the hero colours, the column tagged `metric:<key>`, renders nothing when null.
- `MacroLine(parts: List<MacroPart>, modifier, separator, typography, color)`: each part a tagged `Text` in a `FlowRow`, joined with `MacroLineStyle.Long.separator` (` · `, the default) or `MacroLineStyle.Compact.separator` (a space). Renders nothing for an empty list.
- `LeakSurface(id, label, showsCaloriesInFull, content: @Composable (VisibilityPolicy) -> Unit)` in `com.opson.libraries.nutrition.ui.leak` (see `blind-leak-test.md`). `toString()` is the id, for parameterized test names.

## `:libraries:diary:ui` contents

| Component | Notes |
|---|---|
| `MealSlot.icon`, `MealSlot.tileColors()` | Breakfast carbs, Lunch fat, Snack fiber, Dinner protein, Uncategorized muted on soft. Icons `bakery_dining`, `lunch_dining`, `nutrition`, `dinner_dining`, `format_list_bulleted`. |
| `MealSlot.label`, `sentenceName`, `Precision.rowLabel` (Weighed / Estimate / Named), `Precision.pickerLabel` (Weighed / Estimated / Just the name), `Hunger`, `Fullness`, `Mood` labels | String resources (`strings_diary.xml`, `diary_` prefix). `MealSlot.tone` maps to the design system's `MealTone`. |
| `DiaryItemRow(name, sub, calories: Display, onClick: (() -> Unit)?, showDivider = true)` | Today and Create a meal rows. |
| `FoodPickRow(name, amount, macros: List<MacroPart>, calories: Display, picked, onOpen, onToggle, iconSeed = name, showDivider = true)` | Logger, Foods. The + toggles to a check; content description "Pick {name}" / "Remove {name}". Pass the food's key as `iconSeed`. |
| `FoodIconTile(seed)`, `foodTone(seed)` | The catalog has no category, so the tile is a fork-and-knife icon tinted by a stable hash of the seed (a `FoodRef.key`) over the four nutrient tints. |
| `FavoriteFoodTile(name, picked, onToggle, fullName, iconSeed)` | The logger's round Favorites tile with the + / check badge. |
| `NutrientTotalColumn(metric, label, display)`, `MealCheckChip(slot, done)` | A totals strip column (amount, bar, label; nothing when hidden) and the meal check pill shown instead of totals. |
| `NutrientTile(metric, label, display)` | One number on a surface tile (the amount sheet's Energy, Protein, Fiber, Calcium). |
| `FoodRowsCard { }`, `FoodRowsNote(text)`, `SectionLabel(text)` | The radius-20 card food rows sit in inside a sheet, a muted line inside it, and the bold muted section heading. |

## `:libraries:ui` beyond the parallel component set

The parallel build covers EdgeCard, IconTile, PillChip, SegmentedControl,
AppSwitch, RadioRow, Stepper, SegmentedBar, FillBar, ProgressRing, PagerDots,
HeroBlock, CenterAddButton, BottomNavBar, MenuPopover, SheetHeader, ListRow,
buttons and AppIcons. The screens also need the rows below, all built, each with
a preview. Components take strings and fractions; none formats a nutrition number.

| Component | Shape | For |
|---|---|---|
| `BottomSheet(heightFraction: Float)` | Logger at 0.93. Pins height for the same anchor reason as `scrollableContent`, and lets content own a pinned header, a `LazyColumn` and a pinned footer. The `ScrollInsideBottomSheet` detekt rule reports a `LazyColumn` / `LazyVerticalGrid` in a `BottomSheet` without a named `heightFraction`, and still reports `verticalScroll` with or without it. | Logger |
| `ProgressRing(segments: List<RingSegment>)` | The Eaten ring is split three ways. | Today |
| `MonthGrid(weekdayLabels, cells: List<MonthGridCell?>, onDayClick: (LocalDate) -> Unit)` | `MonthGridCell(date, contentDescription, selected, isToday, enabled, marked)`; null is a leading blank and the caller lays out the month. Selected accent circle, today accent ring, `marked` a `c-fat` dot, disabled in `line` and not clickable. | Calendar sheet |
| `IntakeBarChart(bars: List<IntakeBar>, band: ClosedFloatingPointRange<Float>?)` | `IntakeBar(label, fraction: Float?, isCurrent)`; a null fraction is a gap. Not built on the template `BarChart`: that draws a numeric axis from a value formatter, which on a calorie chart is a calorie amount. | Trends |
| `TrendLine(points: List<Float>, dots: List<Float>?)` | Heights in `0..1`, evenly spaced; a smoothed curve that never overshoots its points, dots behind it. Default size is the handoff's 310 by 130. | Trends, Weight |
| `TextArea`, `MaskedTextField(value, onValueChange, masked, ...)`, `NumberField(value, onValueChange, label?, unit?, allowDecimal, textAlign, ...)` | All on foundation `BasicTextField`, so the value is `EditableText` semantics and the leak test scans it (`TextFieldSemanticsTest` proves it, including that a masked value is not in the tree and the field is marked as a password). `NumberField` runs typed text through `NumberInput.sanitize(raw, allowDecimal)` (digits and one point, comma counts as a point), which a ViewModel can call too. | Notes, masked weigh-in, custom amounts |
| `Banner(text)` | Soft fill, radius 14, secondary text. Information only. | Preset banners on Today |
| `RelativeDay` and `DateCopy` (`com.opson.libraries.ui.time`) | `sealed interface RelativeDay { Today, Yesterday, Tomorrow, Date(date) }`, `RelativeDay.of(day, today)`. `DateCopy.label(day)` gives `Today` / `Yesterday` / `Tomorrow` / `Thu, Oct 22`; also `monthDay` (`Oct 22`), `longMonthDay` (`October 25`), `longWeekdayMonthDay` (`Sunday, Oct 25`), `monthYear` (`October 2026`), `range` (`Oct 19 to 25`), `timestamp(LocalDateTime)` (`Oct 25, 9:41 PM`). English spelled by hand, pure, unit tested. A screen whose copy table names a resource for Today/Yesterday/Tomorrow can map those three itself. | Day labels, back labels, Modes note |

## `:libraries:navigation` additions

- Port Cards' `TabRoute`, `Router.switchTab`, `enterTab`, `tabReselects`,
  `notifyTabReselected` and the `DelegatingRouter` implementation (see `shell.md`).
- Built: `Router.shareFile(name, mimeType, bytes)` on a `FileShareLauncher` seam.
  Android: `AndroidFileShareLauncher` writes `cache/shared/<name>` and sends
  `ACTION_SEND` through the chooser with a `content://` URI from
  `ShareFileProvider` (authority `${applicationId}.fileshare`, paths in
  `res/xml/file_share_paths.xml`). iOS: `IosFileShareLauncher` writes
  `tmp/shared/<name>` in Kotlin and calls the Swift `FileSharePresenter`
  (`IOSFileSharePresenter`, `UIActivityViewController`), passed in through
  `IosAppComponent`'s `create(...)`. Swift gets a path, not the bytes. No JVM
  target exists, so there is no JVM impl. Support needs it for the CSV export.

## 0.1 scope gates

Hidden, not "Coming soon", per system design section 13.

| Thing | Release | Where it would appear |
|---|---|---|
| Barcode scan | 0.2 | Logger search button, Add menu, Meal sheet |
| Photo | Later | Logger chip, Add menu, Meal sheet, Log screen tab, custom food "Photograph the label" |
| Voice log | 0.2 | Meal sheet, meal card mic icon |
| Fasting | 0.2 | Add menu, Day menu, Today card, More |
| Reminders and celebrations | 0.2 | More, Rhythm "Change meal times", water reminders row |
| Watch and health apps, Health card, Expended ring and card, Balance | 0.2 | Today, Trends, More, Targets exercise row |
| Widgets | Later | More |
| Trusted person and clinician sharing | Later, shown as "Coming later" | Support (the handoff itself says "Coming later") |

## Cross-cutting conflicts resolved

| Handoff says | Resolution | Reason |
|---|---|---|
| Remaining ring value is raw calories with "On"/"Past" (prototype `ringThree`) | Goes through `ringValue(Metric.Remaining, ...)` and is offered only when Remaining is `Show` | No screen formats a number; "never 0 left" lives in the policy. |
| COMPETITIVE_REVIEW leaves out Remaining and Balance; README section 4.1 adds both | Remaining kept behind its own metric; Balance not built in 0.1 | README is the later spec and Remaining is policy gated. Balance needs watch data (0.2) and carries `TODO(clinician)`. |
| Eaten ring split by protein, carbs and fat whenever calories show | Split only when all three macros are effectively `Show`; otherwise one solid arc | `HIDE` means nothing renders, and a split leaks the hidden macro's share. |
| "Note" and "Add a note for the day" open Day close | Open a Day note sheet | Day close's Done closes the day, and adding a note must not change anything else. |
| "Stop asking these" toast points to Reminders | Points to More, under Look and feel, where the switch lives in 0.1 | Reminders is 0.2; copy must not name a screen that does not exist. |
| Water "Show" toggles set water to `Show` | Clear the water override when the preset's own level is not `Hide`, else `Show` | A toggle should never show more than the mode's own default. |
| No item delete or edit anywhere | Item sheet with "Remove from {meal}" | A reviewer needs to undo a wrong entry. |
