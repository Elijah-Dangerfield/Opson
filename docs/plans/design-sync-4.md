# Design sync 4 (Round 4, 2026-10-27)

The owner's Claude Design export is at
`/Users/elijahdangerfield/Desktop/Opson Assets/design-sync-4`:

- `screens/*.png`: 104 screens at 3x.
- `screens/manifest.json`: per-screen `notes`, plus `globalNotes` and
  `behaviorBySection`. The behaviour notes are the spec.
- `README.md`: the handoff, ending in "Round 4 changes".
- `PROJECT_RULES.md`: copy rules and the owner's product direction.

Round 4 (designRef 13.x) overrides anything older where they disagree.

## What actually changed

The 76 screens outside 13.x are pixel-identical to design sync 2, which is
built (`docs/plans/design-sync-2.md`). The change is the 28 Round 4 screens,
plus two global changes:

- **One colour scheme, Slate**, light and dark. Appearance is System, Light or
  Dark. The palette picker goes. The tokens are in `colors-slate.png` (13.0)
  and the README's Round 4 section.
- **Four modes**, Full numbers, Nutrients first, Later and No numbers, plus an
  "End after a set time" switch. The old presets map onto them:
  - Sprint becomes End after a set time.
  - Coast becomes Later, weekly.
  - Blind, Rhythm, Mindful and Explorer become No numbers, with optional check-ins.

## Areas

Each area is one agent, in its own worktree. The areas are split so they don't
edit the same files.

| Area | Screens (designRef) | Owns |
|---|---|---|
| **Theme** | colors-slate 13.0, More 13.1 | `libraries/ui` colours and theme, Look and feel / Appearance in settings, the More list |
| **Modes** | modes-v4 13.5a, today-nutrients-first 13.5b, today-later 13.5c, today-no-numbers 13.5d, food-detail-no-numbers 13.5e | `libraries/nutrition` presets, `ModeTiming`, settings Modes, Today's mode layouts, onboarding's mode recommendation, migrating stored presets, leak test |
| **Goals and energy** | targets-goal-style-v4 13.2e, resting-energy 13.2d, energy-today 13.2c | the separate goal-styles session: One number, Set surplus, Burned = resting + activity, the resting energy source, Energy today |
| **Today actions** | today-calendar 13.3a, today-select-v4 13.3b, today-drag-move 13.3c, food-actions 13.3d, edit-a-copy 13.3e | Today's header (the title opens the calendar; no calendar button), select mode's bottom bar, dragging between meals, the food ⋮ sheet, Edit a copy (a custom food "(mine)") |
| **Summary card** | today-summary-1d/1c/1a/1b 13.1, today-deficit-goal 13.2a, today-surplus-goal 13.2b, today-screen-settings 13.6a, today-screen-main-number 13.6b, today-rings 13.6c | the card's four presets (In and out by default), the main number's toggle remembered per goal style, More › Today screen, eaten ± from how each food was logged. Starts after Goals and energy lands. |
| **Adding** | add-sheet-half 13.4a, add-sheet-expanded 13.4b, search-full 13.4c, food-detail-v4 13.4d | the + half sheet with a 12-action grid, full-screen search (+ adds at the last-used amount, with Undo; no pick list), the food detail with a typed amount (typing switches Estimate to Weighed). Starts after the voice describe work lands. |

## Decisions already made by the owner

- The deficit stays locked until a dietitian sets `MAX_DEFICIT_KCAL`
  (2026-10-27). The design's working 500 kcal stepper waits for that number.
  The surplus gets the same kind of cap, set to `TODO(clinician)`.
- `GOAL_FLOOR_KCAL` stays unset; the existing floor rule holds.
- Nothing new is advertised that isn't built. The website copy follows the app.

## Rules for every area

Follow `docs/plans/0.2/README.md` (including "Learned in wave 1"),
`docs/review/PRACTICES.md`, `docs/plans/design-sync-2.md` and
`PROJECT_RULES.md` in the export (no copy that narrates the design).

- **Visibility:** every number goes through `VisibilityPolicy`, and new
  surfaces join the leak test.
- **Settings:** settings that sync are new top-level keys, because the server
  merges settings shallowly with `||`. Prove that a 0.1 save keeps them, in
  `apps/integration`.
- **Previews:** name new previews `<file>__<preview>` so the next sync pairs
  them. Use the design id when you can (for example
  `TodaySummaryInOutPreview`), and render with
  `./scripts/design_sync.main.kts export` to check against the PNGs.
- **Release flags:** refinements to existing screens need no flag. New
  features go behind one, off by default, with nothing at launch.
