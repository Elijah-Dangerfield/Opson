# Where Opson stands (2026-10-27)

## Prod's open tables: closed

Six prod tables had row level security off (profiles, player reports and the
four app config tables), so the app's public Supabase key could read display
names and change remote config. V19 was applied to prod by hand on 2026-10-27
and all six now have RLS on; the prod server stayed healthy. Flyway runs V19
again at the next prod deploy, which is a no-op.

## 0.1.0

Waiting for App Review (build 202610261458). Sign in with Apple or no account;
Google, email and "Coming later" are hidden. Don't approve the prod deploy
waiting in GitHub while it's in review: 0.1 talks to prod as it is.

## The whole-codebase review

Done. Eight reviewers covered all 1,408 source, script and config files
(`docs/review/LEDGER.md`), against the rubric in `docs/review/PRACTICES.md`.
Every finding has a fate in `docs/review/findings/<slice>.md`. The product
ideas that came out of it are in `docs/review/product-notes.md`. Read that one.

The fixes that mattered most:

- Blind users could briefly see Full numbers after an offline sign-in, and
  Trends gave away today's calories in Delayed and Coast. Both closed, with tests.
- Opening the app offline could sign a guest out and lose their data.
- A note containing `</b>` crashed any screen that showed it.
- The server waited up to 27 s on a busy database; it now answers "busy" in seconds.
- Health data and search text were reaching logs, traces, Sentry and Grafana.
- One bad row could block a phone's sync forever.
- Refreshing while a background sync ran could miss another phone's changes.
- A failed Kotlin compile still produced a "successful" iOS build with the old code.
- The Open Food Facts import could hang forever if Postgres restarted; it now
  builds each index separately and resumes.

Also built during the review, behind their flags: Save as meal from Today's
multi-select, the check-in switch in Reminders and its hunger card in
Patterns, the Progress photos row in Modes, and the Trends Expended card.

## 0.2 on main (behind release flags, all off on prod)

| Feature | Flag | State |
|---|---|---|
| Barcode scan, typed codes | `release.barcode` | Merged. Scans read the Open Food Facts export first when it's on. |
| Health, Expended, exercise in the range | `release.health` | Merged. Entitlement held back for Release. |
| Reminders and notifications | `release.reminders` | Merged. Real-device delivery untested. |
| Fasting | `release.fasting` | Merged. |
| Voice in Describe | `release.voice` | Merged. Try on a phone. |
| Photo estimate | `release.photo` | Merged. Needs `ANTHROPIC_API_KEY` on the server. |
| Widgets | `release.widgets` | Native widget held on `hold/0.2-widgets-native`. |
| Search options, Today summary, select, check-in, progress photos | `release.searchOptions`, `release.todaySummary`, `release.todaySelect`, `release.checkIn`, `release.progressPhotos` | Merged. |
| Open Food Facts search | `FOOD_SEARCH_OFF_ENABLED` (server) | On for dev (3.29M products). Off for prod. |

Every app flag is on in dev's remote config, so the Debug build shows them all.
What has to happen before 0.2 ships is in `docs/plans/0.2/release-checklist.md`.

## Design sync

All 449 app screens are published for Claude Design at
https://raw.githubusercontent.com/Elijah-Dangerfield/Opson/design-sync/app/manifest.json.
Export the design as PNGs named by those ids and run
`scripts/design_sync.main.kts import <path>`.

## Needs you

- Decide three things the review left open: the Open Food Facts cache licence question,
  what "a set deficit" should count as burned, and whether a missing release
  signing key should fail the build. Each is in `docs/review/product-notes.md`
  or the findings.
- GitHub › Settings › Actions › General: allow Actions to create pull
  requests, or release-please keeps failing.
- `ANTHROPIC_API_KEY` on dev and prod for photo estimates.
- The `CATALOG_DATABASE_URL` repo secret (prod pooler URL) for the monthly
  USDA refresh.
- Default palette: the icon is Espresso's accent; the app defaults to Pine.
- Before public launch: a dietitian sets `GOAL_FLOOR_KCAL` and the maximum
  deficit; a clinician reviews everything marked `TODO(clinician)`; check the
  helpline and provider links marked `TODO(launch)`.
