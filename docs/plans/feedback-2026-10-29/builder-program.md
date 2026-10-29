# The Builder program

Researched 2026-10-29. Builds on `package-scanning.md` (capture, review triage,
incentives), `pricing-and-pro.md` (the entitlements table and founding members)
and `ai-options.md` (reads, confidence, the auto-approve lane). It doesn't
repeat them.

## Recommendation

- **Call it Builders.** It's the owner's own word, it says what members do, and
  "Founders" is already taken by founding members.
- **Builders add missing packs, check existing foods against packs in hand, and
  report problems.** Typed values count when a label photo backs them.
- **Pro while active.** Ten accepted contributions in the last 30 days keeps
  Pro on; it runs out 30 days after someone drops below that. Granted as an
  `entitlements` row of kind `builder`, refreshed nightly.
- **Four trust levels.** Only Trusted Builders get auto-approval, and 10% of
  their auto-approvals are still audited at random.
- **An owner role whose edits skip the queue but not the record.** Set in
  Supabase `app_metadata`, every edit kept in an edit history that can be
  undone.
- **Bans work in three layers.** The account (`banned_until`, already enforced),
  the Builder grant, and a device marker (a DeviceCheck bit on iOS, a Play
  Integrity device recall bit on Android). The marker blocks contributing and
  free Pro, never logging food.
- **Fix two gaps in today's schema first.** A contributor who deletes their
  account leaves approved foods with no trace of who sent them, and approved
  foods have no edit history.
- **One new Grafana board, `opson-builders`,** on new `support` and
  `analytics` views, with alerts for audit failures and photo reuse.

## What exists today

| Piece | State |
|---|---|
| Submissions | `food_submissions` (V25, V27): parsed, submitted, reviewed, checks, decision, `reviewed_by` from `X-Admin-Actor` |
| Review | Admin console ReviewView behind `X-Admin-Token`; one reviewer |
| Limits | `FoodLabelLimiter`: 10 reads per 10 min and 40 a day; 5 submissions per 10 min and 20 a day; in memory, per machine |
| Bans | `auth.users.banned_until`, read by `PostgresModerationRepository`; every authed call then answers 403 (`BanTest`). No reason, no appeal, no UI in the admin console |
| Report a problem | Opens the bug report with the food's ref key. `TODO(review-queue)` marks where it should reach the queue (`docs/decisions.md`) |
| Accounts | Supabase, including guest (anonymous) accounts (`support.users.is_guest`) |
| Pro | Not built. `pricing-and-pro.md` designs `entitlements` |
| Device attestation | None |

## Program basics

### Name

| Name | For | Against |
|---|---|---|
| **Builders** (recommended) | The owner's word; active; "Builder" works as a status on More | Slightly generic |
| Founders | Warm | Taken: founding members are a different grant, and two "founder" ideas would confuse everyone |
| Pantry crew | Friendly, food-shaped | Cute; "crew" implies a group chat Opson doesn't have |
| Label crew | Says exactly what they do | Narrow once Builders check foods too |
| Contributors | Plain and accurate | Reads like a wiki footer |

Use "Opson Builders" once in the joining copy and "Builder" everywhere else.

### What members do

1. **Add missing foods.** Front, nutrition panel and barcode, through the
   existing Add this food flow with the guided capture from
   `package-scanning.md`.
2. **Check foods they own.** A Builder mode in the barcode scanner shows each
   scanned food's standing: verified, unverified, or missing a label photo. One
   tap photographs the label to confirm or correct it. This is how a cupboard
   becomes useful data.
3. **Report problems.** Anyone can, from any food. A Builder's report goes
   straight to the review queue with their photo attached, if they took one.
4. **Type instead of scan.** Manual entry is accepted for the values. The label
   photo is still required for the contribution to reach the catalog or count
   toward Pro; without one it stays the person's own food, as today.

Builders don't edit the catalog directly and don't review each other, until
the Reviewer level exists (below).

### How people join

