# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-10-30: Opson is for 16 and over, and a hard stretch gets a kind note worked out on the phone

**Decision:** onboarding asks for a birthday after Welcome and before sign-in
(`AgeGate`, `OnboardingStep.Age`). Under 16 sees a kind screen with Find
support, makes no account, and the phone keeps the gate closed until the
sixteenth birthday (`AppData.ageGateOpensOn`, the date only). 16 and 17 get the
goal flow's gentle path (no weights, weight goals or deficits, as after
"numbers have been hard") and celebrations without counts; `weightPlansOff`
keeps the goal flow and its Today card off for anyone under 18. Terms and
privacy say 16. Separately, `HardStretch` reads the last four weeks of the log
on the phone (very low complete days, restrict and big-day swings, many
weigh-ins a day, fast loss, plus weaker signals that need a second) and the
`kind_note` prompt offers Find support, fewer numbers or Not now, at most
every 21 days, behind `release.findSupport` and the person's own
`hardStretchNotes` switch. A prompt can be `sensitive`: no app event, no log
line. Thresholds are TODO(clinician).

**Alternatives:** 13 (the old terms; COPPA's line, but calorie tracking is
linked with disordered eating in teens and Opson has no supervision); 18
(MyFitnessPal and Noom's line, but it shuts out older teens who would find a
tracker elsewhere with fewer protections). Asking age after sign-in (an
account would already exist for a child). Reading the store's age signal
(Declared Age Range, Play Age Signals) instead of asking: a follow-up, since
it covers only some regions so far. Detecting patterns on the server: no
health inference should leave the phone. Full reasoning in
[ed-safety-review.md](research/ed-safety-review.md).

## 2026-10-30: No server AI in v1; labels are read on the phone and a person reviews weekly

**Decision:** the server calls no model unless `AI_CALLS_ENABLED=true` is set
beside `ANTHROPIC_API_KEY`; a key alone does nothing, and v1 sets neither.
Photo estimates stay off (`release.photo`). Add a packaged food reads the
label on the phone for everyone (Vision or ML Kit, then `LabelTextParser`),
fills the form with per-field confidence and marks the unsure fields, guesses
the name from the front, and never asks the server to read; Report a problem
fills its numbers from a label photo the same way. Every submission carries
the OCR text and the phone's draft, the Builder second read is skipped, and
everything waits for a person. The owner reviews weekly in the admin console,
can export the queue as a file for an AI tool on their own subscription to
pre-check, and imports the decisions back after confirming them
([food-review-runbook.md](practices/food-review-runbook.md)). An on-device
model (Apple Foundation Models on iOS 26) may give the parser a second opinion
behind `labels.onDeviceModel`, off by default
([on-device-label-model.md](research/on-device-label-model.md)).

