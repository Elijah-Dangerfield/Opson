# GLP-1 medication tracking: research and v1 spec

## Recommendation

Build a small, honest medication log inside Opson: what you take, when, where you
injected, how you felt, and how that lines up with your weight and your eating.
Ship it behind `release.medication`. Leave the estimated medication level curve
out of v1. Every dedicated tracker has one, but it is a population model dressed
up as a personal measurement, and Opson's whole stance is to avoid numbers that
look more certain than they are. Revisit it as a plain "days since your dose"
phase line, not a curve.

The v1 scope is in [v1 spec](#v1-spec). Everything after it is later or never.

## As built (v1, 2026-10-29)

Behind `release.medication` (default off, on in dev through `seed_dev_config.py`, in the QA
menu). Where the build differs from the spec below, this section wins.

- **Modules.** `:libraries:medication` holds the model, the dose-day rules (`DoseDays`,
  `SiteRotation`, `DoseMarks`), the flag and the Room entities; `:libraries:medication:impl`
  the repository; `:features:medication` the routes and `:features:medication:impl` the page,
  the dose sheet and the Today card. A module of its own because the plan, doses and side
  effects are read by Today, Trends, the provider summary and reminders, none of which should
  own them.
- **On the phone only.** The prod server has no medication storage and is not redeployed with
  this, so nothing syncs and nothing is queued: the plan is a user-scoped device cache shaped as
  the spec's `Settings.medication` key, doses are Room 13 `medication_doses` (uuid ids,
  `clientUpdatedAtEpochMs`, tombstones: the spec's columns), and side effects are
  `symptom_days`, one row per day. No `V28` migration and no `SyncEntity` yet. A user change
  wipes all three. Adding sync is an outbox `enqueue` in `MedicationRepositoryImpl` plus the
  server table, not a data migration.
- **Side effects are not on Close the day.** Check-ins are being rebuilt separately, so they
  are asked on the Medication page ("How are you feeling today?", the spec's symptom list,
  Mild / Moderate / Severe, the care line for severe vomiting or stomach pain). `SymptomLog`
  is the read and write API a check-in can use to ask the same question: `asking` says when.
- **More.** One row through `MoreRowContribution`, in a "Health" card. Its subtitle is fixed
  ("Doses, sites and how you feel"), never the schedule or a dose.
- **Today.** The card comes in through a new `TodayCardContribution` (Today draws whatever
  the set holds, after Fasting). "Show on Today" is on the Medication page; Edit Today's cards
  has no row for it yet. Between weekly doses the card has no Log dose button; it opens the
  Medication page, where Log a dose comes first.
- **Reminders** need `release.reminders` too, since that flag gates every notification.
- **Not built in v1:** the onboarding offer, the export CSV, Patterns' dose-day markers and
  side-effect strip, the nutrition emphasis card, and a "Started on" date picker (the model has
  `startedOn`; until the page sets it, the weight chart's "Started" tick is the first dose).

## What other apps do

| App | Dose log | Site rotation | Reminders | Side effects | Level curve | Nutrition tie-in | Price |
|---|---|---|---|---|---|---|---|
| [Shotsy](https://shotsyapp.com/) | Yes, many meds at once | Picks the next site from a map | Yes, countdown | Yes, with patterns | Yes, from published PK data, paid (Shotsy+) | Calories, protein, water | Core free |
| [Pep](https://pepglp1.com/best-glp-1-tracker-apps/) | Yes, mg or units, compounded | Yes | Yes, plus titration step-up reminders | Logged against the dose | Yes, free | Protein, fiber, water goals free; AI photo is Pro | Pro about $40/yr |
| [Glapp](https://glapp.io/) | Yes | Yes | Weekly shot | Daily check-in: side effects, cravings, "food noise", bowel, sleep | Yes, plus "shot phases" | Tips timed to the cycle | Premium "Max" |
| [MyFitnessPal GLP-1 Support](https://blog.myfitnesspal.com/glp-1-support-medication-tracking/) (Apr 2026) | Medication, dose, site, time on the same screen as food | Records site, no guided rotation | Dose day and time; last dose on Today | iOS only, with severity | No | Food log beside it | Free, US only |
| [Lose It! GLP-1 Logging](https://loseit.zendesk.com/hc/en-us/articles/50221250242964-GLP-1-Logging) | Yes, incl. stomach quadrants | Records site | Not documented | Yes | Yes, peaks and valleys | Protein and fiber targets | Premium |
| [Noom GLP-1 Companion](https://www.noom.com/in-the-news/noom-launches-free-version-of-its-powerful-glp-1-companion/) | Yes | n/a | Yes | Side effect tools | No | Protein targets, muscle workouts | Free since June 2026 |
| WeightWatchers | Dose tracking ([U.S. News](https://health.usnews.com/best-diet/medication/noom)) | n/a | Yes | Yes | No | Inside the points program | From CORE+ |
| Cronometer | None; people use Gold custom biometrics ([Cronometer blog](https://cronometer.com/blog/glp-1-drugs-how-cronometer-can-help/)) | No | No | DIY | No | Best-in-class nutrients | Gold |

MyFitnessPal's own wording is worth copying in spirit: it says it does not verify
the drug, dose, timing or safety and does not alert you to missed or extra doses
([GlobeNewswire](https://www.globenewswire.com/news-release/2026/04/28/3282728/0/en/MyFitnessPal-Launches-Comprehensive-GLP-1-Support-Helping-Users-Stay-Consistent-and-Build-Habits-Alongside-Medication-to-Maximize-Their-Experience.html)).
Most comparison pages are written by competing apps, so treat their feature
tables as marketing.

**What the clinical guidance says.** The May 2025 joint advisory from ACLM, ASN,
OMA and The Obesity Society ([PMC12304835](https://pmc.ncbi.nlm.nih.gov/articles/PMC12304835/)):

- Protein 1.2 to 1.6 g/kg/day during active weight loss, or 80 to 120 g/day as an
  absolute target. Never below 0.4 to 0.5 g/kg.
- Fluids and fiber "should be encouraged", fiber increased gradually. No numbers.
- Small meals every 3 to 4 hours; vomiting is more likely with large meals.
- Strength training at least three times a week plus 150 minutes of moderate
  aerobic activity, to protect lean mass.

**Pharmacokinetics, for the level-curve question.** Semaglutide's half-life is
about a week and tirzepatide's about five days, with steady state after roughly
4 to 5 weeks of weekly dosing ([Australian Prescriber via PMC](https://www.ncbi.nlm.nih.gov/pmc/articles/PMC7450779/),
[rx.com summary of the labels](https://rx.com/education/glp1-half-life-clearance-guide)).
A curve built from that is a textbook average. It cannot know your absorption,
your weight or whether a pen misfired.

## Where Opson is already strong

Opson already has most of the surrounding pieces, so v1 is mostly wiring:

- **Hunger.** Close the day's check-in (`CheckIn` in `libraries/diary/Day.kt`)
  asks how often hunger came up (`DayHunger`, Rarely to Very), energy, sleep and
  moods. Patterns charts it. That is the appetite tie-in, already built.
- **Weight.** `WeightRepository` and Trends › Weight (`WeightScreen`,
  `WeightCurve`) exist.
- **Protein and fiber.** `Targets.proteinG` and `Targets.fiberG` exist, and
  Today's nutrient picks are configurable.
- **Water.** Water goal, Today card and reminders exist.
- **Reminders.** `libraries/reminders` plans local notifications by key; a dose
  reminder is one more `ReminderKey`.
- **Provider summary.** `release.providerSummary` builds a PDF for a provider;
  doses and side effects belong in it.
- **Onboarding knows who has "a medical need" or is "working with a provider"**
  (`OnboardingAnswers.why`). That is the natural moment to offer this.

## v1 spec

### Principles

1. **A record, not a regimen.** Opson logs what the person did. It never tells
   them what dose to take, when to step up, or what to do about a missed dose.
2. **Medication is not a food number.** Logging a dose is allowed in every
   mode. What it is shown next to (weight, protein grams) follows the mode.
3. **No streaks, no "adherence score", no red.** A late dose is just a dose
   logged on a different day.
4. **Health data stays private.** Never in telemetry, logs, Sentry or Grafana;
   in export and the provider summary only.

### The flag

`MedicationEnabled : FlagConfigValue`, path `release.medication`, default false,
in `:libraries:diary` next to `FastingReleaseFlag`, registered in
`apps/admin/config-manifest-registry.json` and the QA menu like the others. Off
means no More row, no Today card, no check-in rows, nothing scheduled.

### Who sees it

| Condition | Result |
|---|---|
| Flag off | Hidden everywhere |
| Any mode, including No numbers | Offered. A dose is not a food number. |
| `onboarding.hard` (numbers have been hard) | Offered, but the weight overlay follows the existing weight rules and no protein gram target is suggested. `TODO(clinician)`: confirm. |
| Medication tracking off in its own settings | No Today card, no reminder, no check-in rows; history kept |

### Data model

**Plan, in `Settings`** (syncs with settings, like `fastPlan`):

```kotlin
@Serializable
data class MedicationPlan(
    val on: Boolean = false,
    val medications: List<Medication> = emptyList(),   // v1 UI allows one; the list keeps room for two
    val sites: Set<InjectionSite> = InjectionSite.DEFAULT,  // the sites the person uses
    val todayCard: Boolean = true,
)

@Serializable
data class Medication(
    val id: String,                    // client uuid
    val drug: MedicationDrug,          // semaglutide, tirzepatide, liraglutide, dulaglutide, other
    val brand: String?,                // "Wegovy", "Zepbound", free text for compounded or other
    val form: MedicationForm,          // Injection, Pill
    val doseUnit: DoseUnit,            // Mg, Units (compounded vials), Mcg (rare)
    val currentDose: Double?,          // what the person says they are on now
    val schedule: DoseSchedule,        // Weekly(dayOfWeek, time) | Daily(time) | AsNeeded
    val reminder: Boolean = false,
    val startedOn: LocalDate?,
)
```

**Doses, a new synced entity** `SyncEntity.MedicationDose("medication_dose")`,
Room table `medication_doses`, server migration `V28__medication_doses.sql`
written like `V24__exercises.sql` (additive, RLS on, `server_seq` trigger, safe
to run twice):

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | client id |
| `user_id` | UUID | FK auth.users, cascade delete |
| `medication_id` | TEXT | `Medication.id` |
| `drug` | TEXT | copied so history reads after the plan changes |
| `brand` | TEXT null | copied |
| `dose` | NUMERIC(7,3) | check 0 < dose ≤ 100 |
| `dose_unit` | TEXT | `mg`, `units`, `mcg` |
| `taken_at` | TIMESTAMPTZ | the person can move it |
| `day` | DATE | local date of `taken_at`, the key Trends uses |
| `site` | TEXT null | `InjectionSite.wire`; null for pills |
| `note` | TEXT null | ≤ 280 chars |
| `client_updated_at`, `server_seq`, `deleted` | | as every synced table |

A dose change is simply a dose with a different amount; the timeline derives
"stepped up to 5 mg on Oct 3" from consecutive doses. No separate titration table.

**Injection sites** (`InjectionSite`, wire values): `abdomen_upper_left`,
`abdomen_upper_right`, `abdomen_lower_left`, `abdomen_lower_right`,
`thigh_left`, `thigh_right`, `arm_left`, `arm_right`. Default: all eight. The
suggested next site is the enabled site used least recently. Nothing else; no
"too soon" warning.

**Side effects, on the existing check-in.** Add to `CheckIn`:

```kotlin
val symptoms: Map<Symptom, Severity> = emptyMap(),  // only asked while MedicationPlan.on
```

`Symptom`: `nausea`, `vomiting`, `constipation`, `diarrhea`, `heartburn`,
`stomach_pain`, `fatigue`, `headache`, `dizziness`, `injection_site`, `other`.
`Severity`: `mild`, `moderate`, `severe`. Additive on the `day_record` wire; an
older app ignores the field and copies it through (check `SyncWire` keeps
unknown check-in fields, as `MealFeeling` does for fullness and moods).

### Medication list (pickers, not rules)

| Drug | Brands | Form | Usual schedule | Dose chips (label steps) |
|---|---|---|---|---|
| Semaglutide | Wegovy, Ozempic | Injection | Weekly | Wegovy 0.25, 0.5, 1, 1.7, 2.4 mg; Ozempic 0.25, 0.5, 1, 2 mg |
| Semaglutide | Rybelsus, Wegovy pill | Pill | Daily | Free entry |
| Tirzepatide | Zepbound, Mounjaro | Injection | Weekly | 2.5, 5, 7.5, 10, 12.5, 15 mg |
| Liraglutide | Saxenda, Victoza | Injection | Daily | 0.6, 1.2, 1.8, 2.4, 3 mg |
| Dulaglutide | Trulicity | Injection | Weekly | 0.75, 1.5, 3, 4.5 mg |
| Compounded semaglutide or tirzepatide | free text | Injection | Weekly | Free entry, mg or units |
| Other | free text | either | any | Free entry |

The chips are shortcuts; a typed dose is always accepted. `TODO(clinician)`:
check the steps against current labels before launch. Brand names need the ®
treatment the lawyer prefers (`docs/legal-review-2026-10.md`).

### Screens

**1. More › Medication** (new row in the extras card, after Fasting; subtitle
"Off", or "Weekly, Thursdays"; never a dose). A settings page in the
`SettingsPage` pattern:

- Intro, first visit only: "Keep a record of your medication next to your
  food and how you feel. Opson doesn't check doses or give medical advice;
  your prescriber does." Button: **Set up**.
- Medication: drug and brand picker, form, dose (chips plus typed), unit.
- Schedule: Weekly (day and time) / Daily (time) / When needed.
- Reminder switch: "Remind me on dose day". Uses the reminder level and Quiet
  mode already in Reminders.
- Injection sites: a body outline with the eight sites as toggles. "Opson
  suggests the one you used longest ago."
- Show on Today switch.
- History: the dose list, newest first, each row editable and deletable
  (Undo snackbar as elsewhere).
- Footer disclosure (see [Copy](#copy)).
- Turn off: "Stop tracking medication". Keeps history; says so.

**2. Log a dose sheet.** Opened from the Today card, the reminder's "Log it"
action, or History's +.

- Medication (preselected), dose (prefilled with `currentDose`), time (now,
  editable, can be yesterday), site (prefilled with the suggestion, a small
  body map to change it), note.
- Save writes a `medication_dose` and, if the dose differs from
  `currentDose`, asks once: "Make 5 mg your current dose?" Yes / Just this once.

**3. Today card, "Medication"** (a card in Edit Today's cards, like Fasting):

| State | Text | Action |
|---|---|---|
| Dose day, not logged | "Dose day. Next site: lower left abdomen" | **Log dose** |
| Logged today | "Logged 7:40 PM, upper right thigh" | tap opens the dose |
| Between doses (weekly) | "Last dose Thursday. Next on Thursday" | **Log dose** in overflow |
| Overdue | "Thursday's dose isn't logged" | **Log dose**, **Skipped** |
| Daily med | "Today's dose" / "Logged 8:02 AM" | **Log dose** |

"Skipped" writes nothing but hides the prompt until the next dose day. No
counting of skipped doses anywhere.

**4. Close the day check-in, extra rows** (only while `MedicationPlan.on`):
"Any side effects today?" with symptom chips; tapping one asks Mild /
Moderate / Severe. Severe `vomiting` or `stomach_pain` shows one line under
the chips (see [Copy](#copy)). Hunger stays the existing row.

**5. Trends › Weight overlay.** Where weight is visible in the current mode,
dose changes draw as small labelled ticks on the weight curve ("5 mg") and the
start date as a "Started" tick. No projection, no "lost X since starting"
headline. In modes that hide weight, nothing.

**6. Patterns.** The hunger card gains dose-day markers so people can see
whether hunger is lower the days after a dose. Side effects get a simple
calendar strip (which days had which symptom). No correlations stated as fact.

**7. Provider summary.** A "Medication" section option: doses in the range,
dose changes, symptom days with severity. Off by default, on when the plan is on.

**8. Export.** `opson-export.zip` gains `medication_doses.csv` and the
symptoms columns in the check-in file.

### Nutrition emphasis (v1, small)

When the plan is first turned on, one card on the Medication page:

> **Protein and fiber help on this medication.** Eating less makes it easier to
> fall short. Want protein and fiber on your Today card?

**Add them** sets Today's nutrient picks to include protein and fiber (and
water if it is off). It does not change targets. In All numbers and Macros with
grams, a second line offers "Suggest a protein target from your weight" using
1.2 g/kg of the latest weigh-in, rounded to 5 g, capped to 80 to 120 g when
there is no weigh-in, with "Ask your care team what fits you." Never offered
when `onboarding.hard` is true. `TODO(clinician)`.

Nothing else changes in the energy maths. People on GLP-1s often eat very
little; the existing goal floor (`GOAL_FLOOR_KCAL`) and maximum deficit still
apply, and Close the day keeps its no-red rule.

### Entry points

1. More › Medication.
2. Onboarding: someone who picked "a medical need" or "working with a provider"
   sees a one-line offer on the last setup step: "Taking a medication like
   Wegovy or Zepbound? You can log it in More." No forced setup.
3. Today card, once set up.
4. Dose-day reminder notification: actions **Log it** (opens the sheet) and
   **Already did** (logs a dose at the reminder time with the suggested site).
5. Edit Today's cards: Medication switch.

### Reminders

New `ReminderKey.Dose(date, medicationId)`, `ReminderKind.Dose("dose")`, id
`dose-<date>-<medicationId>`. Fires at the schedule's time on dose days, is
dropped once a dose is logged for that day, and respects Quiet mode and the
reminder level. Copy never includes the dose amount (lock screens are public):

- Title: "Dose day"
- Body: "Tap to log it when you're done."

### Copy

Plain, short, no exclamation marks, no praise for losing weight.

- Disclosure footer: "Opson keeps a record for you. It doesn't check your
  dose, timing or what to do if you miss one. Follow your prescriber's
  instructions and the leaflet that came with your medication."
- Severe symptom line: "Severe stomach pain that doesn't go away, or vomiting
  you can't keep fluids down through, can be serious. Contact your prescriber or
  get urgent care." `TODO(clinician)` on the wording and which symptoms trigger it.
- Turning off: "Your dose history stays. Turn this back on any time."
- Empty history: "No doses logged yet."

### Privacy

- Medication, doses and symptoms are health data (special category in the EU).
  Covered by the existing health consent question in `onboarding.md`; the
  lawyer should confirm medication needs no extra line.
- Never in telemetry. Events may say `medication_dose_logged` with no drug,
  dose or site. Add the fields to the leak tests and the log scrubber.
- Not visible to Grafana's `support` schema (V22): exclude the table.
- App Privacy label: "Health" already declared if `release.health` ships;
  check it covers this.
- Deleted with the account (cascade) and wiped by `UserScopedDataReset`
  (`ClearableDao`).

### Tests the builder should write

- `MedicationAccess` by flag and mode, including No numbers and `hard`.
- Next-site suggestion: least recently used enabled site; ties go in list order.
- Dose reminder dropped after logging; never contains the amount.
- Sync round trip of `medication_dose`; an older server refusing it as
  `unknown_entity` keeps the row queued.
- Check-in symptoms survive a write from an app that doesn't know them.
- Leak test: no dose, drug or site in any telemetry event or log line.
- Weight overlay hidden in modes that hide weight.

### Not in v1

- Estimated medication level curve.
- Titration schedules and step-up reminders.
- Supply tracking (pens left, refill reminders, vial and syringe unit maths).
- Two or more medications in the UI (the model allows it).
- Widgets.
- "Food noise" as its own question (the hunger row covers it for now).
- Progress photos tied to doses.

## Later, and the level curve question

**Estimated level curve.** The argument for: Shotsy, Pep, Glapp and Lose It all
show one, users like seeing why day 6 feels hungrier than day 2, and it is
cheap to compute (a one-compartment model with the label half-life). The
argument against: it shows a precise-looking line that is really an average of
trial participants; people may time eating or doses off it; it invites "my
level is low, I should take more". Opson's ethos is fewer, truer numbers.

If it ever ships: All numbers only, unitless (no ng/mL), labelled "a rough
shape from published averages, not a measurement", no y-axis values, and never
for `hard`. A safer first step is a "shot phase" line on the Today card ("Day 5
since your dose") and dose-day markers on the hunger chart, which v1 already
gives.

**Titration helper.** Record the step-ups people make; never suggest them.

**Supply.** Pens or vials left and a refill reminder. Useful, low risk, v2.

**Muscle protection.** The advisory's strength training advice pairs with
logged exercise (`release.exercise`); a gentle weekly "strength sessions" count
is possible but counts are close to streaks, so it needs care.
