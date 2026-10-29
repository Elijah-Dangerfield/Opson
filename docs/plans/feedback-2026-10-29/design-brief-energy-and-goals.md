# Design brief: Goals, Targets and Energy Expenditure

Paste this into Claude Design. It asks for information architecture first and
visual polish second. Opson's real screens are published for design sync at
`https://raw.githubusercontent.com/Elijah-Dangerfield/Opson/design-sync/app/manifest.json`;
the screen ids below are from that set.

## The ask in one paragraph

Opson lets people set calorie targets and work out what they burn, but the
pieces grew one at a time and now live in four places. Design one coherent
structure for **who you are** (Profile), **what you burn** (Energy), **what
you are aiming for** (Goal) and **what you eat each day to get there**
(Targets), plus how Today, Close the day and Trends report back. The structure
must stop settings from conflicting, say plainly what overrides what, warn
when a pace is unsafe, and teach a beginner without slowing down someone who
already knows their numbers. Every number must still obey the person's Mode,
which can hide calories or all numbers entirely.

## Recommendation

Go with **Option B, "Plan" hub** (below): one Plan page that reads top to
bottom as Profile, Energy, Goal, Targets, with each section a summary row that
opens its own page. It matches how the maths actually flows, gives every
setting exactly one home, and gives the conflict and safety messages one
place to live. Options A and C are there so the trade-offs are visible.

## Goals for the design

1. **One home per setting.** Any other place that shows it is a shortcut that
   opens that home or says where it lives.
2. **The chain is visible.** Profile feeds Energy, Energy and Goal set the
   daily target, the target drives Today. A person can see why today's number
   is what it is, in one or two taps.
3. **Goals and targets cannot silently disagree.** If they do, the app says so
   and offers fixes the person chooses.
4. **Safe by default, never preachy.** Unsafe paces get a clear line and a
   link to help, not a lecture or a hard wall where a wall isn't needed.
5. **Beginners can learn, experts can skip.** Explanations are folded away
   and stay folded once dismissed.
6. **Modes win.** Hidden numbers stay hidden on every new screen, including
   inside explanations and examples.

## Who this is for

| Person | What they're trying to do | What they need from this area |
|---|---|---|
| **New to losing weight** | "I want to lose 10 lb before summer and have no idea what that means." | A goal in plain words, a suggested pace, a target that follows, and short explanations of TDEE, deficit and why the scale bounces. |
| **The owner (experienced, numbers on)** | A 500 to 800 kcal daily deficit, logged as a calorie range. | Set the range, then see the deficit actually hit: live on Today's top card, and as the day's result on Close the day. |
| **Watch wearer** | Trusts Apple Health or Health Connect for burn. | Pick the watch as the source, decide whether workouts raise the target, never double count. |
| **Gym, no watch** | Logs workouts by hand. | Formula plus everyday activity plus logged exercise, with a clear switch for whether exercise "earns" food. |
| **Gaining (muscle, recovery)** | A modest surplus. | The same structure in reverse, with different safety lines. |
| **Numbers have been hard** | Eat regularly, maybe no calories at all. | None of this in their face. Targets work in the background; no weight goal with a date is offered. |
| **On a GLP-1** | Eating far less than usual. | The floor matters more than the deficit; protein emphasis (see `glp1-research.md`). |

## What exists today

Use these as the starting point. Screen ids are the design-sync names.

| Screen | Where | What it does now |
|---|---|---|
| **Targets** (`targets-goal-style-v4`, `targets-exact`, `targets-surplus`, `targets-by-day-shapes`) | More › Targets | Goal style list: Range, One number, Set deficit, Set surplus, Different by day (up to four day groups, each its own style). Macro split (% of calories or grams, presets Balanced, Higher protein, Lower carb). "Burned comes from" row. |
| **Resting energy** (`resting-energy`, `resting-energy-needs-details`) | Targets › Burned comes from, Energy today | Source: Calculate (Mifflin-St Jeor), health app, or Enter my own. Asks birth year, height, sex when missing. Everyday activity (Mostly sitting, Some walking, On my feet a lot, Physical work). "Add activity to Burned" switch. |
| **Energy today** (`energy-today`) | Tap Today's summary card | Eaten with its ± range, Burned split into resting, movement, workouts with sources, "Deficit so far", "To your range". |
| **Today summary card** (`today-summary-in-out`, `-range-bar`, `-rings`, `-equation`, `today-deficit-goal`) | Today | Four layouts. A main number that toggles between the goal view (To your range) and the balance (Deficit / Surplus). |
| **More › Today screen** (`today-screen-settings`) | More | Card layout, main number, card pages, what's in the list, Close the day button. |
| **Edit Today's cards** | Today ⋮ › Edit cards, or long press the card | Which stats sit on the card, macros page on or off, Fasting card. Overlaps with More › Today screen. |
| **Close the day** | Today header button | Day summary sentence, big-day card, check-in (hunger, energy, sleep, moods), tomorrow's goal. Does not show the day's deficit. |
| **Trends › Weight** | Trends | Weigh-ins and a weight curve. No goal weight anywhere in the app. |
| **Onboarding setup** (`release.onboardingSetup`, off) | First run | Goal step and resting energy step, only for people comfortable with numbers. |
| **Modes** | More › Modes | All numbers, Macros with grams, Macros only, No numbers, plus per-number overrides ("Each number"). |

