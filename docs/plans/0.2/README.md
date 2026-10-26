# 0.2 build brief

0.1.0 is in App Review. 0.2 work lands on `main` while that review runs, so
`main` must stay releasable as a 0.1.x hotfix at any moment. Everything here
adds to `docs/plans/screens/BUILDER-BRIEF.md`, which still applies in full
(setup, re-plan first, SEA ViewModels, previews, tests, detekt, self-review,
report format).

## What 0.2 is

From `docs/design/v3/CHANGES.md` ("Not in 0.1") and the queued plans:

| Workstream | Design | Wave |
|---|---|---|
| Barcode scan and lookup, scan misses logged with a Grafana panel | `logger*.png`, `docs/plans/food-data-import.md` (Future work, the 0.1 item only) | 1 |
| Search quality: ranking, default servings, bounded search for Open Food Facts | `docs/HANDOFF.md`, `docs/food-data-sources.md` | 1 |
| Reminders and the reminder notification | `reminders.png`, `reminders--full.png`, `reminder-notification.png` | 1 |
| Integrations and Health, the Expended row on Today | `integrations.png`, `integrations--full.png`, `today-full.png` ("Expended so far") | 1 |
| Fasting | `fasting.png`, `more.png` | 1 |
| Widgets and watch | `widgets.png` | 2 |
| Photo estimate | `logger-photo.png` | 2 |
| Voice log | shared.md "0.1 scope gates" | 2 |

Not in 0.2 yet, don't start: label photo and community entries, the Grafana
review board (queued in `docs/plans/food-data-import.md`), coaching (v2), the
website.

The v3 PNGs in `docs/design/v3/` are the spec for look and copy. The handoff
(`docs/design/handoff/`) is the older spec; where they differ, v3 wins.

## Rules on top of the builder brief

- **Every new user-facing surface sits behind a release flag**: a
  `FlagConfigValue` (see `libraries/config/.../ConfiguredValue.kt`) with path
  `release.<feature>`, default `false`, contributed to the QA menu. Off means
  not rendered at all, exactly like 0.1. Tests and previews cover both states.
  The 0.2 release flips the flags remotely.
- **Server changes stay backward compatible.** 0.1.0 calls the prod API during
  review. Add endpoints and columns; don't change or remove what 0.1 calls.
  Migrations are additive and idempotent. Don't deploy prod.
- **Databases:** dev only (`apps/server/.env`). Never connect to prod. Watch
  disk: dev went read-only once when a load filled it.
- **Native code** (Swift in `apps/ios`, Android platform APIs) is fine where
  the platform requires it; keep the logic in shared Kotlin behind an
  interface with a fake for tests. If you add an iOS capability or
  entitlement, change the Xcode project and the `.entitlements` file and list
  it in your report, because App Store Connect may need the same capability
  registered on the bundle id.
- **No simulator or emulator driving.** The orchestrator checks screens in
  the simulator after merging. Your previews are the visual proof.
- **Shared machine:** other agents build at the same time. Run Gradle with
  `-Dorg.gradle.jvmargs=-Xmx6g --max-workers=4`, compile only your modules
  plus `:apps:compose` for both platforms, and never run a full build.
- **Copy** goes in your own `strings_<feature>.xml`. Unslopped: no em dashes,
  plain sentences, voice rules in the checklist.
- **Your plan file** is `docs/plans/screens/<feature>.md` from
  `TEMPLATE.md`. Commit it first, then build to it.
