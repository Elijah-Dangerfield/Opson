# Onboarding v2

> The setup steps were reworked on 2026-10-30: a goal is asked for first and is optional,
> and the body is asked only for a goal that needs it. See [onboarding-v4.md](onboarding-v4.md).

Written 2026-10-27 against design sync 4. It answers the owner's question ("are
we asking the right questions and getting useful info?") and records what was
rebuilt.

**Short version.** The questions were mostly fine, but one of them did almost
nothing and setup collected nothing the app needs on day one. v2 drops "What do
you want to notice?", says why a mode was picked, and fixes the statement's copy.
A new account made by Sign in with Apple now goes through setup. Behind
`release.onboardingSetup` (off), setup then asks for what the chosen mode
actually uses: the goal in Full numbers, resting energy for a set deficit or
surplus, and meal times for No numbers or someone eating regularly.

## The flow now

| Step | Who sees it | What it writes | Flag |
|---|---|---|---|
| Welcome | everyone | nothing; "Get started" opens the birthday (a guest from an older build goes straight to sign-in) | |
| When's your birthday? | everyone but an older build's guest, required, before any account exists (2026-10-30) | nothing yet; the birthday goes to the profile step and is saved with setup. Under 16 goes to "Opson isn't for under-16s yet", with Find support, no account and no way back; the phone keeps `AppData.ageGateOpensOn` (the sixteenth birthday) and opens on that screen until then. The steps are `AgeStep` and `TooYoungStep` (`AgeGateSteps.kt`), self-contained so a reworked flow can move them, as long as they stay before sign-in | |
| Sign in | everyone, required (908798dd) | the account: Apple (iOS), Google, email when `SignInMethods.EMAIL` is on. An account that already exists goes to Today and keeps its mode | |
| 1 of 3: Why are you tracking? | everyone, skippable | `onboarding.why` | |
| 2 of 3: How do numbers sit with you? | everyone, skippable | `onboarding.feel` | |
| 3 of 3: Does this sound like you? (optional) | everyone, skippable | `onboarding.hard` | |
| Your starting point | everyone | `numbers` (the mode), unstamped | |
| When do you usually eat? | No numbers, or "Eating regularly" | `mealTimes` | `release.onboardingSetup` |
| A little about you | everyone in setup | `bodySex`, `bodyStage`, `birthDate` (and `birthYear` with it), `heightCm`, `heightUnit`, `weightUnit` and `weightUnitChosen`, and a first weigh-in. No weight field where weight is hidden or after numbers have been hard | `release.onboardingSetup` |
| How active is a usual day? | everyone in setup; nothing to pick once a health app is connected, with Connect offered here | `everydayActivity` | `release.onboardingSetup` |
| What would you like to aim for? | weight on show in the mode, numbers not hard, not pregnant or breastfeeding | `weightGoal` (lose, keep, gain, just track; a goal weight to lose or gain) | `release.onboardingSetup` |
| Pick a pace | losing or gaining | `weightGoal.kgPerWeek`, capped at GoalSafety's gentle pace | `release.onboardingSetup` |
| Your plan | everyone in setup; "You're all set" after numbers have been hard | `targets`, a range on the aim, when a goal sets it | `release.onboardingSetup` |
| Ready to start? | everyone in setup | `planCommitment` ("I'm committed" or "Not now", and the day) | `release.onboardingSetup` |

Every key is a top-level setting, so the shallow `||` merge on the server
keeps them. Skip moves on and keeps whatever is there; Skip on the goal step
means no goal. Only the steps a person saw write anything: someone in No
numbers, or who said numbers have been hard, never gets a goal or targets
written for them. The goal flow replaced the resting energy and goal style
steps on 2026-10-29; see "The goal flow" below.

## The goal flow (2026-10-29)

Owner feedback: a normal onboarding helps people set a goal, with Cronometer's
profile, activity, weight goal, pace and overview as the model, and a
commitment at the end. The steps live in `PlanFlow.kt` (pure) and
`PlanSteps.kt` (drawn), and onboarding and the goal flow from Today
(`GoalSetupRoute`) walk the same ones.

- **Sex for the estimate** lists Female, Male, Pregnant, Breastfeeding and
  Rather not say, each with a line saying it only changes the estimate, and a
  note for trans and non-binary people. Pregnant and breastfeeding use the
  female constant, add that stage's energy (`BodyStage.extraKcal`,
  `TODO(clinician)`) and keep weight change out of the plan.
