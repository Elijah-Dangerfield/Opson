# Eating-disorder safety review

October 2026. What Opson does to avoid helping anyone into an eating disorder, what it was
missing, and what got built. Everything here still needs a clinician's read before 1.0
(`TODO(clinician)` in the code marks each threshold and line of copy that does).

Where to look:

- [Where Opson stands](#where-opson-stands) for the one-paragraph answer.
- [What got built](#what-got-built-in-this-pass) if you only want the changes.
- [The prioritized list](#prioritized-list) for what's next.

## Where Opson stands

Opson was already careful. Most of what the guidance asks for was there before this review:
numbers can be hidden per metric, someone who says numbers have been hard gets a gentler app
throughout, a big day is called okay with a reason not to cut back, any day can be left
untracked, low goals get a soft note, and helplines for six countries ship in the app. Three
gaps mattered most. Anyone 13 or older could sign up, and a 14-year-old got the same weight
goals and deficits as an adult. Nothing noticed when someone's own log started to look like
restriction or a binge and restrict cycle. And one prompt's analytics could have said "this
person was shown a safety note". All three are now closed, pending clinician review of the
thresholds.

## What the research and guidance say

### Tracking apps and eating disorders

- **People with eating disorders use calorie trackers a lot, and many say it made things
  worse.** Of 105 people in eating-disorder treatment, about three quarters had used
  MyFitnessPal, and 73% of those users said it contributed to their eating disorder at least
  somewhat (Levinson, Fewell & Brosof 2017, *Eating Behaviors* 27:14-16). This is a
  retrospective self-report, so it shows the link people feel, not that the app caused it.
- **Calorie and fitness tracking go with more ED symptoms in young adults** (Simpson & Mazzeo
  2017, *Eating Behaviors* 26:89-92), in men too (Linardon & Messer 2019, *Eating Behaviors*
  33:13-17), and weight-related self-monitoring goes with fasting, meal skipping and compulsive
  exercise in first-year students (Hahn et al. 2021, *Eating Behaviors* 42:101520).
- **For low-risk adults new to tracking, a month of MyFitnessPal did no measurable harm** in a
  randomized trial of 192 undergraduate women (Hahn et al. 2021, *J Acad Nutr Diet*
  121(12):2377-2388). The trial excluded anyone with a past ED or high risk, which is exactly
  the group the cross-sectional studies worry about. The reading most researchers take: tracking
  is fine for most adults and risky for a vulnerable minority, which is what Opson's Modes are
  built around.
- **Restriction drives overeating.** Dietary restraint and rigid "make up for it" rules predict
  later binge eating (restraint theory, Polivy & Herman; Stice's prospective work). This is why
  "Why not cut back?" exists and why the restrict and big-day swing is a signal below.
- **Body checking keeps eating disorders going** (Fairburn's transdiagnostic model; Shafran et
  al.). Weighing daily in an adult weight-loss trial has not been shown to cause harm, but
  weighing several times a day is checking, not tracking.

### Teenagers

- The American Academy of Pediatrics advises against dieting and weight talk for adolescents
  and for focusing on healthy habits instead (Golden et al. 2016, "Preventing Obesity and Eating
  Disorders in Adolescents", *Pediatrics* 138(3):e20161649). Project EAT found that dieting
  teens were more likely to have disordered eating and to gain more weight years later
  (Neumark-Sztainer et al.).
- Eating disorders most often start between about 12 and 25, with a peak in mid-adolescence.
- MyFitnessPal's terms now require users to be 18 (checked October 2026). There is no teen mode.

### Safe messaging

NEDA, Beat, ANAD, the National Alliance for Eating Disorders and NICE (NG69) say much the same
for anything that talks about food and weight:

- Don't lead with numbers (weights, calories, BMI) for people who may be vulnerable.
- No "good" and "bad" foods, no praise for eating less, no compensation ("burn it off").
- Don't present rapid weight loss as success. No before and after.
- Always make help easy to reach, and say it is confidential.
- Use person-first, non-judgmental words, and avoid alarm.

NICE NG69 also says not to use a single measure such as BMI to decide whether someone has a
problem, and to treat early signs seriously in young people.

### Stores and law

- **Apple** added 13+, 16+ and 18+ tiers in 2025 and asks about health and wellness topics
  (calorie tracking is its example). A developer can set a higher rating than the questionnaire
  gives. From iOS 26.2 the Declared Age Range API returns an age range (under 13, 13-15, 16-17,
  18+) where the law requires it.
- **Texas SB 2420** (enforced for new Texas Apple Accounts since 2026-06-04) and similar laws
  in Utah, Louisiana and, from 2027, California make the store verify age and get a parent's
  consent for under-18s, and expect apps to read the store's signal. Google's answer is the Play
  Age Signals API.
- **COPPA** covers under-13s in the US. **GDPR** sets the age of digital consent between 13 and
  16 by country. The **UK Age Appropriate Design Code** applies to services likely to be used by
  under-18s: high privacy by default, no nudges that work against the child's interests, and
  profiling off by default.

### What other apps do

| App | Minimum age | Numbers | Safety features |
|---|---|---|---|
| MyFitnessPal | 18 (terms, 2025) | All, always | Won't calculate a goal under 1,200 kcal (women) or 1,500 (men). Completing a diary under 1,000 or 1,200 shows a warning and a five-week projection |
| Lose It! | n/a here | All, always | Warns under 1,200 or 1,500 kcal |
| Recovery Record | n/a here | None | Meal logs with feelings, coping skills, linking to a clinician. Built for ED treatment |
| Rise Up + Recover | n/a here | None | Meal and feelings log, coping strategies, help directory |

Opson sits between them: a full tracker for people who want every number, and a numberless
log for people who don't, with the gentler side on by default for anyone at risk.

## What Opson had before this review

| Area | What it does | Where |
|---|---|---|
| Modes | All numbers, Macros with grams, Macros only, No numbers. Each number can be shown, softened, saved for Later or hidden | `Presets.kt`, `VisibilityPolicy.kt` |
| Pause before showing more | Optional friction before a mode shows more numbers; links to Find support | `PauseViewModel.kt` |
| End after a set time | A mode that switches itself after N weeks | `ModeTiming.kt`, `NumbersSettings.timedOut` |
| "Numbers have been hard" | An optional statement in onboarding, never a diagnosis question. Agreeing hides All numbers from the recommendation, removes the weight goal, pace and deficit, hides BMI and body fat, turns off exercise adjustments, the health and reminder prompts, the over-plan calendar and celebration counts, and spaces prompts out | `SetupAnswers.kt`, `ModeRecommender.kt`, `PlanFlow.kt`, `OnTrack.kt`, `PromptRules` |
| Ranges | Targets default to a range, not one number to hit | `TargetStyle.kt`, `Targets.kt` |
| Low-goal and fast-deficit notes | A soft note under a goal below 1,200/1,500 kcal or faster than about 1% of body weight a week, linking to Find support. Never a block | `GoalSafety.kt`, `TargetsState.safetyNote` |
| Pace tiers | Being reworked by the goal-engine agent | `WeightGoal.kt`, `GoalSafety.weightGoalPace` |
| Find support | Crisis line and ED lines for the US, UK, Canada, Australia, Ireland and New Zealand, offline, with Find A Helpline for everywhere else. Nothing about it is logged | `SupportDirectory.kt`, `FindSupportScreen.kt`, `release.findSupport` |
| Big-day card | "Everyone has big days. You don't need to make up for today." Tomorrow's range stays the same, with "Why not cut back?" explaining why | `DayCloseState.kt`, `WhyNotCutBackSheet.kt` |
| Untracked days | Any day can be marked untracked; Trends leaves a gap | `Day.untracked`, Today's menu |
| Late-day skip card | On an evening with much less than usual logged, offers to skip the day. Counts entries, not calories, where calories are hidden | `LowLoggingNudge.kt` |
| Later | Numbers that open at a set time each day, or once a week | `VisibilityPolicy.isRevealed` |
| Celebrations | For showing up (logging, wrapping up, check-ins), never for eating less or losing weight. A gentle style without counts | `ShowingUp.kt`, `CelebrationPrompt.kt` |
| Trends calendar | Eating far under a deficit is "a bit off", never the best-looking day | `OnTrack.tone` |
| Patterns | Gaps between meals hidden in No numbers | `ModePreset.mealGaps` |
| Progress photos | Hidden by default in the gentler modes, behind Face ID, never uploaded | `ModePreset.progressPhotos` |
| Copy rules | Nothing says exercise earns food; no praise for eating less | `strings_exercise.xml` header, `docs/practices/prompts.md` |
| Leak test | Every surface rendered in every calories-hidden mode and scanned for an amount | `:apps:leaktest` |
| Analytics | Step names and booleans only; never a setup answer | `OnboardingViewModel` KDoc and its test |
| Age | Birthday asked in the profile step, accepted from 13. Terms said 13+ | `PlanFlow.AGES`, `legal/terms.md` |

### What the copy audit found

I searched every string file for praise, comparison and pressure words ("great job", "only X
left", "too much", "cheat", "earn", "burn it off", "bad day") and read the Today, Day close,
Trends and goal copy. The only "Great" is a check-in answer about mood and sleep. Under-target
days are never praised and no screen in Today or Trends uses the danger or warning colour. The
copy was already clean, so the fixes in this pass are about behaviour, not words. One change:
Welcome now says the next step asks for a birthday.

## Gaps found

1. **Anyone 13 and up could make an account**, and a 14-year-old got weight goals, deficits and
   adult BMI.
2. **Nothing noticed a pattern.** The tools for a single hard day were there; nothing looked at
   the last few weeks.
3. **Prompt analytics would have recorded a safety note being shown**, tied to an install id.
4. **Profile showed adult BMI to teenagers.** Adult BMI lines don't apply under 18.
5. **Skipping low days hides them.** The late-day card offers to mark a light day untracked, and
   untracked days leave every calculation, including the new check. For someone restricting, a
   string of skipped light days is itself a sign. Not changed: it needs a clinician's view (see
   below).
6. **No store age signal.** Opson doesn't read Apple's or Google's age range, which Texas and a
   few other states now expect.

## What got built in this pass

### Age gate (16 and over)

- Onboarding asks "When's your birthday?" after Welcome and before sign-in, so no account
  exists yet. The question doesn't say where the line is, so nobody is told which answer gets
  them in.
- Someone under 16 sees "Opson isn't for under-16s yet", a short reason, a suggestion to talk to a
  parent, school nurse or doctor, and Find support. No account is made, there is no way back to
  the question, and the phone keeps the gate closed until their sixteenth birthday
  (`AppData.ageGateOpensOn`, the date only). The birthday itself isn't kept, and the app sends no
  step event or abandoned event for them.
- Someone 16 or 17 gets an account, with the same gentle goal path as after "numbers have been hard":
  no weight, goal weight, pace or deficit in setup, no goal flow card on Today, no BMI or body
  fat on Profile, and celebrations without counts unless they choose counts.
- Why 16: 13 matches COPPA but not the evidence; the peak onset of eating disorders is
  mid-adolescence and the AAP advises against dieting for teens. 18 matches MyFitnessPal, but an
  older teen turned away will find a tracker with fewer protections. 16 is also GDPR's highest
  age of consent, and Apple has a 16+ tier. The owner asked for 14-year-olds to be turned away,
  and 16 does that with margin.
- Nothing locks out an account made before the gate. If its birthday
  says under 18, it gets the gentle goal path and no BMI. There is no new server column: the
  birthday is already in the synced settings, which is the account-level record.
- For the onboarding rework, the steps are `AgeStep` and `TooYoungStep` in
  `AgeGateSteps.kt`, the rules are `AgeGate` in `:libraries:diary`, and the view model applies
  them in `handleAgeContinue`, `isAgeGateClosed` and `withAgeAnswer`. Move them anywhere, as long
  as they stay before sign-in.
- Terms, privacy policy and the consumer health data policy now say 16. The store listing notes
  the rating to set by hand.

### Pattern check and the kind note

`HardStretch` (`:libraries:diary`) reads the last four weeks of the log on the phone. Only fair
days count: tracked, in the past or wrapped up, with an amount on every food. A light day only
counts as low if it looks complete (wrapped up, or food in three meals), so a forgotten lunch is
not restriction.

| Signal | Strength | Rule (all `TODO(clinician)`) | Why |
|---|---|---|---|
| Low days | strong | 4+ complete days in 14 under 1,000 kcal (women), 1,200 (men), 1,100 (not said) | MyFitnessPal's own "too little" lines on completing a diary |
| Swings | strong | 2+ big days in 21 (over 1.5x their own usual) with a low day within 2 days | The restrict and binge cycle |
| Many weigh-ins | strong | 3+ days in 14 with 3+ weigh-ins an hour or more apart | Body checking; a scale sending two readings counts once |
| Fast loss | strong | A trend line over 3+ weeks falling faster than 1% of body weight a week | Faster than any healthy pace, and past the first weeks' water loss |
| Late after big days | supporting | 2+ big days in 14 with food logged after 10:30 pm | Night eating after a big day |
| Why not cut back | supporting | Opened 3+ times in 14 days | Wanting to compensate |
| Hard days | supporting | 4+ days in 14 wrapped up as Hard | The person's own word |

The note shows with one strong signal or two supporting ones. It is a prompt (`kind_note`),
so it never lands on another prompt and waits out the quiet first days, and it sits above
every other prompt. It says "This might be a hard stretch. If food has felt heavy lately,
talking to someone can help." with Find support, Show fewer numbers (unless already No
numbers), Not now, a line saying it came from the log on the phone only, and "Don't show these
notes". It never names a number or what set it off. It comes back at most every 21 days. It is
offered after "numbers have been hard", since those are the people it is most for. It is
behind `release.findSupport` and the person's own switch in Support and sharing.

Nothing about it leaves the phone: the check runs on device, the "Why not cut back?" opens are
kept in `AppData` and dropped on sign-out, and the prompt is `sensitive`, so the processor sends
no app event and writes no log line for it.

### Smaller fixes

- Profile shows no BMI or body fat under 18.
- `PromptCandidate.sensitive` for any future prompt that reacts to eating or weight.

## Prioritized list

### Must-have

| Item | Status | Risk if missing |
|---|---|---|
| Age gate before any account, 16+ | Built | Children get adult deficits and weight goals; store and legal exposure |
| Gentle defaults for 16 and 17 | Built | Teens get weight-loss plans against AAP guidance |
| On-device pattern check with help and an opt-out | Built | Someone sliding into restriction gets nothing but more numbers |
| No analytics about safety notes or minors turned away | Built | Health inference tied to an install id in a third-party log |
| Clinician review of every `TODO(clinician)` (thresholds, copy, ages) | **Open, owner** | Thresholds an engineer picked go to real people; false alarms erode trust, misses hurt |
| Set the store rating to 16+ (App Store Connect) and target audience 16-17 and 18+ (Play) | **Open, owner, by hand** | The listing says 12+ while the terms say 16 |
| Pace tiers and a personal deficit limit | Goal-engine agent | Fast loss stays possible for adults |

### Should-have

| Item | Why | Risk if missing |
|---|---|---|
| Read the store's age range (Declared Age Range on iOS 26.2+, Play Age Signals) and, in Texas and similar states, honour a parent's consent for 16 and 17 | Required by SB 2420 and its siblings for new accounts in those states | Non-compliance in a growing list of states; a self-reported birthday is easy to fake |
| Count frequently skipped light days as a supporting signal | Gap 5 above; skipping a light day is offered by the app itself | A person restricting can hide every low day from the check with one tap a day |
| Hold milestone celebrations while the check holds | A streak celebration in a hard stretch can reward the tracking itself | Celebration lands on someone who is struggling |
| Compensatory exercise as a signal (exercise logged right after a big day) | Exercise logging exists now (`release.exercise`) | A common compensation goes unseen |
| An optional weigh-in limit ("once a week") in Weight settings | Gives people a tool against checking, not just a note about it | Only the note helps |
| Kind note research: log on device only whether it was shown and answered, for the person's own view, never sent | Lets a clinician later ask "did it help?" with consent | No way to know if it works |
| Recheck the helplines each quarter | Services move; `SupportDirectory` says so | A dead number in a crisis |
| Exclude navigation route names from release logs and crash breadcrumbs, or keep the neutral naming rule (`KindNoteRoute`, `FindSupportRoute`) | A breadcrumb trail can say someone opened Find support | Health inference in crash reports |

### Later

| Item | Why |
|---|---|
| Supervised accounts (a parent, coach or clinician) that could open Opson to 13 to 15 | The owner's condition for younger users; needs real supervision tooling |
| A clinician-linked mode, as Recovery Record does | Lets a treatment team use Opson with a client |
| A short validated self-check (for example SCOFF) behind Find support | Helps someone decide whether to reach out; needs clinical wording |
| Helplines beyond six countries | Find A Helpline covers the rest for now |
| An opt-in study of the detectors' accuracy | Thresholds are guesses until measured |

## Open questions for a clinician

1. Are the thresholds in `HardStretch.kt` and `GoalSafety.kt` reasonable? Too many false alarms
   is its own harm.
2. Should a skipped (untracked) day still count for the check when it looks complete? It would
   catch more, but untracked means "don't count this", and the check is meant to be private.
3. Is 16 the right minimum, and is the older-teen path gentle enough?
4. Every string in `strings_safety.xml`, "Why not cut back?", and the onboarding statement.