**Alternatives:** keep the cloud read for everyone with a spend cap (the
owner can't fund it for v1, and a cap that trips turns into an outage);
train a custom vision model now (no labelled data yet; see the research doc).

**Why:** the owner's call: no AI bill that grows with users. The phone's read
is free and offline, and human review was already the fallback.

## 2026-10-29: A weight goal sets Targets as a fixed range, and says when it no longer does

**Decision:** onboarding's setup (behind `release.onboardingSetup`) and a new
goal flow from Today ask for a profile, a usual day's activity, a weight goal
and pace, show the plan, and end on "I'm committed". The goal is stored as one
top-level `weightGoal` object; it writes Targets as a range of the same width
centred on the aim (a typical day's burn, plus pregnancy or breastfeeding's
extra, plus the pace's energy by 7,700 kcal a kilogram) and stamps that aim.
While Targets' aim matches the stamp, Targets says "Set from your weight
goal"; a hand edit makes it the person's, and Targets offers Use my goal.
Paces stop at GoalSafety's gentle lines and never aim under the low-goal line;
a goal weight under a healthy BMI is a soft note with Find support, not a block.
Nothing about a goal is offered after numbers have been hard, where weight is
hidden, or while pregnant or breastfeeding. The full birth date is a new key,
`birthDate`, with `birthYear` still written for older builds and every
calculation.

**Alternatives:** a set deficit style aimed at Burned, which moves through the
day as activity syncs and only works in All numbers; rejected for a first
goal because a fixed number is easier to plan meals around and works the same
in every mode (the owner's open question in the energy and goals brief, still
open for the Plan hub). Blocking a goal weight under BMI 18.5, as the energy
and goals brief proposed; rejected because the owner asked for a soft note
that never blocks. Keeping the goal in sync automatically after a hand edit;
rejected as a silent conflict.

**Why:** people asked for help setting a goal on day one, and a goal that
quietly fought a target someone set by hand would be worse than none.

## 2026-10-29: A meal's + opens the add sheet on that meal

**Decision:** the meal sheet (Add food, Note, Copy yesterday's, Save as a meal)
is gone. A meal's +, the header of a meal with food and anywhere on an empty
meal's card open the add sheet with that meal chosen. Save as a meal moved
into the add sheet's grid, shown when the meal on the sheet has food.

**Alternatives:** keep the meal sheet and only skip it for Add food, or put
Save as a meal in a ⋮ on the meal header. The first keeps the extra tap the
owner asked to lose for most people. The second adds a control to every meal
card for a rare action, when the add sheet is already on that meal.

**Why:** owner feedback, "fewer clicks is better". The add sheet already had
Note and Copy a meal, so the meal sheet was one tap in front of the same
things. Save as a meal shows for any meal with food, including one the
nav + picked, so the grid reads the same however it was opened.

---

---

## 2026-10-29: Celebrations count showing up, forgive a quiet day, and never count food

**Decision:** behind `release.celebrations`, Opson celebrates showing up
(owner, 2026-10-29: "create an internal dialog of 'I can do this'"). A day
counts when the person logged anything by hand (a food, "ate, no details", a
glass of water), wrapped the day up, or checked in; any one is enough
(`DayMark.showedUp`). A repeat's foods do not count, since food that logged
itself is not someone coming back. Untracked days are rests: they neither add
nor break, however many. One quiet day between counted days is forgiven; two in
a row end the run. Today never breaks a run until midnight. Milestones: the
first wrap-up; 3, 7, 14, 30, 100, 182 and 365 days in a row; 1, 7 and 30
check-ins. A milestone, once reached, stays reached, and celebrates once, only
in the week after it was reached (`ShowingUp.FRESH_DAYS`), so a new phone or a
sync catching up shows at most the highest fresh one. The celebration is a
prompt (`celebration`, a new `PromptPresentation.Screen`) so it obeys the
one-a-session and no-stacking rules: after Wrap up the day it plays as Today
settles; after logging, the next time Today settles past the quiet start. Its
button is "I can do this". The first one also offers "I'd rather not see
these", which turns `celebrationsEnabled` off; More › Reminders and
celebrations turns it back on. After numbers have been hard, celebrations are
gentle by default (`celebrationCounts` null follows `onboarding.hard`): a kind
title and line, no count anywhere, and no badges. The streak sits small in
Today's header and opens Your progress. Close the day's button is now "Wrap up
the day" everywhere; the code keeps its day close names.

**Why:** "no streaks" was a rule (`docs/review/product-ideas-2026-10-27.md`)
because people describe real distress when one breaks. So this one is built
not to break easily, and never to reward eating less: nothing here reads an
amount, a goal or a deficit. Asking for "enough" logged was rejected because
it would ask someone to eat a meal to keep a run. "I can do this" beat "Keep
going" (an instruction from the app, not something you say to yourself) and
"Nice" (says nothing). "Wrap up the day" beat "Done for today" (wrong for a
past day, and reads as giving up) and "Finish the day" (sounds like a chore).
Celebrated milestones live on the phone (`CelebrationStore`), not in settings:
a second phone may celebrate a fresh milestone once more, which costs nothing,
where a synced list would race between phones and add an outbox write per
celebration. Quiet mode kept its job (notification sound) and lost "no
confetti" from its line: it defaults on for everyone from 0.1's synced
prototype defaults, so following it would have made almost every celebration
silent; celebrations have their own switch and follow the system's reduce
motion instead.

**Alternatives:** total days logged instead of a run (never lost, but the
owner asked for streaks and "akin to closing the day"); streak freezes to earn
or spend (more to explain, and a currency for showing up); celebrating from
Day close directly (would stack on another prompt and skip the session rule);
a synced `celebratedMilestones` key. **Before release:** the App Store listing
says there are no streaks (`docs/app-store/listing.md`); it has to change when
`release.celebrations` turns on in prod.

---

## 2026-10-29: Builders apply, and only the owner's Accept makes a Builder

**Decision:** Joining Builders is an application now (V32 `builder_applications`),
reversing the self-serve join below at the owner's request. The app asks why
they want to help (optional), how often they buy packaged food, their label
region, and for the rules; the gates are the same ones joining had, plus
`ContributionGate.mayReceiveFreePro`. One application is pending at a time, it
can be withdrawn, and a decline holds the next one off for 30 days. The owner
accepts or declines in the console's Builders tab, beside a summary of the
account (age, logging, contributions and how they went, moderation, earlier
applications). Accepting writes the membership and the `builder` role, so Pro
still comes through entitlements. Builders who joined before keep their place.
An older app's `POST /v1/builders/join` applies, and answers in the four codes
that app knows: a pending or declined application reads as `full` ("Builders is
full right now"), the nearest true thing it can say. The decision reaches the
person on the Builder page and once as a prompt on Today; no push.

**Alternatives:** a per-person feature flag or a hand-set entitlement for each
accepted Builder (the `builder` role already brings Pro through the bundles and
carries the while-active window; a second switch would drift from it); one row
per account overwritten on reapply (loses the record of an earlier decline,
which is what the owner wants to see on the second application); a new
`joinBlock` code for pending (an older app treats an unknown code as no block and
offers Join again forever); letting someone who left rejoin without applying
(under V31 nobody looked at them, so leaving and coming back is the moment to).

## 2026-10-29: Builders join themselves, Pro holds while active, and auto-approval starts in shadow

**Decision:** Opson Builders (`release.builders`, V31) is self-serve. A claimed
account with an approved food or a week of age, not restricted, accepts the
rules and gets the `builder` role, whose Pro bundle (`entitlements.roles`) now
holds only while the person is active: a first month, then 30 days after the
last time ten accepted contributions fell inside 30 days, worked out in closed
form on every read (`BuilderRules.proUntil`). The app reads the label on the
phone (Vision on iOS through a Swift bridge, ML Kit text recognition on
Android) with a deterministic parser in `:libraries:foods`, and sends the OCR
text, its draft and measured signals with the submission. The server grounds
every headline number on its own row of that text, reads the label photo once
more with Haiku blind to the OCR, and routes: owners straight in, a Trusted
Builder's clean camera scan that both reads agree on to the auto lane, the rest
to review with reasons. The auto lane ships in `shadow` (`builders.autoApprove`)
and audits the first 20 after a promotion, then 10%. Builders check existing
foods by sending the label's text only; a mismatch becomes a report with the
photo. Passive label reading in the everyday scanner is not built: a Builder
turns on Also read the label, and takes the photo themselves.

**Alternatives:** a waitlist with owner approval (more owner work for no extra
protection: the same gates keep guests and new accounts out, and
`builders.capacity` gives a closed alpha); a nightly job extending an
entitlement grant (needs a scheduler the server doesn't have, and lags a
reviewer's decision by a day); a downloadable on-device vision model (1.8 GB,
two runtimes, no measured accuracy on labels, ai-options.md); trusting the
backend read without review (models misread digits and read the wrong column;
only the narrow lane is safe, once measured); auto-approving straight away
(the doc asks for 99% agreement with reviewers on 300 or more first); reading
camera frames while scanning (no visible consent, and the panel is rarely in
frame, passive-label-capture.md).

## 2026-10-29: Bans are an app table, device marks follow contributions only, and provenance outlives the account

**Decision:** Bans, suspensions and contribution restrictions live in
`moderation_actions` (V30) with a reason, a note, an end, the actor and an
appeal state, and the ban gate reads them beside Supabase's `banned_until`.
The console never sets `banned_until`. A restriction keeps the app working
and refuses only food submissions and reports. Device bans mark an install key
(a Keychain item on iOS, the install id on Android) and, once configured, a
DeviceCheck bit or a Play Integrity device recall bit; they block contributing
and free Pro, never logging. The app sends those signals only with
contribution requests. `food_provenance` records each accepted submission and
report, and every moderation table keeps a keyed hash of the account id after
the account is deleted.

**Alternatives:** setting Supabase's `ban_duration` too (it also refuses the
token refresh, so within the hour a banned app falls to "session expired" and
sign-in errors instead of the blocked screen and its appeal link); blocking a
device-marked phone from the whole app (families share phones and phones are
sold, and a DeviceCheck bit outlives an erase); sending the device signals on
every request (more data than the purpose needs); keeping provenance on
`food_submissions` (it cascades away with the account, so a bad actor could
delete the account and leave their foods untraceable).

**Why:** the owner asked for an easy way to remove bad actors that holds after
a reinstall, and a review board to see who is entering fake data. Apple's
licence (3.3.7(F)) wants a reset path for DeviceCheck data and forbids relying
on it alone, so a mark comes from a person's decision on an account, shows in
the console, and can be cleared there. The keyed hash after deletion rests on
fraud prevention as a legitimate interest; a lawyer should confirm it, as
`builder-program.md` says.

## 2026-10-29: Food data is guarded on the way out, and corrections are an overlay

**Decision:** Every food the catalog serves goes through `ServedFood`: a
reviewer's correction from `food_corrections` (V29) laid over the stored row,
then an energy guard that serves a 0 kcal the macros contradict as an estimate
held under 5 kcal a serving. Anyone signed in can report a catalog food from
its detail (`release.foodReports`); reports go to the admin console's Review
area and never change a food until a reviewer accepts one. Owners and admins
(`food.edit.direct`) have their suggested values applied at once, recorded
and reversible like any other correction.

**Alternatives:** fixing the zero in the importers (a monthly reload away
from reaching prod, and the raw row would no longer say what USDA said);
writing corrections into `foods` with a `corrected_at` the importers respect
(Open Food Facts' reload swaps the whole `off_foods` table, so it cannot keep
a column, and every importer would have to know about review); an outbox for
reports (they can carry a photo, and adding a pack is online only too, so the
sheet keeps everything and says so when offline).

**Why:** the owner scanned Monster Energy Zero Ultra and got 0 kcal where the
can says 10. USDA Branded, which a scan prefers, stores the label's rounded
"Calories 0" for 8 fl oz, and its ml serving had no grams, so the app could
only offer 100 g. Doing it on the way out fixed every row already loaded, and
it found the same zero on every Monster Ultra flavour and on erythritol sodas,
which is why the estimate is capped at what the label allows rather than
taken from the macros alone (`docs/food-data-sources.md`, "Zero-calorie
labels").

## 2026-10-29: Roles and entitlements, with Pro as a bundle in config

**Decision:** Paid features are entitlements, named capabilities
(`photo_estimate`, `share_professional`, `share_friends`, `adaptive_burn`,
`long_range_reports`, `recipe_import`), resolved on the server per account as
the union of `entitlements.everyone`, the bundles the account's role brings
(`entitlements.roles`), and its own grants of an entitlement or a bundle
(`entitlement_grants`, V28, with expiry, source and reason), minus its
revocations. Pro is the bundle `pro` in `entitlements.bundles`. The three
policy values are ordinary remote-config flags, so moving a feature between
Free and Pro, giving it to everyone, or rolling it out to a percentage needs
no release. Free features (barcode, package scanning, fasting, GLP-1, export)
have no entitlement at all.

Roles are `owner`, `admin`, `moderator`, `builder`, and member by default,
one per account in `user_roles`. What a role may do is code, not config:
`food.contribute`, `food.review`, `food.edit.direct` (owner and admin, so the
owner's food edits are direct fixes and count as verification). Which bundles a
role brings is config, seeded so owner, admin and builder get Pro, which is
what the Builder program needs. Server code asks `EntitlementService.has` or
`Permissions.can`, never a role name. The admin console sets roles and grants;
every change is audited in `access_audit`, policy edits in `app_config_audit`.

Photo estimate is the first gate: the route answers 403 `entitlement_required`
without `photo_estimate`, and the app hides Photo without it. `everyone` is
seeded with `photo_estimate`, so nothing changes on deploy. The app caches
`GET /v1/me/entitlements` per account (synced on sign-in, foreground and
reconnect) and, until it has an answer, assumes the shipped default rather
than nothing, so a fresh install or an old server never hides a free feature.
The owner's role is set after deploy through the admin API, not a migration,
so no account id or email is committed.

**App Store 3.1.1.** Apple wants paid features unlocked through in-app
purchase and forbids "own mechanisms" such as licence keys. A grant made by
Opson (founding member, builder, comp) is not something a person buys or types
in, which is closer to what the guideline allows for grandfathered or earned
benefits, but it is a reviewer's reading. So, as the pricing plan says: every
Pro feature must also be purchasable in-app on iOS once Pro exists, no code
field ever appears outside Apple's own offer-code redemption, the founding and
builder programs are explained in the review notes of the version that
launches Pro, and if a reviewer objects the fallback is a $0 offer code for
the same product. Store purchases will write `pro` bundle grants with source
`app_store` / `play`, so there is still only one question everywhere.

**Alternatives:** an `is_pro` boolean or a `plan` column (can't piecemeal a
feature or open one to everyone without a release); a separate policy table
(remote config already has editing, targeting, audit and a console); roles as
config (permissions are product rules that ship with the routes that check
them); RevenueCat entitlements as the source of truth (grants that are not
purchases would then live with a vendor). Old builds that meet the 403 show
their generic "couldn't read that photo"; only builds with this change show
the calm unavailable state. Acceptable, since nothing is gated yet.

---

## 2026-10-29: The day's note is one note, All notes lists it, and More has a Journal card

**Decision:** All notes lists each day's own note, labelled "Day note", first in
its day, above that day's meal Notes; a tap opens the Note sheet. Close the day
and the Note sheet already wrote the same field (`day_records.note`), so there
was nothing to unify or migrate: the list simply never read it. That is why a
note written while closing the day never showed. Items and the check-in carry
no note of their own.

The day's note is no longer refused on a closed day. `setDayNote` writes it
closed or not, Today's ⋮ menu keeps Add a note on a closed day, and the Note
sheet no longer gives way to the closed sheet. Close the day's Save already
edited the note on a closed day, so the old rule blocked the same note in one
place and allowed it in another, and opening a closed day's note from All notes
would have asked to reopen the day. Like the check-in, what someone writes about
the day is not its log. Meal Notes stay with their meal and are still refused.

Close the day keeps the check-in as a row that opens the check-in sheet, now
under its own "How are you feeling? · optional" label beside the note's. One
sheet is the one editor: an inline copy would be a second set of controls and a
second save rule to keep in step, the drift the Edit Today merge above removed.

More gets a Journal card under the extras, from Today through
`MoreRowContribution`: Check in (with `release.checkIn`) and Notes. The
contribution gained `placement` and `order` so a feature's card can sit higher
than the end of the list. `NotesRoute` now takes a `NotesOrigin` (Today, Trends,
More) for its back label. Close the day was already reachable from Today's
header check as well as the menu, so it needs no new button.

**Alternatives:** a separate close note (two notes for one day); the check-in
inline on Close the day (a second editor); the Journal card at the end of More
with Medication (too far down for something used daily). Patterns' "How you've
felt" was left alone: its chart label floats over the card's header and is too
small for a whole check-in and a note.

## 2026-10-29: Today is arranged in one place, and an overridden choice says so

**Decision:** Edit Today's cards (a sheet in `:features:today:impl`) folded into
More › Today screen (`:features:settings:impl`). The ⋮ item, now "Edit Today
screen", and a long press on the summary card open that page; More's row is
there whatever the release flags say. The page carries the sheet's Meal groups,
Fasting card and summary stats. Where another setting overrides a choice on the
page (the levels hide water, Equation has no Burned to subtract, the mode sets
the card), the row says so and links to that setting. Today and the page read
the drawn preset from one rule, `TodayCardRules`.

**Why:** the owner opened the ⋮ item expecting More › Today and got a different,
smaller sheet; and picked Equation on More › Today and saw nothing change,
because Today fell back to In and out without Burned and nothing said so. Two
places that write overlapping settings with different meanings (the sheet's
water switch hid the water level, the page's hid the card) cannot both be
right.

**Alternatives:** keeping the sheet as a quick editor of the same state (two
components to keep in step, which is how they drifted); disabling Equation
without Burned (the pick would be lost, and it starts working once resting
energy is set up, so it stays picked with a note instead).

## 2026-10-29: The check-in is its own sheet, hunger is a 1 to 10 scale, and Close the day is a recap

**Decision:** the check-in leaves Close the day. It is a sheet of its own
(`CheckInRoute`, `release.checkIn`), opened from a Check in tile on the + sheet,
a Check in row on Close the day and the QA menu, and written on its own
(`DayCheckIns.setCheckIn`), never with the close. A closed day still takes one:
how someone feels is not part of the day's log, so the 2026-10-27 rule that a
closed day takes nothing does not reach it. `DayReflection` lost its
`checkIn`. The Reminders switch that hid the old card is no longer read; the
flag alone decides. A check-in reminder is not built yet: it needs a new
reminder kind through the planner, the platform wire, both platforms' tap
routing and a Reminders row, and delivery is still untested on real devices.

Sleep moved because people close the day at night, when "how did you sleep
last night?" is the wrong question. The sheet asks it first, as last night,
before noon; after noon it waits behind "Add last night's sleep" unless it was
answered. Still one check-in per day: the sheet opens on what the day holds and
saves the whole, so the morning's sleep is there when the evening moves hunger.
Several timestamped check-ins a day would need a table, an outbox entity and a
server migration, and the build going out now talks to a prod server that will
not be redeployed, so the check-in stays on the day record's `check_in` JSON,
which prod already stores and round-trips (length checked only).

**Hunger is a slider on the 1 to 10 hunger and fullness scale** used in
intuitive-eating work (1 ravenous, 5 neutral, 10 stuffed), with each step named,
the ends and middle labelled under the track, and a tick on each step. It asks
"How hungry are you right now?", the question that scale is made for; the old
row asked how often hunger came up through the day, which no slider answers
well. Energy, mood and sleep are five labelled points in a segmented row rather
than sliders: five words read faster than a track, the scale has no numbers to
mean, and a tap is quicker for a lightweight check-in. Faces were left out to
keep the app's plain-word voice. Mood is a new 5-point level; the old mood tags
stay as an optional row.

**Migration, read-time only:** `CheckInWire` gained `hungerLevel` and `mood`.
An old row's hunger word reads as a step (rarely 6, some 5, steady 4, often 3,
very 2): approximate, since the questions differ, but it keeps the order on the
charts. Energy's and sleep's three old words are the middle three of the five,
same wire values. Writes keep the nearest old word in `hunger` so a 0.2 phone
and the server's export keep showing a hunger; a 0.2 phone reads the new top
and bottom words as unanswered. Patterns reads the scale (taller is hungrier)
and adds **How you've felt**, a bar a day over the four weeks for each aspect.

**Close the day is a recap.** A line on how much was logged, in words, in every
mode. Eaten and burned: Burned with `release.goalsAndEnergy`, else the watch's
total today, the difference, and how it sits with the deficit or surplus the
day's goal works out to (a set one, or the aim against what was burned when that
is 100 kcal or more): on plan, more, less, or the other way, said without praise
for eating less. The card exists only when calories and energy out are both
shown in full, since the difference between two hidden numbers is a number.
Then the macros against their targets in any mode that shows macros, and
Today's four vitamins and minerals as percents of their references. Macros and
micros step aside on a big day, as the macros did. No nutrient score: the math
is not validated, so nothing adds the parts up.

Alternatives: a separate check-ins table with times (deferred, above); hunger
on five frequency words as before (the owner asked for a scale, and "right now"
is a better question at any hour); a Reminders switch kept for the check-in
(removed with the Reminders reorganization).

## 2026-10-29: Digesting food counts in Burned, if the person asks

**Decision:** "Count digesting food" on Resting energy, off by default, adds 10%
of the day's eaten (`THERMIC_EFFECT_OF_FOOD_SHARE`) to Burned as its own part.
It is not activity: it adds whichever source the day's activity comes from, and
"Add activity to Burned" off keeps it. Today's Activity card shows it as its own
line; Energy today has a row for it. Only where calories show in full: a tenth of
eaten is eaten, so with calories short of Show it leaves every Burned amount on
that screen, line and total (`Burned.shownOn`), even with energy out turned up to
Show. A calorie target aimed at Burned still counts it, since that target only
shows where calories do.

**Why:** the owner asked for it. The objection in the 2026-10-27 entry (a deficit
aimed at Burned moves as food is logged) still holds, so it stays off until
someone turns it on. Neither health store includes it, so it never double counts.

**Alternatives:** a percentage the person sets (more to explain for a number
whose error bars are wider than the choice); weighting by macro (protein costs
more to digest), which is closer to the truth but makes the line jump as foods
are edited.

---

## 2026-10-29: A low goal gets a note, not a lock; day groups are always open

**Decision:** on Targets a calorie goal can be set anywhere from 800 to 6,000
kcal (`MIN_GOAL_KCAL`, `MAX_GOAL_KCAL`, `TODO(clinician)`). Where the person
started is a record, no longer a floor. Under 1,200 for a woman, 1,500 for a
man and 1,350 when sex isn't said, a note says the goal "is lower than most
people should go without a doctor's guidance" and links to Find support; a set
deficit over 1,000 a day gets a gentler-pace note the same way. Nothing is
refused. `GOAL_FLOOR_KCAL` stays null and, once a dietitian sets it, is a hard
floor again. A goal Opson works out from the burn (a deficit or surplus) never
comes to less than 1,200, whoever the person is. Onboarding keeps its own floor
at the app's starting range, because setup has no note.

Different by day starts with two groups, every day in the first and none in
the second, and every group's controls are always open. A tap on a day moves it
into that group or, for a day already there, back to the first; the first
group's own days are not buttons. Every group but the first can be removed,
its days going back to the first. An empty group is allowed and says how to
fill it.

**Why:** the owner found the range locked at 1,900 to 2,300 with no way down,
and the by-day editor hard to use: an Edit tap per group, no way to delete a
group, and on the first group no day that did anything. The old floor was the
right instinct with the wrong mechanism: it treated whatever number someone
started on as the safe one, which it never was, and it blocked people with a
real reason to go lower. The trackers people know warn instead
(`docs/research/goals-ux.md`), and a soft note keeps the person in charge while
still saying the thing that matters. For day groups, making one group the rest
of the week is what keeps every state valid: whatever is tapped or removed, each
day is in exactly one group, so there is no invalid state to validate and no
Save button to add. Starting with two groups means the first thing anyone does
after picking the style is pick days.

**Rejected:** a Save button that validates the week (more taps, and a state the
page can be left in); pruning empty groups automatically (a group would vanish
under the person mid-edit); an absolute floor of 1,200 (blocks the people the
note is for from tracking what they are told to eat). The deficit and surplus
caps are unchanged and still wait on a dietitian.

---

## 2026-10-29: Repeating foods log themselves through the diary, from rules kept on the phone

**Decision:** a repeat (Foods tab, "Repeating") is a rule: a food, several foods
or a saved meal, a meal, the days of the week, a start and an optional end, and
paused or not. On the day it falls on, its foods are written as ordinary log
entries, through `DiaryWriter` and the outbox, so they sync, edit and delete
like anything logged by hand. Behind `release.repeats`, which is also the kill
switch, since this writes to the person's log unasked.

- **When.** `RepeatScheduler` runs `RepeatMaterializer.logDue()` once the person
  is signed in and their settings are known, on every foreground, at each local
  midnight while the app is open, and after every save. Only today is logged. A
  day the app was not opened stays empty: filling it later would put food in
  the log that may not have been eaten, and an empty day already reads as a gap
  in Trends.
- **Once per day.** `repeat_logs` records each entry a rule wrote, keyed by
  entry id, and a rule with any record for a day is done for that day. The
  record stays when the entry is removed, and the entry's tombstone keeps its id,
  so a removed repeat stays removed that day. "Repeat this" on Today records the
  entry it came from as the rule's today, so that food is not logged twice.
- **Ids.** `repeatEntryId(rule, day, index)` is a deterministic version 8 UUID.
  Two phones holding one rule write the same ids and the same rows: the server
  keeps one row per id and a second identical write changes nothing. Each row's
  `clientUpdatedAt` is the start of its day, deterministic too and older than
  anything the person can do to the entry, so their edit or removal on one phone
  always outranks the other phone's copy of the auto-logged row.
- **Rows.** Method `saved` for a saved meal and `copy` otherwise, both of which
  every server build accepts (an unknown method is refused). Logged at the
  meal's time from `Settings.mealTimes`, so Today's meal header and learned meal
  times read the meal, not the moment the app opened. That time can be later
  than now, so fasting's "last eaten" now ignores entries timed in the future.
  Logging this way never ends a fast, and never stops one starting: "last
  eaten" leaves out every entry in `repeat_logs`, so a dinner repeat timed
  after the window closed does not cancel that evening's fast. Closed and untracked days are skipped and
  not recorded, so reopening or tracking the day again lets the next run log it.
- **Not synced yet.** The prod server could not be redeployed for this, and
  settings JSON was ruled out: older builds drop unknown keys on save (which
  would erase the rules) and it would put the rules' food snapshots into a
  64 KB blob. So `repeat_rules` (Room 12) is device-local and lost on
  reinstall. It is the one diary table a sign-out keeps: the dump is safe for
  the rest because the server gives them back, and these it would lose for good.
  `RepeatOwner` records whose they are (one account per phone, in a small cache,
  so no migration): the same account signing back in finds them, nobody sees or
  logs any while signed out, and a different account signing in deletes them
  and their ledger before it can read one. Rules from before the owner was
  recorded go to the first account seen signed in, or at a user change before
  that, as they always did. A deleted account's rules stay, inert, until the
  next sign-in deletes them. It already has what an
  outbox row needs (UUID, `clientUpdatedAt`, tombstone); syncing it is a new
  `SyncEntity`, a server table and migration, and nothing else on the client.
  Until then a rule set up separately on two phones has two ids and logs twice.
- **The mark.** `LogItem.repeatRuleId` comes from `repeat_logs` when a day is
  read. It is local, so another phone shows the entry as an ordinary one.

**Alternatives:** back-filling missed days (rejected above); a new `repeat`
log method (the prod server refuses unknown methods); a local-only column on
`log_entries` for the mark (a pulled row would overwrite it).

**Name:** "Repeating" for the section and "Repeat this" for the action. It
describes what the person set up, in the verb they act with. "Everyday foods"
is wrong for a weekdays-only rule, and "Auto-log" is jargon for what is, to the
person, simply eating the same thing again.

## 2026-10-28: A packaged food is added from its label wherever a food is added, and says when it is unverified

**Decision:** "Add this food" is now "Add a packaged food", and it opens from
five places, all behind `release.submitFood`: a barcode miss and an empty
search (as before), a "Can't find it?" row under every search result list
that has a query, a "Packaged food" tile on the + sheet beside Scan barcode,
and the first card of the Foods tab's Add a food. From the Foods tab there is
no meal, so the same screen saves the food without logging it ("Save food");
`SubmitFoodRoute` moved to the logger's api so the Foods feature can open it,
with an optional day and meal and an `entry` for telemetry. The server's
`origin` stays `barcode` or `search` (whether a code was scanned), so an older
server takes every submission; the app event carries the finer `entry`.

The + sheet's meal estimate tile reads "Estimate a meal" and its screen's
title the same ("we'll estimate what's on it"); the pack's tile and buttons
say "Packaged food" and "Add it from the label", and the copy says the food
goes into the person's foods and Opson's food database. No acronym is shown.

A food in Opson's food database that is waiting for review is marked
**Unverified** wherever its submitter meets it: their own copy (the custom
food) on search rows, Recent, the Foods tab and the food detail, and the
catalog's pending row when their own scan finds it. An approved one is
Opson's: "From Opson's food database", and with the search options an "Opson"
badge with the verified check. Every verified catalog food's detail gets the
check before its source. The marks are words, so every mode shows them.

To mark the person's copy, the app names it on the submission
(`customFoodId`, V27) and keeps the statuses on the phone
(`SubmittedFoods`): marked the moment the server takes the food, refreshed
from a new `GET /v1/foods/submissions` on every account edge, wiped on a user
change. A food's response carries `reviewStatus` only when it is pending or
rejected. Both additions are optional fields, so neither an old app nor an old
server breaks; a server without the list route leaves the marks as the phone
recorded them. Pending foods stay their submitter's alone: search still never
returns one.

**Why:** the owner found the pack photos only behind an empty search, could
not tell the meal estimate from cataloging a food, and wanted foods people add
to build Opson's own database, verified or not. A label read with checks is
the most exact food data a person can give, so it belongs wherever a food is
added, and the difference from a plate estimate has to be in the words on the
tile, not in the person's memory of which camera does what.

**Alternatives:** showing the pending catalog row in its submitter's search
next to their custom copy (two rows for one food, and the person cannot tell
which to pick); logging the catalog row instead of a custom food (logging
would wait on the network, and an offline save would have nowhere to go);
keeping the link only on the phone (lost on a reinstall or another phone);
a "my submissions" screen (not asked for; the mark on the food says the same
where it matters); offering the pack from the + sheet's Photo tile with a
choice inside (two decisions behind one ambiguous camera icon).

## 2026-10-27: Vitamin and mineral references fit the person, and "not listed" is not 0

**Decision:** each vitamin and mineral's reference comes from `MicroReferences`:
the DRI for the person's age band and sex (Resting energy's birth year and sex),
halfway between the sexes for "Prefer not to say", the 31 to 50 band without a
birth year, and the FDA Daily Value when nothing is known, with a number of
their own over any of them (`microTargets`). Today's nutrients page lists four
the person picks (`todayNutrients`). Both are top-level settings keys, and both
default to empty rather than null, because the server's shallow merge keeps a
key a write leaves out and `explicitNulls = false` leaves a null out: going
back to the defaults would never reach the other phone.

A micronutrient no logged food lists has no percent at all (`MicroPercents.of`
leaves it out) and reads "Not listed"; a food that lists 0 is 0. Rows say how
many foods list a nutrient when only some do. The catalog, the wire, Room and
sync already kept the two apart; only the sum and food detail collapsed them.

**Alternatives:** keep one adult table for everyone (wrong by 10 mg of iron
for half of adults); use the Daily Values for "Prefer not to say" (they are the
higher of the two for most nutrients, so the percent would read low).

**Why:** the percent is only honest if the reference is the person's and the
total says when it rests on partial data. Details and sources in
`docs/research/nutrient-targets.md`; the table is `TODO(dietitian)`.

## 2026-10-27: How sure is worked out, and a custom food carries its weight, sizes and label

**Decision:** the food detail no longer asks How sure up front. It works it
out: an amount typed in grams or another weight or volume is Weighed, a scanned
label's serving is From label, anything else (a count of spoons, a chip, the
last amount again) is an Estimate. A quiet How sure row under Amount, Meal and
Time shows the answer and can change it; a pick sticks. No numbers has no row,
because the ± it feeds is a number that mode never shows.

A custom food gains `servingGrams` (what one serving weighs), `sizes` (one of
each unit with its weight) and `micros` (the rest of the label, keyed like a
log entry's). That is what Edit a copy needs to turn a food listed only in
tablespoons into one that logs by the gram. Server migration V25 and Room
version 11 add the three, nullable. A row without `sizes` comes from a 0.1
build, which knows nothing of them, so both the server and the client keep the
stored three rather than wipe them; a 0.2 build always sends `sizes`, empty for
none. Remove from recents is a top-level settings key, `hiddenRecentFoods`
(food key to when), so the server's one-level merge keeps it through an older
save, and a food logged again after that comes back.

Report a problem opens the existing bug report with the food's ref key and
nothing else. The food review queue (`SubmitFoodRoute`, the ops review board)
takes new packs, not corrections to a food the catalog already has, so a
report cannot go there yet. (Superseded 2026-10-29: with `release.foodReports`
on, a catalog food's report goes to the review queue; see "Food data is
guarded on the way out".)

**Why:** the owner found the old detail odd next to Cronometer. The segmented
How sure was the loudest thing on the screen for a setting that only moves the
day's ±, and typing an amount already turned Estimate into Weighed even when
the amount was "3 tbsp". The creamer they fixed in Cronometer could not be
fixed in Opson at all: a custom food had one serving and five numbers.

**Alternatives:** keeping How sure as a segmented control under the amount
(rejected: it is what made the screen busy); dropping it entirely (rejected:
a label or a scale is real information for the ±); storing the extra label
lines as more columns (rejected: the catalog and log entries already keep them
as a keyed map, and a column per nutrient is a migration per nutrient);
overwriting the new columns from an old build's save (rejected: an old phone
editing a food's name would erase what a new one weighed). Research in
`docs/research/food-detail.md`.

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
(0.2) by default. No thermic effect of food (since 2026-10-29, an opt-in; see
below). Goals keep reading
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
