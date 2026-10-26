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

## Paused, committed on branches (resume these)

Each is a git worktree under `.claude/worktrees/` with a `wip`/`chore`
checkpoint commit as its tip. To resume, start a session and say "resume the
paused screen reworks from docs/HANDOFF.md".

| Work | Branch (also on GitHub) | State |
|---|---|---|
| Today, its sheets and Day close, to v3 | `worktree-agent-a773f0e123d7f62d9` (`wip/today-v3`) | **Done and green** (73 tests, both platforms). Not built: Mark as untracked, the Explorer food bridge, Blind's "How did today feel?" chips, and the 6 s Undo, which the Logger's `RecentLogEvents` should feed. Watch for merge clashes on `CreateMealRoute` in `:features:foods` and `TodayLayout.Journal` in nutrition. |
| Logger to v3 | `worktree-agent-a569e21155a608e60` (`wip/logger-v3`) | Main code done and compiles on Android: search-first sheet, meal dropdown, Describe and Saved inside the sheet (`LogRoute` kept as an alias), Pick and keep going, How it felt, `RecentLogEvents`/`LoggedToMeal`. **Left:** rewrite `AmountTest`, `LoggerStateBuilderTest` and `LoggerViewModelTest` (they don't compile yet), add `HowItFeltTest`, run tests, iOS compile, detekt, screenshots vs v3. |
| More, Modes, Targets, Support to v3 | `worktree-agent-ae25ce58b390844eb` (`wip/settings-v3`) | Green commits: the v3 plan, the shared ui changes (`PageHeader` is a top app bar with the same signature, 48 dp `minTouchTarget`, bars clamp at 100%), and `revealAt` plus `TargetStyle`/`rangeFor` in nutrition. **The tip WIP commit does not compile**: More, Modes and the pause sheet rewritten, Targets state builder written. **Left:** Targets screen and VM, goal-style sheet and route, the Support delete dialog, Water and Look-and-feel previews (`LightAndDark` was removed), fixtures, leak surfaces, entry point, tests, detekt, screenshots. |

Merge order when they finish: More/Modes/Targets first (it owns the shared
changes), then Logger, then Today. Each needs a rebase on `main` and the
combined checks (both app compiles, every feature's tests, detekt) before it
merges.

## Not started yet

1. Onboarding to v3 (statement screen, account last, Skip lands on Full).
2. Trends to v3, plus the new Patterns screen.
3. Foods to v3 (needs the Logger's pick-for-this-meal mode).
4. `BlindModeLeakTest` across every surface (`docs/plans/screens/blind-leak-test.md`).
5. Sweep screen previews that use `PreviewLightDark` (one theme per screen).
6. Prod data: load the food catalog into `opson-prod` (generic seed, then
   `scripts/import_usda_foods.py --branded --psql`), set the `grafana_ro`
   password on prod, add an `opson-prod-db` datasource and a dev/prod switch on
   the dashboards. Dev's branded load was running when work paused; it is
   idempotent, so just run it again if dev search lacks branded items.
7. iOS build to TestFlight, screenshots from the simulator, submit 0.1.

## Needs you

- `/design-login` in a `claude` terminal, so the `claude_design` connector
  works (the `design-sync` skill covers it until then).
- Decide the default palette: the new icon is Espresso's accent; the app
  defaults to Pine.
- Before public launch: a dietitian sets `GOAL_FLOOR_KCAL` and the maximum
  deficit; a clinician reviews the copy marked `TODO(clinician)`; check the
  helpline and provider links marked `TODO(launch)`.
