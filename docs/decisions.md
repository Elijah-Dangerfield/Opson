# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-10-27: Onboarding offers goal numbers only to someone comfortable with them

**Decision:** behind `release.onboardingSetup`, the goal step is shown when the
starting mode shows calorie amounts (Full numbers) and the person did not agree
that numbers have been hard. Full numbers is never offered after that
statement, so the second condition is a guard rather than a second rule, and it
is spelled out in `OnboardingState.showsGoal` so a future mode cannot slip past
it. Everyone else gets no goal number in onboarding at all: Macros with grams,
Macros only and No numbers go straight to the meal times step where it applies,
or to Today, and their goal stays the app's starting range in the background.
Someone who said "I'd rather not see them" and then picked Full numbers from
All modes does get the goal: they chose every number.

With `release.goalsAndEnergy` on as well, the resting energy step comes first
and the goal starts from the ordinary day it gives (resting energy plus
everyday activity, as Burned works it out with no health app), rounded to the
nearest 50 kcal, with "Based on about 2,450 burned on an ordinary day" under the
stepper. `GoalAnswer.startingFrom` goes through `Targets.withEnergy`, so the
start never goes under the floor: while `GOAL_FLOOR_KCAL` is unset, an estimate
below the app's starting range (2,100) starts there and says nothing about the
burn, and the floor stays where Targets puts it. Macros scale with it, as ratio
mode does. Once the person moves the amount, a changed estimate no longer moves
it. A deficit or surplus picked straight away starts at zero, because the aim
already is the burn; that is the existing rule ("A deficit can be chosen without a
health app", below), not a new one.

Both goal steps trade Skip for "Set this later", which goes past both and
writes neither. Start on the recommendation offers them again.

With `release.goalsAndEnergy` off the goal step is the range alone, from the
app's starting range, as before. With `release.onboardingSetup` off there are
no setup steps and nothing changes.

**Alternatives:** asking for the goal from everyone and hiding the amount
(rejected: a goal without a number is still a goal to hit, which is what the
numbers answer asked us not to set up); a "macros only" goal step that picks a
split such as Higher protein or Lower carb (rejected for now: the named splits
are engineer-picked, `TODO(dietitian)`, and "Lower carb" is diet language this
audience does not need on day one); starting from the raw burn with no floor
(rejected: a typo or a small person's estimate would set a low floor that only a
dietitian should set); keeping the goal before resting energy (rejected: the
goal could not start from the estimate the person had just given).

**Units.** Height is typed in centimetres or feet and inches wherever it is
entered (onboarding and Resting energy, through the shared `HeightField`) and
stored in centimetres. Weight in onboarding has its own kilograms or pounds
switch instead of one switch for both. Both start in the phone region's units
(`RegionUnits`: the US, Liberia and Myanmar get feet and pounds), read through
`DeviceRegion`, which moved to `:libraries:core` for it. The height unit is a
new top-level `heightUnit` key; until it is picked, someone in pounds keeps
inches, as height followed weight before.

## 2026-10-27: A closed day takes nothing until it is reopened

**Decision:** once a day is closed with Close the day (`Day.closedByUser`,
`closed_at` set), nothing can be added to it or changed on it until it is
reopened. The diary enforces it, not the screens: every write that would add
food, water, exercise, a meal's note or the day's note, or change or remove
what is there, answers `DayWrite.DayClosed(day)` and writes nothing, checked
inside the same transaction as the write. That covers `addItems`, the copies
into the day (`copyDay`, `copySlot`, `copyItems`), `updateItem`,
`removeItem(s)`, `restoreItems`, `moveItem(s)`, `clearDay`, the three water
writes, `setDayNote`, `setAteNoDetails`, `setFeeling` and
`ExerciseRepository.add/update/delete`. A batch that touches a closed day
writes none of it. Food a closed day refuses does not end a running fast.

Edits and deletes are blocked too, not only adds. Closing says the day is done,
and a day whose food can still change under its close is not done. Reopening is
one tap from wherever the block shows, so nothing is lost by asking for it.

Still allowed on a closed day: closing it again (Day close's Save updates the
note, the feeling and the check-in in the same write as the close, which is why
`closeDay` takes them as a `DayReflection` and `setDayFeeling` and
`DayCheckIns.setCheckIn` are gone), reopening it, marking it untracked, its
meal groups, copying its food out to an open day, and starring a food.

A past day nobody closed stays open, as before: "closed" here means Close the
day, not the date. `Day.isClosed(today)`, which reveals delayed numbers for
past days too, is unchanged; the header check is filled only for a day the
person closed.

Where it shows: on a closed day Today has no + on meals, water or Burned, no
select, drag or swipe, and its menu drops Copy yesterday, Add a note and Clear
this day, with Reopen in Close the day's place. A line under the card says
"Today is closed. Reopen it to add more." with Reopen. The + sheet and search
show the same line in place of what would log (a weigh-in and the fasting timer
stay, since they are not part of the day). A food's sheet offers only Copy and
the star, with the line under them. A write refused anyway (the day closed on
another phone a moment ago, or from a sheet already open) lands on the
`DayClosedRoute` sheet with Reopen and Not now. Day close on a closed day says
Save and Reopen this day, and its caption no longer promises the day can be
edited after closing. Reminders stop for a closed day and its delivered ones
are cleared.

**Sync:** `closed_at` already travels on the day record, so a second phone
holds the block once it has pulled the close
(`apps/integration/.../ClosedDaySyncTest`). The server does not check it: a
phone that wrote offline before it saw the close still lands its write, which
was made while the day was open on that phone. Refusing it would roll the
person's food back with no way to tell them why.

**Left open:** starting a fast at the window's close (fasts are not part of a
day's log, and closing the evening is when one usually starts); a weigh-in,
which belongs to no day; Edit a copy's "use it for this entry" when the day
closes while that screen is open, where the copy is saved and the entry keeps
its food.

**Alternatives:** blocking only adds and leaving edits and deletes open.
Rejected for the reason above. Enforcing it in the screens only. Rejected: the
widget, a reminder's Already ate and a second phone all reach the diary without
Today's screens. A thrown exception instead of a typed result. Rejected: an
expected outcome is a value here (`docs/review/PRACTICES.md`, Errors at the
edges).
## 2026-10-27: Packaged foods come in through their label; the meal photo estimate is quick actions only

**Decision:** a barcode miss and a search with no results offer Add this food
(`release.submitFood`): photos of the pack's front and nutrition label, read
by `claude-fable-5-1`, converted and checked in code, corrected by the person,
logged at once as their own custom food, and sent to a review queue. An
approved food joins search and barcode lookup for everyone. The meal photo
estimate (`release.photo`) is offered from the + sheet's Photo tile and nowhere
else: search's "Photograph it" is gone, and the scanner, Describe and the
empty search never offer it. Its behaviour is otherwise unchanged, and it
stays out of 0.2.

The submitted food is a `foods` row (source `user_submitted`, tier 4,
`review_status = 'pending'`) from the first moment, not a separate table that
approval copies from, so the submitter's own scan and the reviewer's edits work
on the row everyone will later see. Search filters on `review_status`; the
row's barcode stays null until approval so a pending submission can never take
a code from another product. The photos live in Postgres
(`food_submission_photos`), not Supabase Storage: they are read only by the
token-gated admin API, a cascade removes them with the account, Testcontainers
covers them, and there is no bucket or service-role key to configure. At about
300 KB a photo and deletion 30 days after review, the table stays small; if
volume grows, Storage behind the same `photo()` call is the move.

A barcode missed and then found by searching within ten minutes is reported
as a pairing (`barcode_link_suggestions`: barcode, food, count, first and last
time, no account, no search text) for a reviewer to attach. Nothing attaches a
barcode on its own.

**Alternatives:** the meal estimate as the fallback for packaged foods (it
guesses portions from a plate; a label states them, so the label is the better
source every time there is one); a `food_submissions` table that approval
copies into `foods` (two copies of every food to keep in step, and the
submitter's own lookups would need their own path); Supabase Storage for the
photos (a second store, a secret and a bucket per environment for a few
hundred KB per submission); attaching a paired barcode automatically (one
person's mistaken pick would rename a product for everyone).

**Why:** a label read with arithmetic checks is the most accurate food data
the app can get from a person, and review keeps one person's typo out of
everyone's diary. Keeping the estimate to one entry point stops it competing
with the label for a food that has one.

## 2026-10-27: One everyday activity setting for exercise and goals

**Decision:** the exercise work and the goals work each added a "how active is
an ordinary day" choice (`EverydayActivity` with `everydayActivity`, and
`ActivityLevel` with `activityLevel`), with the same four shares of resting
energy. Neither had shipped, so they are one now: `EverydayActivity`, the
`everydayActivity` key, and `burnedFor` as the only place that decides what a
day's activity is. Resting energy, Targets ("Burned comes from") and
onboarding's body step all edit that one setting.

Everyday activity no longer waits for `release.exercise`. With
`release.goalsAndEnergy` on and no health app reading activity, Burned is
resting energy plus everyday activity, so a deficit works without Health;
`release.exercise` only adds logged exercise on top. With a health app reading
activity, its movement and workouts replace both, as before. "Add activity to
Burned" off is still resting energy only, whatever the source.

**Why:** two keys for one answer would let Today and Targets disagree about
Burned. `EverydayActivity` matches `Burned.everydayKcal` and the research note.

## 2026-10-27: A deficit can be chosen without a health app; the caps stay

**Decision:** a set deficit (and a set surplus) can be picked whether or not a
health app is connected, as a style or for any day group. With no health app,
Burned is resting energy (Mifflin–St Jeor, as before) plus the everyday
activity the person picks (`EverydayActivity`, Mostly sitting unless they say
otherwise), and logged exercise on top. When Burned can't be worked out at
all, the card asks for the missing details instead of saying "Until Burned is
known, your goal is your range."

The safety rules do not change. `GOAL_FLOOR_KCAL`, `MAX_DEFICIT_KCAL` and
`MAX_SURPLUS_KCAL` stay null until a dietitian sets them, and while they are
null no goal goes under where the person started and no deficit or surplus
grows past where it started. Choosing a deficit is not the same as growing
one: it starts at what the aim already implies against Burned, so on the day
it is picked the goal is the aim. The copy now says what a number is based on
("Based on about 2,210 burned today", "This is the deficit you started with")
rather than what a dietitian has not done yet.

This replaces the owner's earlier call (design sync 4 notes, Targets) that the
deficit stay locked until `MAX_DEFICIT_KCAL` is set. They tried the screens
and asked to be able to choose one; the lock on growing it is what keeps it
safe, and that stays.

The deficit and surplus are offered only with `release.goalsAndEnergy`, the
flag that has Today, Trends and the widget read Burned at all. Without it a
deficit could only ever be the range everywhere, which is the dead end this
removes. One saved on a phone with the flag on still shows.

**Per-day goals:** a day group can be any goal type (range, one number,
deficit, surplus). `GroupShape.Deficit` travels inside the top-level
`targetStyle` with the group's `minKcal` and `maxKcal` kept, so a build that
predates it reads that day as its own range. Everyday activity is the
top-level `everydayActivity` key, which the server's one-level merge keeps
through an older save.

**Alternatives:** an activity multiplier on all of Burned, health app or not
(rejected: the health app measures movement, so it would count it twice);
defaulting to Lightly active as some trackers do (rejected: a higher estimate
starts a bigger deficit, so the default is the lowest); balancing per-day
goals against a weekly total as Lose It and MacroFactor do (rejected: raising
one day would quietly lower another, which the floor rule exists to stop).
Research in `docs/research/goals-ux.md`.

**Reconcile:** built beside the exercise entry below, which had the same
idea; see the entry above for how the two became one.

## 2026-10-27: "Ate, no details" is no longer offered; old marks stay

**Decision:** the meal sheet and the add sheet stop offering "Ate, no details".
The stored mark (`ate_no_details` on the day) is untouched: meals marked before
still read as eaten on Today, in No numbers' timeline and in Trends, and the
record still syncs.

**Why:** the owner tried it on a phone and called it pointless next to the
Note, which already records that a meal happened and how it felt. Removing the
offer is a client change; removing the record would need a migration and would
erase what people already marked.

**Alternatives:** dropping the column and its sync. Rejected for now: 0.1
phones still write it, and old marks are the person's own log.

## 2026-10-27: Today's card opens Nutrients from a page, and Close the day is a header button

**Decision:** the summary card loses its "Nutrients ›" link and gains a last
page with sodium, potassium, calcium and iron and See all. Close the day's card
at the end of the list becomes a check-circle button in the header (empty until the day is closed, then filled); the ⋮ menu keeps
it. More › Today screen's two switches (`todayNutrientsLink`, `todayCloseDay`)
keep their wire names and now govern the page and the button.

**Why:** the owner asked for both after using a dev build. Fiber already sits
with the macros on page 1, and sugars and saturated fat are not tracked, so the
page is the minerals people watch most. Keeping the setting keys means nobody's
choice resets.

**Alternatives:** new setting keys named for what they do now. Rejected: a
rename is a migration for no change anyone would see.

## 2026-10-27: Exercise logged by hand, and one source of activity per day

**Decision:** behind `release.exercise` (which also needs `release.goalsAndEnergy`),
Burned is resting energy plus everyday activity plus logged exercise when no
health app reads activity, and resting energy plus the health app's movement
and workouts when one does. An entry marked "My watch didn't record this" adds
on top in the second case. Exercise energy is (MET − 1) × latest weigh-in ×
hours from the 2024 Compendium, or a typed number, stored with the entry when
it is saved. Everyday activity is a share of resting energy, Mostly sitting
(0.2) by default. No thermic effect of food. Goals keep reading
`BurnedRepository`, so a deficit or surplus works without a health app.

**Alternatives:** MyFitnessPal's running "calorie adjustment" between its
estimate and the tracker's, which can go negative and confuses people;
Cronometer's gradual replacement of the baseline as tracker activity arrives,
which would change how Burned behaves with a health app connected, and goals
already rely on that; MacroFactor's expenditure from intake and the weight
trend, a much larger feature that needs weeks of data. Adding everything
together double counts.

**Why:** without a baseline, a deficit aimed at resting energy alone sets a goal
several hundred calories too low. One source per day is easy to explain and
never counts the same walk twice. Research and sources:
`docs/research/energy-expenditure.md`. Server table `exercises` is V24; Room
schema 10.

---

## 2026-10-27: Accounts are required; a reinstall starts signed out

**Decision:** onboarding goes Welcome, sign in, questions, mode, Today. Sign-in
is Apple on iOS and Google through the browser, as in Cards; email stays behind
`SignInMethods.EMAIL`. There is no guest path: `GuestAccountCreator`,
`createGuestSession` and anonymous sign-in are gone, and the healer
(`SessionHealer`) sends a stranded device to sign in instead of minting.

A new account goes on to the questions. An account that already exists goes
straight to Today: its mode is on the server, and running setup again would
write over it, which for someone who hides numbers is the worse mistake. The
split reads the server's one-shot `isNewAccount` (`AuthOutcomeClassifier`).

**Guests from older builds** keep their log. An onboarded guest is sent to the
sign-in step (`AppViewModel.signInRequired`), which links Apple or Google onto
the anonymous user (Supabase identity linking, as the account step did). If
that Apple ID or Google account already has an Opson account, the step says
so and offers to sign in to it, warning that the guest's log stays behind.

**A new install starts signed out.** iOS keeps Keychain items when the app is
deleted, so a reinstall read the last install's session; `OnboardingViewModel`
then took a leftover guest for a returning one and went to Today. That is why
the owner's fresh TestFlight install skipped onboarding (`release.onboardingSetup`
only adds steps and was not the cause). Now the first session load of an
install that has not finished onboarding (`FreshInstall`, a marker in the app's
own files) drops a signed-in session it finds in the Keychain. Signing in again
brings the account back. An anonymous one is kept, because a guest has no
credential to come back with and the sign-in step links onto it.

**Alternatives:** keep the Keychain session on reinstall and skip sign-in.
Rejected: deleting an app reads as signing out, the person may not be the same
one, and it is the behaviour the owner reported as a bug. Run setup again for
returning accounts. Rejected for the reason above.

## 2026-10-27: The four modes are about how many numbers: all, macros with grams, macros only, none

**Decision:** the presets are All numbers (`full`), Macros with grams
(`macros_grams`: no calories, remaining or energy out anywhere, macros and
everything else shown), Macros only (`macros_only`: the four macros as words
and bars, no grams, no calories) and No numbers. Design sync 4's Nutrients
first and Later are retired (`RetiredPreset`). A stored `nutrients_first`
reads as Macros only with the person's own changes kept. A stored `later`
reads as All numbers with every number still on Later and its reveal time and
weekly choice kept, so a Later user sees what they saw. A timer that ends in
Nutrients first switches to Macros only; one that ends in Later is dropped.

**Why:** after TestFlight the owner wanted the mode list to answer one
question, how many numbers, and asked for exactly these four. Later is a
"when", not a "how many", and it survives as a level in Each number. Nutrients
first was already the mode without calories, so it maps to Macros only, which
also stops showing calories as a fill bar.

**Alternatives:** keeping Later as a fifth mode (the owner asked for four), and
migrating `nutrients_first` with every old level kept as an override (that
would leave everyone who picked it on "Macros only, 3 changes", which is not
the mode they chose). Showing words for carbs and fat to a former Nutrients
first user is more than they had; it is words and bars, never an amount, and
it is what the owner defined the mode to be.

## 2026-10-27: Grafana may see one person, through `support`, and never their log

**Decision:** a second read-only schema, `support` (V22), holds per-person
views for the owner's User detail and Users boards and the admin console's
user lookup: account id, email, display name, which builds each install ran,
counts per day, timestamps and Settings choices from an allowlist. `grafana_ro`
reads it as well as `analytics`. `analytics` keeps its rule: aggregates only,
no user id.

**Why:** the owner asked for Cards' user detail board as a CRM stand-in, and
support needs to answer "what is this person running and how do they use it".
The V9 promise that mattered was never "no user id" for its own sake; it was
that no panel can show one person's food log. That holds: no `support` column
carries a food name, amount, nutrient, weight, water, note, feeling, check-in
or onboarding answer, target or fast length, and `PostgresUserSupportTest`
fails if one appears. Emails reach the admin console and the User detail board,
which read the database; they never reach telemetry.

**Alternatives:** stamping `user_id` on client events in Loki. Rejected: it
only reaches builds from 0.2 on, and it would put the account id on every
event, where the install id is enough once `user_installs` maps one to the
other.

## 2026-10-27: One colour scheme, Slate; the palette setting is dropped

**Decision:** the four palettes (Pine, Espresso, Clay, Plum) are gone. Slate,
in light and dark, is the only scheme (design sync 4, `colors-slate.png`).
Appearance (System, Light, Dark) is the existing top-level `theme` key. The
`palette` key is no longer read or written: the field left `Settings`, and
decoding ignores it.

**Why:** the owner chose one scheme. Dropping the key, rather than keeping it
with one value, means no stored value can be wrong: a phone that had Espresso
reads Slate like everyone else. The server merges settings one level deep, so a
0.2 save leaves the key where it was, and a 0.1 phone on the same account keeps
the palette it picked (`AppearanceAcrossVersionsTest`).

**Alternatives:** map each palette to an appearance (Espresso to Dark, say).
Rejected: every palette had a light and a dark set, so a palette says nothing
about light or dark.

## 2026-10-27: Full numbers shows vitamins and minerals

**Decision:** Full numbers (and Sprint, which is "same as full, plus a banner")
shows micros. Every other preset keeps its level: Delayed, Blind, Nutrient,
Rhythm, Explorer, Mindful, Fuel and Coast all still hide them.

**Why:** the handoff ported the prototype's `FULL_VIS`, which made micros an
opt-in through Modes and numbers. Full's own line is "Every number visible, all
day", and the owner, on Full on a real phone, asked why no food had a
micronutrient breakdown when about 98% of catalog rows carry one. The opt-in
was a tidiness call with no safety reason behind it (nothing in the plans or
the `TODO(clinician)` notes mentions micros), and the modes that exist for
safety never showed micros in the first place.

**Alternatives:** keep the opt-in and make the Nutrients page's "turn them on"
card louder. Rejected: it still breaks Full's promise, and the amount sheet
has no such card, so a Full user there sees nothing at all.

**What changes for people:** a Full or Sprint user sees All nutrients on the
amount sheet and vitamins and minerals on the Nutrients page. Anyone who
turned micros on by hand keeps an override that now matches the preset; anyone
who turned them off keeps them off.

---

## 2026-09-21: One Grafana stack for every project, separated by service_name

**Decision:** every app generated from this template sends to the same Grafana
Cloud stack. They are told apart by the `service_name` label, which
`GrafanaLogTree.SERVICE_NAME` and the server's `OTEL_SERVICE_NAME` both derive
from the project name, so the rename pass makes it per-project for free.
`deployment_environment` splits dev from prod inside that. The three
`GRAFANA_*` credentials are therefore account-wide and live in the machine
store alongside the Sentry org token.

**Why:** the separation already exists in the labels, so a stack per project
buys nothing you do not already have and costs a second thing to administer.
One stack means cross-project queries, one alerting setup, and one set of
credentials that `setup_github_secrets.main.kts` pushes without anyone typing
them. It is the same shape as Sentry: one org, per-app separation inside it.

**What it costs, and this is the real argument for the other choice:** quota is
shared, so a chatty app can eat the log allowance and blind the others.
Retention is per-stack, so you cannot keep one app's data longer. And one
`glc_` token sits in every repo's CI, so a revocation takes out all of them at
once. Grafana auto-revokes tokens it finds in public repos, which makes that
less hypothetical than it sounds.

**Why the quota risk is survivable:** the levers are already per-app and
remote. `telemetry.appEventsSampleRate` is stable-hashed per session, so a
sample rate is all-or-nothing per session rather than a partial trace, and
`telemetry.appEventsEnabled` is an instant kill switch. Either can be turned
down for one noisy app without touching the others or shipping a build.

**When to split:** one app turns genuinely high-volume, or a project needs
different retention or access (a client's app, say). Moving is three secrets
and a rebuild rather than a migration, precisely because the separation lives
in the labels rather than in the stack.

## 2026-09-20: Provisioning is scripted per service, orchestrated by one entry point

**Decision:** each external service gets its own idempotent script
(`setup_supabase`, `setup_fly`, `setup_sentry`, `setup_github_secrets`,
`setup_credentials`), and `scripts/setup.main.kts` runs them in dependency
order, skipping what is done or not applicable. `init_project.main.kts` offers
to run the orchestrator at the end but never runs a service script directly.

**Why not one big script:** the steps have genuinely different prerequisites.
Supabase needs nothing but a token. Fly needs Supabase's output. Both
GitHub-facing steps need a repo that does not exist at the moment a project is
generated. A single script would have to encode all of that as internal
branching, and a failure halfway would leave no obvious re-entry point. Separate
scripts mean "re-run the one that failed" is always the answer.

**Why the orchestrator exists anyway:** the *order* is the part that is not
obvious, and getting it wrong does not fail loudly. It half-configures things
and leaves you to work out which half.

**Why init only offers it:** at the end of init there is no GitHub repo, so two
of the five steps cannot run. Running the chain inline would half-finish by
construction. The offer exists because the alternative, a wall of instructions
nobody reads, is how this template already shipped a blank Sentry DSN.

**What stays manual, permanently:** Play Console (app entry, service-account
invite, data safety, content rating) has no API for any of it. App Store Connect
listing copy and screenshots. The first release promotion in each store. These
are called out as their own group in init's closing output rather than mixed in
with the automatable steps, because a checklist that blurs "run this" with "go
fill in a web form" is one people stop reading.

## 2026-09-20: Apple sign-in is native-only, so there is no secret to rotate

**Decision:** Apple sign-in runs through the iOS system sheet
(`ASAuthorizationController` → identity token → Supabase
`signInWith(IDToken)`), and Supabase is configured with the bundle ID in
**Authorized Client IDs** and nothing else. The Services ID and client-secret
fields stay empty. `scripts/rotate_apple_sign_in_token.main.kts` is deleted,
along with the two store keys that fed it.

**Why:** the client secret belongs to Apple's browser OAuth flow, which nothing
here calls: the UI only ever passes `OAuthProvider.Google` to
`signInWithOAuth`, and `AndroidAppleSignInCoordinator` is a deliberate no-op.
Apple caps that secret at 180 days, so keeping the script meant every generated
app inherited a recurring six-month chore for a flow it does not use, plus a
SETUP checklist line telling people to do it. A maintenance obligation nobody
needs is worse than a missing feature: it gets done.

**What it costs:** offering Apple sign-in on Android later means the browser
flow, which means a Services ID, a client-secret JWT, and rotation before every
expiry. The script that minted that JWT is in this repo's history. It signed
an ES256 assertion from an Apple `.p8`, and the shape is the same one Supabase's
own docs describe. `toSupabaseProvider` carries a comment at the dead Apple
branch so the obligation is discovered at the code, not after shipping.

## 2026-09-20: Declining the backend at init is destructive, not staged

**Decision:** `init_project.main.kts` asks whether the project ships its own
backend. Answering no **deletes** `:apps:server`, `:apps:admin`,
`:apps:integration`, both Fly deploy workflows and the server/integration CI
jobs, and edits `settings.gradle.kts`, the root `build.gradle.kts` and the
three root docs. Nothing is staged for a later opt-in.

**Alternatives:** follow the CI precedent and stage the modules under
`template/` for a `scripts/enable_backend.sh`. **Why not:** the CI opt-out can
stage because installing CI afterwards is a pure file move: the staged files
have already been through the rename and substitution passes, so
`enable_ci.sh` just moves them. A backend is not a file move: it has to come
back into `settings.gradle.kts`, into `ci.yml` and into two deploy workflows.
A staged copy would be a folder of source with no switch, and one that quietly
rots because nothing builds it. Adding a backend later is a diff against the
template, which is where it stays maintained. The prompt says this out loud
rather than implying reversibility by analogy with the CI question.

**Also decided:** the prompt asks about *this repo shipping a server*, not
about network access. `:libraries:identity` (Supabase auth) and
`:libraries:networking` do not depend on `:apps:server`, so a client-only app
still signs in and calls third-party HTTP. "Does your app have a backend"
invites the wrong answer from anyone who reads it as "does your app use the
internet".

**Cost accepted:** every edit is anchored on exact text, so rewording the
affected passages breaks generation. It fails loudly rather than skipping the
edit, and `verify_template.sh` generates a client-only project on every run.

## 2026-09-20: One machine-local store for setup credentials

**Decision:** the setup scripts resolve credentials environment variable →
`~/.config/appsetup/credentials.properties` → prompt. The store holds only
values that are identical across every project you generate (Sentry org and
tokens, Fly deploy tokens, Apple team and key IDs, Supabase access token).
`scripts/setup_credentials.main.kts` populates it; `scripts/lib/setup_store.main.kts`
is the shared library.

**Why env first:** CI already exports these, so env-first means CI keeps
working untouched and a one-off override stays possible.

**Why the path says nothing about this template:** `init_project.main.kts`
rewrites the template's name in every text file it copies, so a path
containing it would be rewritten per project, and the entire point is that
the app you generate next month reads what you typed today. Same reasoning as
the `serverOnly` system property being project-agnostic.

**Why every script still prints two summaries:** the store will often be
absent (another laptop, a CI runner, a contributor who is not you), so it is
an accelerator and never a requirement. But a store that quietly fills some
values and leaves others blank reproduces the blank-Sentry-DSN failure across
every credential it touches. Blank is a supported value meaning "off", and off
looks exactly like working. So every run says where each value came from
before it starts, and what is still unset and what that costs when it
finishes. A non-interactive run with a missing required value dies naming the
value and how to supply it rather than proceeding with a blank.

**Not stored:** whether `gh` is authenticated. That is probed live. A cached
"yes" goes stale on token expiry, and a script that skips asking because of a
stale flag then fails somewhere less obvious, the exact silent
misconfiguration this design exists to prevent.

## 2026-06-21: Server mirrors client conventions

**Decision:** `:apps:server` reuses the client's stack, kotlin-inject + anvil DI
(`ServerScope`/`ServerComponent`), the `domain/` interface + `data/` impl split,
conventional commits, the version catalog. It's a plain JVM `application` module
(no convention plugin; those are KMP-only).

**Why:** one mental model across client and server. An agent (or human) moving
between them doesn't re-learn DI, error handling, or module layout. The cost was
accepted: the server can't use the KMP `:libraries:core` (`Catching`, logging)
because that module has no JVM target, so it keeps a couple of small local
equivalents rather than forcing a `jvm()` target onto every client library.

## 2026-06-21: Graceful degradation over required config

**Decision:** `DATABASE_URL`, `SUPABASE_URL`, `SENTRY_DSN`, and the OTLP endpoint
are all optional. With none set, the server boots and serves `/_health` +
`/v1/example`; DB-backed and authenticated routes simply aren't mounted, Sentry
no-ops, and OpenTelemetry exports to stdout.

**Alternatives:** require `DATABASE_URL` + `SUPABASE_URL` like the Cards origin
(fail-fast). **Why optional:** this is a template, "clone and run, see it boot"
beats a fail-fast error on first run. The fail-fast discipline still applies per
field via `Env.require` when a future field genuinely can't be defaulted.

## 2026-06-21: Auth is JWKS verification, never a shared secret

**Decision:** the server verifies Supabase JWTs against the project's public keys
(JWKS / ES256). The `JwtVerification` sealed seam has `Jwks` (prod) and `Static`
(tests mint HS256 tokens against a known verifier).

**Why:** no Supabase secret ever lives on the server, and auth. The highest-risk
surface, is fully testable offline (route tests + `FullStackMeTest` run the real
validate/challenge path with no network).

## 2026-06-21: `NoOpAuthTokenProvider` lives in the `:networking` api module

**Decision:** the default no-op `AuthTokenProvider` binding sits in
`:libraries:networking` (api), not `:impl`.

**Why:** the module-boundary rule forbids one `:impl` depending on another, but
`:libraries:identity:impl` must reference `NoOpAuthTokenProvider` to override it
with `@ContributesBinding(replaces = [NoOpAuthTokenProvider::class])`. Putting the
default binding next to the interface it defaults keeps the replacement
boundary-clean. (See also the `enforceModuleBoundaries` self-edge fix in
`build-logic`.)

## 2026-06-21: `serverOnly` build slimming

**Decision:** `-DserverOnly=true` makes `settings.gradle.kts` include only
`:apps:server`, so a Docker image build needs no Android/iOS toolchain. The
property name carries no project prefix on purpose, so the rename pass cannot
break the Dockerfile↔settings contract.

**Why:** this is a KMP monorepo; without slimming, a server image build would
configure every client module and need the Android SDK + Kotlin/Native. The
server has no client-library deps today, so the gate is a pure settings change;
if it gains one, add an always-included `include(...)` + a Dockerfile `COPY`.

## 2026-06-21: Flyway SQL is the schema source of truth

**Decision:** migrations under `resources/db/migration` define the schema; the
Exposed `Tables.kt` objects are read-side projections kept honest by
`DatabaseSchemaTest`. Repositories treat a unique-violation (SQLSTATE `23505`) as
the arbiter rather than pre-checking for races.

**Why:** one procedure for schema change (add the next `V##__name.sql`, never edit
an applied one), and idempotency that's correct under concurrency.

## 2026-09-09: Ports considered and rejected

**Decision:** three things a downstream app offered back are deliberately not in
this template. Recorded so they don't get re-proposed each time someone reads
that app's setup and notices the gap.

- **Macrobenchmark `FrameTimingMetric` in CI.** The right tool for catching jank
  regressions, and it needs a real device. Emulator frame timing on a shared CI
  runner varies more run-to-run than the regressions worth catching, so any
  threshold produces flaky red and gets disabled within a month. Only worth it
  for a project with a device farm. Real-user frame timing (`app.jank`) covers
  the same question continuously and is in the template instead.
- **The Grafana dashboards themselves.** The queries encode one app's event
  names. The *conventions* are portable and already carried; the boards are not.
- **The observability routine and its skills.** Genuinely useful, and shaped
  entirely around one project's dashboards, alert ids and inbox. Revisit only if
  a second app wants the same thing. That is the point at which the generic
  shape becomes visible.

**Why here rather than the port queue:** the queue is work waiting to happen, and
these are closed questions. Keeping them there made an empty queue impossible.

## 2026-09-14: Grafana dashboards stay out; the events registry comes in

**Decision:** this template ships no dashboard JSON and no dashboard contract
test. It does ship [telemetry-events.md](telemetry-events.md), a description of
what a generated project emits before anyone adds a feature.

**Why:** a downstream app re-proposed the dashboards with a sharper argument:
that more of its event surface is app-shaped than game-shaped, and that a
purchase-funnel board in particular is the one most likely to be wrong in a way
nobody notices. Both true. But its own recommended order starts with "decide
which events the template *guarantees* every generated app emits", and that is a
design decision nobody has made. A board querying an event a generated app might
not send reads zero forever and is indistinguishable from a real zero, which is
strictly worse than no board.

So the prerequisite landed and the boards did not. The registry is deliberately
worded as a description rather than a contract, because promoting it to a
guarantee is the decision still outstanding. Writing it surfaced one real bug
immediately: `conn.reconnecting` exists only in a test fixture, so any panel
built on it would have read zero forever.

The contract test that holds queries to emit sites is the genuinely portable
idea in that proposal, and it is worth writing the moment a generated project
has boards worth holding. Its traps are recorded at the bottom of the registry
so nobody re-derives them, in particular that a Gradle test task which does not
declare `inputs.files(...)` stays UP-TO-DATE and passes while checking nothing.