- **Birthday** is a full date (`birthDate`); `birthYear` is written with it,
  and every calculation still reads the year.
- **The goal drives Targets.** A goal to lose, keep or gain sets a range of
  the same width on the aim: a typical day's burn (resting energy plus
  everyday activity), plus a pregnancy or breastfeeding extra, plus the pace's
  energy by the 7,700 kcal a kilogram rule (3,500 a pound). A fixed range, not
  a set deficit, so the day's target is the same in every mode and all day.
  The goal stamps the aim it wrote (`WeightGoal.targetsKcal`); Targets says
  "Set from your weight goal" while they match, and once the person moves the
  target by hand, that the goal no longer sets it, with Use my goal.
- **Pace limits.** The stepper stops at the gentler of 1,000 kcal a day and 1%
  of body weight a week for a loss, 0.5% for a gain, and never plans an aim
  under the low-goal line. A goal weight under a BMI of 18.5 for their height
  gets a soft note and Find support, never a block (the owner's brief; the
  energy and goals design brief proposed a block).
- **The overview** shows the goal weight, a rough date, the day's target and
  how it compares with a typical day's total burned (TDEE), with the working
  in the energy words, each only where the mode shows it. In Macros with
  grams it says the target is kept in the background.
- **The commitment** records `planCommitment`; nothing changes with the
  answer, and a celebration can mention it later.
- **Existing accounts** that never answered get Today's "Set up your goal in a
  minute" card (`set_up_goal`, docs/practices/prompts.md).
- Telemetry carries the new step names (`profile`, `activity`, `weight_goal`,
  `goal_rate`, `goal_overview`, `commitment`) and one boolean,
  `onboarding.commitment`'s `committed`. `goal` and `resting_energy` are gone.

## What each question buys

| Question | What reads the answer | Verdict |
|---|---|---|
| Why are you tracking? | The summary card's starting stats (`SummaryStats.suggestionGoal`: changing weight, eating regularly, eating enough, performance). Two nudges in the alternatives. Now also whether meal times are asked. | **Keep.** Cheap, a soft opener, and Today uses it. Three of seven options (curious, a medical need, working with a provider) change nothing yet. |
| How do numbers sit with you? | The recommendation. It decides it in every case but one. | **Keep.** It is the question. |
| What do you want to notice? | Only the recommendation, and only when the numbers answer was skipped or "Not sure yet": "How eating feels" gave No numbers, everything else gave Later. Nothing else in the app read it. | **Cut.** One tap for almost no signal, and the numbers question already covers it. `OnboardingAnswers.notice` stays in the model, empty. |
| Numbers or tracking have been hard for me before | Full numbers is never offered (`ModeRecommender.isOffered`), the fasting timer stays out (`Fasts`), watch activity never adds to the range (`Settings.exerciseInRange`). | **Keep**, as an optional statement. It is the most important safety input. |

Telemetry names did not move: the numbers question is still `question_2`, the
statement still `statement`. A funnel that showed `question_3` before v2 just
stops showing it.

## What the app needs on day one

**Needed, and asked:**

- **A mode.** Everything on Today follows from it.
- **A goal, only for someone comfortable with numbers.** Today's main number
  is "to your range", so a Full numbers user sees a goal on the first log.
  Every other mode keeps targets in the background, and someone who agreed
  numbers have been hard never sees a goal number in onboarding. The step
  offers the range on a stepper, and with `release.goalsAndEnergy` also one
  number, a set deficit and a set surplus. Different by day is left to
  Targets: it means building day groups, and until someone does that it is
  identical to a range.
- **Resting energy, before the goal** (`release.goalsAndEnergy`). The goal
  starts from the ordinary day it gives, never under the floor, and a deficit
  or surplus is aimed at it. Without these details Burned is null and a
  deficit quietly falls back to the range, which is the product-notes
  complaint that "a set deficit never knows what you burn". Weight goes in as
  the first weigh-in, because Burned reads the latest weigh-in rather than a
  setting. Height is centimetres or feet and inches, weight kilograms or
  pounds, each starting in the phone region's units.
- **Meal times, for No numbers and eating regularly.** No numbers' Today is a
  meal timeline that says "around 8:00", and the counted summary names the next
  meal and its time. Meal times are only editable in Reminders, which is behind
  `release.reminders`, so today nobody can change them at all.

**Asked later, in context:**

