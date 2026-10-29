# Where Opson stands (2026-10-27)

## Prod's open tables: closed

Six prod tables had row level security off (profiles, player reports and the
four app config tables), so the app's public Supabase key could read display
names and change remote config. V19 was applied to prod by hand on 2026-10-27
and all six now have RLS on; the prod server stayed healthy. Flyway runs V19
again at the next prod deploy, which is a no-op.

## 0.1.0

Approved (build 202610261458) and left unreleased on purpose: that binary
signs in to the dev Supabase project, so every call to prod fails with 401.
Fixed on main (`d67368c3`). The next store submission will be 0.2.

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

## 0.2 on main (behind release flags; off on prod except the TestFlight build)

| Feature | Flag | State |
|---|---|---|
| Barcode scan, typed codes | `release.barcode` | Merged. Scans read the Open Food Facts export first when it's on. |
| Health, Expended, exercise in the range | `release.health` | Merged. Entitlement held back for Release. |
| Reminders and notifications | `release.reminders` | Merged. Real-device delivery untested. |
| Fasting | `release.fasting` | Merged. |
| Voice in Describe | `release.voice` | Merged. Try on a phone. |
| Photo estimate | `release.photo` | Not in 0.2. Quick actions only. Needs `ANTHROPIC_API_KEY` on the server. |
| Widgets | `release.widgets` | iOS extension ships in every build and the snapshot is written whatever the flag says; the flag is only the More row. Six iOS widgets now (Today, Calories, Macros, Water, Meals, Fasting, with Lock Screen sizes), and the snapshot is a file in the App Group after build 703's widget stuck on 0 (`docs/plans/screens/widgets.md`). Android receiver still held on `hold/0.2-widgets-native`. |
| Medication (GLP-1) tracking | `release.medication` | On the feature branch. Doses, the plan and side effects stay on the phone (no server table yet). Spec and what was built: `docs/plans/feedback-2026-10-29/glp1-research.md`. |
| Search options, Today summary, select, check-in, progress photos | `release.searchOptions`, `release.todaySummary`, `release.todaySelect`, `release.checkIn`, `release.progressPhotos` | Merged. |
| Open Food Facts search | `FOOD_SEARCH_OFF_ENABLED` (server) | On for dev (3.29M products). Off for prod. |

Every app flag is on in dev's remote config, so the Debug build shows them all.

**TestFlight 0.2.0 (202610271424), versionCode 702 (2026-10-27).** Built from
`08fd575c` and pointed at prod. The first upload (700) was rejected for a
missing `NSHealthUpdateUsageDescription`, now fixed. Prod has one rule per flag
(iOS, versionCode 700 to 702, priority 10) that turns on everything except `release.health`
and `release.widgets`. Older builds, including the 0.1 store build, see no
change. Photo estimate fails until prod has `ANTHROPIC_API_KEY`, and search
has no Open Food Facts results until `FOOD_SEARCH_OFF_ENABLED` is on for prod.
A newer TestFlight build needs its own rules, or widen these with the admin
console. `main` still says 0.1.0: the bump was local, for this build only.

**TestFlight 0.2.0 (202610290944), versionCode 703 (2026-10-29).** Built from
`341a9a72`, internal testers only, pointed at prod. The prod rules above now run
700 to 703, and build 703 alone also gets `release.exercise`,
`release.providerSummary`, `release.submitFood`, `release.repeats` and
`release.medication`. Two things a laptop build needs that the credential store
lacks: a UTF-8 locale for fastlane (now handled in the Fastfile) and
`GRAFANA_LOGS_WRITE_TOKEN`, which is account-wide and was taken from Cards'
`local.properties` for this build. Add `grafana.logsWriteToken` to the store so
`scripts/beta_ios.sh` stops failing the release check as half configured.

**Website:** live at https://www.opsontracker.com (the bare domain forwards).
What has to happen before 0.2 ships is in `docs/plans/0.2/release-checklist.md`.

## Design sync

All 449 app screens are published for Claude Design at
https://raw.githubusercontent.com/Elijah-Dangerfield/Opson/design-sync/app/manifest.json.
Export the design as PNGs named by those ids and run
`scripts/design_sync.main.kts import <path>`.

## Needs you

- **Google sign-in is set up** (2026-10-27): Google Cloud project `opson-512920`
  ("Opson", published, External), one Web client per Supabase project, and
  Google enabled on dev and prod with manual linking on. The client ids and
  secrets are in the credential store (`opson.google.{dev,prod}.*`); Google
  never shows a secret again. The account chooser says "continue to
  <ref>.supabase.co" until Supabase has a custom domain (paid add-on), e.g.
  `auth.opsontracker.com`. Keep anonymous sign-ins on while 0.1 and
  TestFlight 702 are installed anywhere.
- Decide three things the review left open: the Open Food Facts cache licence question,
  what "a set deficit" should count as burned, and whether a missing release
  signing key should fail the build. Each is in `docs/review/product-notes.md`
  or the findings.
- GitHub › Settings › Actions › General: allow Actions to create pull
  requests, or release-please keeps failing.
- `ANTHROPIC_API_KEY` on dev and prod for photo estimates.
- The `CATALOG_DATABASE_URL` repo secret (prod pooler URL) for the monthly
  USDA refresh.
- Before public launch: a dietitian sets `GOAL_FLOOR_KCAL` and the maximum
  deficit; a clinician reviews everything marked `TODO(clinician)`. The
  helpline list was checked on 2026-10-27; recheck it each quarter. Legal
  follow-ups are in `docs/legal-review-2026-10.md`.
