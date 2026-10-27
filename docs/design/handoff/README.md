# Handoff: Friendly Tracking, a calorie tracker that works for people with eating disorders

## Overview
Friendly Tracking is a food logger with the features people expect from Cronometer or MacroFactor: fast multi-add logging, a diary grouped by meal, macros, micronutrients, water, health data, trends, targets and fasting. Every nutrition number can be shown, softened, delayed or hidden, and each mode sets those choices consistently across every screen. It is built so that people in or near eating disorder recovery can use it safely and people who love numbers still get all of them.

Target stack, from the original brief: **Android, Kotlin, Jetpack Compose, Material 3**. An iPhone app comes later and shares the same domain layer.

## About the design files
The files in this bundle are **design references made in HTML**. They are working prototypes that show the intended look and behavior. They are not production code to copy. The job is to **rebuild these designs in Compose** using its normal patterns: ViewModels, StateFlow, Navigation and Material 3. Treat the logic class inside `Phone.dc.html` as an executable spec for behavior, especially the VisibilityPolicy.

## Fidelity
**High fidelity.** Colors, type, spacing, copy and interactions are final for the MVP unless noted otherwise. Rebuild the UI to match. Material 3 components are fine as long as they're restyled to these tokens.

---

## 1. The core rule: VisibilityPolicy
**No screen formats or shows a nutrition number by itself.** Every number goes through one policy object.

### Metrics (10)
`calories, remaining, energyOut, protein, carbs, fat, fiber, micros, weight, water`

### Levels
| Level | Shows |
|---|---|
| `SHOW` | The formatted amount |
| `SOFTEN` | A word or a fill bar, never an amount. Calories: "Early in the day / Partway through / Most of the way / In your range / A bigger day". Macros: "Building / Getting there / Plenty today". Micros: "Some / Most / Plenty". Weight: "Trend only". Water: glasses bar only, no text |
| `DELAY` | Hidden until the reveal. Delayed reveal opens at day close (9:00 pm) and on the week view. Coast opens on the week view only. Past days are always revealed |
| `HIDE` | Nothing renders, including the **label**. Not just the digits |

### Resolution
`level = overrides[metric] ?? preset.vis[metric]`. Picking a preset clears the overrides.

```kotlin
enum class Visibility { SHOW, SOFTEN, DELAY, HIDE }
data class ModePreset(val id: String, val label: String, val today: TodayLayout, val vis: Map<Metric, Visibility>,
                      val revealOn: Reveal? = null, val mealCount: Boolean = false, val banner: String? = null)
class VisibilityPolicy(settings: Settings, clock: Clock) {
  fun resolve(metric: Metric, value: Double?, surface: Surface, of: Double? = null): Display // Hidden | Soft(text) | Shown(text)
}
@Composable fun NutrientValue(metric: Metric, value: Double?, surface: Surface) // renders nothing when Hidden
```

Formatting rules, all inside the policy:
- `remaining` never says "0 left". It says **"On target"** when within the range, and "Above your range today" when over it.
- Calories bare: `1,245`. With unit: `1,245 kcal`. Macros: `47 g` or `47 g of 120 g`. Weight: `72.6 kg`. Micros: `62%`. Water: `750 of 2,000 ml` or fl oz.

### Presets (`PRESETS` in Phone logic)
| id | Today layout | Notes |
|---|---|---|
| full | full | Everything SHOW. The prototype hid micros as an opt-in; the app shows them (`docs/decisions.md`, 2026-10-27) |
| delayed | full | Everything DELAY, weight SOFTEN, revealOn = day |
| blind | meals | Everything HIDE. 2×2 meal tiles with checkmarks |
| nutrient | full | Calories HIDE. Protein and fiber SOFTEN, weight SOFTEN |
| rhythm | rhythm | Meal checklist with "Already ate" and "Log". mealCount on |
| explorer | explorer | Safe foods and new foods this month |
| mindful | meals | Banner about tagging how meals felt |
| fuel | full | Calories SOFTEN, protein and fiber SHOW, mealCount on |
| sprint | full | Same as full, plus a banner "day 9 of 14" |
| coast | meals | Calories DELAY (weekly), weight SOFTEN |

Water is SOFTEN in every preset except full and sprint, which use SHOW.

### Friction ("Pause before showing more")
This is an optional setting. When it's on, any change that **raises** the visibility score (SHOW 3, SOFTEN 2, DELAY 1, HIDE 0, summed over metrics) opens a sheet. The sheet has a 10-second countdown and an optional "What's prompting this?" chip question. Lowering visibility always applies right away.

