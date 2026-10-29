# Trends plan

Trends and its settings sheet, Nutrients detail (This day and Week average),
Weight, and Patterns. Read `shared.md` first.

## Source
- **Design sync 2 is the spec now** (`docs/plans/design-sync-2.md`): the
  `trends-previews__*` pairs, `weight-screen__weight`, `weight-trend`,
  `weight-hidden`, and the manifest notes per screen. It supersedes v3 where
  they differ; "Design sync 2" below lists what changed.
- **Design v3** (before sync 2) (`docs/design/v3/CHANGES.md`, `manifest.json` and
  the images): `trends`, `trends--full`, `trends-blind`, `trends-coast`,
  `nutrients-day`, `nutrients-week`, `nutrients-week--full`, `patterns`,
  `patterns--full`, `patterns-blind`, `weight`, `weight-trend`,
  `weight-hidden`. It supersedes the handoff wherever they disagree.
- Handoff, for what v3 did not change: `README.md` 4.6 to 4.8,
  `PROTOTYPE_NOTES.md` "Week view", `phone-logic.js` `wk`, `trs`, `nut`, `wv`,
  `WEIGHTS`; `COMPETITIVE_REVIEW.md` (no weight change readout).

Routes in `:features:trends` (`com.opson.features.trends`); impl
`:features:trends:impl` (`com.opson.features.trends.impl`).

### Global v3 rules this feature follows
- Sub-screens (Nutrients, Weight, Patterns) use `PageHeader`, now an Android
  top app bar (back arrow and title). A subtitle sits under the title.
- Touch targets at least 48 dp. Bars stop at 100%; over never looks bigger.
- No visibility toggle on a content screen. Nutrients and Weight link to Modes
  and numbers (`ModesRoute`) instead; the pause applies there.
- Meal colours only tell meals apart.

---

## Design sync 2

What changed against this plan, and the differences kept on purpose.

- **Trends**: already matched. The design PNGs for Trends, Nutrients week and
  Patterns are full scrolls; the renderer draws every preview at 844 dp, so
  the `…Below` previews show the rest. Kept on purpose: `Patterns ›` stays on the Blind `Your week` hero. The
  design drops it, but the Blind Patterns page (`patterns-blind`) is designed
  and this link is its only way in. Expended (`Daily average from your
  watch`) stays unbuilt: Health has a per-day `energy(day)` flow only, and a
  span average needs a range read in `:libraries:health`.
  `TODO(design-sync-2)` in `TrendsScreen`.
- **Coast**: the design says `7:00 pm`; the reveal is 9:00 pm everywhere
  (`VisibilityPolicy.DAY_CLOSE_REVEAL`, the day-close design), so the app
  keeps 9:00 pm. Ranges keep "to" (`Oct 19 to 25`).
- **Nutrients day and week**: match.
- **Patterns** (Full): the hunger card is now **Hunger by weekday**, a
  bar per weekday (Mon to Sun) of how hungry the tagged meals were, with
  `From your check-ins` beside the title, and one plain fact under it when
  the data says so: `Hungrier on days you skipped breakfast (Thu, Tue).`
  (`MealPatterns.hungerByWeekday` and `hungrierWithoutBreakfast`; the fact
  needs at least two tagged days each way and a gap of half a step or more,
  thresholds `TODO(clinician)`). Bars carry no amounts, so they stay in Blind.
  Bars use `accentTint`, a shade lighter than the design's.
- **Patterns** (Blind): the order is When you eat, How often, New foods, then
  hunger below the fold. Full keeps When you eat, How big, hunger, New foods.
  Blind still drops gaps, later-on lines and durations (`mealGaps`).
