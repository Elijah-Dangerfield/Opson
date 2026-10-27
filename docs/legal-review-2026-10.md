# Legal and health-safety review, October 2026

Done 2026-10-27 against `main` at `fb9bbf84`. **This is not legal advice.** It
is an engineering check of the standard boxes for a health app, with every
claim read against the code. Items marked **Lawyer** need one before you rely
on them. Items marked **You** need a decision or an account only you have.

Status words: **Done** means it was already right. **Fixed** or **Added** means
this review changed it. **Open** means it's on the release checklist
(`docs/plans/0.2/release-checklist.md`, "Legal and health safety").

Nothing here is published yet. Merging `legal/` to `main` publishes it to
nightjarlabs.llc within minutes, so read the "Before merging" list first.

## Before you merge

1. **Done 2026-10-27:** governing law is New York, where Nightjar Labs LLC
   is organised.
2. **Done 2026-10-27:** the contact on every page is Nightjar Labs LLC,
   418 Broadway, Albany, NY 12207.
3. **Lawyer:** one read of `legal/privacy.md`, `legal/terms.md` and
   `legal/health-data.md`.

## 1. Medical disclaimer

| Where | Status |
|---|---|
| Terms | **Fixed.** "Not medical advice" now covers: not a medical device; doesn't diagnose, treat, cure or prevent; no substitute for a doctor, dietitian, therapist or eating disorder treatment; nutrition values and photo estimates can be wrong; check with a professional before decisions; 988 and the local emergency number. |
| Website | **Added** to the footer on every page, with the Open Food Facts credit. |
| Onboarding | **Added** one muted line under the plan card on Welcome: "Opson is a food log, not medical advice. Food values are estimates." |
| More › Support and sharing | **Added** an About Opson card at the end: the full note, then Privacy policy, Terms of service and Credits and licenses. The More row's subtitle now reads "Helplines, your data and privacy" so people can find it. |

The copy avoids warnings, capitals and "you must". It sits at the bottom of the
pages, in the muted colour.

## 2. Crisis resources

**Fixed, and one decision for you.**

- **Every number, keyword and URL was checked on the organisation's own site**
  (988 Lifeline, ANAD, the National Alliance for Eating Disorders, Crisis Text
  Line, Samaritans, Shout, Beat, 9-8-8, NEDIC, Kids Help Phone, Lifeline,
  Butterfly, Text About It, Bodywhys, 1737, EDANZ). All numbers were right.
  The `TODO(launch)` notes are gone.
- **Two sources were broken.** EDANZ's old domain, edanz.org.nz, now redirects
  to an unrelated site. Ireland's 50808 was renamed Text About It, and
  text50808.ie doesn't load. Both are fixed.
- **The two Alliance links pointed at its home page.** The helpline link now
  opens Find treatment, which shows the number, its hours and the 988 note.
  The provider link opens findEDhelp, the Alliance's own directory.
- **Other countries.** If the phone's region isn't one of the six countries,
  the crisis row opens Find support. That page says to call the local
  emergency number and lists every country's line. It now ends with
  **Find A Helpline** (findahelpline.com), which covers over 175 countries
  and is the directory the IASP points to.
- **Hours.** Only the crisis lines are 24/7. The eating disorder lines keep
  office hours. The Alliance says its helpline "is not a 24/7 crisis line".
  The app labels only the crisis lines 24/7 and lists them first.
- **Reach.** Support is two taps from anywhere (the More tab, then the row).
  The day-close card links to it, or to Find support when the flag is on, and
  so does the pause sheet's "Talk to someone" with the flag on.
- **Open (You):** with `release.findSupport` off, which is what 0.1 shows,
  Support has the Alliance's helpline and findEDhelp but **no 24/7 crisis
  line**. Flip the flag once Call and Text have been tried on a phone.
- **Open:** recheck the list each quarter. Services rename, and domains lapse.

## 3. Privacy policy completeness

`legal/privacy.md` was rewritten from a code audit. What changed and why:

