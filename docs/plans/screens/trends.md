# Trends plan

Trends and its settings sheet, Nutrients detail (Day and Week average), and
Weight. Read `shared.md` first.

## Source
- Handoff: `README.md` sections 4.6, 4.7, 4.8; `PROTOTYPE_NOTES.md` "Week view"; `COMPETITIVE_REVIEW.md` (weight change readouts left out).
- Prototype markup: `Phone.dc.html` `is.week` (404 to 454), `sheet.trends` (907), `is.nutrients` (455 to 493), `is.weight` (602 to 630).
- Logic: `phone-logic.js` `wk`, `trs`, `tr`, `trMacros`, `nut`, `nm`, `groups`, `wv`, `WEIGHTS` EMA.

Routes in `:features:trends` (`com.opson.features.trends`); impl
`:features:trends:impl` (`com.opson.features.trends.impl`).

---

## Trends

### Route
`TrendsRoute : TabRoute()`, `screen<>`.

### State

```kotlin
data class TrendsState(
    val loading: Boolean = true,
    val rangeLabel: RangeLabel,                // Week(start, end) "Oct 19 to 25" | LastFourWeeks | LastThreeMonths
    val intake: IntakeCard?,                   // calories effectively Show on Week and the card is on
    val recap: RecapCard?,                     // card on
    val nutrients: NutrientsCard?,             // any macro or micros not Hidden on Week, and the card is on
    val weight: WeightCard?,                   // card on
)
data class IntakeCard(
    val title: StringResource,                 // "Daily intake" | "Weekly average intake"
    val average: Display,                      // "Average 2,104 kcal" via resolve(Calories, avg, Week)
    val bars: List<IntakeBar>,
    val band: ClosedFloatingPointRange<Float>, // targets.min..max over the chart scale
)
data class IntakeBar(val label: String, val fraction: Float?, val isCurrent: Boolean)   // fraction null = untracked gap
data class RecapCard(val breakfastDays: Int, val newFoods: Int, val daysLogged: Int, val days: List<RecapDay>)
data class RecapDay(val label: String, val mealsLogged: List<MealSlot>)   // dots coloured per meal
data class NutrientsCard(val subtitle: StringResource, val macros: List<MacroTile>)   // GoalStyle.None, of = goal
data class WeightCard(val subtitle: StringResource, val now: Display, val trend: List<Float>?)   // trend: normalised EMA, null when weight is Hidden
```

`TrendsStateBuilder.build(inputs, policy)`, pure. Chart scale is
`targets.max × 1.2` rounded up to the next 100, so bar heights come from the
builder as fractions and the composable only draws them.

**The week window** (`PROTOTYPE_NOTES.md`): the last full week ending on or
before today for the chosen start day. `end` is the latest date on or before
today whose weekday is `weekStart − 1`; `start = end − 6`. Recap always uses this
window, whatever the date range.

