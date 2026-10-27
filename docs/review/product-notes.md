# Product notes from the review

Ideas about the product rather than the code, gathered from every slice's
findings (`findings/<slice>.md` has the detail). Each says how sure the
reviewer was. Already fixed during the review: goal styles now reach Today and
Close the day, the feedback counter turns ink at the cap, and Recent no longer
counts quick numbers.

## Before 0.1 is released

- **Prod's early tables have no row level security.** Profiles, player
  reports and the four app config tables are readable and writable with the
  app's public Supabase key, so anyone who pulls the key out of the released
  app could read display names or flip release flags for everyone. V19 fixes
  it. *Applied to dev and prod on 2026-10-27; done.*

## Before 0.2 ships

- **There's no remote kill switch or forced upgrade.** The admin flags and
  server checks exist, but no app reads them, so a bad build can't be met with
  a maintenance screen. Worth a small client gate soon. *Sure.*
- **Open Food Facts answers are cached into our own `foods` table** when a
  barcode misses, while the importer keeps ODbL data out of it on purpose
  (share-alike). Two ways out: stop caching live answers when the export is
  on, or cache them into `off_foods`. Either changes what a 0.1 app sees for
  that food, so it's your call. *Medium.*
- **Export misses most of what people write:** notes, feelings, check-ins,
  fasts, custom foods, saved meals and settings aren't in `export.csv`.
  Someone exporting "my data" expects them. *Sure.*
- **Times are always 12-hour,** whatever the phone says. Anyone outside the US
  notices on the first meal time. *Sure.*
- **Signing in with Apple on a fresh install skips setup** and lands on Today
  in Full numbers. Someone who taps Sign in by mistake misses the questions. *Fairly sure.*
- **The website says "Opson is with Apple for review"**; it goes stale the day
  0.1 is approved. *Sure.*
- **Warn-level app logs go to Grafana by default,** which grows with users
  against a shared quota. Decide the level before launch. *Medium.*
- **One bad cache decode resets the whole app record,** onboarding included, so
  a schema slip would send everyone back through setup. *Medium.*

## Behaviour that will surprise people

- **Logging yesterday's food ends today's fast.** Back-filling last night's
  snack at 8 am stops the morning's fast. *Fairly sure.*
- **"A set deficit" never knows what you burn,** on Targets or Today, even with
  a watch connected. Which number to use (7-day average, today so far, the
  watch's daily total) is your call. *Fairly sure.*
- **Delayed users see a gap where today's Trends bar was;** the caption still
  calls it "striped because in progress". Say when it opens instead. *Fairly sure.*
- **Closing Create a meal throws away the foods picked,** with no "discard?"
  step. *Moderate.*
- **Photo swap offline says "no matches"** instead of "offline". Small fix. *Fairly sure.*
- **Copying yesterday's meal from the meal sheet has no Undo,** while the
  logger's Yesterday's chip does. *Fairly sure.*
- **"Add to dinner" logs the whole basket** when foods are already picked; a
  count on the button would say so. *Unsure.*
- **Progress photos can look empty while the phone is locked** (file
  protection). Expect "my photos vanished" reports. *Medium.*
- **When reminders ship, Tone and the hunger questions move** from Look and
  feel to Reminders. Say so in the release notes. *Fairly sure.*
- **Coast with a weekend week start** shows the last closed week on Week and a
  partly closed one on Month. Maybe Coast should force Monday. *Not sure.*

## Small polish

- Search still opens on several USDA Branded "Nutella" rows that differ only by
  rounding; one row per brand would read better. *Fairly sure.*
- The server now answers a slow database with 503 `busy`; the app could say
  "Search is slow right now" instead of a generic error. *Medium.*
- Unit labels repeat their weight ("container (150 g) · 150 g"). *Moderate.*
- The iOS splash plays 1.5 s on every launch, on top of the boot gate. *Fairly sure.*
- No store review prompt ever appears; wire it to a good moment or delete it. *Sure.*
- Feedback goes to Sentry only and caps at 200 characters; a server endpoint
  and a higher cap would make it dependable. *Fairly sure.*
- A long-press on Water's + could add a glass without the sheet. *Unsure.*
- Today's page dots are small tap targets; swiping works. *Low.*
- The design gallery still shows the old hero, which can confuse design syncs. *Sure it confuses.*
- The website doesn't mention Android. *Moderate.*
- Android has no account backup in 0.1 (Apple sign-in only). *Sure.*

## Worth checking in the dashboards

- Sentry for guests signed out by an offline launch before the fix
  (`markSessionUnrecoverable`); anyone who hit it lost their data.
- A step down in iOS session counts after this release: the old app counted
  every return from Control Center as a new foreground.
- Photo estimate latency and spend: slow estimates used to fail at 30 s and get
  retried by hand, each a paid model call.

## Onboarding decisions (from `docs/plans/onboarding-v2.md`, 2026-10-27)

- `release.onboardingSetup` (the goal, resting energy and meal times screens)
  is off. They have no design yet; they reuse the app's visual language.
- EU and UK health-data consent: build the one-line "Agree and continue" on
  Welcome, or limit the App Store to the US. Needs the lawyer.
- "Skip and start logging" lands in Full numbers, as the design says. Later
  would be safer for someone who told us nothing.
- Three "why" options (curious, a medical need, working with a provider)
  change nothing yet. Keep, use or cut.
- A "yes" to the statement can't be changed anywhere later.
- A new Apple account that quits mid-setup and signs in again lands on Today;
  a small "setup done" setting would fix it.
- Units could default from the phone's region, so a US user isn't in kg.
- A clinician should review the statement copy and the recommendation's
  reason lines.