### Acceptance test (port this)
`BlindModeLeakTest`: set the Blind preset, then walk every route and sheet listed in `SURFACES` in `Friendly Tracking.dc.html`. Assert:
- `onAllNodesWithTag("metric:calories" | "metric:remaining" | "metric:energyOut")` is empty
- no visible text matches `/\d[\d,.]*\s*(kcal|cal\b|calories)|\d[\d,]*\s*left\b/i`

In the prototype, every number carries `data-metric="…"`. Map that to `Modifier.testTag("metric:…")`.

---

## 2. Safety rules (non-negotiable)
- `GOAL_FLOOR_KCAL = null`, with a **TODO(dietitian)** to set it. Until then the energy target can be **raised freely but never lowered below the starting target**.
- There is no evening "calories left" notification, and no setting to turn one on.
- After a big day, tomorrow's target **never decreases**. The big-day card only appears when calories are fully shown.
- Exercise adjustment can be "All / Some (half) / None". It never lowers tomorrow.
- **Fasting** is off by default and only offered in the Full and Sprint presets. Windows are 12:12, 14:10 and 16:8. Logging food always ends a fast, and the app never asks you to wait. There are no streaks, no longest-fast records, and nothing over 16 hours. **TODO(clinician)**: confirm which modes allow it and the cap.
- Logging exercise by hand is **left out on purpose**, because it encourages compensating behavior.
- Balance (eaten minus expended) is only shown when both calories and watch energy are SHOW. **TODO(clinician)**: review.
- Health data on Today is **off by default in the gentle modes**.
- Settings only change when the user changes them. Show who changed the mode and when.
- The helpline link (allianceforeatingdisorders.com) must be verified before launch.

---

## 3. Information architecture
Bottom navigation has 5 slots: **Today · Trends · [+] · Foods · More**. The center + is a 60px raised circle that opens the Logger sheet.

## 4. Screens

### 4.1 Today
**Sticky top block.** The status bar, header and summary card are one color block (`--hero`) with a bottom radius of 24px. The block is `position: sticky`, so it stays in place while the list scrolls under it.
- **Header row**: a 3-column grid `1fr auto 1fr`. The day label is truly centered.
  - Left: the close-day check button (40px circle; open = white at 14%, closed = solid white with a hero-colored icon).
  - Center: ‹ label ›, where the label is "Today", "Yesterday", "Tomorrow" or "Thu, Oct 22" (800 weight, 1.5em). Tapping the label opens the **calendar sheet**. You can go up to 6 days into the future.
  - Right: a **+** (Add menu) and a **⋯** (Day menu), each 40px.
- **Summary card** (only shown when calories are SHOW), with 3 swipe pages using horizontal scroll-snap. Below it, a dots row with ‹ › that's 22px tall. Use a HorizontalPager.
  - The title row reads `ENERGY SUMMARY ›`, `MACROS ›` or `NUTRITION SCORES ›` (0.75em, 800 weight, uppercase, 0.08em tracking) and opens Nutrients detail. On page 1 there's a right-side toggle `TARGET ⇄`, `REMAINING ⇄` or `BALANCE ⇄` that cycles the third ring. The choice persists in settings as `ringMode`.
  - Page 1 has 3 rings at 62px (stroke 8 on an 86 viewBox, track white at 13%):
    - **Eaten**: a full ring split by protein, carbs and fat.
    - **Expended**: a full ring split into resting, movement and workout in white at 92%, 60% and 36%.
    - **Third ring**: Target (fill = eaten/target), Remaining ("On target" when in range), or Balance (signed, like "−950").
  - Page 2 shows macros in a 2×2 grid: dot, label, grams, and a 5px bar.
  - Page 3 shows 4 nutrition scores in a 2×2 grid. If micros are HIDE, it shows "Nutrition scores are off" with a **Turn on** button.
- When calories are SOFTEN or DELAY, the alternate cards render instead: a soft card, a locked card ("Open at 9:00 pm" / "Saved for your weekly check"), a macros card and an energy card. In meals-layout modes, the 2×2 meal tiles render.
- **Cards** run edge to edge: horizontal margin `-20px` against the 20px page padding, radius 22, background `--surface`, padding 16px 20px.
- **Water**, in one row: icon tile, "Water", `750 of 2,000 ml`, a segmented bar (one segment per goal glass, 6px tall, `--c-fat`), and a + button that opens the **Water sheet**.
- **Uncategorized card**: only shown when it has items, or always when meal groups are off. When groups are off it's titled "All food" and holds every item.
- **Meal cards** (Breakfast, Lunch, Snack, Dinner):
  - Header: a colored icon tile, the name and time, kcal on the right, and a 40px + that opens the **Meal sheet**.
  - Below the header: a macro line such as `47 g protein · 80 g carbs · 8 g fat`. Each part only appears when that macro is SHOW.
  - Item rows: name, then `amount · precision` (Weighed / Estimate / Named), with kcal on the right.
  - Optional at the bottom: "how it felt" chips and a note line (or a mic icon for a voice note).
