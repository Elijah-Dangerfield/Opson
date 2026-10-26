# Where Opson stands (2026-10-26, evening)

Everything below is on `main` and pushed.

## Shipped to main

- Every 0.1 screen at design v3: Onboarding, Today (Undo after adding, Mark as
  untracked, Explorer's food bridge, Blind's day feeling), Logger, Foods,
  Trends with Nutrients, Weight and Patterns, More/Modes/Targets/Support.
- `BlindModeLeakTest` across every surface (`:apps:leaktest`), tokens and type
  scale measured from v3, one theme per screen preview.
- Food catalog: USDA generic and Branded (445,414 foods) in dev and prod.
- Open Food Facts: `off_foods` table (V12), importer with an atomic monthly
  reload, benchmark script. **Not loaded anywhere and not in search.** See
  "Open Food Facts" below.
- Text fields keep fast typing (a value no longer round-trips through the
  ViewModel and eats keystrokes), and the search field's Search key closes
  the keyboard.
- iOS is iPhone-only for 0.1 (`TARGETED_DEVICE_FAMILY = 1`).

## App Store

- Listing, privacy labels, age rating, price, review notes: done.
- Five 6.9-inch screenshots from the real app are uploaded; the files are in
  `docs/app-store/screenshots/`.
- TestFlight build from the latest main is in CI; once it processes it gets
  attached to 0.1.0 and submitted.

## Open Food Facts

Size and speed, measured on a local Postgres sized like dev: 3.29M products,
2.1 GB with indexes. Search p50 66 ms, but p95 1.3 s and p99 5.7 s: short
prefixes and common words scan too many rows. Details in
`docs/food-data-sources.md`.

Before it goes anywhere near search or prod:

1. Bound the search candidates (minimum prefix length, a per-tier cap, or a
   hot subset ranked by `scans`), then re-run `scripts/bench_off_search.py`.
2. Load dev and benchmark there. Dev's disk is now 12 GB; the first attempt
   hit the old size and Supabase put dev in read-only mode for about 15
   minutes.
3. Prod's disk is **2 GB**. A reload holds two copies, so grow it to fit
   about 6 GB on top of what's there before any load.
4. Then set the repo variable `OFF_IMPORT_ENABLED=true` for the monthly job.

## Found while taking screenshots (not fixed yet)

- Search ranking: "brown rice" puts a branded "Crispy Sushi Rice Patties"
  (brand "Rice Browns") above plain brown rice.
- Default serving: blueberries default to "1 berry, 1 kcal". The importer
  should prefer a cup or a typical serving over a single piece for small
  items.

## At the end: the Opson website

No domain yet; one comes later, so build it to deploy anywhere and switch
domains without code changes.

- Marketing pages, privacy policy and terms, built **very similar to how
  Downcard (the Cards project) does its site**. Study that site's structure,
  stack and deploy before starting.
- A **waitlist signup** that lands somewhere the owner can contact people
  from (a `waitlist` table in Supabase behind a server endpoint or an edge
  function, with email, created_at and source). Include abuse protection and
  a way to export or view the list.
- Privacy and terms: the app's `legal/` files are already published at
  nightjarlabs.llc/opson/privacy and /terms; the site should link to or reuse
  them rather than fork the text, and the waitlist needs its own privacy
  wording (what the email is used for, how to get removed).

## Needs you

- Set the `CATALOG_DATABASE_URL` repo secret (prod pooler URL) so the monthly
  USDA refresh runs.
- `/design-login` in a `claude` terminal, so the `claude_design` connector
  works.
- Decide the default palette: the icon is Espresso's accent; the app defaults
  to Pine.
- Before public launch: a dietitian sets `GOAL_FLOOR_KCAL` and the maximum
  deficit; a clinician reviews the copy marked `TODO(clinician)`; check the
  helpline and provider links marked `TODO(launch)` (both open the Alliance's
  home page today).
