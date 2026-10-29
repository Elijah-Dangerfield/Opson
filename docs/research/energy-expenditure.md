# Energy expenditure and manual exercise

Researched 2026-10-27, for logging exercise by hand and counting it in Burned
without a health app. What four trackers do, then what Opson does and why.

## What the others do

**Cronometer** adds five parts: BMR (Mifflin-St Jeor), a baseline activity
level, tracker activity, exercise and, off by default, the thermic effect of
food. The baseline is a fraction of BMR added on top: None, Sedentary (BMR ×
0.2), Lightly active (× 0.375), Moderately active (× 0.5), Very active (× 0.9),
or a custom number. The whole day's BMR and baseline count from the start of
the day, so a target can be planned before anything is logged. Exercise is
logged from the Compendium of Physical Activities; user tests show it counts
(MET − 1) × kg × hours, so an hour of a 1.0 MET activity burns 0 extra. A
tracker that counts general activity gradually replaces the baseline ("Adjusted
Baseline Activity") so the two are never added together, and staff tell people
who wear a device all day to set the baseline to None. For Garmin workouts it
subtracts BMR for the workout's length, because the watch's number includes
resting energy.
([Energy Expenditure](https://support.cronometer.com/hc/en-us/articles/31974307318420-Energy-Expenditure),
[Mobile Energy Expenditure](https://support.cronometer.com/hc/en-us/articles/33132110153492-Mobile-Energy-Expenditure),
[baseline levels, forum](https://forums.cronometer.com/discussion/1206/calories-summary-section),
[do exercise calories include BMR, forum](https://forums.cronometer.com/discussion/3093/do-exercise-calories-include-bmr),
[Garmin](https://support.cronometer.com/hc/en-us/articles/16957825757204-Garmin))

**MyFitnessPal** adds logged cardio to the day's goal straight away, with gross
MET calories (resting included). With a tracker synced it computes a "calorie
adjustment": the tracker's burn minus MFP's own estimate minus logged
exercise. That adjustment can go negative, which is how it avoids counting a
workout twice, but negative adjustments are off by default, and the forums are
full of people double counting or confused by it.
([Understanding your Calorie Adjustment](https://support.myfitnesspal.com/hc/en-us/articles/360032623871-Understanding-your-Calorie-Adjustment),
[forum](https://community.myfitnesspal.com/en/discussion/10891381/apple-watch-calorie-adjustment-exercise-calories))

**Lose It!** starts from a Target Burn, BMR × a chosen activity level, and only
gives a "calorie bonus" when the synced burn (Apple Health resting plus Apple
Watch active energy) goes past it. Workouts that sync from a tracker show
crossed out and do not add, because active energy already holds them; a
workout the watch did not see can be switched back in with "Exclude from Total
Calories".
([The Calorie Bonus](https://loseit.zendesk.com/hc/en-us/articles/47492627092372-The-Calorie-Bonus),
[Using Apple Watch with Lose It!](https://loseit.zendesk.com/hc/en-us/articles/47273244741396-Using-Apple-Watch-with-Lose-It))

**MacroFactor** does not use exercise at all. Expenditure comes from logged
intake and the weight trend over about three weeks, solved backwards from
energy balance, so activity changes show up as measured expenditure rather
than estimates.
([Expenditure](https://help.macrofactorapp.com/dashboard/expenditure/),
[V3 algorithm](https://macrofactor.com/expenditure-v3/))

**MET values** come from the 2024 Adult Compendium of Physical Activities
(Herrmann et al.), which lists each activity with a code and a MET value,
for example walking 3.5 to 3.9 mph at 4.8 (17200) and running 6 mph at 9.3
(12050). Energy is MET × kg × hours.
([2024 Adult Compendium](https://pacompendium.com/wp-content/uploads/2025/02/1_2024-adult-compendium_1_2024.pdf))

## What Opson does

Burned already had resting energy (Mifflin-St Jeor, the health app's own
number, or one the person types) plus the health app's movement and workouts.
Two things join it: everyday activity, with `release.goalsAndEnergy`, so a
deficit works without a health app, and logged exercise, with
`release.exercise` on.

**Logged exercise.** An entry is an activity from a list of about fifty common
Compendium activities, a duration and a start time. Its energy is
(MET − 1) × latest weigh-in in kg × hours: net of resting energy, which Burned
already counts for the whole day, as Cronometer does. The person can type their
own number instead (a machine's display, a watch that does not sync), and
"Other" is a name and a typed number. The number is worked out when the entry
is saved and stored with it, so a later weigh-in does not rewrite past days.
With no weigh-in there is no estimate and the sheet asks for a number.

**Everyday activity.** A baseline for life outside workouts, as a fraction of
resting energy: Mostly sitting (0.2, the default), Some walking (0.375), On
my feet a lot (0.55), Physical work (0.725). These are the usual activity
factors (1.2 to 1.725 × BMR) less the BMR itself. Like Cronometer, it counts in
full from the start of the day so a deficit can be planned in the morning.
Without it, a deficit aimed at resting energy alone would set a goal several
hundred calories too low for almost everyone.

**The rule against double counting.** One source of activity per day:

- With a health app connected and reading activity (`release.health` on, the
  store connected, a reading for the day), Burned's activity is the app's
  movement and workouts. Everyday activity is left out, since the watch's
  movement is the measured version of it, and logged exercise is left out,
  since the watch saw it. An entry marked "My watch didn't record this" still
  adds, like Lose It's switch.
- Without one, Burned's activity is everyday activity plus every logged entry.
- "Add activity to Burned" off still means resting energy only, whatever the
  source.

So switching Health on or off moves a day between the two sources whole, never
half of each.

**The thermic effect of food, off by default.** Digesting food costs about 10%
of what is eaten (measured values run from about 8 to 15%, more on a
protein-heavy day). "Count digesting food" on Resting energy adds
`THERMIC_EFFECT_OF_FOOD_SHARE` (0.10, `libraries/nutrition`'s `Burned.kt`) of
the day's eaten to Burned as `Burned.foodKcal`, a part of its own: not
activity, so "Add activity to Burned" off keeps it, and it adds on either side
of the one-source rule. Neither health store counts it: Apple Health's and
Health Connect's resting and active energy are modelled from the body, never
from food, so it cannot count twice whether the day reads the store's active
energy or its resting energy too. It does make Burned rise with what is eaten,
so a deficit aimed at Burned moves a little as food is logged (10 kcal per 100
eaten). That converges and is the person's choice, which is why it is off
until they turn it on, as Cronometer has it. Today's Expended so far and
Deficit so far stats, which read the health app's total directly, add it the
same way. Trends' Expended card does not: it is the watch's daily average, not
Burned.

**Not done:** subtracting everyday activity for the length of a workout
(Cronometer does this for workouts over an hour). The overlap is small next to
the error in any MET estimate.

**Where it shows.** Burned is a number, `Metric.EnergyOut`, so it follows the
visibility levels already in place: All numbers shows it; Macros with grams,
Macros only and No numbers hide it. Exercise exists only to count energy, so
where energy out is hidden the Exercise option is not offered and Today's
Activity card does not draw. It is also never offered to someone who said
numbers have been hard, like exercise in the range and fasting
(TODO(clinician) to confirm). Goals read Burned through `BurnedRepository`,
unchanged, so a deficit or surplus works without a health app.

**Sync.** Entries are a new sync entity, `exercise`, in a new table `exercises`
(V24), returned only to clients that send `accepts: ["exercise"]`, so 0.1 and
earlier 0.2 builds get exactly the response they did before.

**Today's Activity card** (2026-10-29) shows Burned as lines that add up to it:
resting energy, then the health app's active energy or everyday activity, then
logged exercise, then digesting food when it counts. With a health app counting
activity it says "From Apple Health", and adding exercise is a quieter button
for what the watch missed, opening the sheet with "My watch didn't record this"
on, under a line saying the watch's workouts are already counted. That is Lose
It's approach: logging by hand stays possible for a swim the watch did not see,
but the default path cannot double count. The store's workouts are not
imported as entries: `HealthReader` reads energy sums, not workout samples, so
the card shows them as the workouts part of active energy.