- **Weight**: previews move to `WeightScreen.kt` so their ids are
  `weight-screen__weight`, `weight-screen__weight-trend` and
  `weight-screen__weight-hidden`. No change readout; no view switch (it
  lives in Modes and numbers, behind the pause, owned by Settings). Trend
  only's word comes from the slope (`TODO(clinician)` on the thresholds in
  `NutritionFormatter`). Kept on purpose: the Hidden design's `Send
  weigh-ins to my provider · Coming later` row is not drawn (no "Coming
  later" rows, App Review).
- **Weight → Progress photos row** (new, behind `release.progressPhotos`):
  under the log card, `Progress photos` / `Last taken Oct 25 · private` (or
  `Private, on this phone only` before the first), a lock and a chevron.
  Opens `ProgressPhotosRoute`. Shown or hidden by Modes and numbers' Progress
  photos row (`NumbersSettings.showsProgressPhotos`): Blind, Mindful, Rhythm
  and Fuel default to Hide, the rest to Show. The plan for the photos is
  `progress-photos.md`.

---

## Trends

### Route
`TrendsRoute : TabRoute()`, `screen<>`.

### Layout, top to bottom
1. Header: the range label (`Oct 19 to 25`, `Last 4 weeks`, `Last 3 months`)
   over `Trends`, the tune button on the right (week start, which cards show).
2. **Range control** Week / Month / 3 months. It covers the whole page (the
   chart, recap, nutrients and weight), not only Weight. Shown only when a card
   that follows the range is on the page (intake, nutrients or weight); without
   one (Blind, Coast before its check) the page is the week and the control is
   not drawn. Picking writes `settings.trendsRange`.
3. **Weekly check** (Coast before the check opens): hero card, calendar-check
   tile, `Your weekly check opens tonight`, `Sunday at 9:00 PM. You'll see your
   weekly average and how weight is trending.`
4. **Eaten** (calories effectively Show for the span): `Eaten`,
   `2,122 kcal avg` (the tagged amount, then `avg` as a label), bars over the
   range band. Every bar is the same colour, in the band or out of it. The
   in-progress bar (today on Week, this week on Month and 3 months) is striped
   and left out of the average. Untracked days are gaps. Caption says which.
5. **Your week** (the recap leads, when there is no Eaten card and no weekly
   check): hero card `Your week` with `Patterns ›`, one column per day, four
   dots per day coloured by meal, a dim dot for a meal not logged, and a legend.
6. **Recap** rows card. With Eaten above it: title `Recap` with `Patterns ›`,
   then Days logged, Breakfast, New foods. Leading (under Your week): no title,
   the same rows, and the new foods as chips. Coast before its check: Days
   logged and Meals logged only.
7. **Last week's check** (Coast before the check): `Last week's check · Oct 18`,
   Daily average (calories for the previous week, which is open) and Weight
   (the slope word).
8. **Nutrients**: `Nutrients` / `Daily average`, chevron; four macros as
   `96 / 120 g` with bars. Removed when every macro is hidden.
9. **Expended** is 0.2: not rendered.
10. **Weight**: `Weight` / `Smoothed 7-day trend`, the trend value (or the
    slope word when weight is soft) and chevron, the trend line over the span.
    Removed when weight is hidden.

Coast before its check shows 3, 6 and 7 only: the check holds the weight
trend, so the Weight card waits for it too.

### State

```kotlin
data class TrendsState(
    val loading: Boolean = true,
    val rangeLabel: RangeLabel? = null,          // Week(text) | LastFourWeeks | LastThreeMonths
    val range: TrendsRange? = null,              // the page control; null hides it
    val weeklyCheck: WeeklyCheck? = null,
    val intake: IntakeCard? = null,
    val week: WeekDots? = null,                  // the "Your week" hero
    val recap: RecapCard? = null,
    val lastCheck: LastCheck? = null,
    val nutrients: NutrientsCard? = null,
    val weight: WeightCard? = null,
)
data class WeeklyCheck(val day: DayOfWeek, val at: LocalTime, val tonight: Boolean)
data class IntakeCard(
    val average: Display,                        // resolve(Calories, avg, Week, day = span end)
    val bars: List<IntakeBar>,                   // IntakeBar(label, fraction?, isCurrent): isCurrent draws striped
    val band: ClosedFloatingPointRange<Float>,
    val caption: IntakeCaption,                  // Plain | TodayInProgress | WeekInProgress
)
data class WeekDots(val days: List<RecapDay>)    // RecapDay(label, meals: List<MealSlot?>, isToday)
data class RecapCard(val titled: Boolean, val rows: List<RecapRow>)
sealed interface RecapRow {
    data class DaysLogged(val days: Int, val of: Int)
    data class Breakfast(val days: Int, val of: Int)
    data class MealsLogged(val meals: Int)
    data class NewFoods(val count: Int, val names: List<String>?)   // names only when the recap leads
}
data class LastCheck(val weekEnd: String, val average: Display, val weight: Display)
data class NutrientsCard(val macros: List<MacroTile>)                // GoalStyle.Slash
data class WeightCard(val subtitle: WeightSubtitle, val value: Display?, val trend: List<Float>)
// WeightSubtitle: Trend | NoWeighIns. value: the trend (EMA) shown, or the slope word when soft.
```

