# How the big trackers set calorie and macro goals

Research for the Targets rework (2026-10-27). Cronometer, MyFitnessPal,
MacroFactor and Lose It, from their own help centres. Claims we could not
confirm from an official source are listed at the end.

## What we took from it

- **Burn comes from a profile when there is no wearable.** Every app starts from
  resting energy (Mifflin-St Jeor where it is published) times an activity
  level, and adds logged exercise on top. So Opson's Burned falls back to the
  resting calculation plus an everyday activity level, and a deficit or surplus
  works without Apple Health or Health Connect.
- **Calories drive, macros follow as percentages; grams break the link.** That
  is the shape of every app's editor. Opson keeps its two macro modes: "% of
  calories" (grams follow the calorie goal) and "Grams" (they stay put). In
  grams, the card says when the macros add up to more or less than the goal
  and offers both fixes: make the goal match the macros, or fit the macros to
  the goal.
- **Presets plus custom.** MyFitnessPal and Cronometer both offer a handful of
  named splits and a custom option. Opson offers Balanced, Higher protein and
  Lower carb, then Custom as soon as a number is touched.
- **Steppers and typed entry, in sensible steps.** MyFitnessPal steps
  percentages by 5; Cronometer and Lose It take typed grams. Opson does both: a
  stepper for small moves and a typed field for a known number.
- **Per-day goals are independent days, not a weekly budget.** MyFitnessPal and
  Cronometer let each weekday have its own goal and sum whatever you set. Lose
  It and MacroFactor rebalance the other days to hold a weekly total, which
  would quietly lower some days. Opson keeps independent days, because a day
  must never go down because another went up.
- **Floors are soft, as everywhere else.** MyFitnessPal and Lose It use 1,200
  and 1,500 kcal lines and warn rather than block. Opson did block, holding
  every goal at or above where the person started until a dietitian set a
  floor, and that locked everyone's low end at their first range. Since
  2026-10-29 (`docs/decisions.md`) the only hard limits are 800 and 6,000 kcal,
  and under the 1,200 and 1,500 lines Targets shows a gentle note with a link
  to Find support. The deficit and surplus caps still wait on a dietitian.

## Cronometer