| Topic | Status |
|---|---|
| What's collected | **Fixed.** Adds fasts, check-ins, the setup answers (including "numbers have been hard"), Sign in with Apple email, typed meals going to the server, feedback, install and device IDs. Removes "sign in with an email": that is compiled off in 0.1. |
| Apple Health / Health Connect | **Added.** Lists the exact types read. They are read on the phone only and never sent, stored or written. |
| Photos (Anthropic) | **Added.** Not stored, not linked to the account, not used for training. |
| Voice | **Added.** iOS uses on-device recognition when the phone supports it, otherwise Apple's servers. Android does the same with the system recognizer. Opson never receives the audio. |
| Barcode | **Added.** Covers Open Food Facts, Nutritionix (off unless keyed), the stored lookups and ML Kit. |
| Progress photos | **Added.** On the phone only and excluded from backups. |
| Processors | **Added** a table: Fly.io, Supabase, Sentry, Grafana Labs, Anthropic, Apple, Google. Open Food Facts gets only barcodes. |
| Crash reports | **Fixed.** The old text said reports "never include what you logged". They carry the account ID, and **a failed food search can carry its words in the request URL**. The policy now says so. **Open:** strip query strings in `AppTelemetry.kt`, then delete that sentence. |
| Retention | **Added.** Account data until deleted (with tombstones until the account goes), backups 30 days, crash reports and logs 90 days, usage events 90 days. **Checked 2026-10-27:** Sentry 30 days, Grafana logs and traces 30 days, Supabase backups 7 days, all inside the policy. |
| Deletion and export | **Fixed.** The export is a CSV of entries, weigh-ins and water only. The policy now says so and offers the rest by email. |
| Phone backups | **Added.** The log on the phone is in iCloud and Google backups today. **Open:** exclude it (see Apple below). |
| Children | **Done.** Under 13 not allowed, and the terms now say so. Age rating 12+. No age gate, which is fine for a general-audience app under COPPA's actual-knowledge standard. **Lawyer:** whether an ED-adjacent app should go 16+ in the EEA. |
| Regional rights | **Added** rights for everyone, an appeal process, California, and EEA/UK legal bases and transfers. **Lawyer:** see below. |
| Website and waitlist | **Fixed.** `site-privacy.md` named GitHub Pages; the site now publishes to Cloudflare Pages. The waitlist text matches the code: no IP is stored. |

**Washington My Health My Data, Nevada SB 370, Connecticut.** **Added**
`legal/health-data.md`, a separate Consumer Health Data Privacy Policy, as
these laws want. It publishes at nightjarlabs.llc/opson/health-data. The Opson
site renders it at `/health-data/` and links it from the footer of every page,
the homepage included. The privacy policy links it at the top.

- **Open (You):** add the same link to the Nightjar homepage footer.
  Washington wants it on the regulated entity's homepage.
- **Lawyer:** whether everything Opson collects counts as "necessary to
  provide" the service. If any of it doesn't, collecting it needs a separate
  consent: the onboarding statement answer and the usage events are the ones
  to ask about.
- **Lawyer:** processor contracts. The policy says processors act only on our
  behalf, which assumes their standard DPAs are accepted.

**FTC Health Breach Notification Rule.** It applies. The FTC's own example is
a diet app that takes entered weights plus calories from another source. The
policy now commits to notifying people and the FTC as the rule requires.

- **Open (You):** have a one-page breach runbook. The deadlines are 60 days
  for people; for 500 or more, the FTC at the same time and the media; for
  fewer, a yearly FTC log.
- The rule also counts an unauthorised disclosure as a breach, not only a
  hack. That is one more reason to stop failed searches reaching Sentry.