- **Units.** Only Full numbers shows a weight or water amount. Weight and water
  units are in More. The resting energy step has its own height and weight
  switches, starting in the region's units (US, Liberia, Myanmar get feet and
  pounds), and saves what was picked. `weightUnit` now reads in the region's
  unit for everyone until something sets it (`Settings.withRegionWeightUnit`);
  `waterUnit` still starts metric for someone who never sees that step.
- **End after a set time.** A Modes feature for people who already know a mode.
- **Health app, activity, macros, the summary card layout, reminders.** Each
  has a page that explains itself, and none changes the first log.

## Is the recommendation trustworthy and explained?

It was trustworthy but unexplained: the screen named a mode and listed what it
shows, never why. v2 adds one muted line under the mode, from
`ModeRecommender.reason`, naming the answer that decided it ("You said some
numbers are fine."). It shows only while the recommendation is the mode chosen,
so it never explains a mode someone picked themselves. For someone who agreed
with the statement it says "You asked for a gentler start", and never repeats
the statement back. A test holds the reason and the mode to each other for
every answer.

**Numbers have been hard, and Full is hidden: yes.** Full numbers is never
recommended, never in the alternatives and never in All modes. The rules are:

| Numbers answer | Without the statement | With the statement |
|---|---|---|
| skipped everything | Full numbers | No numbers |
| I like seeing them | Full numbers | Macros with grams |
| Some are fine, just not calories | Macros only | Macros only (calories are already hidden, as the design draws) |
| I'd rather not see them | No numbers | No numbers |
| I'm not sure yet, or only a reason | Macros only | No numbers |

(Updated for the four modes, 2026-10-27: Later and Nutrients first are retired.)

Two gaps outside onboarding, for the owner: the statement can't be changed
later anywhere in the app, and Modes still offers Full numbers behind the pause
to someone who agreed. The second is probably right (it is their choice, with
friction); the first means a yes is permanent.

## How long is it?

Taps from Welcome to Today, answering one option per question, counted before
sign-in became required (908798dd). The one-tap Skip is gone, and every path
now also passes the sign-in step:

| | Fastest | Answering everything |
|---|---|---|
| Opson before v2 | 1 (Skip and start logging) | 11 |
| Opson v2, flag off | 1 | 9 |
| v2, Full numbers | 1 | 10 |
| v2, Full numbers with `release.goalsAndEnergy` | 1 | 11, plus three typed fields ("Set this later": 10) |
| v2, No numbers | 1 | 10 |

Compared, from memory of the apps rather than a fresh check:

- **MacroFactor** runs 15 to 20 screens before the first log: sex, birthday,
  height, weight, body fat, activity, goal, goal weight, rate, diet and macro
  program, then a trial paywall. Its whole value is a computed target, so it
  front-loads the body.
- **Cronometer** asks for an account first, then sex, birthday, height, weight,
  activity and a weight goal, about ten screens, with calories everywhere after.
- **Rise Up + Recover** and **Recovery Record** are short, with no calories at
  all. Recovery Record leads with an account and linking to a treatment team;
  Rise Up gets to a meal log in a few screens.

Opson sits with the gentle apps on length and only asks for a body when the
person is in Full numbers and something reads Burned. That is the right trade
for this audience.

## Safety

- **No weight-loss pressure.** (Changed 2026-10-29, the goal flow.) A goal
  weight, a pace and a rough date are offered only where the mode shows
  weight, never after numbers have been hard and never while pregnant or
  breastfeeding. The pace starts slow (half a pound, 0.2 kg, a week), stops at
  GoalSafety's gentle line, and the copy never praises eating less. A goal
  weight under a healthy BMI gets a soft note and Find support, never a block.
- **No target below the low-goal line.** A goal's aim never goes under
  `GoalSafety.lowGoalKcal` for their sex; the pace stepper simply has no step
  that would. With no goal, a new account starts from a typical day's burn but
  never under the app's starting range. Tests hold both (`WeightGoalTest`,
  `PlanFlowTest`).
- **16 and over.** The age question comes before sign-in, so nobody under 16
  ever gets an account (`AgeGate`, `docs/research/ed-safety-review.md`). 16 and
  17 get the same gentle goal path as after "numbers have been hard": no
  weight, no goal weight, no pace, no deficit, and celebrations without counts.
  Nothing is sent about someone turned away: no step event, no abandoned event.
- **A statement, not a diagnosis.** Optional, phrased as something to agree
  with. The body now says what a yes does, because "the answer is used for
  nothing else" was not true: it also keeps fasting and exercise adjustments
  off. Clinician review is still open (`TODO(clinician)` in the strings).