**4 weeks and 3 months**: one bar per week, the weekly average of tracked days;
labels `M/d` on every bar for 4 weeks, every third for 3 months. The current
week uses its own average so far.

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs, policy)` | Days in range, settings, weigh-ins, new-food firsts. |
| `SettingsTapped` | Event `OpenTrendsSettings`. |
| `NutrientsCardTapped` | `OpenNutrients(today, WeekAverage, fromTrends = true)`. |
| `WeightCardTapped` | `OpenWeight`. |
| `TabReselected` | `ScrollToTop`. |

### Data
`DiaryRepository.observeRange(from, to): Flow<List<Day>>` (totals are computed,
never stored), `DiaryRepository.observeFirstLogged(since)` for new foods,
`WeightRepository.observe(since = 28 days)`, `SettingsRepository`, policy.
Offline: all Room.

### Visibility surfaces
Surface `Week`. Delayed numbers are revealed on Week, Coast included.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Intake chart and average | calories | bars, band, `Average 2,104 kcal` | card hidden (bars read as amounts) | revealed, as Show | card hidden |
| Nutrients card | protein, carbs, fat, fiber | `96 g` with bar | soft word with bar | revealed | tile and label removed |
| Recap | none (counts) | always | always | always | always |
| Expended card | energyOut | 0.2 | | | |
| Weight value (resolved on `Weight`, as the prototype) | weight | `72.6 kg` | no value, trend line | no value, no line until 9 pm or a past day | no value, no line |

### Copy

| Key | String |
|---|---|
| `trends_title` | Trends |
| `trends_settings_cd` | Trends settings |
| `trends_range_week` | %1$s to %2$s (`Oct 19 to 25`, or `Oct 29 to Nov 4` across months) |
| `trends_range_4w` / `_3m` | Last 4 weeks / Last 3 months |
| `trends_intake_daily` / `_weekly` | Daily intake / Weekly average intake |
| `trends_average` | Average %s |
| `trends_intake_caption` | Shaded band is your range. A gap means a day you left untracked. |
| `trends_recap` | Recap |
| `trends_recap_breakfast` / `_value` | Breakfast / %d of 7 days |
| `trends_recap_new_foods` | New foods |
| `trends_recap_days` / `_days_value` | Days logged / %d of 7 |
| `trends_recap_caption` | Each dot is a meal logged. |
| Day labels | Mo Tu We Th Fr Sa Su (first two letters of the weekday) |
| `trends_nutrients` | Nutrients |
| `trends_nutrients_sub` / `_sub_micros` | Daily average this week / Daily average, with vitamins and minerals |
| `trends_weight` | Weight |
| `trends_weight_hidden` / `_now` / `_trend` | Hidden. Tap to log a weigh-in. / This morning / Smoothed trend, 4 weeks |

`This morning` becomes the relative day of the latest weigh-in when it is not
today (`Yesterday`, `Thu, Oct 22`). Expended (`Expended`, `Daily average from
your watch`) is 0.2.

### Edge cases
- **No days tracked in the window**: intake card shows the band and empty bars; average Hidden (value null).
- **Today inside the window**: its bar uses `c-fat`; other bars accent.
- **Week starts Saturday**: the window can end before today (prototype behaviour).
- **No weigh-ins**: Weight card shows the title and `Hidden. Tap to log a weigh-in.` style subtitle for its level, no value, no line.
- **Fuel** (calories Soft): no intake card at all; the recap still renders.
- **Large text**: recap rows wrap; the chart keeps its height.

### Tests
`TrendsStateBuilderTest`: window for each start day with today on a Sunday and a
Wednesday; 4 weeks and 3 months bar counts and labels; Blind (recap and weight
card only); Coast (intake revealed); Fuel (no intake); card toggles off;
untracked days are gaps not zeros. `TrendsViewModelTest`: events. Previews:
Full, Blind, Coast, week starts Saturday, 3 months. `SURFACES`: `trends`
(`showsCaloriesInFull = true`).

---

## Trends settings sheet

`TrendsSettingsRoute()`, `bottomSheet<>`. `TrendsSettingsViewModel`.

```kotlin
data class TrendsSettingsState(
    val range: TrendsRange,
    val weekStart: DayOfWeek,
    val toggles: List<CardToggle>,             // only cards that could show
)
data class CardToggle(val card: TrendsCard, val on: Boolean)   // TrendsCard { Intake, Recap, Nutrients, Weight }
```

Intake listed only when calories are effectively Show on Week; Nutrients only
when a macro or micros is not Hidden on Week; Recap and Weight always; Expended
is 0.2.

| Action | Does |
|---|---|
| `InputsArrived(settings, policy)` | |
| `RangePicked(range)` | `settings.trendsRange`. |
| `WeekStartPicked(day)` | `settings.weekStart` (Mon, Sat, Sun). |
| `CardToggled(card)` | Flips it in `settings.trendsCards`. |
| `CloseTapped` | `Dismiss`. |

None of these change visibility, so no friction. Copy: `Trends settings`;
`Close`; `Date range`: Week / 4 weeks / 3 months; `Week starts on`: Monday /
Saturday / Sunday; `Show on Trends`: Intake chart / Recap / Nutrients / Weight.
Tests: toggles filtered by level; each action writes the right field.
`SURFACES`: `trends-settings`.

---

## Nutrients detail

### Route
`NutrientsRoute(dayIso: String, range: NutrientsRange, fromTrends: Boolean = false)`,
`screen<>`, typeMap for `NutrientsRange { Day, WeekAverage }`.

### State

```kotlin
data class NutrientsState(
    val backLabel: BackLabel,                  // Trends | Day(RelativeDay)
    val subtitle: NutrientsSubtitle,           // Day(date) | WeekAverage(start, end)
    val range: NutrientsRange,
    val macros: List<NutrientRow>?,            // null when all five are Hidden
    val micros: List<MicroGroup>?,             // micros not Hidden
    val microsOff: MicrosOff?,                 // micros Hidden and some macro shown
    val nothing: Boolean,                      // everything Hidden
)
data class NutrientRow(val metric: Metric, val label: StringResource, val display: Display)   // GoalStyle.Of: "1,245 kcal of 2,100", "47 g of 120 g"
data class MicroGroup(val title: StringResource, val rows: List<MicroRow>)
data class MicroRow(val micro: Micronutrient, val reference: String, val display: Display)    // "62%", fraction of 100
data class MicrosOff(val canTurnOn: Boolean)   // level(Micros) == Hide
```

Surface is `Nutrients` for Day and `Week` for Week average, so Delayed numbers
open on Week average and at 9 pm or past days on Day. Week average uses the same
window as Trends and averages tracked days only.

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs, policy)` | Day or window, settings. |
| `RangePicked(range)` | Re-subscribes to the day or the window. |
| `ShowVitaminsTapped` | `NumbersChanger.request(Override(Micros, Show))`; `NeedsPause` opens the pause sheet. |
| `BackTapped` | `Close`. |