What the app does **not** have: a goal weight, a goal date, a pace, a weekly
view of targets against a goal, a Profile page (body details live inside
Resting energy), the deficit on Close the day, or an "about this number" for
how Burned is worked out.

Rules already decided that the design must keep (`docs/decisions.md`):

- **Floor and caps.** `GOAL_FLOOR_KCAL`, `MAX_DEFICIT_KCAL` and
  `MAX_SURPLUS_KCAL` wait for a dietitian. Until then no goal goes under where
  the person started and no deficit grows past where it started.
- **One source of activity per day.** With a health app reading activity, its
  movement and workouts replace everyday activity and logged exercise. Without
  one, everyday activity plus logged exercise. A workout marked "My watch
  didn't record this" adds either way.
- **No thermic effect of food.** It makes Burned rise as you eat, so a deficit
  aimed at Burned grows with intake. Circular and hard to explain. Offer it, if
  at all, as an explained advanced toggle that is off.
- **Per-day goals are independent.** Raising Tuesday must never quietly lower
  Wednesday. A weekly total is shown as a check, never enforced by rebalancing.
- **No red, no "over", no negative remaining.** Not in the deficit readout
  either.

## The model the IA has to express

```
Profile  ->  Energy (what you burn)  ->  Goal (where, by when)  ->  Targets (each day)  ->  Today / Close the day / Trends
age, height,    resting + activity        weight and date,          range, one number,          deficit hit,
weight, sex,    + exercise, source        or just a pace            deficit, by day             weekly check
body fat        per part
```

Each arrow is a dependency. When something upstream is missing, the downstream
screen asks for exactly that thing and links to it.

## IA options

### Option A: Targets grows (minimal change)

Keep More › Targets as the hub. Add a Goal card at the top (weight and date),
a weekly check under the styles, and move body details out of Resting energy
into a Profile page linked from More's account area.

- **For:** least new navigation; the existing screens survive.
- **Against:** Targets is already long. Energy stays two levels deep under
  "Burned comes from", which is exactly what makes TDEE feel hidden. The
  conflict between "goal" and "goal style" stays a naming problem.

### Option B: a "Plan" hub (recommended)

Rename More › Targets to **Plan** (or "Goals and energy"). One page, four
summary rows in dependency order, each opening its own page:

1. **You**: "34, 5'10", 182 lb, male" → Profile
2. **What you burn**: "About 2,450 a day · formula plus Apple Watch" → Energy
3. **Goal**: "Lose 5 lb by Dec 20 · about 1 lb a week" or "No weight goal" → Goal
4. **Each day**: "1,700 to 1,900 · about 650 under what you burn" → Targets

Under the four rows sits the **weekly check** (see screens) and any conflict
or safety notice. Macros stay on Targets.

- **For:** the page *is* the model above; every setting has one home; a
  conflict has one obvious place to appear; a beginner reads it top to bottom.
- **Against:** one more level for people who only change the range (mitigate:
  the "Each day" row has an inline stepper for the most common edit).

### Option C: split by question

Three More rows: **Profile**, **Energy**, **Goals and targets**. No hub.

- **For:** each page is short and focused; Energy gets the prominence the
  owner wants.
- **Against:** the chain between them is invisible; conflicts have to be
  repeated on two pages; More gets longer.

## Key screens and states to design

### 1. Plan hub (Option B)