- **The crisis link.** "Talk to someone" sits on the statement step whatever is
  picked. It opens Find support with `release.findSupport` on, Support otherwise.
- **Nothing that triggers.** Calorie amounts appear only where calories show,
  and a deficit or the working of a typical day only where energy out does
  too. Someone who agreed with the statement sees no weight, no goal weight,
  no pace and no deficit. The profile, goal, pace, plan and commitment steps
  are in the leak test (`onboarding-profile`, `onboarding-weight-goal`,
  `onboarding-goal-rate`, `onboarding-goal-overview`, `onboarding-commitment`)
  and resolve every number through `VisibilityPolicy`.
- **Telemetry** carries step names and the commitment's one boolean, never an
  answer. A test checks no setup value reaches an event.

## Data and consent

**Recommendation: one consent moment, on Welcome, only where the law needs it.
Not built; it needs the lawyer.**

- **EU and UK.** Food logs and the statement answer are special category health
  data. The privacy policy says they rest on explicit consent, and the app has
  no consent step. Explicit consent has to be a clear, separate act, so it
  can't hide in "Get started". The place for it is Welcome, before anything is
  written: the first write is the account at sign-in, then the setup answers. One line and a button ("Agree and continue", linking
  the health data policy), shown when the device region is in the EEA or UK.
  The legal review's alternative, limiting App Store availability to the US
  until a lawyer answers, needs no code.
- **Washington and Nevada.** Consent is needed for collection beyond what's
  necessary to provide the service. The setup answers are necessary: they pick
  the mode the person asked for, and the statement turns on safety behaviour.
  The usage events are the doubtful part, and they start at app launch, not in
  onboarding. If the lawyer says they need consent, the switch belongs at
  launch or in More, not in the questions.
- **Not in each question.** Asking for consent on the statement step would make
  the most sensitive question feel more clinical, and the answer is needed for
  the service anyway. Its body now says what it does, which is what informed
  consent needs from it.

## The Sign in with Apple bug

Sign in with Apple creates the account when the Apple ID has none, so on a
fresh install "Sign in" is often a new account. The sign-in screen marked
everyone onboarded and went to Today in Full numbers. Now a successful sign-in
asks the server's new-account signal (`AuthOutcomeClassifier`, the one email
verification already uses): a returning account goes to Today, a new one opens
setup at the first question with the account already claimed, so the account
step is skipped. Browser OAuth is unchanged: its "success" only means the
browser opened. Fixed in `SignInViewModel`; tests cover Apple, email and OAuth.

One edge remains. The new-account signal is one-shot. A person whose Apple
account was created, who quit during setup and signed in again later, is
"returning" and lands on Today with default settings. A top-level `setupDone`
key written at the end of setup would close it; worth doing if the funnel shows
it.

## Where this differs from the design

- **Two questions and the statement, not three.** Progress reads "1 of 3", the
  bar has three segments. The design's 1.3 note ("Question 3 uses this same
  layout") no longer applies.
- **A reason line** under the mode on 1.5.
- **The statement's body** says what a yes does.
- **Three new steps** with no design yet: `onboarding-goal`,
  `onboarding-resting-energy`, `onboarding-meal-times`. They use the questions'
  layout: the same top bar (back and Skip, no progress bar, since how many
  steps follow depends on the mode), the title, choice rows, the edge card and
  the pinned button. The goal and resting energy steps put "Set this later"
  under the button instead of Skip in the bar. The goal stepper is the shared
  `Stepper`, and height the shared `HeightField`. They need a design pass
  before the flag goes on; `onboarding-goal-from-burn` is a new preview id.
- Unchanged from 0.1's own departures: no Plus row on Welcome, no coach line on
  the account step.

## Decisions for the owner

1. **Turn on `release.onboardingSetup`?** It is independent of
   `release.goalsAndEnergy`; with only the first, the goal step is the range
   alone. Wants a design pass on the three new screens first.
2. **Consent** for the EEA and UK: build the Welcome line, or limit the store
   to the US. Lawyer.
3. **The why options that do nothing** (curious, a medical need, working with a
   provider): keep them so people see themselves in the list, or cut them.
4. **Changing the statement later.** Today a yes is permanent.
5. **A `setupDone` key** for the abandoned-setup edge above.
6. **Units from the device region** for everyone. Done for the resting energy
   step, Resting energy's height and `weightUnit`; `waterUnit` still starts
   metric for someone who never sees that step.
7. **Clinician review** of the statement copy and the reason lines.
