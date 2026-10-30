# Onboarding v4: a goal is optional, inputs are fast, and expectations are set

Owner feedback, 2026-10-30. Builds on [onboarding-v2.md](onboarding-v2.md) (the questions and
the mode), the age gate (`docs/research/ed-safety-review.md`) and the goal engine
([goals-v2.md](goals-v2.md)). The code is `PlanFlow.kt`, `PlanSteps.kt`, `OnboardingViewModel.kt`
and `AgeGateSteps.kt` in `features/onboarding/impl`.

## What the owner asked

1. "Some people aren't goal-oriented ... might even be triggered by us asking their height and
   weight before they even use the app." Ask whether to set a goal; ask about the body only when
   a goal needs it.
2. Many kinds of goal: a weight, tracking every day, a deficit, a deficit averaged through the
   week, three meals a day.
3. A streak only after a goal, and that is where "I'm committed" belongs.
4. Type the birthday, with autofill, and set autofill content types on every field.
5. Say Opson is in beta, Pro isn't here yet, and beta members get a lifetime discount.
6. The age gate goes where the birthday is first asked, before account creation.

## The flow

```
Welcome (beta card with release.betaNotice)
  → Age: "When's your birthday?" typed, before any account (under 16 stops here)
  → Sign in
  → Why are you tracking? → How do numbers sit with you? → Numbers have been hard (optional)
  → Recommendation (the mode)
  → [meal times, where Today is built around them]
  → Want to set a goal?                                  (release.onboardingSetup from here)
      Not now, just let me log → save, Today. Nothing about a body asked.
      Yes, help me set one → What kind of goal?
          Weight             → profile, activity, lose/keep/gain, pace, overview (the plan drawn in)
          Energy balance     → profile, activity, how much under or over (each day or a weekly
                               average), overview
          Burn               → active energy a day
          Steps              → steps a day (where a health app can be connected; it offers to)
          Track every day    → (nothing more)
          Meals              → meals a day
      → Keep a streak? (with release.celebrations on)
      → I'm committed / Not now → save, Today
```

- **The pieces are the goal screens' own** (`:libraries:goals:ui`): the kinds and their lines
  (`GoalKindChoices`), the typed stepper for each amount (`TypedStepper` in a `GoalPanel`),
  Daily or Weekly average (`AveragingChoice`), and on a weight goal's overview the projection
  drawn in with the success tap (`GoalReveal`). A goal is set the same way here as in More.
- **Kinds on offer** follow `GoalCatalog.offered` for the mode being started. A weight goal needs
  weight on show; an energy balance and a burn goal need calories and energy out on show; the
  weekly average needs `GoalCatalog.weeklyOffered`; steps need a health app that can be connected
  (`release.health` on and a store on the phone), since that is where they are read. After
  "numbers have been hard", and for 16 and 17 year olds, only Track every day and Meals.
- **The body** (sex, height, weight) and a usual day's activity are asked only for a weight goal
  or an energy balance, the two that set a calorie target from a typical day's burn. The profile
  says why ("Used to estimate how much energy your body uses in a day") and both steps keep Skip.
  A body typed for a weight goal and then left for another kind is not saved, and its weight is
  not logged.
- **The birthday** is asked once, on the age question before sign-in, and the profile reuses it.
  It is typed in three boxes in the region's order (`DateField`), with a number pad, moving on
  when a box is done, and autofill hints for the day, month and year of a birthday. The age step
  opens with the keyboard up in the first empty box, keeps Continue above it, and the last box's
  Done (the bar over the iOS number pad) is Continue. Digits typed or pasted faster than the
  cursor moves carry on into the next boxes, and delete in an empty box goes back one. The question
  stays neutral: under the boxes it only ever says "There's no such date" or "Check the year",
  never where the age line is. Profile in More types it the same way.