**Apple HealthKit rules.** **Done in the code, now stated in the policy.**
HealthKit data is never used for advertising, never sold or disclosed, and
never stored in iCloud: it is never stored at all. The privacy policy is
linked inside the app now, as 5.1.1(i) requires (it wasn't before).

**GDPR and UK GDPR.** **Lawyer.** Food logs and weight are health data, a
special category. The policy says health data rests on explicit consent, but
the app has no explicit consent step. Two more open questions are an EU and UK
representative, and the transfer basis for each US provider. Until a lawyer
answers them, consider limiting App Store availability to the US.

**CCPA/CPRA.** Nightjar Labs is almost certainly under its thresholds. The
California paragraph is there anyway because it costs nothing and is true.
**Lawyer** to confirm.

## 4. App Store guidelines

| Guideline | Status |
|---|---|
| 1.4.1 (health apps, remind users to see a doctor) | **Fixed.** The disclaimer is in onboarding and Support. The listing makes no diagnosis, treatment or accuracy claims. |
| 5.1.1(i) (privacy policy in the app) | **Fixed.** Support › About Opson links it. |
| 5.1.1(v) (account deletion) | **Done:** More › Support and sharing › Delete everything. **Open:** Apple also asks apps with Sign in with Apple to revoke the Apple token on deletion. `DELETE /v1/me` doesn't. |
| 5.1.2(vi), 5.1.3 (HealthKit) | **Done.** Read-only. Nothing is stored or synced, and no HealthKit entitlement is on `main`'s release build yet. **Open:** exclude `opson.db` from iCloud backup. The log isn't HealthKit data, but keeping health data out of iCloud is the safe reading of 5.1.3(ii). |
| 2.5.1 (HealthKit for health purposes) | **Done.** The entitlement stays off `main` until the UI ships (checklist). |
| Privacy manifest | **Open.** There is no `PrivacyInfo.xcprivacy`. Sentry ships its own; the app's own required-reason APIs need one. |
| Listing wording | **Done.** "Food log with gentle numbers" makes no medical claim. **Fixed** the App Privacy notes: `TelemetryUserBinder` never sends email, and Customer support and Search history were missing. **Added** a line to the review notes pointing to the disclaimer and policy. |

## 5. Data and licence attribution

| Item | Status |
|---|---|
| USDA FoodData Central | **Added** USDA's suggested citation to `legal/credits.md`. It's public domain, so the credit is courtesy. |
| Open Food Facts (ODbL) | **Done** in the app (Foods tab, Logger badge and line). **Added** on the website footer and on the credits page, in Open Food Facts' own wording with links to the ODbL and the Database Contents License. The app doesn't show their images. |
| NEVO | **Not used yet**, so it isn't credited. Crediting a source that isn't in the catalog would be false. The exact citation and RIVM's conditions are on the checklist for when the importer lands. Those conditions: values unchanged, and NEVO data free to users even with Plus. |
| Atkinson Hyperlegible Next | **Done.** OFL 1.1, with the licence file next to the fonts in the app and on the site. **Added** to the credits page. |
| **Lust Script font** | **Open, licence risk.** It's a commercial Positype font ("All rights reserved") that ships in the app binary, and only the typography catalog uses it. Remove it or buy the app licence. |
| Open-source notices | **Added** `legal/credits.md`, linked from Support. It lists every library group in the Android release and iOS builds, with each licence checked on the project's repo or POM. **Open:** a generated in-app licence screen before Android ships. Apache 2.0 asks for the licence text to travel with the binary, and a web page is the weaker form of that. |

## 6. Terms

**Fixed** in `legal/terms.md`:

- the party is now Nightjar Labs LLC, not "the author";
- who can use it (13+, and a parent's agreement under the local age of
  majority);
- the right to change, charge for or stop features, with notice for paid
  ones;
- no warranty, now including accuracy;
- limitation of liability, with a cap and a consumer carve-out;
- termination;
- governing law;
- the waitlist terms (free, no commitment, no promised date, leave any
  time);
- third-party data and services, linked to the credits page;
- the App Store terms, which were already right;
- contact details.

- **You:** the state for governing law, and an address.
- **Lawyer:** the US$50 cap. Also arbitration and indemnity, both left out on
  purpose: they read harshly for this audience and need a lawyer's call.

## What changed, by file

- `legal/privacy.md`, `legal/terms.md`: rewritten.
- `legal/health-data.md`, `legal/credits.md`: new.
- `legal/README.md`: updated.
- `.github/workflows/legal-sync.yml`: publishes all four files.
- `.github/workflows/pages.yml`: rebuilds the site when any of them changes.
- `website/`:
  - footer note and links;
  - `/health-data/` and `/credits/` pages;
  - table styles for the legal pages;
  - `site-privacy.md` hosting fix.
- `features/settings/impl/.../support/`:
  - the About card and its links;
  - the verified directory and its sources;
  - the Find A Helpline row;
  - tests.
- `features/onboarding/impl/.../OnboardingScreen.kt`: one line on Welcome.
- `libraries/resources/.../strings_settings.xml`, `strings_onboarding.xml`:
  the new strings.
- `docs/app-store/listing.md`, `docs/plans/0.2/release-checklist.md`,
  `docs/plans/screens/settings.md`, `docs/HANDOFF.md`: updated to match.
