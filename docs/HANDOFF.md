# Where Opson stands (paused 2026-10-26)

Work stopped mid-flight on purpose. Nothing is lost: finished work is on
`main`, and the three in-progress screen reworks are committed as checkpoints
on their own branches.

## On `main` and deployed

- Foundation: visibility policy, design system, server (sync, search,
  describe, export), local-first data layer with sync, app shell and tabs.
- Screens built to the first handoff: Onboarding, Logger, More/Settings,
  Trends (with Nutrients and Weight).
- Food catalog: USDA generic foods (13k) and USDA Branded (432k products with
  barcodes), source tiers and `verified`, pluggable paid sources (off).
- Every `@Preview` renders to a screenshot (`:apps:screenshots`), and
  `scripts/design_sync.main.kts` syncs with Claude Design. Skill: `design-sync`.
- Design v3 from Claude Design is in `docs/design/v3/` with `CHANGES.md`.
  **v3 is the spec now.**
- New app icon on iOS and Android.
- Supabase: `opson-dev` and `opson-prod` (Nightjar Labs). Prod Fly server is
  wired to `opson-prod`; prod deploys need approval in the `production`
  GitHub environment.
- Grafana: Opson folder, 8 dashboards, `opson-db` datasource on dev.
- App Store Connect: app record, listing, privacy labels, age rating, price.

## Merged since the pause

Today, Logger, and More/Modes/Targets/Support are all at v3 and on `main`,
along with the shared ui changes (top app bar `PageHeader`, 48 dp touch
targets, bars capped at 100%), the reveal-time setting and goal styles.

## In progress (agent worktrees under `.claude/worktrees/`)

- Onboarding to v3.
- Trends to v3, plus the new Patterns screen.
- Foods to v3, including the Logger's pick-for-this-meal mode.

## Not started yet

1. Today follow-ups: the 6 s Undo snackbar fed by `RecentLogEvents`
   (`docs/plans/screens/logger.md`, "After adding"), Mark as untracked, the
   Explorer food bridge, Blind's "How did today feel?" chips.
4. `BlindModeLeakTest` across every surface (`docs/plans/screens/blind-leak-test.md`).
5. Sweep screen previews that use `PreviewLightDark` (one theme per screen).
6. ~~Prod data~~ done: both databases hold all 445,414 foods, prod has its
   `grafana_ro` login and `opson-prod-db` datasource, and the dashboards have a
   Database dropdown. Left: set the `CATALOG_DATABASE_URL` repo secret (prod
   pooler URL) so the monthly refresh runs; until then it skips with a notice.
7. iOS build to TestFlight, screenshots from the simulator, submit 0.1.

## Needs you

- `/design-login` in a `claude` terminal, so the `claude_design` connector
  works (the `design-sync` skill covers it until then).
- Decide the default palette: the new icon is Espresso's accent; the app
  defaults to Pine.
- Before public launch: a dietitian sets `GOAL_FLOOR_KCAL` and the maximum
  deficit; a clinician reviews the copy marked `TODO(clinician)`; check the
  helpline and provider links marked `TODO(launch)`.
