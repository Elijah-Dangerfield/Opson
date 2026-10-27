# Onboarding v2

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
| Welcome | everyone | nothing ("Skip and start logging" writes Full numbers) | |
| 1 of 3: Why are you tracking? | everyone, skippable | `onboarding.why` | |
| 2 of 3: How do you feel about seeing numbers? | everyone, skippable | `onboarding.feel` | |
| 3 of 3: Does this sound like you? (optional) | everyone, skippable | `onboarding.hard` | |
| Your starting point | everyone | `numbers` (the mode), unstamped | |
| Your daily goal | the mode shows calories, so Full numbers | `targets` (style and range) | `release.onboardingSetup` |
| What you burn at rest | a set deficit or surplus was picked | `birthYear`, `heightCm`, `bodySex`, `weightUnit`, `everydayActivity`, and a first weigh-in | also `release.goalsAndEnergy` |
| When do you usually eat? | No numbers, or "Eating regularly" | `mealTimes` | `release.onboardingSetup` |
| Save your setup | a phone that can sign in (iOS) | links the guest | |

Every key is an existing top-level setting, so the shallow `||` merge on the
server keeps them. Skip moves on and keeps whatever is there. Nothing asks
again later. Only the steps a person saw write anything: someone in No numbers
never gets a goal written for them.

## What each question buys

| Question | What reads the answer | Verdict |
|---|---|---|
| Why are you tracking? | The summary card's starting stats (`SummaryStats.suggestionGoal`: changing weight, eating regularly, eating enough, performance). Two nudges in the alternatives. Now also whether meal times are asked. | **Keep.** Cheap, a soft opener, and Today uses it. Three of seven options (curious, a medical need, working with a provider) change nothing yet. |
| How do you feel about seeing numbers? | The recommendation. It decides it in every case but one. | **Keep.** It is the question. |
| What do you want to notice? | Only the recommendation, and only when the numbers answer was skipped or "Not sure yet": "How eating feels" gave No numbers, everything else gave Later. Nothing else in the app read it. | **Cut.** One tap for almost no signal, and the numbers question already covers it. `OnboardingAnswers.notice` stays in the model, empty. |
| Numbers or tracking have been hard for me before | Full numbers is never offered (`ModeRecommender.isOffered`), the fasting timer stays out (`Fasts`), watch activity never adds to the range (`Settings.exerciseInRange`). | **Keep**, as an optional statement. It is the most important safety input. |

Telemetry names did not move: the numbers question is still `question_2`, the
statement still `statement`. A funnel that showed `question_3` before v2 just
stops showing it.

## What the app needs on day one

**Needed, and asked:**

- **A mode.** Everything on Today follows from it.
- **A goal, in Full numbers only.** Today's main number is "to your range", so
  a Full numbers user sees a goal on the first log. Every other mode keeps
  targets in the background. The step offers the range on a stepper, and with
  `release.goalsAndEnergy` also one number, a set deficit and a set surplus.
  Different by day is left to Targets: it means building day groups, and until
  someone does that it is identical to a range.
- **Resting energy, only for a set deficit or surplus.** Both are aimed at
  Burned. Without these details Burned is null and the goal quietly falls back
  to the range, which is the product-notes complaint that "a set deficit never
  knows what you burn". Weight goes in as the first weigh-in, because Burned
  reads the latest weigh-in rather than a setting.
- **Meal times, for No numbers and eating regularly.** No numbers' Today is a
  meal timeline that says "around 8:00", and the counted summary names the next
  meal and its time. Meal times are only editable in Reminders, which is behind
  `release.reminders`, so today nobody can change them at all.

**Asked later, in context:**

- **Units.** Only Full numbers shows a weight or water amount. Weight and water
  units are in More. The resting energy step has its own kg/lb toggle because it
  needs one to read the fields. A follow-up worth doing: default `weightUnit`
  and `waterUnit` from the device region (US, Liberia, Myanmar get lb and fl oz).
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
| I love them | Full numbers | Later |
| Some are fine | Nutrients first | Nutrients first (calories are already hidden, as the design draws) |
| I'd rather not see them | No numbers | No numbers |
| Not sure yet, or only a reason | Later | No numbers |