`TrendsStateBuilder.build(inputs, policy)`, pure, with
`TrendsInputs(today, settings, days, weighIns, timeZone)`.

**The week window** (`PROTOTYPE_NOTES.md`): the last full week ending on or
before today for the chosen start day. `end` is the latest date on or before
today whose weekday is `weekStart − 1`; `start = end − 6`.

**The span** each range covers: Week is the window, daily bars. Month is the
last 4 weeks and 3 months the last 12, from the chosen start day to today,
weekly bars (each the average of its tracked days); the last bar is the week
holding today. Labels: weekday initials on Week (`Mon`), `M/d` of the week's
first day on Month, every third bar on 3 months. Recap counts, nutrients
averages, new foods and the weight line all use the same span. When the range
control is hidden the span is the Week window.

Chart scale is `targets.max × 1.2` rounded up to the next 100; heights arrive
as fractions. A day's intake is `day.totals().kcal.value` when it has anything
logged; nothing logged, or only name-only items, is a gap.

**New foods**: `NewFoods.between(days, from, to)` in `:libraries:diary`. A food
(catalog id, custom food id, or lower-cased name; quick numbers never count) is
new when it is logged in the span and not in the 90 days before it. The VM
observes from `span start − 90 days` to today so one `observeDays` covers all
of it, the previous week for Coast's last check included.

