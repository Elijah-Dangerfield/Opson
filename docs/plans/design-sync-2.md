# Design sync 2

The owner's latest Claude Design export (77 screens, named by app screen id)
is the spec now. It supersedes `docs/design/v3/` wherever they differ.

## Inputs (in the main checkout, read from your worktree by absolute path)

- Side by sides, app left and design right:
  `/Users/elijahdangerfield/Workspace/Opson/design-sync/compare/<id>.png`
- Full-size design PNGs: `/Users/elijahdangerfield/Workspace/Opson/design-sync/design/<id>.png`
- Full-size app renders: `/Users/elijahdangerfield/Workspace/Opson/design-sync/app/<id>.png`
- **The designer's notes are the behaviour spec:**
  `/Users/elijahdangerfield/Desktop/Opson Assets/design-sync-2/manifest.json`
  (`notes` per screen; "Section behavior" paragraphs describe interaction).
- Pairing table: `/Users/elijahdangerfield/Workspace/Opson/design-sync/compare/README.md`.

## Rules

- Follow `docs/plans/0.2/README.md` (including "Learned in wave 1") and
  `docs/plans/screens/BUILDER-BRIEF.md`. Re-plan in your area's plan file
  first, then build.
- Design wins on layout, type, spacing, colour, copy and behaviour, through
  design-system tokens and components. If the design needs a token the system
  lacks, add it to `:libraries:ui` with a preview.
- **Deliberate differences stay:** no Plus price or "Coming later" rows (App
  Review), Google and email sign-in hidden (`SignInMethods`), iPhone only,
  "to" in ranges, nothing advertised that isn't built. Note any other
  difference you keep on purpose, with the reason, in your plan file.
- **Out of scope:** coaching (v2 screens), "Add a package" label scan (queued
  in `docs/plans/food-data-import.md`), the watch face.
- **New features go behind a release flag** (default off, registered in the
  config registry and drift test, nothing at launch while off). Refinements to
  existing screens don't need a flag.
- Every number keeps going through the visibility policy; new surfaces join the
  leak test. Previews for every state the design shows, one theme per screen.
- Rename or add previews so their screen ids match the design ids, so the next
  sync pairs automatically.
- Run `./scripts/design_sync.main.kts export` in your worktree at the end and
  look at your screens next to the design PNGs yourself.
- Commits: small, conventional, each ending
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Don't push.
- If something can't be done well, leave a `TODO(design-sync-2): …` in code
  and list it in your report rather than stalling.