| Entry point | When | Copy, roughly |
|---|---|---|
| After Report a problem | Right after a report is sent | "Want to fix foods like this yourself? Join Opson Builders." |
| A one-time prompt | When a person's first submission is approved, the quiet thank-you moment in `package-scanning.md` section 7. Never shown twice | "Your granola is in Opson's food database. Builders add foods like this and get Pro while they do." |
| More › Builders | Always, behind a flag | What it is, the rules, Join |

To join, a person needs:

- A claimed account, not a guest one. Guests are free to create in bulk.
- At least one approved submission, or an account at least 7 days old.
- No ban on the account or the device.
- A passing device check (App Attest or Play Integrity, below).
- To accept the Builder terms.

### Rules and terms

A short page in plain words, linked from the join screen and added to
`legal/terms.md`:

- Photograph only packs you have in front of you. No photos from websites,
  other apps or other people.
- Don't copy numbers from other apps or databases. That covers MyFitnessPal,
  Cronometer and Open Food Facts. Their data is licensed (Open Food Facts is
  share-alike; see `food-data-sources.md`), and a copied number has nobody
  checking it.
- You license Opson to store your photos for review and to publish the food
  data, as the privacy policy already says for submissions.
- Optional, a separate checkbox: let Opson keep your label photos for up to 2
  years to test how well it reads labels. This is the only way to build the
  evaluation set in `ai-options.md`, because the policy otherwise deletes
  photos 30 days after review. `legal/privacy.md` needs a matching line.
- One account per person.
- No payment. Pro lasts while you're active and the program runs; Opson can
  change or end the program with notice.
- Fake or copied data means removal from Builders, loss of Builder Pro, and
  removal of your contributions. It can also mean a ban.

On Apple's rules, guideline 3.2.2(x) says apps "may otherwise incentivize users
to take specific actions within apps"
([App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)),
which covers rewarding contributions. Unlocking Pro without in-app purchase
carries the same 3.1.1 risk as the founding grant, with the same mitigations
(`pricing-and-pro.md`): Pro stays purchasable in the app, no code fields, and an
explanation in the review notes. A lawyer should confirm that an in-kind reward
for data creates no employment or tax question; at about $40 a year of value
it shouldn't, but it is the kind of thing to ask once.

### What Builders get

**Pro, through the entitlements system.** Add `builder` to the `kind` values in
`pricing-and-pro.md`'s table:

```
entitlements
  kind        'founding' | 'pro_store' | 'pro_promo' | 'builder'
  source      'builder_v1'
  expires_at  now() + 30 days, refreshed nightly while the rule holds
  revoked_at  set on removal from the program
```

- A nightly job counts each Builder's accepted contributions in the last 30
  days. Ten or more, and `expires_at` moves to 30 days from now. Fewer, and it
  is left alone to run out.
- "Accepted" means approved by a reviewer, auto-approved and not overturned by
  an audit, or a check that agreed with an independent source. Submitted
  doesn't count; rejected never does.
- It is a threshold, not a per-food credit. Past ten a month there's nothing
  more to earn, which removes the reason to send junk.
  `package-scanning.md` warned against paying per food for the same reason.
- Founding members already have Pro. They get the Builder status and the
  thank-you notes, and nothing else changes.
- Someone already paying through a store keeps paying; Opson can't pause a
  store subscription. Show them "Builders covers your Pro until 12 December"
  and leave cancelling to them.

**What it costs Opson.** Pro's per-use cost is AI meal estimates, capped at
about 150 a month at about 2 cents (`pricing-and-pro.md`), so at most about $3
a month a Builder. Ten scans on the recommended cascade cost about 10 cents
(`ai-options.md`). For that Opson gets at least ten checked foods a month from
each Builder, which no vendor sells at that price (NCCDB's startup licence alone
is $1,710, `food-data-sources.md`).

**Recognition, quietly.** A count of foods added and "used by 23 people", as in
`package-scanning.md`. No leaderboard, no streak.

## Trust

### Levels

