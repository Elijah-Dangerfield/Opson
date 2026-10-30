# Goals v2: the goal engine

Owner feedback, 2026-10-30, on the weight goal that shipped on 2026-10-29. This doc is the design:
what was considered, what was picked, the math, and the edge cases. The code follows it; where the
code and this doc disagree, fix whichever is wrong in the same commit.

Who reads this: the agent building the goal UI (charts, Today's burn line, typed inputs) and the
agent reworking onboarding's "Do you want to set a goal?". Both build on the API in
[The engine API](#the-engine-api).

## What was wrong

1. **Pace was capped at 1% of body weight and 1,000 kcal a day,** whichever was lower, and could
   never take the day's aim under the low-goal line. For a 150 kg person that is 1,000 kcal, about
   0.9 kg a week, when clinical programmes often plan 1% a week. The cap also knew nothing about
   the person: a lean 70 kg person at 15% body fat can lose fat without losing muscle at only about
   700 kcal a day, so the old "safe" 1,000 was too fast for them.
2. **"Use my goal" landed on a different range each time.** Root cause, in order of impact:
   - The goal moved the *middle* of whatever range was on Targets and kept its *width*
     (`Targets.withEnergy` shifts both ends). Every hand edit in between changed the width, so the
     two ends the person reads came out different every time, even with the same aim.
   - The aim was worked out from a *live* burn: the latest weigh-in, and with "Health" as the
     resting source, the health store's most recent whole day. Onboarding's goal flow used the
     weight typed in the flow and ignored Health. So the flow wrote one number, Use my goal another
     the same day, and another again after any weigh-in or overnight.
   - Near the floor, `withEnergy` clamps the shift, so a range near 800 kcal never reached the aim.
3. **A goal and a hand edit were equals.** Any stepper on Targets silently took the target away
   from the goal, with a line saying so afterwards. Nothing stopped a lose goal running on a surplus.
4. **The goal picked "Range" for you.** A goal wrote a range of the old width, so the range looked
   like a setting that fed the goal, when the goal set the range.
5. **Every day stood alone.** 600 under one day and 400 the next read as one day off.
6. **Only weight goals existed.**
7. **Inputs changed under the goal silently.** Connecting Apple Health changes resting energy and
   activity, and with them what the goal means, with no word to the person.

## Decisions

### A goal owns its inputs: the baseline

A goal that sets a calorie target stores a baseline, the typical day's burn (TDEE) it was set
against and the inputs that produced it. The day's target is a pure function of the goal and its
baseline, so the same goal always gives the same target, on any phone, any day:

```
aim  = round10(baseline.burn + stage extra + goal delta)        (never under the low-goal line for a loss)
band = aim - tolerance .. aim + tolerance                        (tolerance 100 kcal by default)
```

The baseline only changes when the person accepts a recalculation (below). "Use my goal" re-applies
the stored baseline; it never re-reads a live burn.

**Typical day's burn** (`GoalBaselines.burnKcal`):

| Part | Where it comes from |
|---|---|
| Resting | The source picked on Resting energy: the equation (latest weigh-in), the health store's most recent whole day, or the person's own number. As Burned. |
| Activity | Health connected and activity counted: the mean of the last 7 whole days' movement and workouts the store read, rounded to 50, when at least 3 days have a reading. Otherwise the everyday activity level's share of resting, as onboarding has always worked it out. |
| Digesting food | With "Count digesting food" on, 10% of what the day's target has you eat. Solved for the target: `burn = (resting + activity + 0.1 x delta) / 0.9`. |

Rounded to 10. Activity is rounded to 50 so a store's day-to-day noise does not move the baseline.

Considered: aiming every day at that day's live Burned (a set deficit does this). Rejected for goal
targets: the number moves through the day as activity syncs, which is hard to plan meals around,
and it is exactly what made "Use my goal" non-deterministic. Considered: a 7-day average of the
whole Burned including logged exercise. Rejected: one long workout would move the goal for a week.

### Inputs changing: a recalculation the person accepts

The engine recomputes the baseline from current inputs whenever they change and compares
(`GoalBaselines.changes`). What it compares: resting source, resting kcal, activity source (level or
health), activity kcal, digesting food on or off, weight, height, age, sex, pregnancy or
breastfeeding, body fat.

- A **discrete** change (source switched, health connected or disconnected, digesting food toggled,
  activity level, height, age, sex, stage) offers a recalculation as soon as the new target differs
  from the current one by 10 kcal or more.
- A **drift** (weight, a health average) offers one only once the target would move by 100 kcal or
  more. A weekly weigh-in during a loss crosses that every month or so, which is about right.
- Body fat changes the personal fat-loss limit, not the target. It never prompts on its own; the
  new limit shows wherever pace is shown.

The offer says what changed, the old and new daily target, and gives **Use the new target** or
**Keep my current target**. Keep stores the inputs declined, and the same inputs are never offered
again; a further change is. Nothing about the target changes until the person answers. It shows on
Targets as a dialog when Targets opens, and over Today as its own dialog (`GoalRecalculationRoute`),
opened by a screen prompt (`goal_recalculated`) so the prompt rules pace it: a dialog prompt would
end for good after its first answer, and each recalculation is a new question. Closing either
without a choice asks again later.

To keep the flow and the repository from disagreeing (which would offer a recalculation the moment
a goal is saved), both build the inputs through `GoalBaselines.inputs` from the same
`BaselineEnvironment`: the account's resting source, own number, "Add activity" and "Count digesting
food", and the health app's readings (`HealthRepository.goalReadings`).

Considered: applying small changes silently. Rejected: the owner asked that people know everything
is taken into account, and a target that moves by itself is the bug in item 2.

### Pace: tiers and a personal fat-loss limit, never a block

Paces are offered up to 2% of body weight a week for a loss (1% for a gain), in whole steps. Each
pace gets a tier, shown as a note under the pace with an (i) that explains the idea and a Find
support link from the faster tiers:

| Tier | Loss, weight known | Loss, weight unknown | Gain | Note |
|---|---|---|---|---|
| Gentle | up to 0.5% a week | up to 500 kcal a day | up to 0.25% a week | "Gentle" |
| Steady | up to 1% a week | up to 1,000 kcal a day | up to 0.5% a week | "Steady" |
| Faster | over 1% | over 1,000 | over 0.5% | "Faster than most guidance. Consider talking to a doctor." |
| Above your limit | deficit over the personal fat-loss limit | | | "Above your estimated fat-loss limit. More of this would likely come from muscle." |

Above-the-limit wins over the pace tier: a lean person can cross it at a Steady pace.

**Personal fat-loss limit** (Alpert, 2005, "A limit on the energy transfer rate from the human fat
store in hypophagia", J Theor Biol 233:1-13). The most energy fat can supply a day is about
31.4 kcal per pound of fat mass (69.2 kcal per kilogram). A deficit past it is met mostly from lean
tissue.

```
fat mass (kg)  = weight x body fat %
limit (kcal)   = 69.2 x fat mass
```

Body fat comes from Profile. Without it, an estimate from BMI, age and sex (Deurenberg, Weststrate
and Seidell, 1991, Br J Nutr 65:105-114), clamped to 5 to 60%, and labelled as an estimate with an
invitation to add body fat on Profile:

```
adults (18+):   BF% = 1.20 x BMI + 0.23 x age - 10.8 x sex - 5.4      (sex: 1 male, 0 female, 0.5 not said)
13 to 17:       BF% = 1.51 x BMI - 0.70 x age - 3.6 x sex + 1.4
```

No height or age and no body fat: no limit, and the note says what would give one.

A goal's target is never worked out under 1,200 kcal, for anyone (`GoalTargets.WORKED_OUT_FLOOR_KCAL`,
the same line a set deficit already stops at). Under the person's own low-goal line (1,500 for a
man, 1,350 when sex isn't said) the target is set and the low-goal note shows with Find support. A
pace that would need less than 1,200 is not offered, and the step says so. That is the one place the
engine stops, and it is about how little someone eats, not how fast they lose: holding a 150 kg man
to 1,500 would have capped him under 1% a week. All lines are `TODO(clinician)`.

Considered: blocking past 1%. Rejected by the owner. Considered: a hard block at the fat limit.
Rejected: it is an estimate with wide error, and the person's own judgment wins.

### Locking: a goal's target is the goal's

With an active goal that sets the target, Targets shows the goal's target read-only: "1,850 a day,
on track from 1,750 to 1,950", where it comes from, and **Change**. Change opens a dialog:

- **Edit my goal** opens the goal flow.
- **Set my own target** keeps the goal (it is not deleted) but stops it setting the target. Targets
  unlocks and says "Your own target. Your goal isn't setting it", with **Use my goal**.
- Cancel.

Once unlocked, an edit that points the target against the goal (a loss goal with a target at or
above the baseline burn, a gain goal at or under it, a keep goal more than 300 kcal either side)
asks again, more firmly: "This target means eating more than you burn. Your weight goal is to lose.
Pause the goal?" Continue pauses the goal; Cancel leaves the target as it was.

"Use my goal" re-applies the stored baseline: the same target every time.

### Range or one number: one number with a band

A goal sets **one daily target with a tolerance band** (default ±100 kcal, a goal parameter the UI
may offer as 50, 100, 150 or 200). It is written to Targets as a range around the target so Today,
Trends and the recap read it unchanged, but Targets says what it is: the goal's target and the band
it counts as on track. The width never comes from whatever range was there before.

Projection reads the band: eating at the low end is the best case, the target the middle, the high
end the worst (`GoalProjections.weight`).

Considered: one number with no band. Rejected: a single bite past it reads as over. Considered:
keeping the person's own range width. Rejected: that is half of item 2.

### Weekly averaging: opt-in, capped, and never "make up for"

A goal that sets a calorie target can be **Daily** (default) or **Weekly average**.

Daily is the default. Weekly averaging is what the owner asked for and what an experienced tracker
wants, but its tightening side (a big Saturday asks Sunday to eat less) is the restrict-after-binge
loop the app exists to avoid. So it is opt-in, only offered where calories and energy out are on
show, never after "numbers have been hard", and both directions are capped:

- The week runs from the person's week start. Nothing carries into the next week.
- **Counted days** are past days with something logged and every amount known, and today once
  wrapped up. A skipped (untracked) day and a past day with nothing logged are left out: the week's
  total is the target times the days that count, so a skipped day asks nothing of the others.
- The **balance** is the counted days' targets minus what was eaten on them.
- The balance is spread over the days left (today, if not wrapped up, and the rest of the week,
  minus days already marked untracked).
- Each day's adjustment is capped: at most +20% of the target in the easing direction (eat more
  than the target) and at most 10% of the target, and never more than 200 kcal, in the tightening
  direction. A loss never tightens past the personal fat-loss limit or under the low-goal line.
- Whatever the caps leave over is dropped, not carried. A big day is okay.

Copy says what today's number is ("About 1,950 today keeps your week on track"), never that a day
is being made up for. Tests pin the caps.

All three numbers (20%, 10%, 200) are `TODO(clinician)`.

### Goal types

| Type | Parameters | Sets a calorie target | Offered after numbers have been hard | Data |
|---|---|---|---|---|
| Weight | direction (lose, keep, gain), target weight, pace, start weight | yes | no | weigh-ins |
| Energy balance | kcal a day under (negative) or over what you burn | yes | no | eaten, baseline |
| Burn | kcal a day, total burned (TDEE) or active only | no | no | Burned |
| Steps | steps a day | no | no | the health app's daily steps (`StepsRepository`), read only while a steps goal is active |
| Track every day | none | no | yes | the day was shown up for |
| Meals | meals a day, 3 by default | no | yes | meals with food |

At most one goal sets the calorie target at a time; the others sit beside it. Every goal has an id,
a type, a start date and a status (active, paused, done).

Modes: a type that needs a number hidden in the mode is not offered (`GoalCatalog.offered`).
Weight needs weight on show; energy balance and burn need calories and energy out on show; steps
are not a calorie number and are offered wherever activity is. After "numbers have been hard", only
Track every day and Meals.

### Storage and migration

A new top-level settings key, `goals`: a list of goal objects. The server merges settings one level
deep, so the list is replaced whole, which is what we want.

- `goals` absent: the list is migrated from `weightGoal` on read. Lose, keep or gain becomes a
  Weight goal with the same fields; its baseline burn is recovered from the aim it last wrote
  (`targetsKcal - delta`), with unknown inputs, which the next comparison treats as a drift.
- Every write of `goals` also writes `weightGoal`, so builds that only know `weightGoal` keep
  working: a goal that sets the target writes its aim as `targetsKcal`; paused or overridden writes
  none, which those builds show as "changed by hand".
- `weightGoal` that no longer matches what `goals` would write was changed by an older build; the
  weight goal is rebuilt from it and keeps its baseline.
- A goal type this build doesn't know is kept as it came and never acted on, so an older build
  doesn't delete a newer build's goal.

## The engine API

Pure, in `:libraries:nutrition`:

- `FatLoss.limitFor(...)`, `FatLoss.bodyFat(...)`: the limit and where body fat came from.
- `GoalPace.assess(...)`, `GoalPace.fastestKgPerWeek(...)`: tier per pace, and how far the stepper goes.
- `GoalBaselines`: the typical day's burn, the comparison, the threshold rule.
- `GoalTargets.of(goal, ...)`: the aim and band, and `apply` to Targets.
- `WeeklyBudgets.of(...)`: the week, its counted days, today's adjusted target.
- `GoalDays.verdicts(...)`: on track per day for any goal type.
- `GoalProjections.weight(...)`: best, middle and worst weekly series.

In `:libraries:diary`: `Settings.goals` and `GoalBook` (migration, the `weightGoal` mirror, writes).

In `:libraries:goals`: `GoalRepository`, the flows every screen reads (today's status for each
goal, a span of day verdicts for the Trends calendar, the recap and celebrations, this week's day
verdicts for any goal type (`weekDays`), the week's budget, the projection, the pending
recalculation, accept and keep, and `wrapUp(date)`: each goal on a day judged as if wrapped up,
with its week after the day and its streak) and `GoalFixtures` for previews. `GoalOutcomes` turns
a verdict into the words Wrap up the day and the recap pick by (on plan, a bit over or under, a big
day, well under, not this time), and `GoalStreaks` is the opt-in goal streak (only Track every day,
Meals, Burn and Steps). A steps goal reads the health app's steps (`StepsRepository`) only while
one is active.

## Edge cases

- **No burn known** (no details): a target goal has no baseline; Targets says what is missing, as
  before. The goal is kept and starts setting the target once a baseline can be worked out (that is
  a recalculation offer with "your details are complete" as the reason).
- Pregnant or breastfeeding: no weight or energy balance goal is offered, and one set before stops
  setting the target (it reads as the person's own, so nothing is locked); Use my goal brings it
  back afterwards.
- **Goal reached** (within 0.5 kg): status stays active; the UI may offer keep. Never failure words.
- Date passed: the forecast is a projection; nothing expires.
- **A day group style on Targets** when a goal is applied: the goal replaces the style with its band,
  and says so in the confirm when it would.
- **Partial days** (a food with no calories) are not counted in a week; they are not skipped
  either: they just don't count.
- Week start changed mid-week: the week recomputes from the new start; nothing is stored.
- Modes: nothing in the engine formats a number. Every surface resolves through the policy.
- **Celebrations** stay about showing up and never read a goal (2026-10-29). A goal streak is its
  own opt-in run (`goalStreakEnabled`), only for goals whose "met" isn't eating less; nothing about
  calories reaches a streak (docs/decisions.md, 2026-10-30).

## Where the app reads a goal

Built on 2026-10-30 (docs/decisions.md, "With a goal, the app is organized around it"):

- **Today**: the card's chip names the goal and opens it; under a weekly average the band moves by
  `GoalToday.weekAdjustmentKcal` (the morning's band once wrapped up) with "To stay on track this
  week: about N today"; the burned bar marks a usual day (`UsualBurn`) and a burn goal's line.
- **Wrap up the day**: a "Your goal" card from `GoalRepository.wrapUp`, and the day's range is the
  band its week asked of it.
- **Trends**: the calendar and its day sheet judge days from the goal's start by `GoalRepository.days`;
  the running total's "Since you started" counts from the goal's start.

## The goal UI

Built on 2026-10-30 from the owner's feedback on the goal and target screens. Everything is behind
`release.onboardingSetup`, as goals already were.

- **`:libraries:goals:ui`**, the pieces more than one feature draws, so onboarding's goal question
  can build from the same ones: `ProjectionChart` (the band between eating at the low and high end,
  the target's line through it, the goal weight dashed), `GoalReveal` (the line draws in, then the
  forecast fades up and the success haptic plays; at rest under reduce motion and in previews, the
  haptic still plays under reduce motion), `ProjectionForecastLines`, `GoalKindChoices`,
  `AveragingChoice` (Daily or Weekly average, the caps said gently), `BandChoice`, `TypedStepper`
  and `InlineNumberField`. `ProjectionCharts.ui` lays a `WeightProjection` out and words it through
  the policy; it returns null where weight isn't on show, so there is no chart then rather than a
  chart with no numbers (a line of weight is weight).
- **`TargetProjections`** (`:libraries:goals`): where a target with no goal weight leads, twelve
  weeks against a typical day, with the same 7,700 kcal rule and digesting food. Used for an energy
  balance goal and for the person's own range, one number, deficit or surplus on Targets.
- **Set a goal** (`GoalEditRoute`, `features/settings` `goals/`): every type, the type's own answers
  typed or stepped, Daily or Weekly average and the band where a calorie target follows, the target
  it comes to, and where it leads. Weight needs a weigh-in and the details resting energy needs; the
  editor links to the goal flow (`GoalSetupRoute`) for them. A new calorie goal pauses the other one
  and says so. Saving plays the reveal.
- **Goals** (`GoalsRoute`): each goal with this week from `GoalRepository.days`, a weekly average's
  number for today, the typical day behind its target (with the TDEE sheet), its projection, and
  `Goal.history`: every recalculation the person answered (`GoalBook.accept` and `keep` record it
  when given the day). Pause and pick up again. Opened from Targets' goal card and Today's chip.
- **Targets**: range ends, a set deficit or surplus and a day group's number are typed in place
  (`Targets.withEnd`, `withDeficit`, `withSurplus`, `withGroupNumber`, the same limits as a step;
  Done on a number that can't be used says why). The macro split shows grams at each end of a range
  (or one number's leeway). The goal card draws its projection.
- **Digesting food (TEF)**: named for what it is, with its own (i) sheet (`EnergyTopic.Digesting`)
  and its switch; the Total burned (TDEE) sheet lists only the parts this person's Burned has and
  carries the same switch.

## Not in this package

Onboarding's "Do you want to set a goal?" and its goal streak question (built from the pieces
above), and the recap. The steps data source is built: `StepsRepository` feeds a steps goal's days
through `GoalRepository`.