- **Energy balance** is a side (eat less or eat more) and an amount, typed or stepped, 50 to
  1,000 kcal a day in steps of 50, never so far under a typical day that the target would be
  under the 1,200 line the engine holds everyone to. The pace note (Gentle, Steady, Faster, Above
  your fat-loss limit) and the low-goal note with Find support show as for a weight goal. Daily or
  Weekly average is the goal screens' choice, where it is offered; the overview of a weekly one
  says "Judged across your week. A big day is just a day."
- **"Not now, just let me log"** saves the weight goal as answered with no goal (`JustTrack`), so
  Today's "Set up your goal in a minute" card doesn't ask again. Any other kind does the same.
- **The streak** explains which one. Always the showing-up streak (celebrations). For a goal
  whose "met" can never mean eating less (Track every day, Meals, Burn, Steps:
  `GoalStreaks.eligible`) the same yes also turns on the goal's own streak
  (`goalStreakEnabled`); a weight goal or an energy balance only ever gets the showing-up one.
  No streaks turns celebrations off. Nobody without a goal is asked.
- **"I'm committed"** comes only after a goal.

## Autofill

`FieldContent` (`libraries/ui`) sets both halves: Android's autofill reads the semantics content
type, and iOS reads `textContentType`, which Compose only sets from
`KeyboardOptions.platformImeOptions` (its own fallback knows email, password and phone). Set on:
sign-in email (username), password; sign-up email, new password twice; forgot password email;
the birthday's day, month and year boxes on the age question, the goal flow's profile and
Profile. Height and weight already use the number pad. There is no name field and no one-time
code field in onboarding or sign-in today; `FieldContent.Name` and `OneTimeCode` are there for
when one is added.

## The beta

Welcome shows "You're early. Opson is in beta. Pro isn't here yet, and we're working on it.
Everyone who joins during the beta gets a lifetime discount when it arrives." while
`release.betaNotice` is on (off by default, on in dev). While it is on, every signed-in account
is recorded on the server once, as a `beta` grant (`POST /v1/me/beta`; "Beta members" in
`apps/server/README.md`), including accounts from before this build the first time they open it.
Turning the flag off ends the beta: the card goes, and the server records nobody new.

**Owner decisions still open:**

- **What the discount is.** This build promises "a lifetime discount" and records who is owed
  it. `docs/plans/feedback-2026-10-29/pricing-and-pro.md` proposes something different: the
  first 1,000 accounts active on 5 days of their first 30 get Pro free for life ("founding
  members"). The two can coexist (founders free, every other beta member discounted) or one can
  replace the other. The amount, and whether it applies to monthly, yearly or both, are yours.
  Apple's 3.1.1 note in the pricing doc applies to a discount too: on iOS it would be an offer
  code or introductory price on the in-app purchase, not something the server applies.
- **App Review and the word "beta".** Guideline 2.2 says betas belong on TestFlight, and a
  reviewer can read "Opson is in beta" in a store build as an unfinished app. The flag lets you
  turn the card off for a submission, or reword it ("early access") without a release.

## Judgment calls

- The goal question comes after the mode, since the kinds on offer depend on what the mode
  shows; it is the first setup step after meal times.
- A steps goal starts at 8,000 a day and steps by 500, as the goal screens' editor does.
- A burn goal counts active energy only (`BurnMeasure.Active`), so it needs no body details.
- Its number is shown only where calories show too, as the overview's working is.
- The energy balance starts at 250 kcal under, the small end of Gentle; the owner listed a
  deficit first.
- "Not now" on the goal question counts as answered, so Today doesn't nag. A goal can still be
  set from Targets.
- The beta is recorded per account on the sync edges rather than at the end of onboarding, so
  existing accounts are counted too and an offline sign-up is recorded later.
- The age question keeps the age gate's neutral wording; the typed field's hints never say an
  age.

## Not done

- The band (how far either side counts as on track) isn't asked in onboarding; a goal starts at
  the engine's default and the goal screens change it.
- Today's goal flow (`GoalSetupRoute`) is still a weight goal only.