**Coast's weekly check** comes from the policy, never from the mode id:
`policy.revealsWeekly` and `policy.isRevealed(Surface.Week, FormatContext(day =
window.end))`. A week opens at the reveal time (`VisibilityPolicy.DAY_CLOSE_REVEAL`,
9:00 pm) on its last day and stays open after. `FormatContext.day` on
`Surface.Week` is the week's last day; with no day the week view is open, as
before.

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs, policy)` | Days in the span, settings, weigh-ins. |
| `RangePicked(range)` | Writes `settings.trendsRange`; the span re-subscribes. |
| `SettingsTapped` | Event `OpenTrendsSettings`. |
| `PatternsTapped` | Event `OpenPatterns`. |
| `NutrientsCardTapped` | Event `OpenNutrients(today)`: Week average, back to Trends. |
| `WeightCardTapped` | Event `OpenWeight`. |
| `AllNotesTapped` | Event `OpenNotes`: the Notes list, through `NotesListLink`. Until the Notes list's module binds it the link is not drawn (`TODO(notes-route)`). |

**Hunger** and **Notes** sit at the foot of the page, behind the `Hunger and
notes` switch (`TrendsCards.journal`). Both read the meal notes through
`MealJournal` over the page's span. Hunger: a bar per day on Week, per week on
the longer ranges, each the average `Hunger.level` (a fifth of the height for
stuffed, full for starving), then each meal on a stuffed-to-starving track
with its usual answer. One colour; hunger is information, not a score. With no
hunger noted, one `EmptyCard` sentence. Notes: meals with a note out of meals
done, then fullness and moods only where older "How it felt" rows have them,
and only when anything was written. Note text never reaches Trends.

**Check-in hunger** (`release.checkIn`) joins the Hunger card above the meal
notes' bars: `From your check-ins`, the check-in's 1 to 10 level
(`CheckIn.hunger`) over the page's span (`TrendsJournal.checkInHunger`). A dot
a day on Week (one check-in a day), a dot a week on the longer ranges at the
week's average with a bar from its fullest to its hungriest answer
(`RangeDotChart` in `:libraries:ui`). Hungrier is higher, as on the meal bars;
the side names the scale's anchors (Ravenous, Hungry, Neutral, Full, Stuffed),
never a number. Untracked days are gaps. Before this, Trends charted only the
meal notes' five-step hunger; the 1 to 10 level was only on Patterns (Hunger
by weekday and the How you've felt bars, a fixed four weeks).

### On-track calendar
Under the Eaten chart (or Your week), behind `TrendsCards.calendar`
(`On-track calendar` in the settings sheet, every mode). This month and the two
before (`OnTrack.MONTHS`), swiped (`SwipePages`) or stepped with the arrows,
laid out from the week start, today ringed in ink. Pure rules in `OnTrack`,
tested at the band edges (`OnTrackTest`):

- The day's goal is `Targets.rangeFor(date, preset, that day's Burned)`, the
  goal Today shows, so a set deficit is judged on the day's own deficit
  (burned less eaten) against the one set. One number gets Today's
  `ONE_NUMBER_LEEWAY_KCAL` either side.
- **On plan** inside it. **A bit off** up to `nearMarginKcal` past it, the
  larger of 150 kcal and a tenth of the goal (`TODO(clinician)`). **Over** or
  **under** beyond that.
- The fills are `AppTheme.colors.plan` (Slate): a sage green, a wheat gold and
  a soft clay for off, never an alarm red, with ink on each (`SlateTest`).
  Clay is clearly off in the direction away from the goal: over for a
  deficit, under for a surplus, either way for a range. Far past the goal the
  other way (far under a deficit) is gold, never green: eating very little is
  not the best day. Words are `Over plan`, `Under plan`, `Off plan`, never a
  verdict.
- Gray: nothing logged, untracked, a meal without an amount (unless the known
  part is already over), today until it is closed, a day Later hasn't opened,
  and days to come (no fill).
- **Logged only** (`CalendarMode.LoggedOnly`): a day is logged or not, no
  colors for how it went. Wherever calories are short of Show on the week
  view, energy out too for a goal aimed at Burned, and always for someone who
  said numbers have been hard (`onboarding.hard`).
- The (i) opens `How to read this`, worded for the plan's direction. A count
  line under the month (`12 on plan · 5 a bit off · 3 over`) and a legend.
- A day opens `TrendsDayRoute(dayIso)`, a `bottomSheet<>`: the day's word,
  Eaten, Burned, the difference (`480 kcal under what you burned`) and the plan
  where the mode shows them in full, and `Open this day` (selects the day and
  switches to Today).

### Running total
Over the Weight card, behind `TrendsCards.runningTotal` and Burned
(`release.goalsAndEnergy`, which `burnedByDay` needs). Every counted day's
Burned (`BurnedRepository.burnedBetween`, the same Burned Today uses, digesting
food included when it is on) less eaten, added up over `7 days` or `30 days`
(default), or `Since you started` once the goal has a start date
(`TrendsInputs.goalStart`, `TODO(weight-goal)` until Settings has one). A day
with nothing logged, untracked, with a meal without amounts, or without a
Burned is left out, and today waits until it is closed. Shown as `3,500 kcal
under what you burned` and `Roughly 1.0 lb of body weight lost, by the rule of
thumb of 3,500 kcal a pound` (7,700 a kilogram, `KCAL_PER_KG`), in the
person's weight unit, then the weight trend over the same days. The (i)
explains the estimate and its limits. Only where calories and energy out show
in full, never after numbers have been hard; the pounds and the scale only
where weight shows in full. `RunningTotalTest`.

### Data
`DiaryRepository.observeDays(from, to)`, `WeightRepository.observeWeighIns(since
= today − 120 days)`, `SettingsRepository.settings`,
`VisibilityPolicySource.policy`, `Clock`. All Room, offline.

### Visibility surfaces
Surface `Week` (with `day` = the span's last day), weight on `Weight`.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Eaten chart and average | calories | bars, band, `2,122 kcal avg` | card removed | Delayed: open on the week view. Coast: weekly check card until the week opens | card removed |
| Last week's check | calories, weight | `2,140 kcal`, slope word | | Coast only | |
| Nutrients card | macros | `96 / 120 g` with bar | soft word with bar | as calories | tile removed; card removed when all are |
| Recap, Your week | none (counts) | always | always | always | always |
| Weight card | weight | trend `73.4 kg` and line | slope word and line | line | card removed |
| Hunger, Notes | none (levels and counts) | always | always | always, before a weekly check too | always |
| On-track calendar | calories (and energy out for a goal aimed at Burned) | colored against the plan | logged or not | each day once it opens | logged or not |
| Running total | calories, energy out, weight | total, pounds, scale trend | card removed | days that have opened | card removed |
| Day sheet | calories, energy out | eaten, burned, difference, plan | logged or not | once the day opens | logged or not |

### Copy (`strings_trends.xml`)
`Week` / `Month` / `3 months`; `Eaten`; `avg`; captions `The band is your
range. A gap is a day you left untracked.` / `The band is your range. Today is
striped because it's still in progress, so it isn't in the average.` / `The
band is your range. This week is striped because it's still in progress, so it
isn't in the average.`; `Recap`; `Patterns`; `Days logged` / `%1$d of %2$d`;
`Breakfast` / `%1$d of %2$d days`; `Meals logged`; `New foods`; `Your week`;
legend from the meal labels; weekly check `Your weekly check opens tonight` /
`Your weekly check opens %1$s` and `%1$s at %2$s. You'll see your weekly average
and how weight is trending.`; `Last week's check · %1$s`; `Daily average`;
`Weight`; `Nutrients` / `Daily average`; `Smoothed 7-day trend`; `No weigh-ins
yet. Tap to log one.` Day labels `Mon` to `Sun`.

### Edge cases
- No days tracked: bars are gaps, average Hidden.
- Week starts Saturday: the window can end before today (prototype behaviour).
- Fuel (calories soft): no Eaten card, Your week leads, Nutrients and Weight follow.
- Large text: recap rows wrap; the chart keeps its height.

### Tests
`TrendsCalendarTest` (windows, spans). `TrendsStateBuilderTest`: Full (range
control, no current bar mid-week, striped today on the window's last day, the
average leaves today out, gaps), Month and 3 months bar counts and labels,
Blind (Your week leads, nutrients and weight removed, chips, no range control),
Coast before and after its check (weekly check, two rows, last week's check;
afterwards the Eaten card), Fuel, cards off. `TrendsViewModelTest`: each event
and `RangePicked` writing the setting. Previews: `trends`, `trends-blind`,
`trends-coast`, Month, empty, dark. `SURFACES`: `trends`, `trends-blind`,
`trends-coast`, `trends-deficit`, `trends-day`. `OnTrackTest`, `RunningTotalTest`,
`CheckInHungerTest`, `TrendsDayTest`.

---

## Trends settings sheet

`TrendsSettingsRoute()`, `bottomSheet<>`. The tune button: week start and which
cards show. The date range moved onto the page.

```kotlin
data class TrendsSettingsState(val weekStart: DayOfWeek, val toggles: List<CardToggle>)
data class CardToggle(val card: TrendsCard, val on: Boolean)   // Intake, Recap, Nutrients, Weight
```

Intake listed only when calories can show on the week view; Nutrients when a
macro can; Weight when weight is not hidden; Recap, the On-track calendar and
Hunger and notes always; Running total with Burned (`release.goalsAndEnergy`)
where calories and energy out show, and not after numbers have been hard.

| Action | Does |
|---|---|
| `InputsArrived(settings, policy)` | |
| `WeekStartPicked(day)` | `settings.weekStart` (Mon, Sat, Sun). |
| `CardToggled(card)` | Flips it in `settings.trendsCards`. |
| `CloseTapped` | `Dismiss`. |

Copy: `Trends settings`; `Week starts on`: Monday / Saturday / Sunday; `Show on
Trends`: Eaten chart / On-track calendar / Recap / Nutrients / Expended / Running total / Weight / Hunger and notes. `SURFACES`: `trends-settings`.

---

## Nutrients detail

### Route
`NutrientsRoute(dayIso: String, range: NutrientsRange, fromTrends: Boolean = false)`,
`screen<>`, typeMap for `NutrientsRange { Day, WeekAverage }`. **Week average
is the default from Trends** (a single day of vitamin K is noise); This day is
the default from Today.

### Layout
Top app bar `Nutrients` with the subtitle (`Sunday, Oct 25` or `Oct 19 to 25`);
segmented `This day` / `Week average`; the macros card (no title): Energy
`2,058 kcal · range 1,900 to 2,300` on a bar with the range as a dashed box, then
Protein, Carbs, Fat, Fiber `88 of 120 g` with bars that stop at 100%. Then
either the Vitamins and Minerals cards and the caption, or the off card.

The **Nutrition scores** page is cut: a score built from hidden data is a
number with no source. `NutritionScore` and `NutritionScores.compute` are
removed from `:libraries:nutrition`; the percent sum moves to
`MicroPercents.of`.

### State

```kotlin
data class NutrientsState(
    val loading: Boolean = true,
    val backLabel: BackLabel,                  // Trends | Day(text); the arrow's spoken label
    val subtitle: NutrientsSubtitle?,          // Day(text) | WeekAverage(range)
    val range: NutrientsRange,
    val energy: EnergyRow?,                    // calories not hidden
    val macros: List<NutrientRow>?,            // the four macros not hidden; null when none
    val micros: List<MicroGroupRows>?,         // micros not hidden
    val microsOff: Boolean,                    // micros hidden while something else shows: links to Modes
    val nothing: Boolean,                      // everything hidden
)
data class EnergyRow(val eaten: Display, val range: Display, val rangeStart: Float, val rangeEnd: Float)
data class NutrientRow(val metric: Metric, val label: StringResource, val display: Display, val opensFoods: Boolean)
data class MicroGroupRows(val group: MicroGroup, val title: StringResource, val rows: List<MicroRow>)
data class MicroRow(
    val micro: Micronutrient, val label: StringResource, val reference: String, val isLimit: Boolean, val display: Display,
    val coverage: MicroCoverage,   // "9 of 12 foods list it" under the reference when only some do
    val opensFoods: Boolean,       // a food gave some: a tap opens which ones
)                                  // notListed (no food lists it): "Not listed" where the percent goes, never 0%
```

Surface `Nutrients` for This day, `Week` (day = window end) for Week average.
Each reference is the person's own (`Settings.microReferences`, by the age and
sex Resting energy collects, with their own numbers over it), from
`docs/research/nutrient-targets.md`.

### Actions

| Action | Does |
|---|---|
| `InputsArrived(inputs, policy)` | |
| `RangePicked(range)` | Re-subscribes to the day or the window. |
| `ModesTapped` | Event `OpenModes`: Modes and numbers, where the pause applies. |
| `NutrientTapped(nutrient)` | Event `OpenFoods(nutrient, range)`: the foods sheet below. |
| `BackTapped` | `Close`. |

### Visibility surfaces

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Energy | calories | `2,058 kcal` with range and bar | soft word with bar, no range | Day: hidden until reveal; Week: open | row removed |
| Macros | macros | `88 of 120 g` with bar | soft word with bar | as above | row removed |
| Vitamins, minerals | micros | `78%` with bar, `of 700 µg` | `Some` / `Most` / `Plenty` | as above | off card linking to Modes |

### Which foods gave it

`NutrientFoodsRoute(dayIso, nutrient, range)`, a `bottomSheet<>` Trends
registers, opened from a macro or micro row here and from a row on Today's
nutrients page. `nutrient` is `Nutrient.key` (`iron`, `protein`); energy is
never one. The foods are ranked by how much each gave (`contributions` in
`:libraries:diary`), the same food logged twice is one row, a food that lists
none or 0 is not in the list, and the top eight show with "And 3 more".
Week amounts are per day over the days the average counts.

| Mode | Shows |
|---|---|
| All numbers, Macros with grams | Each food's amount (`2.1 mg`, `38 g`) and share (`40%`) on a bar |
| Macros only (macros soften) | The foods in order, no amounts or shares, "Ranked by how much each gave." |
| A level that hides the nutrient (No numbers; micros in Macros only) | Nothing of it, food names included |

Every row is the nutrient's own colour, sodium too: nothing reads as a warning
and nothing is framed as a food to cut. When only some foods list a micro the
sheet says "3 of 4 foods list it, so the full amount may be higher." Leak
surfaces `nutrient-foods`, `nutrient-foods-week`, `nutrient-foods-protein`.

### Copy
`Nutrients`; `This day` / `Week average`; `· range %1$s`; group titles; `of
%1$s` (`of a %1$s limit` for sodium); caption `Percent of a typical daily
target. Most people land under on some and over on others, and that's fine. A
week tells you more than a day.`; off card `Vitamins and minerals are off` /
`Turn them on in Modes and numbers.` / `Modes and numbers`; nothing `No nutrient
detail in this mode. Your meals are all still logged.`

### Tests
Builder: Full day (micros shown), Full with micros hidden (off card), micros shown on Week average, Delayed week (open),
Fuel (soft words, no range), Blind (`nothing`), bars capped by the component.
ViewModel: range switch re-subscribes, `ModesTapped` emits `OpenModes`.
`SURFACES`: `nutrients`, `nutrients-week`.

---

## Weight

### Route
`WeightRoute(focusInput)`, `screen<>`, from Trends, the + sheet's Weigh-in,
Today's weight row, Profile's Current weight and the exercise sheet's "Log a
weigh-in". `focusInput` (a plain Boolean, no type map) focuses the weigh-in
field with the keyboard up once the screen is in; the + sheet, Profile and the
exercise sheet pass it, since they open Weight only to log one. The page pads
for the keyboard so the focused field scrolls above it.

The **Numbers / Trend only / Hidden switch is gone** from this screen: it lives
in Modes and numbers, behind the pause. Sending weigh-ins to a provider is v2
and not built.

### Layout per level (surface `Weight`)
- **Show**: summary card `Trend` with the trend as the large number (`73.4 kg`)
  and `This morning` (or `Yesterday`, `Thu, Oct 22`) with today's reading small
  on the right. Chart card: `Last month` with `1W` / `1M` / `3M`, weigh-in dots,
  the trend line, the first and last dates under it, caption `The dots are
  weigh-ins. The line is a smoothed trend, so a day of water weight doesn't
  steer you.`
- **Soften** (Trend only): one card, `Last month`, the slope word large
  (`Steady`, `Rising slowly`, `Falling slowly`), the line. No numbers, no dots.
- **Hide**: `Weight is hidden` card with an eye-off tile, `You can still log it.
  Digits turn into dots as you type, and nothing shows afterwards.`
- All: `Log a weigh-in`, the input with the unit, `Save`. The input masks digits
  unless weight is Show. Soften adds `After you save, it only shows up in the
  trend.` After saving, a confirmation: `Saved.` at Show, `Saved. It won't show
  anywhere.` otherwise.

No change readout (`−1.6 kg`) anywhere, pending a clinician.

**Slope word**: the policy picks it. `FormatContext.trendKgPerWeek` carries the
slope of the smoothed line over the shown range; soft weight resolves to
`SoftWord.Steady` / `RisingSlowly` / `FallingSlowly` (spelled by
`NutritionFormatter`, thresholds `TODO(clinician)`), or `Trend only` without a
slope.

### State

```kotlin
data class WeightState(
    val loading: Boolean = true,
    val trend: Display? = null,                // Show: the EMA now. Soften: the slope word
    val today: Display? = null,                // Show only: the latest reading
    val todayWhen: WeighInWhen? = null,
    val range: WeightRange = WeightRange.M1,   // W1 | M1 | M3
    val chart: WeightChart? = null,            // null when hidden
    val hiddenCard: Boolean = false,
    val input: String = "",
    val inputMasked: Boolean = true,
    val unit: WeightUnit = WeightUnit.Kg,
    val saved: SavedNote? = null,              // Plain | OutOfView
)
data class WeightChart(val trend: List<Float>, val dots: List<Float>?, val from: String?, val to: String?, val ranges: Boolean)
```

### Actions
`InputsArrived`, `RangePicked(range)`, `WeightTyped(text)`, `SaveTapped`,
`BackTapped` (as before, minus `ViewPicked`), and `ProgressPhotosTapped`
(event `OpenProgressPhotos`). State gains `photos: PhotosRow?`
(`PhotosRow(lastTaken: LocalDate?)`), `null` while the flag is off or the
mode hides photos.

### Tests
Builder: three levels, the slope word from the policy, dots and ranges only at
Show, masking, range slicing, no change readout. ViewModel: save converts lb,
invalid input ignored. `SURFACES`: `weight`.

---

## Patterns (new)

### Route
`PatternsRoute()` in `:features:trends`, `screen<>`, opened from `Patterns ›`
on the Recap card (and on Your week). Top app bar `Patterns`, subtitle `Last 4
weeks`: the 28 days ending today.

### Layout
1. **When you eat**: a row per meal, label then a track from 6 am to 9 pm with
   one dot per logged meal and the usual window tinted behind the dots (meal
   colour, light). Axis `6a 9a 12p 3p 6p 9p`. Then plain facts, no advice:
   `Breakfast, usually` / `8:10 AM`; `Dinner on Fri and Sat` / `About 1 h
   later`; `Longest gap` / `Lunch → dinner, 6 h`.
2. **How big each meal is** (calories Show): a bar split by each meal's share,
   then per meal `445 kcal · 21%`. Removed in Blind: even percentages reveal
   amounts. In its place, **How often**: `Breakfast` / `22 of 28 days` per meal.
3. **Hunger by weekday** (some meals
   tagged): a bar per weekday, height the average hunger of that weekday's
   tagged meals (Not hungry 0 to Very hungry 3, over 3), `From your
   check-ins`, and the optional skipped-breakfast fact. Design sync 2. With
   `release.checkIn` and some check-in hunger it reads the check-ins instead,
   one answer a day on the 1 to 10 scale, taller is hungrier.
4. **New foods** with the count and the names as chips. In Blind it comes
   before hunger.
5. **How you've felt** (`release.checkIn`, 2026-10-29): the check-ins over the
   four weeks, a bar a day, one of hunger, energy, mood or sleep at a time
   (a segmented pick, only aspects someone answered). Gaps for days without
   one and untracked days. Inspecting a bar says the day and the answer's word,
   never an amount, so it stays in every mode.

### Domain (`:libraries:diary`, `MealPatterns.kt`, pure, unit tested)

```kotlin
object MealPatterns {
    fun of(days: List<Day>, from: LocalDate, to: LocalDate, timeZone: TimeZone): MealPatternFacts
}
data class MealPatternFacts(
    val dayCount: Int,
    val times: Map<MealSlot, List<Int>>,            // minute of day per timed meal
    val usual: Map<MealSlot, IntRange>,             // middle half of the times, 3 or more meals
    val typical: Map<MealSlot, Int>,                // median minute
    val frequency: Map<MealSlot, Int>,              // days the meal was done
    val laterOn: LaterOn?,                          // slot, weekdays, minutes later than usual (45 or more)
    val longestGap: MealGap?,                       // the consecutive meal pair with the longest median gap
    val hunger: Map<MealSlot, Hunger>,              // most common tag per meal
    val taggedMeals: Int,
    val energyPerMeal: Map<MealSlot, Double>,       // average energy of a done meal; the screen resolves it
)
object NewFoods { fun between(days, from, to, lookbackDays = 90): List<String> }
```

A meal's time is its first item's `loggedAt` when that was on the same day
(items back-filled on a later day have no honest time and are left out of the
timing). "Ate, no details" counts for frequency only.

### State

```kotlin
data class PatternsState(
    val loading: Boolean = true,
    val timing: TimingCard? = null,
    val sizes: List<MealSize>? = null,               // calories Show
    val often: List<MealFrequency>? = null,          // when sizes is null
    val hunger: HungerCard? = null,               // HungerCard(days: List<HungerDay>, skippedBreakfast: List<DayOfWeek>?)
    val newFoods: List<String> = emptyList(),
    val hungerLast: Boolean = false,                // Blind: hunger under New foods
)
data class TimelineRow(val slot: MealSlot, val dots: List<Float>, val usual: ClosedFloatingPointRange<Float>?)
data class TimingCard(val rows: List<TimelineRow>, val facts: List<TimingFact>)
// TimingFact: Usually(slot, time) | LaterOn(slot, days, minutes) | LongestGap(from, to, minutes)
data class MealSize(val slot: MealSlot, val energy: Display, val share: Display, val fraction: Float)
```

Amounts go through the policy: energy with `resolve(Calories, …, Surface.Week,
day = today)`, the share with `energyShare(percent, …)` (Shown only when
calories are Show). `PatternsStateBuilder.build(inputs, policy)` with
`PatternsInputs(today, settings, days, timeZone)`.

### Actions
`InputsArrived(inputs, policy)`, `BackTapped` (`Close`).

### Tests
`MealPatternsTest` in `:libraries:diary`: usual window, median, frequency,
longest gap, later-on days, hunger mode, back-filled items left out, new foods.
`PatternsStateBuilderTest`: Full (sizes, no often), Blind (often, no sizes, no
amount anywhere), hunger only with tags on. `PatternsViewModelTest`: back.
Previews `patterns`, `patterns-blind`, dark. `SURFACES`: `patterns`,
`patterns-blind`.