| Level | How you get there | What changes |
|---|---|---|
| 0. Anyone | Default | Every submission reviewed. The food is theirs at once, as today |
| 1. Builder | Joined | Every submission reviewed, sorted by demand. Counts toward Pro. Submission limit raised from 20 to 60 a day |
| 2. Trusted | 50 or more reviewed contributions; headline agreement with reviewers of 97% or more over the last 50; no rejection for fake or copied data, ever; under 5% rejected in the last 30 days; account at least 30 days old; device check passing | Auto-approval when the machine lane agrees (`ai-options.md`, routing). 10% of those audited at random; the first 20 after promotion all audited |
| 3. Reviewer (later) | Trusted for 90 days, invited by the owner | Can confirm another Builder's pending food by photographing the same pack. Two independent photos that agree approve it |

**Agreement** is measured per reviewed contribution: did the reviewer leave
every headline field (kcal, protein, carbs, fat, serving size) within label
rounding of what the Builder sent? The rate is over the last 50.

**Demotion is automatic.** Trusted drops back to Builder when the 30-day audit
failure rate passes 5% or agreement falls under 95%. Any confirmed fake removes
the person from the program. The owner can hold a promotion.

These thresholds are starting points. Before any lane goes live, run it in
shadow: compute what it would have auto-approved while people still review
everything, and switch it on when its decisions match the reviewers' on at
least 99% of 300 or more cases. That is the same evidence `ai-options.md` asks
of the AI lane.

### Audits

- Random, never chosen by the reviewer, so the measured error rate is honest.
- 10% of Trusted auto-approvals, all of a newly promoted Builder's first 20,
  and twice the rate for foods many people scan, where an error spreads
  furthest.
- A failed audit puts the food back to pending, tells the Builder what was
  wrong in plain words, and counts against them.

### The owner and admin role

Today reviewers act through the admin console with `X-Admin-Token`. They can
correct and approve submissions, but no route edits a food already in the
catalog.

- **The role.** `app_metadata.role = 'owner'` or `'admin'` on the Supabase
  account, set only through the Admin API. `app_metadata` isn't writable by the
  user; `user_metadata` is, so it must never carry a role. The server reads the
  claim from the JWT it already verifies.
- **In the app.** An owner's submission is approved on arrival, with
  `reviewed_by = 'owner:<id>'`. Photos, checks and the record are kept as for
  anyone. A blocking check still asks for a confirmation.
- **In the admin console.** Edit any user-submitted food directly. For USDA,
  Open Food Facts and NEVO rows, write an override row instead of changing the
  import: the next reload would wipe the change, and editing an Open Food Facts
  row breaks its licence containment.
- **Every edit is recorded** in a new `food_edits` table (food, actor, before,
  after, reason, when), with Undo in the console. The role skips the queue, not
  the history.

### Two gaps to close first

1. **Provenance doesn't survive account deletion.** V25 cascades
   `food_submissions` away with the account, and an approved food then has
   "no account attached". So a bad actor can delete their account, and their
   approved foods can't be found or rolled back. Add a `food_provenance` table
   with no cascade: food id, submission id, lane, approval time and a
   contributor key, an HMAC of the user id under a server secret. That links a
   later ban, or a device marker, to every food the person contributed, without
   keeping who they were. The privacy policy has to say so. A lawyer should
   confirm that fraud prevention (GDPR Recital 47) justifies keeping the key
   after an erasure request.
2. **No edit history.** `food_edits` above, so removal and bulk rollback can be
   undone.

## Anti-fraud signals

Computed at submission, stored on the submission as codes and numbers (never as
values), so they outlive the photos.

