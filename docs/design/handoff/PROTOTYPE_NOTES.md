# Friendly Tracking, MVP prototype

Open `Friendly Tracking.dc.html`. Three tabs:

- **Prototype**: one clickable phone. Mode chips and a "big day" toggle sit beside it for quick review. Settings save to localStorage under `ft-settings-v1`.
- **Preview gallery**: every screen in every mode where it looks different.
- **Blind leak test**: the acceptance check. It renders 18 surfaces in Blind and in Full numbers, then scans each one. Currently 18 of 18 pass.

All the app UI lives in `Phone.dc.html`. The page around it only hosts it. `Phone v1.dc.html` is the earlier plain version.

## Look and adding food

- An indigo summary card with a ring split by protein, carbs and fat. Each nutrient has its own color: coral, saffron, teal and lilac. The colors only tell nutrients apart. They never mean over or under.
- Each meal has its own color and icon. Blind mode gets a 2×2 grid of meal tiles, so it looks like a finished screen without any numbers.
- The center **+** opens a quick-add sheet. Pick the meal, then choose one of nine actions (search, scan, photo, say or type it, saved meals, yesterday's meal, ate with no details, weigh-in, just a number), or re-add a recent food in one tap. "Just a number" becomes "Add a note" when calories are hidden. The sheet is included in the leak test.

## Information architecture

Tabs are grouped by the question each one answers:

- **Today**: what did I eat on this day? Use the arrows to change days. The check at the top closes the day. Tap the summary card to see that day's nutrients.
- **Trends**: how am I doing over time? It holds the week's intake and recap, average nutrients (tap through for vitamins and minerals), and weight. Weight lives here and not in the nav because it's hidden in several modes and shouldn't get a tab of its own.
- **+**: the quick-add sheet.
- **Foods**: saved meals, recent foods, custom foods and meals. Copy and saved meals also show up in context, as "Copy yesterday's" on empty meals and in the + sheet.
- **More**: settings and support.

Nutrients is a single screen with Day and Week average views. Today opens it on Day, and Trends opens it on Week average.

## About the stack

The brief asks for Kotlin, Jetpack Compose and Material 3. This tool builds HTML prototypes, so you get a working HTML prototype with the same architecture, laid out so it maps one to one onto Compose. I haven't written or built the Android project. The next step is a Claude Code handoff using this file and the mapping below.

| Prototype | Compose |
| --- | --- |
| `PRESETS`, `M`, `LEVEL` at the top of the Phone logic | `data class ModePreset(val vis: Map<Metric, Visibility>)`, `enum class Visibility { SHOW, SOFTEN, DELAY, HIDE }` |
| `vis()`, `nv()`, `format()`, `soften()` | `class VisibilityPolicy(settings, clock)` with `fun resolve(metric, value, surface): Display` |
| `{on, soft, text}` objects plus `<sc-if>` | `@Composable fun NutrientValue(metric, value, surface)`, which renders nothing when hidden |
| `data-metric="…"` attributes | `Modifier.testTag("metric:calories")` |
| Leak test tab | `BlindModeLeakTest`: set Blind, walk every route and sheet, assert `onAllNodesWithTag("metric:calories")` is empty and no text matches the calorie regex |
| `FOODS`, `MEALS`, `WEIGHTS`, `WATCH` | Fake in-memory repositories in `commonMain` |

## How VisibilityPolicy works

There are 9 metrics: calories, remaining, watch energy, protein, carbs, fat, fiber, micronutrients and weight. Each one is set to one of four levels.

- **show**: the formatted amount.
- **soften**: a word or a fill bar, with no amount. Calories use "Partway through" or "In your range". Weight becomes trend only.
- **delay**: hidden until the reveal. Delayed reveal opens at day close and on the week view. Coast opens on the week view only.
- **hide**: nothing renders. The label goes too, not just the digits.

A mode preset is just a map from metric to level. Per-metric overrides from the matrix sit on top of it: `overrides[m] ?? preset.vis[m]`. Picking a preset clears the overrides. Today picks its layout (full, meals, rhythm, explorer) from a field on the preset, so no screen checks which mode is active.

No screen formats a nutrition number itself. Everything goes through `nv()`, which also handles "On target" in place of "0 left".

## Decisions where the doc was unclear

- **Nutrient focus, Fuel, Mindful, Sprint and Coast** reuse the five required Today layouts and add a banner or a softened card. They aren't separate builds.
- **Delayed reveal** is fixed at 9:00 pm. The picker for end of day, weekly or never isn't built yet.
- **Friction** only kicks in when "Pause before showing more" is on, and only when the new settings score higher (show 3, soft 2, later 1, hide 0). Showing fewer numbers always applies right away. The pause is 10 seconds plus an optional question about why. The trusted-person lock is v1.1, so it appears as "Coming later".
- **Exercise adjustment**: "Some" counts half of movement plus workouts. Tomorrow's target is always the base target, so a big day never lowers it.
- **Big-day card** shows only when calories are fully visible, never when softened. Its event setting does three things: Off hides it, Subtle shows the card, Full adds the commitment buttons. Full is the default. The buttons save nothing.
- **Quick add by number** is hidden whenever calories are hidden, since typing a number is itself a calorie entry.
- **Week view** shows the last full week from your chosen start day. Untracked days show up as gaps.
- **Pricing** ($3.99/mo Plus) is a placeholder. Barcode scan, recipes and every mode stay free.

## Safety placeholders

- `GOAL_FLOOR_KCAL = null` has a `TODO(dietitian)`. Until someone sets it, `setGoal()` won't let a target drop below the current one. I didn't make up a number.
- The helpline link points to `allianceforeatingdisorders.com`. TODO: check the URL and the provider-finder link before launch.
- The app has no evening "calories left" message, and there's no setting that turns one on. The Reminders screen says so.
- Settings only change when you change them. Mode changes record a timestamp, and the Modes screen shows who changed them and when.

## Things I'd change in the doc

1. **Define "soften" per metric.** "Fuzzy indicators or percent of goal" covers several ideas. Pick one for each metric, and decide whether a fill bar counts as a number. A bar can be read as one.
2. **Say whether meal count and weigh-in count are allowed in Blind.** I treated counts as fine and amounts as hidden. A clinician should confirm that.
3. **Separate the reveal time from the mode.** Coast and Delayed both mean "show later". A single `revealAt` setting (day, week, never) would make that clearer.
4. **Pin down the commitment button.** The doc calls it "Duolingo-style" but also says it records nothing. Duolingo-style suggests streaks, which the same doc rules out.
5. **Add gentle modes to the MVP table.** The table puts Rhythm and Explorer in v1.1, but the screen list wants them on Today for MVP. Moving them to MVP would make the two consistent.