### Data
`DiaryRepository.observeDay` / `observeRange`, entry `micros` summed per
`Micronutrient` and divided by its reference (`NutritionScores` helpers),
settings, policy.

### Visibility surfaces

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Energy | calories | `1,245 kcal of 2,100` with bar | soft word with bar | Day: Hidden until reveal; Week: Show | row removed |
| Protein, Carbs, Fat, Fiber | macros | `47 g of 120 g` with bar | soft word with bar | as above | row removed |
| Vitamins and minerals | micros | `62%` with bar, reference shown | `Some / Most / Plenty` | as above | "Vitamins and minerals are off" card |

### Copy
`‹ %s` (Trends or the day label); `Nutrients`; subtitle `Sunday, Oct 25`
(today and ±1 use `Weekday, Mon d`, others `Thu, Oct 22`) or `Daily average,
Oct 19 to 25`; `Day` / `Week average`; `Energy and macros`; group titles
`Vitamins` / `Minerals`; micro labels and references from the prototype
`MICROS` table (`Vitamin A`, `700 µg`, ... `Sodium`, `2,300 mg limit`); caption
`Percent of a typical daily target. Most people land well under on some and over
on others, and that's fine. A week tells you more than a day.`; off card
`Vitamins and minerals are off` / `They stay off until you want them, so the
basics don't get buried. You can turn them on here, or one at a time in Modes
and numbers.` / `Show vitamins and minerals`; nothing `No nutrient detail in
this mode. Your meals are all still logged.`

### Edge cases
Entries without micros (custom foods, name-only) count as zero for micros, so a
week of mostly custom foods reads low: the caption covers it. No tracked days in
the window: rows Hidden (value null), shows `nothing` only if levels say so.
Sodium is a limit, not a target, and keeps the same bar colours (no red).

### Tests
Builder: Full Day (micros off card with Turn on), Full with micros Show,
Week average with Delayed (revealed), Fuel (calories soft word), Blind
(`nothing`). ViewModel: range switch re-subscribes, Show vitamins goes through
`NumbersChanger`. Previews: the five in the prototype gallery.
`SURFACES`: `nutrients`, `nutrients-week` (both `showsCaloriesInFull = true`).

