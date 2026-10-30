Your Opson export
================

Everything your account holds, made on {exported_at}. Each file opens in any
spreadsheet app. Times are shown in {time_zone}, the time zone your phone was
in when you made this, with the exact UTC time beside them.

Every number you logged is here, whichever mode you use in the app. If your
mode keeps calories, macros or weight out of sight, this file does not.

Files
-----

entries.csv
  Every food you logged: the day, the time, the meal, the name and amount,
  grams, calories (kcal), protein, carbs, fat and fiber in grams, vitamins and
  minerals where the food listed them (as JSON), how sure the amount was
  (weighed, estimate, label or named) and how you logged it.

day_notes.csv
  One row per day you wrote a note, said how the day felt, closed the day,
  marked it untracked, changed meal groups or marked a meal "ate, no details".

meal_notes.csv
  The note and hunger you added to a meal, with fullness and moods where an
  older version of the app asked for them.

check_ins.csv
  Close the day's check-in: hunger through the day, energy, sleep and moods.

water.csv
  Water per day, in millilitres.

weigh_ins.csv
  Every weigh-in, in kilograms.

exercise.csv
  Exercise you logged by hand: the activity, minutes, and the calories it
  counted in Burned.

fasts.csv
  Every fast: when it started and ended, its goal and how long it ran.

custom_foods.csv
  Foods you made, with their serving and numbers.

saved_meals.csv
  Your saved meals, one row per food in each meal.

recipes.csv
  Your recipes, one row per ingredient, with how many servings each makes,
  your notes, and its share code if you shared it.

daily_summary.csv
  One row per day with anything in it: how many foods, the day's totals,
  water, exercise, the last weigh-in that day, and whether it was closed or
  untracked. A total adds up the foods that had that number;
  foods_missing_kcal says how many did not, so a day with gaps reads as at
  least that much.

settings.json
  Your settings as the app stores them: your mode, targets, units, reminders
  and the answers you gave during setup.

Blank cells mean nothing was recorded, which is not the same as zero. Dates are
YYYY-MM-DD and times are 24 hour.