States: everything set; Profile incomplete ("Add your height to work out what
you burn"); no goal; goal and targets disagree; goal pace unsafe; Mode hides
calories (rows show words, see Modes); No numbers (the page is the existing
"Targets work in the background" card, nothing else).

### 2. Profile

The single source for birth year (age), height, latest weight (read from
weigh-ins, with "Log a weight" rather than a field), sex for the equation
(including "Prefer not to say", which uses the midpoint), and optional body
fat %. Each field says what uses it ("Used for what you burn and your vitamin
targets"). Health app can fill height, weight and birth date; say which values
came from it. Units switch here.

States: empty, partial, filled from Health, body fat added (Energy can then
offer Katch-McArdle).

### 3. Energy (what you burn, the TDEE page)

Top: one number, "About 2,450 a day", with a stacked bar of its parts.
Then one row per part, each with its source and a change control:

| Part | Sources to choose from |
|---|---|
| Resting | Formula (Mifflin-St Jeor; Katch-McArdle if body fat is known), Health app, Enter my own |
| Everyday activity | The four levels, or "From my watch" |
| Workouts | Health app, logged by hand, both with "My watch didn't record this" |
| Food digestion (thermic effect) | Off by default, with an honest explanation |

Two decisions get their own clear control, because they change the target:

- **"Workouts change my target"** (on/off). On: burning more raises today's
  target. Off: Burned is shown but the target uses resting plus everyday
  activity only.
- **"Which day's burn sets the target"**: today so far, today's projected
  total, or a 7-day average. This is the open product-notes question ("A set
  deficit never knows what you burn"); design the control and its explanation
  so the owner can pick a default.

A folded **"How this is worked out"** section with the formula in words, and
links (NIH Body Weight Planner, the energy-expenditure research note).

States: no details yet; formula only; watch connected; watch connected but no
reading today; own number typed (everything else greyed with "You entered your
own number"); Macros with grams (no amounts at all, only the source choices).

### 4. Goal

Three ways in, from easiest to most specific:

- **"Lose / maintain / gain"** with no number: sets direction only.
- **A pace**: "About 0.5 / 1 / 1.5 lb a week", shown as the daily deficit it
  means.
- **A weight and a date**: "Lose 5 lb by Dec 20". The app shows the pace that
  needs and the daily deficit, and turns it into a suggested target.

Show the projection honestly: a band, not a line, and a note that weight
moves slower than the simple maths says over months, because burn drops as
weight drops ([Hall et al., Lancet 2011](https://pubmed.ncbi.nlm.nih.gov/21872751/);
[NIH Body Weight Planner](https://www.niddk.nih.gov/bwp)). Never "you will
weigh X on date Y".

States: no goal; pace only; weight and date fine; date too soon (unsafe pace,
see rules); goal reached (offer maintain); goal weight under a healthy BMI
(not allowed, see rules); weight not logged recently.

### 5. Targets (each day)

Keep the existing styles. Add:

- **A deficit range** as a style, or as a readout on the range style. The
  owner's case: "500 to 800 under what I burn". Either the range moves with
  Burned, or the range is fixed calories and the deficit is reported. Design
  both and recommend one (see open questions).
- **The weekly check**: seven small bars (each day's target against Burned),
  the week's total deficit, and how it compares to what the goal needs: "This
  week adds up to about 3,900 under. Your goal needs about 3,500." When it
  doesn't add up: "About 1,200 short of your goal's pace" with fixes the
  person picks (raise the deficit on some days, move the date, change the
  goal). Never an automatic rebalance.

### 6. Today summary card, with the deficit

The owner wants the deficit hit visible at the top of Today while logging to a
range. Design the card so a range user can see both "To your range" and
"Deficit so far" without toggling, in at least the In and out and Equation
layouts. States: morning (deficit is huge because little is eaten; say "so
far"), evening, burn unknown, surplus day, Mode hides energy out.

### 7. Close the day

Add the day's result: "About 640 under what you burned" with the ± when
estimates are involved, and the week so far against the goal. Neutral words
on a surplus day ("About 200 over what you burned"), no colour change.

### 8. Learn (skippable education)

A small set of cards reachable from a "New to this?" link on the Plan hub and
from each "How this works" fold. Topics: what TDEE is; what a deficit does;
why the scale jumps day to day; why slow is easier to keep; what to eat more
of in a deficit (protein, fiber); when to talk to a professional. Each card
dismissible, never shown again once dismissed, never shown in No numbers.

## Rules and edge cases

### What overrides what

Design a consistent way to show precedence. Proposed order, strongest first:

1. **Mode** (and Each number overrides). Decides what is visible anywhere.
2. **"Numbers have been hard"** answer. Removes weight goals with dates,
   deficits and exercise-in-target, whatever the Mode.
3. **Safety floor and caps.**
4. **A typed number** (own resting energy, own exercise kcal) over a source.
5. **Health app** over the formula, per part, when it has a reading.
6. **Day group** over the overall style, for its days.
7. **Defaults.**

Wherever a lower item is overridden, the control shows it in place: "Set by
your mode, Macros only. Change mode ›" or "Using your own number, 1,650. Use
the formula instead ›". No disabled control without a reason next to it.

### Settings reachable from several places

Today ⋮ › Edit cards, long-press on the card, More › Today screen and Targets
all touch Today's card. Proposal:

- **More › Today screen is the home** for layout, main number and what's on
  the card. Edit Today's cards becomes a quick sheet that edits the same
  settings and ends with "All Today settings ›".
- **Plan is the home** for anything that changes a number (targets, burn,
  goal). Today never edits those; it links.
- Any control shown in two places is the same control with the same words,
  and changing it in one place shows instantly in the other.
- When a Plan change resets a Today choice (picking a range drops Rings), say
  so in a snackbar: "Rings needs one number, so Today is back to In and out."

### Safety thresholds

Numbers below are proposals for the dietitian, who sets the real ones
(`TODO(dietitian)` in code). The design needs the states either way.

| Situation | Proposed line | Behaviour |
|---|---|---|
| Goal pace over about 1% of body weight a week, or a daily deficit over about 1,000 kcal | Warn | Show the safer date. Allow continuing with a confirm. Link to Learn and support. |
| Daily target under the dietitian's floor (`GOAL_FLOOR_KCAL`; common app floors are 1,200 women / 1,500 men per [goals research](../../research/goals-ux.md)) | Block | The target stops at the floor. Explain why, once. |
| Goal weight under a BMI of 18.5 | Block | "Opson can't set a goal below a healthy weight range." Link to support. |
| Surplus over about 500 kcal | Warn | As the deficit warning. |
| Profile under 18 | No deficit goals | `TODO(clinician)` to confirm. |
| Someone who said numbers have been hard | No weight-and-date goals, no deficits | Never offered; not greyed out, absent. |

The usual reference for pace is 1 to 2 lb a week from a roughly 500 kcal
daily deficit ([NIH News in Health](https://newsinhealth.nih.gov/2022/12/healthy-weight-control)).
Support links go to the existing Find support page (helplines, checked
quarterly).

### Modes

| Mode | Plan hub | Energy | Goal | Today deficit | Close the day |
|---|---|---|---|---|---|
| All numbers | Everything | Amounts | Weight, date, pace, deficit | Shown | Shown |
| Macros with grams | Rows in words ("Set from your profile") | Sources only, no amounts | Direction and date in words; no calories, no deficit | Hidden | Hidden |
| Macros only | As above | As above | As above | Hidden | Hidden |
| No numbers | "Targets work in the background" only | Hidden | Hidden | Hidden | Hidden |

Weight itself follows the Weight visibility already in Modes. A weight goal is
only offered where weight is visible. Every new screen needs a "leak"
state in the design: the same screen in each Mode, so nothing hidden slips
into an example, a chart axis or a Learn card.

### Other edge cases

- Weight not logged for 30+ days: resting energy uses an old weight. Say so.
- Burn unknown: deficit targets fall back to the range and say why, with one
  link to the missing detail.
- Goal date passed without reaching it: no failure language. "Your date has
  passed. Keep going, pick a new date, or switch to maintain."
- Goal reached early: offer maintain, quietly.
- Changing Profile mid-week moves Burned. The weekly check redraws; past days
  keep the target they had.
- Travel or time zone changes: a day is the local day, as everywhere else.

## Disclosures

Short, placed where the number is, foldable:

- On Energy: "Every way of working out what you burn is an estimate. Watches
  can be off by 20% or more. Your weight trend over a few weeks is the best
  check." A Stanford study found no wrist device it tested estimated energy
  within 20% ([Shcherbina et al., 2017](https://www.mdpi.com/2075-4426/7/2/3)).
- On Goal: "This is a rough projection, not a promise. Weight loss usually
  slows over time."
- On safety blocks: why, in one sentence, and who to talk to.
- Global: "Opson isn't medical advice. If you have a health condition, are
  pregnant, or are under 18, check with a professional before setting a
  deficit."

## Copy tone

Plain, calm, short. Second person. Numbers rounded ("about 650"). No
exclamation marks, no "great job", no "crushing it", no "cheat day". "Under"
and "over" what you burn, never "good" or "bad". The existing app voice
("Based on about 2,210 burned today", "This is the deficit you started with")
is the reference.

## Open questions for the owner

1. **Deficit as the target, or deficit as the readout?** For the 500 to 800
   case, should the range move with Burned each day (a "deficit range" style),
   or stay fixed calories with the deficit reported? Moving is more correct;
   fixed is easier to plan meals around.
2. **Which burn sets a deficit target:** today so far, today's projected
   total, or a 7-day average?
3. **Does logged exercise raise the target by default?** Lose It and MFP say
   yes; Cronometer makes it a switch; MacroFactor ignores it.
4. **Rename Targets to Plan?** Or "Goals and energy"?
5. **Should the weekly check ever suggest a change on its own,** or only when
   the person opens it?
6. **Body fat %:** worth a field now, or only when the health app has it?
7. **Thermic effect of food:** offer the toggle at all, given the circularity?
8. **Adaptive burn from intake and weight trend** (MacroFactor-style,
   product-ideas #21): design a slot for it now as a future source in Energy?