---

## Weight

### Route
`WeightRoute()`, `screen<>`. Reached from Trends, the Logger's Weigh-in chip and
the Add menu.

### State

```kotlin
data class WeightState(
    val selected: WeightView,                  // Hidden | TrendOnly | Numbers; Delay shows as TrendOnly
    val now: Display?,                         // Shown only at Show
    val average: Display?,                     // 7-day average, Shown only at Show
    val chart: WeightChart?,                   // null when weight is Hidden
    val hiddenCard: Boolean,
    val input: String,
    val inputMasked: Boolean,                  // true unless weight is Show
    val unit: WeightUnit,
    val saved: SavedNote?,                     // Plain | OutOfView
)
data class WeightChart(
    val range: WeightRange,                    // W1 | M1 | M3, default M1
    val trend: List<Float>,                    // WeightTrend.ema, normalised to the chart box
    val dots: List<Float>?,                    // daily weigh-ins, only at Show
    val caption: StringResource,
)
```

The y range is min minus 0.3 kg to max plus 0.3 kg of the visible weigh-ins, as
the prototype. Points are normalised in the builder; no axis labels, so the line
carries no number at Trend only.

### Actions

| Action | Does |
|---|---|
| `InputsArrived(weighIns, settings, policy)` | |
| `ViewPicked(view)` | `NumbersChanger.request(Override(Weight, Hide / Soften / Show))`; raising with pause on opens the pause sheet. |
| `RangePicked(range)` | 1W (7), 1M (28), 3M (90) days. |
| `WeightTyped(text)` | Digits and one decimal point; clears `saved`. |
| `SaveTapped` | Parses in `weightUnit`, converts to kg, `WeightRepository.add(kg, now)`, clears input, `saved` set. |
| `BackTapped` | `Close`. |

### Data
`WeightRepository.observe(since = 90 days)`, settings, policy. EMA over
weigh-ins in time order with alpha 0.2 (`WeightTrend.ema`). Offline: local.

### Visibility surfaces
Surface `Weight`.

| Number | Metric | Show | Soften (Trend only) | Delay | Hide |
|---|---|---|---|---|---|
| Latest and 7-day average | weight | `72.6 kg` / `This morning`, `72.8 kg` / `7-day average` | not shown | not shown | not shown |
| Chart | weight | dots and trend line | trend line only | hidden card until 9 pm (the picker shows Trend only, as the prototype) | hidden card |
| Input | weight | plain text | masked | masked | masked |

No weight change readout ("−1.6 lbs") anywhere, per `COMPETITIVE_REVIEW.md`.

### Copy
`‹ Trends`; `Weight`; `Hidden` / `Trend only` / `Numbers`; `This morning`;
`7-day average`; range label `Last 7 days` / `Last 4 weeks` / `Last 3 months`
with chips `1W` / `1M` / `3M`; captions `Dots are daily weigh-ins. The line is
the smoothed trend.` / `Smoothed over about a week, so day-to-day water swings
barely move the line.`; hidden card `Weight is hidden` / `You can still log it.
Digits turn to dots as you type and nothing shows afterwards.`; `Log a
weigh-in`; placeholder `kg` or `lb`; `Save`; saved `Saved.` / `Saved. It stays
out of view.`

### Edge cases
Several weigh-ins in a day: all count for the EMA, the latest is "now". Fewer
than two points: no line, dots only at Show. Back from the pause sheet
cancelled: selection stays where it was. lb input converts with
`Units.lbToKg`. Back label is "Trends" whatever opened it (prototype).

### Tests
Builder: three views, a Delay override selects Trend only in the picker and
hides the chart before 9 pm, dots only at Show, masking, range slicing. ViewModel: save converts lb, invalid input
ignored, `ViewPicked` raise with pause emits `OpenPause`. Domain:
`WeightTrend.ema` (exists). Previews: Numbers, Trend only, Hidden.
`SURFACES`: `weight` (no calories; `showsCaloriesInFull = false`).