- **Health card**, collapsible:
  - Rows: Steps, Heart rate (with the day's range), Resting HR, HRV, Sleep, VO2 max, and Walking (with kcal only when energyOut is SHOW).
  - Which rows show is set under More → Watch and health apps. It's not shown on future days.

### 4.2 Sheets on Today
- **Add menu** (header +): a popover with Food, Scan a barcode, Photo, Describe it, Glass of water, Weigh-in, Note, and Start/End fast (only when fasting is on).
- **Day menu** (⋯):
  - Close the day
  - This day's nutrients
  - Copy previous day
  - Copy this day to tomorrow
  - Add a note for the day
  - Turn meal groups on/off
  - Hide/Show water
  - Fasting
  - Clear this day: the first tap changes the label to "Tap again to clear", the second tap clears.
- **Calendar sheet**: a month grid. A dot marks days with something logged, the selected day gets an accent circle, and today gets an accent ring. Days more than 6 ahead are disabled. Footer buttons: Today and Done.
- **Meal sheet** (the + on each meal):
  - Add food, Scan a barcode, Photo
  - **Voice log**: tap to record, then Save; it's stored as a voice note on the meal
  - **Add a note**
  - **How it felt**: Hunger before (Very hungry / Hungry / Peckish / Not hungry), Fullness after (Still hungry / Satisfied / Full / Very full), Mood with multi-select (Calm, Happy, Social, Rushed, Stressed, Anxious, Low), and the caption "Only you see this"
  - Copy yesterday's {meal}
  - Ate, no details
  - Save as a meal, which opens Create a meal pre-filled
- **Water sheet**: Add a glass (shows the size), a custom amount input with Add, Remove a glass, and Water settings.

### 4.3 Logger (center +)
A full-height sheet (93% tall), modeled on MacroFactor.
- **Pinned header**:
  - ✕ button and meal chips: Breakfast, Lunch, Snack, Dinner, Uncategorized.
  - A live totals strip: 4 columns for Energy, Protein, Carbs and Fat, each with `x / goal` and a bar. When nothing numeric is visible, it shows meal checkmark chips instead.
  - A search field with a barcode button.
  - Method chips that scroll sideways: Photo, Describe it, Water, Saved meals, Yesterday's {meal}, Ate no details, Weigh-in, and Just a number. "Just a number" becomes "Add a note" when calories are HIDE.
- **Body**:
  - When the search is empty: a **Favorites** row of round icon tiles with a + badge, "Usual at {meal}" picks, and Recent.
  - When typing: results.
  - Each row has an icon tile, the name, `amount · 1P 27C 0F` (each macro only if SHOW), kcal, and a + that toggles into a ✓.
- **Basket**: tapping + adds a food to the basket. A pinned footer appears only when something is picked: "N picked", the names, the kcal total, and **Add to {meal}**. Tapping a food's name opens the amount sheet, and "Pick this amount" returns to the logger.
- **Amount sheet**:
  - "How sure are you?": Weighed / Estimated / Just the name.
  - An amount stepper with unit chips.
  - Nutrition tiles (policy-gated).
  - Optional hunger and fullness chips, plus "Stop asking these".

### 4.4 Log screen (behind Photo / Describe / Saved)
- **Photo**: a placeholder image and a note field ("Anything the photo can't show"). Make an estimate gives editable rows, then Add.
- **Describe**: free text ("2 eggs and toast") gives parsed items. "Or just a number" only appears when calories aren't HIDE. There's also "Just save the name".
- **Saved**: copy yesterday's meal, plus a saved meals list.

### 4.5 Day close
Shows totals or a soft line, the range, and protein. A **big-day card** appears only when calories are SHOW and eaten is over the max. It has commitment buttons that save nothing. Then a note field and Done.

### 4.6 Trends
- Header: the range label, the "Trends" title, and a **settings button** (tune icon) that opens the **Trends settings sheet**:
  - Date range: Week, 4 weeks or 3 months.
  - Week starts on.
  - Show on Trends switches: intake chart, recap, nutrients, expended and weight.
- Cards:
  - **Intake chart**: daily bars, or weekly averages at 4 weeks and 3 months, with the range band shaded.
  - **Recap**: breakfast days, new foods, days logged, and a dots-per-meal row. It always covers this week.
  - **Nutrients**: macro bars, tapping through to Nutrients detail on Week average.
  - **Expended**: 7-day average and a sparkline. Only shown when energyOut is SHOW.
  - **Weight**: current weight plus the trend line.

### 4.7 Nutrients detail
A Day / Week average toggle. Energy and macros as bars. Vitamins and minerals are off by default; when off, a card explains that and offers **Show vitamins and minerals**.

### 4.8 Weight
Hidden / Trend only / Numbers switch. Range chips: 1W, 1M, 3M. Shows daily dots plus the smoothed trend line (EMA with α 0.2). When hidden, weigh-in input is masked (password type).

### 4.9 Foods
- The search bar is **sticky**.
- Create rows: Create a meal, Add a custom food.
- Then Your foods, Saved meals and Recent.
- **Create a meal**: name, "Usually at" chips, item list with remove, a total line, and an add-foods search list. Save is disabled until there's a name and at least one item.
- **Add a custom food**: name, brand, "One serving is" (amount plus unit chips), and nutrition fields per serving. Only the fields whose metric isn't HIDE are shown. In Blind, instead of fields, show "Nutrition stays out of view" with "Photograph the label".

### 4.10 More
Modes and numbers, Targets, Reminders and celebrations, Watch and health apps, Widgets, Fasting, Support and sharing.
- **Modes and numbers**: preset list (radio), then a per-metric matrix (Show / Soft / Later / Hide), the pause toggle, a link to Targets, and a "Last changed by you, …" note.
- **Targets**:
  - Energy range with Lower/Raise in 50 kcal steps. Lower is blocked by the floor rule.
  - Macros by Ratios or Grams.
  - Exercise adjustment link.
  - When calories aren't SHOW, show "Targets work in the background" instead.
- **Watch and health apps**: sources list, "Health on Today" master switch plus per-row switches, energy rows, the exercise-adjustment radio, and days left out of the math.
- **Water settings**: Show on Today, daily goal (6, 8, 10 or 12 glasses), glass size (200, 250, 330 or 500 ml), units (ml / fl oz), reminders.
- **Fasting**: timer switch, show on Today, eating window, and a status card with Start/End. In other modes, show "Fasting isn't part of this mode" and link to meal reminders.
- **Reminders**: quiet mode; per-event Off / Subtle / Full for big day, new foods, weekly recap and meal reminders; meal reminder switches; tone (Plain / Warm); hunger questions switch.
- **Support**: helpline, find a provider, trusted person and clinician sharing ("Coming later"), export CSV, delete everything (with a confirm step).

### 4.11 Onboarding
1. Pricing comes first (Free: logging, barcode scan, recipes, every mode. Plus at $3.99/mo, a placeholder: photo estimates and watch quick-log).
2. Four skippable questions.
3. A recommended mode with alternatives. If the user said numbers have been hard before, the Full numbers option is hidden.

---

## 5. Design tokens

### Type
- **Atkinson Hyperlegible Next** at weights 400, 500, 600, 700 and 800. Base 16px; large text 19px. Tabular numbers on every figure.
- Scale: h1 2em/800, sub-screen h1 1.75em/800, card title 1.0625em/800, body 1em, secondary 0.875–0.8125em, captions 0.75em, ring labels 0.75em.
- Icons: **Material Symbols Rounded**, optical size 24, weight 500, FILL 0 or 1.

### Base colors, light
`--bg #F5F2F4 · --surface #FFFFFF · --ink #1B1520 · --muted #5E5664 · --line #E7E1E8 · --track #E9E3EA · --soft #EFE9F1 · --on-accent #FFFFFF`

### Base colors, dark
`--bg #141016 · --surface #1F1922 · --ink #F2EDF4 · --muted #ABA2B0 · --line #2F2733 · --track #2F2733 · --soft #2A222E · --on-accent #141016`

### Brand palettes (these set `--hero`, `--accent`/`--fill` and `--t-accent`)
| Palette | Light hero / accent / tint | Dark hero / accent / tint |
|---|---|---|
| **Pine (default)** | #143D37 / #1D7A6B / #DCEFEA | #1A4740 / #5CC2AE / #183A35 |
| Espresso | #2D231E / #A4512D / #F5E4DA | #3A2D26 / #E39A6F / #3A2A20 |
| Clay | #6B3324 / #B0472C / #F7E2DA | #5A2A1E / #EE8D6E / #3E2219 |
| Plum | #2E1A3B / #6B3FA0 / #EDE5F6 | #3A2149 / #B79AE6 / #33274A |

The user's latest preview pick was **Espresso**. Confirm the final palette with them.

### Nutrient colors (they only tell nutrients apart; they never mean over or under, and there's no red/green)
| | Light color / tint | Dark color / tint |
|---|---|---|
| Protein (raspberry) | #D1477A / #FAE3EC | #EE77A0 / #3D2230 |
| Carbs (apricot) | #E0892B / #FCEBD8 | #F2A65A / #3D2C1A |
| Fat (blue, also used for water) | #3D7FD9 / #DFEAFA | #6FA4EE / #1C2B40 |
| Fiber (walnut) | #8E6E3A / #F1E8DC | #C29A62 / #33291C |

Meal icon tiles: Breakfast → carbs, Lunch → fat, Snack → fiber, Dinner → protein, Uncategorized → muted on `--soft`.

### Shape, spacing and elevation
- Phone frame: 390×844. Page padding 20px, gap between sections 14px.
- Radius: cards 22, sheets 28 (top only), sticky top block 24 (bottom only), inputs 12–14, icon tiles 12, chips and pills 999.
- Touch targets ≥ 44px. The summary-card header buttons are 40px by design, and the dots row is 22px.
- Shadows: sticky block `0 8px 20px rgba(30,15,40,.14)`; popover menus `0 14px 40px rgba(10,12,20,.25)`; center + button `0 8px 20px rgba(20,20,30,.28)`. Sheet scrim `rgba(10,12,20,.45)`.
- Switches are 50×30 with a 24px white knob: accent when on, track color when off.

### Copy voice
Warm, direct and short. "On target", not "0 left". Keep "No extra credit for coming in under." Never "cheat", "bad", "guilt", "burn it off" or anything like them. A Plain or Warm tone setting swaps empty-state lines.

## 6. State
- **Settings** (persist with DataStore):
  - mode, overrides, lock, exercise, quiet, tone, weekStart, askTags, events, mealRem
  - ringMode, trRange, trCards
  - waterGoal, glassMl, waterUnit, waterRem
  - fasting, fastToday, fastPlan
  - mealGroups, healthToday, healthRows, healthOpen
  - changedAt
- **Day data**: meals[] (`{name, time, items[]}`), loose[] (Uncategorized), water ml, mealNotes{meal: {voice, text}}, mealFeel{meal: {hunger, full, mood[]}}, ate{}, closed{}, dayNote.
- **Item**: `{name, amount, precision: Weighed|Estimate|Named, kcal?, p, c, f, fib}`. kcal can be null for name-only entries.
- Repositories: the `FOODS`, `MEALS`, `WEIGHTS`, `WATCH`, `MICROS` and `WEEKLY` mocks in the Phone logic become fake in-memory repositories first, then Room plus Health Connect.

## 7. Assets
There are no raster assets. All icons are Material Symbols Rounded ligatures, and food photos are placeholders.

## 8. Files in this bundle
- `Phone.dc.html`: the whole app UI and logic. The `<script data-dc-script>` block at the bottom holds the policy, presets, mock data and the `renderVals()` view-model, which is effectively the ViewModel spec.
- `Friendly Tracking.dc.html`: the host page with three tabs: the clickable Prototype, a Preview gallery of every screen in every relevant mode, and the **Blind leak test** (`SURFACES` list plus the regex).
- `support.js`: the runtime needed to open the .dc.html files in a browser. Open `Friendly Tracking.dc.html` directly.
- `PROTOTYPE_NOTES.md`: earlier decisions and open questions for the product doc.
- `COMPETITIVE_REVIEW.md`: the comparison with MacroFactor and Cronometer, and what was taken or left out on purpose.

## 9. Suggested build order
1. Domain: `Metric`, `Visibility`, `ModePreset`, `VisibilityPolicy` plus unit tests (formatting, soften words, delay or reveal, the "On target" rule, the floor rule).
2. The `NutrientValue` composable and test tags. Port the Blind leak test early and keep it in CI.
3. Today (sticky block, pager, meal cards, Uncategorized, water, health) and the Logger with its basket.
4. Foods (create meal, custom food), Nutrients, Trends plus settings, Weight.
5. More: Modes matrix with friction, Targets, Water, Fasting, Health, Reminders, Support.
6. Onboarding.