| Signal | How | Catches | Strength |
|---|---|---|---|
| Impossible values | `LabelChecks`, plus the additions in `package-scanning.md` section 3 | Typos, unit slips, lazy fakes | Strong for errors, weak against care |
| Grounding | Each submitted headline number must appear in the phone's OCR tokens | Numbers typed from somewhere other than the photo | Strong |
| Read disagreement | Blind cloud read and phone parser against the submitted values (`ai-options.md`) | Invented or edited numbers | Strong |
| Exact photo reuse | SHA-256 of the uploaded bytes | The same file sent twice | Strong |
| Near photo reuse | 64-bit difference hash (dHash) of the panel crop, about 20 lines of code; Hamming distance of 8 or less is a match | Re-encoded or cropped copies, the same photo for two barcodes, the same photo from two accounts | Strong, especially across accounts |
| Copy and paste | Identical nutrient vectors across a contributor's different foods; values identical to another barcode's row; one panel's values on many barcodes | Templates and bulk fakes | Medium |
| Source mismatch | The barcode is already in a tier 1 to 3 source and the values differ a lot | Wrong product, or a fake | Medium; sometimes the label changed |
| Capture source | Camera or library, sent with the submission (the app already logs it in `food_add.photo_taken`); screenshot hints (PNG, exact screen dimensions) | Photos lifted from websites | Weak alone |
| Velocity | Submissions per hour and day against the person's own median; runs of consecutive GTINs; many submissions not from a barcode miss | Scripts, farming for Pro | Medium |
| Account and device | Guest or brand-new account; App Attest keys per device in 30 days; integrity verdicts; a device marker already set | Sock puppets, modified apps, emulators | Medium |
| After approval | Other people reporting the food, or using Edit a copy on it after logging it | Wrong data that got through | Strong, but late |

**EXIF is the wrong tool.** Both apps re-encode photos before upload
(`IosStillCamera` and `IosMealPhotoPicker` redraw and write a fresh JPEG;
`AndroidMealPhotoPicker` re-encodes), so EXIF never reaches the server, by
design. Adding it back would send location for a signal anyone can edit.
Instead, have the app sign what matters: on iOS, an App Attest assertion over
the photo's hash; on Android, a Play Integrity standard request whose request
hash covers it. That proves a genuine copy of the app sent those bytes. It
doesn't prove the photo is real, which is what grounding and reuse checks are
for.

Signals add up to a lane, not a single magic score:

| Lane | When | Effect |
|---|---|---|
| Normal | No flags | Review, or auto-approval at Trusted |
| Watch | One medium flag | Reviewed, flags shown first |
| Hold | A strong flag, or two medium | Owner reviews; never auto-approved |
| Quarantine | The contributor is removed or banned | Pending items hidden; approved ones back to pending unless another source confirms them |

## Moderation and bans

### The flow

```
submission → checks and signals → lane → review or auto-approval → decision
          → random audit → feedback to the Builder → trust level updated
```

The admin console needs a Contributor page: their trust level, counts, flags
and every contribution, reached from any submission. It offers these actions,
mildest first:

1. **Note to the Builder** with a reason from a short list.
2. **Remove from Builders.** Sets `revoked_at` on the entitlement and the trust
   level to removed, and sends the last 90 days of their auto-approved foods
   back to review. They keep using the app.
3. **Quarantine their contributions,** as in the lane table.
4. **Ban the account.** `banned_until` through the Supabase Admin API
   (`ban_duration`), which the server already enforces. Add a
   `moderation_actions` table for the reason, the actor, the date and any
   appeal. `ModerationRepository`'s own comment says a richer
   suspended-or-banned distinction needs an app-level table.
5. **Set the device marker** (below).

Appeals go to the support email. Apple requires one for DeviceCheck in any case.

### Device-level bans

Deleting an account and signing up again through Sign in with Apple's private
relay takes a minute, so the account alone isn't enough for a ban.

**iOS**