Two gaps outside onboarding, for the owner: the statement can't be changed
later anywhere in the app, and Modes still offers Full numbers behind the pause
to someone who agreed. The second is probably right (it is their choice, with
friction); the first means a yes is permanent.

## How long is it?

Taps from Welcome to Today, answering one option per question and keeping the
account step on the phone:

| | Fastest | Answering everything |
|---|---|---|
| Opson before v2 | 1 (Skip and start logging) | 11 |
| Opson v2, flag off | 1 | 9 |
| v2, Full numbers | 1 | 10 |
| v2, Full numbers and a set deficit | 1 | 13, plus three typed fields |
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
person picked a goal that needs one. That is the right trade for this audience.

## Safety

- **No weight-loss pressure.** No goal weight, no rate, no dates, no BMI. Range
  is the default style and the first row. "Changing my weight" never picks a
  deficit: the answer can mean gaining.
- **No target below the floor.** The goal step starts at `Targets.Default` and
  moves through `Targets.raise` and `Targets.lower`, so with `GOAL_FLOOR_KCAL`
  unset nobody can start lower than the app's starting range. A deficit or
  surplus starts at what the aim already implies against an ordinary day's
  burn (resting energy plus the everyday activity asked on the same step, as
  Burned works it out with no health app), as Targets does, so it never asks
  for more than the person had. Tests hold both.
- **A statement, not a diagnosis.** Optional, phrased as something to agree
  with. The body now says what a yes does, because "the answer is used for
  nothing else" was not true: it also keeps fasting and exercise adjustments
  off. Clinician review is still open (`TODO(clinician)` in the strings).
- **The crisis link.** "Talk to someone" sits on the statement step whatever is
  picked. It opens Find support with `release.findSupport` on, Support otherwise.
- **Nothing that triggers.** Calorie amounts appear only in Full numbers, and
  only on the goal and resting energy steps. Someone who agreed with the
  statement can't reach either, because Full numbers is never on offer. Both
  steps are in the leak test and resolve every number through `VisibilityPolicy`.
- **Telemetry** carries step names only, as before. New names: `goal`,
  `resting_energy`, `meal_times`. A test checks no setup value reaches an event.

## Data and consent

**Recommendation: one consent moment, on Welcome, only where the law needs it.
Not built; it needs the lawyer.**

- **EU and UK.** Food logs and the statement answer are special category health
  data. The privacy policy says they rest on explicit consent, and the app has
  no consent step. Explicit consent has to be a clear, separate act, so it
  can't hide in "Get started". The place for it is Welcome, before anything is
  written: the first write is either the setup answers or, with "Skip and start
  logging", the first log. One line and a button ("Agree and continue", linking
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
  the pinned button. The goal stepper is the shared `Stepper`. They need a
  design pass before the flag goes on.
- Unchanged from 0.1's own departures: no Plus row on Welcome, no coach line on
  the account step.

## Decisions for the owner

1. **Turn on `release.onboardingSetup`?** It is independent of
   `release.goalsAndEnergy`; with only the first, the goal step is the range
   alone. Wants a design pass on the three new screens first.
2. **Consent** for the EEA and UK: build the Welcome line, or limit the store
   to the US. Lawyer.
3. **Should "Skip and start logging" land in Full numbers?** The design says
   yes. Later would be the safer default for someone who told us nothing.
4. **The why options that do nothing** (curious, a medical need, working with a
   provider): keep them so people see themselves in the list, or cut them.
5. **Changing the statement later.** Today a yes is permanent.
6. **A `setupDone` key** for the abandoned-setup edge above.
7. **Units from the device region**, so a US user isn't in kg.
8. **Clinician review** of the statement copy and the reason lines.