- Resting energy is Mifflin-St Jeor, or a fixed number the user enters.
  Baseline activity adds a fraction of it: Sedentary 0.2 (the default), Lightly
  active 0.375, Moderately active 0.5, Very active 0.9.
  [Energy Expenditure](https://support.cronometer.com/hc/en-us/articles/31974307318420-Energy-Expenditure)
- The target comes from a weight goal, a rate stepped with + and −, which shows
  the daily deficit or surplus. A typed custom target is under Advanced.
  [Energy Target](https://support.cronometer.com/hc/en-us/articles/31975503009044-Energy-Target)
- Exercise above baseline can be added to the target, with a switch.
  [Energy Target](https://support.cronometer.com/hc/en-us/articles/31975503009044-Energy-Target)
- Per day: the Macro Scheduler assigns named templates (ratios, fixed grams or
  keto, each with its own energy target if wanted) to weekdays. No weekly
  total ties them together.
  [Macro Scheduler](https://support.cronometer.com/hc/en-us/articles/4406127050260-Macro-Scheduler)
- Macros: Ratios are percentages of the energy target and rescale with it;
  Fixed is grams per macro. Default split 25 / 45 / 30.
  [Edit Macronutrient Targets](https://support.cronometer.com/hc/en-us/articles/360060119292-Edit-Macronutrient-Targets),
  [blog](https://cronometer.com/blog/training-stalled-how-to-check-protein-calories-and-carbs-in-cronometer/)
- A low-energy rate asks the user to confirm a "Goal Weight Review" message.
  No numeric floor is published.
  [Mobile Weight Goal](https://support.cronometer.com/hc/en-us/articles/32978027080980-Mobile-Weight-Goal)

## MyFitnessPal

- The goal comes from age, height, weight, sex and activity level (Sedentary,
  Lightly active, Active, Very active, describing daily routine, not workouts),
  adjusted by the weekly rate.
  [Initial goals](https://support.myfitnesspal.com/hc/en-us/articles/360032625391-How-does-MyFitnessPal-calculate-my-initial-goals),
  [Macro Calculator](https://support.myfitnesspal.com/hc/en-us/articles/24763932864397-Macro-Calculator)
- Logged exercise raises that day's goal; turning that off is Premium.
  [Exercise calories](https://support.myfitnesspal.com/hc/en-us/articles/360032623851-Why-your-daily-nutrient-values-and-calorie-goal-change-you-log-exercise)
- It does not calculate goals under 1,200 (women) or 1,500 (men).
  [Complete entry](https://support.myfitnesspal.com/hc/en-us/articles/360032624131-How-does-the-Complete-This-Entry-or-Finish-logging-for-today-feature-work)
- Macros: free users set percentages in 5% steps that must total 100%; Premium
  adds grams. Presets: Default (20 / 50 / 30), High protein, Low carb, Keto,
  Paleo, Custom.
  [Customize goals](https://support.myfitnesspal.com/hc/en-us/articles/360032274432-Customize-your-nutritional-goals)
- Premium "different goals by day" covers calories and macros; the week is
  the sum of the days.
  [Premium features](https://support.myfitnesspal.com/hc/en-us/articles/360032625951-MyFitnessPal-Premium-features)

## MacroFactor

- Starts from its own resting and activity equations (not published), then
  re-estimates maintenance from logged intake and weight trend after two to
  four weeks.
  [Algorithms](https://macrofactor.com/macrofactors-algorithms-and-core-philosophy/)
- Coached, Collaborative and Manual programs. The 1,200 kcal floor applies to
  Coached only.
  [Program Styles](https://help.macrofactorapp.com/macro_program/program_styles)
- Diet styles Balanced, Low-fat, Low-carb, Keto; protein Low to Extra high.
  Calories can be shifted to chosen days inside the weekly budget, and editing
  one day spreads the difference over the unlocked days.
  [Coached options](https://help.macrofactorapp.com/en/articles/34-what-are-the-different-program-options-in-coached-mode),
  [Rollover](https://help.macrofactorapp.com/en/articles/108-how-to-adjust-calorie-targets-to-account-for-overages-or-to-roll-over-unused-calories)
- Ignores wearable burn on purpose.
  [Wearables](https://help.macrofactorapp.com/en/articles/33-does-macrofactor-use-energy-expenditure-data-from-my-wearable-activity-tracker)

## Lose It

- Resting energy times one of four activity levels, minus 250 to 1,000 kcal a
  day for 0.5 to 2 lb a week. A typed custom budget is also allowed.
  [Budget calculation](https://loseit.zendesk.com/hc/en-us/articles/47497714327060-How-the-Calorie-Budget-is-Calculated)
- Warns, but does not block, when the weekly average falls under 1,500 (men)
  or 1,200 (women).
  [Low Budget Warning](https://loseit.zendesk.com/hc/en-us/articles/47345064673556-Low-Budget-Warning)
- Calorie Schedule: high days get +150 to +500 and the other days drop so the
  seven-day average holds.
  [Calorie Schedule](https://loseit.zendesk.com/hc/en-us/articles/50220249404308-Managing-Calorie-Schedule-and-Custom-Budgets)
- Macros as a % range or fixed grams, 4 / 4 / 9, independent of the budget.
  [Macronutrient calculation](https://loseit.zendesk.com/hc/en-us/articles/51382066592148-Macronutrient-Calculation),
  [Nutrient goals](https://loseit.zendesk.com/hc/en-us/articles/47649705562004-How-to-Create-Nutrient-Goals)
- Steps from the phone's health store can earn a calorie bonus.
  [Calorie Bonus](https://loseit.zendesk.com/hc/en-us/articles/47492627092372-The-Calorie-Bonus)

## Not confirmed from an official source

- Which resting equation MyFitnessPal and Lose It use, and their activity
  factors.
- MacroFactor's protein amounts per kilogram and its kcal per gram.
- Whether Cronometer or MyFitnessPal recompute calories when typed grams do
  not add up.
- Cronometer presets named Balanced, Low carb or High protein (its docs name
  only Ratios, Fixed and Keto).