| Option | What it is | Survives | Use for Opson |
|---|---|---|---|
| Keychain item | A random id saved with `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` | App deletion on every iOS release except the 10.3 betas, which Apple rolled back. Apple DTS calls it "an implementation detail", says "don't rely on that", and points to DeviceCheck ([thread 36442](https://developer.apple.com/forums/thread/36442), [thread 730924](https://developer.apple.com/forums/thread/730924)). No change found for iOS 17, 18 or 26. Doesn't move to a new device through a backup ([docs](https://developer.apple.com/documentation/security/ksecattraccessibleafterfirstunlockthisdeviceonly)) | A hint linking accounts on one install lineage, never a ban on its own |
| DeviceCheck | Two bits per device, shared by every app on the developer team, with a last-changed timestamp, set and read by Opson's server ([docs](https://developer.apple.com/documentation/devicecheck/accessing-and-modifying-per-device-data)) | Reinstall, transfer to another person, and Erase All Content and Settings ([WWDC21 10244](https://developer.apple.com/videos/play/wwdc2021/10244/)) | Bit 0: banned from contributing. Bit 1: a free Pro grant (founding or Builder) was claimed on this device. Check no other Nightjar Labs app uses the bits |
| App Attest | A hardware key proving a genuine, unmodified copy of the app ([docs](https://developer.apple.com/documentation/devicecheck/establishing-your-app-s-integrity)) | App updates only; not reinstall, migration or restore | Required to join and to submit, which stops scripts calling the API. Its risk metric counts keys per device over 30 days ([docs](https://developer.apple.com/documentation/devicecheck/assessing-fraud-risk)); a high count means reinstall churn |

Apple's licence agreement (section 3.3.7(F)) requires a way for customers to
ask Opson to reset DeviceCheck values, and says Opson "may not rely on the
DeviceCheck Data as a single identifier of fraudulent conduct"
([DPLA](https://developer.apple.com/support/terms/apple-developer-program-license-agreement/)).
So the bit triggers review, not automatic punishment. Section 3.3.3(B) forbids
deriving data from a device to identify it, which rules out fingerprinting. A
random keychain id isn't derived from the device, but it is used to recognise
one, so treat it as the gray option it is and lean on DeviceCheck.

**Android**

| Option | What it is | Survives | Use for Opson |
|---|---|---|---|
| Play Integrity device recall (beta) | Three bits per device kept by Google, written and read by Opson's server ([docs](https://developer.android.com/google/play/integrity/device-recall)) | Reinstall and factory reset; kept 3 years after the last read or write | Bit 0: banned from contributing. Bit 1: free Pro claimed. Bit 2: spare. Enrolment is an interest form, then a Play Console switch, so file it in phase 0 |
| Play Integrity verdicts | Device integrity, app integrity and licensing; standard requests take a few hundred ms ([overview](https://developer.android.com/google/play/integrity/overview)) | n/a | On joining and on each submission. The default quota is 10,000 requests a day, with increases by form ([setup](https://developer.android.com/google/play/integrity/setup)) |
| `ANDROID_ID` | Per signing key, user and device since Android 8 ([docs](https://developer.android.com/reference/android/provider/Settings.Secure#ANDROID_ID)) | Reinstall with the same signing key; not a factory reset | Don't store it. Play's User Data policy says persistent device identifiers "may not be linked to other personal and sensitive user data" ([policy](https://support.google.com/googleplay/android-developer/answer/10144311)), and Google recommends Play Integrity for abuse ([best practices](https://developer.android.com/identity/user-data-ids)) |

### Keeping it proportionate and legal

- The device marker only blocks contributing and free Pro grants. Logging food
  is never blocked by device. Families share phones and phones get sold, and a
  DeviceCheck bit outlives an erase, so a second-hand iPhone could arrive
  carrying someone else's marker. The reset path handles that case.
- Declare it. Apple's App Privacy answers and Play's Data safety form ("Device
  or other IDs", purpose "Fraud prevention, security, and compliance",
  [Play](https://support.google.com/googleplay/android-developer/answer/10787469))
  and `legal/privacy.md` all need a line.
- GDPR treats fraud prevention as a legitimate interest
  ([Recital 47](https://gdpr-info.eu/recitals/no-47/)). Storing and reading a
  keychain id is covered by Article 5(3) of the ePrivacy Directive, and whether
  the security exemption from consent applies to it is a judgement call
  ([WP29 Opinion 04/2012](https://ec.europa.eu/justice/article-29/documentation/opinion-recommendation/files/2012/wp194_en.pdf)).
  A lawyer should confirm both.
- None of this stops someone with a second phone. It raises the cost of
  starting over; review and the signals catch the rest.

## Grafana dashboard

A new board, `opson-builders` ("Opson · Builders"), generated by
`ops/grafana/generate_db_dashboards.py` like the other product boards, with the
same `env` dropdown. Postgres (`opson-prod-db` or `opson-db`, as `grafana_ro`)
for everything the server knows; Loki (`grafanacloud-logs`) only for app
events. Server logs don't reach Loki yet (`ops/grafana/README.md`), so AI usage
needs a ledger table rather than log queries.

The README's rule holds. `analytics` carries aggregates and `support` carries
per-person counts, and no panel shows a food's name, a nutrient, a barcode or a
photo. Panels link into the admin console by submission or contributor id;
looking at the food itself happens there.

### New views and tables

| Object | Columns | Notes |
|---|---|---|
| `label_reads` (table) | submission or parse id, model, step (`blind`, `escalate_1`, `escalate_2`), input and output tokens, latency ms, outcome, created at | Written per model call. Source for cost and latency |
| `analytics.contributions_daily` | day, lane, trust level, submitted, auto-approved, approved, rejected, audits passed, audits failed | |
| `analytics.contribution_flags_daily` | day, flag code, count | |
| `analytics.label_reads_daily` | day, model, step, reads, estimated USD, p50 and p95 ms | Prices kept in the view, updated when they change |
| `analytics.builder_program_daily` | day, active Builders, Builders with Pro, joined, removed, banned | |
| `support.contributors` | user id, trust level, joined at, account age, submitted 30d, accepted 30d, rejected 30d, agreement over last 50, flags 7d, audit failures 30d, photo reuse hits, Pro until, removed at, device marker set | One row per Builder |
| `support.contribution_flags` | submission id, user id, created at, flag codes, lane | Ids and codes only |
| `support.photo_reuse` | submission id, other submission id, both user ids, distance, cross-account | |

### Panels

**Program**

| Panel | Type | Query, roughly |
|---|---|---|
| Active Builders | stat | `SELECT count(*) FROM support.contributors WHERE accepted_30d > 0 AND removed_at IS NULL` |
| Builders with Pro | stat | `SELECT count(*) FROM support.contributors WHERE pro_until > now()` |
| Joined and removed per week | timeseries | `SELECT date_trunc('week', day) AS time, sum(joined), sum(removed) FROM analytics.builder_program_daily WHERE $__timeFilter(day) GROUP BY 1 ORDER BY 1` |
| Accepted per day by lane | stacked timeseries | `SELECT day AS time, lane, sum(approved + auto_approved) FROM analytics.contributions_daily WHERE $__timeFilter(day) GROUP BY 1, 2 ORDER BY 1` |
| Auto-approved share | stat | `sum(auto_approved) / nullif(sum(approved + auto_approved), 0)` over the range |

**Quality**

| Panel | Type | Query, roughly |
|---|---|---|
| Audit failure rate, 30 days | stat, red above 5% | `sum(audits_failed)::float / nullif(sum(audits_passed + audits_failed), 0)` where day is in the last 30 |
| Reviewer edited a headline field | stat | Share of reviewed submissions whose `reviewed` differs from `submitted` on a headline field, from a column the view computes |
| Rejections by reason | bar | `reject_reason` code counts; reasons become a fixed list first (`package-scanning.md` section 4) |
| Agreement across Builders | histogram | `SELECT agreement_last_50 FROM support.contributors WHERE submitted_30d > 0` |
| Waiting, by lane, and oldest | stats | Extend `analytics.food_submission_queue` with `lane` |

**Abuse**

| Panel | Type | Query, roughly |
|---|---|---|
| Builders to look at | table, sorted | `SELECT user_id, trust_level, flags_7d, rejected_30d, photo_reuse_hits, submitted_30d FROM support.contributors ORDER BY flags_7d DESC, photo_reuse_hits DESC LIMIT 50`, each id linked to User detail and the admin Contributor page |
| Flags per day | timeseries by code | `analytics.contribution_flags_daily` |
| Photo reuse | table | `SELECT * FROM support.photo_reuse WHERE created_at > now() - interval '7 days' ORDER BY cross_account DESC, distance` |
| Bursts | table | Builders whose last 24 hours exceed three times their daily median |
| New accounts, many foods | table | Accounts under 3 days old with 10 or more submissions |
| Integrity failures and device markers | timeseries | Counts from the attestation ledger: failed verdicts, joins blocked by a marker |

**AI cost and speed**

| Panel | Type | Query, roughly |
|---|---|---|
| Reads per day by model | stacked timeseries | `analytics.label_reads_daily` |
| Escalation rate | stat | Share of submissions with a step other than `blind` |
| Estimated spend per day | timeseries | `sum(estimated_usd)` |
| Cost per accepted food | stat | Spend over the range ÷ accepted contributions |
| p95 latency by model | timeseries | `p95_ms` |

**In the app (Loki)**

New typed events, like `SubmitFoodTelemetry`, registered in
`docs/practices/app-events.md` so `DashboardQueryContractTest` covers them:
`builder.prompt_shown` (entry), `builder.joined` (entry), and
`builder.scan_checked` (standing: verified, unverified or missing). Counts
only. Panels: the join funnel by entry point, and scans per day in Builder mode.

### Alerts

| Alert | Condition | Why |
|---|---|---|
| Audits failing | Audit failure rate over 5% across the last 7 days, with at least 20 audits | Auto-approval is letting errors through |
| Photo reused across accounts | Any new `cross_account` row | Almost always sock puppets |
| One Builder, many flags | 5 or more flags in 24 hours | Catch it before Pro renews |
| Auto-approval share jumps | Day over day change above 20 points | A check or model has broken |
| Escalations high | Over 30% of submissions for a day | Model regression or a prompt change |
| Spend high | Daily estimated spend above twice the trailing 7-day mean | Runaway cost |
| Hold lane ageing | Oldest Hold item over 72 hours | The owner's queue is stuck |

Grafana alert rules can read SQL datasources that return a number. Before any
of this works, `opson-prod-db` needs its Grafana password set
(`ops/grafana/README.md`, "Is anything flowing?").

## Rollout

| Phase | Scope | Done when |
|---|---|---|
| **0. Foundations** (1 to 2 weeks) | `entitlements`; `food_provenance`; `food_edits` with Undo; `label_reads`; `support.contributors` and the views; the owner role; Report a problem into the review queue; file the device recall beta form | The owner can edit any food from the app or console and undo it |
| **1. Measure** (in parallel) | The 300-label evaluation set from `ai-options.md`; trust levels and the auto-approve lane computed in shadow | Accuracy and cost per model on Opson's own labels |
| **2. Closed alpha** (2 to 4 weeks) | The owner plus 10 to 25 invited people; Builder terms; everything reviewed; Pro granted by hand; the Grafana board live | Reviewer edit rate and the fraud signals tuned on real submissions |
| **3. Integrity** (1 to 2 weeks) | App Attest and DeviceCheck on iOS, Play Integrity and device recall on Android, at joining and submission | A reinstalled app on a banned device can't rejoin |
| **4. Open enrolment** | The three entry points; the ten-in-thirty rule; a cap of 500 Builders with a waitlist after, so review stays bounded | The queue clears daily |
| **5. Trusted auto-approval** | When the shadow lane matches reviewers on 99% of 300 or more cases, and random audits run | Owner review time per accepted food falls by half |
| **6. Reviewers** | Peer confirmation by a second Builder's photo | Needed only past a few thousand submissions a month |

## Open questions

1. How many hours a week can the owner review at launch? That sets the cap in
   phase 4.
2. Is 10 accepted a month the right bar, or should it start lower in the alpha?
3. Should Builders be thanked by name on the Credits screen, opt-in?
4. Is the optional 2-year photo retention acceptable to put in the privacy
   policy?
5. Should a Builder's approved food show the verified check, or the softer
   "Checked" mark, until a second source agrees?
