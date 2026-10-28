# Product ideas from comparing Opson with Cronometer and others (2026-10-27)

Research covered Cronometer, MacroFactor, MyFitnessPal (MFP), Lose It, Yazio, Recovery Record, Rise Up + Recover and Bitesnap. Sources were help centres, forums, App Store and Trustpilot reviews, and two studies. Reddit was blocked for the research tools.

Each item is ranked by conviction. **Status** says what happened to it.

Sizes are S, M or L.

## Ranked

| # | Idea | Size | Status |
|---|---|---|---|
| 1 | Quick add in every mode: kcal plus protein, carbs, fat and fiber in the Describe pane, grams only in Macros with grams | S | Built |
| 2 | Edit, rename and delete saved meals and Your foods (Foods has no edit or delete today) | S/M | After the food detail work lands |
| 3 | Tap a nutrient to see which foods gave it (Cronometer's core report) | M | Building |
| 4 | Micronutrient targets by sex and age, an override per nutrient, and you pick the four on Today | M | Building |
| 5 | Say when a nutrient % rests on partial data ("9 of 12 foods list vitamin D"), and show "not listed" instead of 0 | S/M | Building |
| 6 | A summary the person makes for their provider: pick a range and contents, preview it, share a PDF; offered to people who said they work with a provider | M | Built, behind `release.providerSummary` |
| 7 | An export with everything: notes, check-ins, fasts, water, weight, custom foods, meals, settings, a daily summary | S/M | Built: `opson-export.zip` |
| 8 | oz, lb and ml for any food with a known weight or volume | S | Added to the food detail work |
| 9 | Recipes with servings and a cooked weight | M/L | Later |
| 10 | Merge "untracked" and "left out of the math" days into one state | S | Later |
| 11 | Regroup More by what people are trying to do | S | Later |
| 12 | Change day by swiping the date header; long-press for the calendar; a "Back to today" chip | S/M | Later |
| 13 | Meal groups people can rename, hide or add to (up to three snacks; ED meal plans are 3 + 2 or 3) | L | Later, needs a migration |
| 14 | Lock screen widget, interactive widgets (add a glass, log my usual), app icon quick actions, Siri | M | Later |
| 15 | Photo-only meals for No numbers (a photo journal, no estimate) | M | Ask first: it overlaps "Ate, no details", which was cut |
| 16 | Reminders that back off after days of no response, a week's pause, a daily cap | S | Later |
| 17 | Coming back after a gap: a neutral greeting and one tap to leave the missed days out; never count them | S | Later |
| 18 | Copy a meal from any day; offer "Your usual breakfast" when the same foods repeat | S/M | Later |
| 19 | "Something looks wrong with this food" goes to the same review queue as label submissions; collapse rounding duplicates | S | Partly in the food detail work |
| 20 | Accessibility: full system text size, spoken chart summaries that follow the mode | M | Partly in the chart work |
| 21 | Burned estimated from intake and weight trend, All numbers only, never for "numbers are hard" | L | Later |
| 22 | Fasting window on the "When you eat" chart; fix logging yesterday ending today's fast | S | Later |

## Don't build, or build the safe version

- **No streaks.** People describe real distress when one breaks. Keep "Days logged 5 of 7" as plain text.
- **No red or orange, "over", or negative remaining**, micronutrients included.
- **No "if every day were like today you'd weigh…"** projections.
- **No food grades, scores, tips after meals, or rewards.**
- **No live clinician dashboard by default.** Start with the one-off summary (6).
- **Every new screen gets a leak test.** Cronometer's hide-calories mode still shows kcal while adding a food.
