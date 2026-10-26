# Where Opson stands (2026-10-26, night)

## 0.1.0

Submitted to App Review and **waiting for review**. Build 202610261458,
uploaded from the Mac. 0.1 offers Sign in with Apple or no account; Google,
email and every "Coming later" row are hidden for review. Don't approve the
prod server deploy that's waiting in GitHub while it's in review: 0.1 talks to
prod as it is.

## 0.2 on main (all behind release flags that default off)

| Feature | Flag | State |
|---|---|---|
| Barcode scan, typed codes, misses logged for Grafana | `release.barcode` | Merged. Real-camera test pending. |
| Integrations, Health, Expended, exercise in the range, days left out | `release.health` | Merged. Entitlement held back. |
| Reminders and notifications | `release.reminders` | Merged. Real-device delivery untested. |
| Fasting (Full and Sprint only, opt-in, 16 h cap) | `release.fasting` | Merged. |
| Voice log in Describe | `release.voice` | Merged. Simulator reads as no microphone; try on a phone. |
| Photo estimate | `release.photo` | Merged. Needs `ANTHROPIC_API_KEY` on the server. |
| Home screen widgets | `release.widgets` | Kotlin, More row and page merged. Native widget and receiver held on `hold/0.2-widgets-native`. |
| Search ranking, default servings, bounded Open Food Facts search | `FOOD_SEARCH_OFF_ENABLED` (server) | Merged, on dev. |

Also merged: a v3 design polish pass (amounts read "¼ cup", Blind Patterns
shows no meal gaps, Targets fits at large text). Every app flag is on in
dev's remote config so the Debug app shows them; prod config is untouched.

Each merge is checked with detekt, the module tests, the Blind leak test,
the server tests, an iOS simulator build and `scripts/ios_launch_smoke.sh`,
plus a walk through the feature in the simulator.

**What has to happen before 0.2 ships** is in
`docs/plans/0.2/release-checklist.md`.

## Design sync

All 405 app screens are published for Claude Design:
https://raw.githubusercontent.com/Elijah-Dangerfield/Opson/design-sync/app/manifest.json
Give that URL to Claude Design so its exports use the app's screen ids.

## Queued, don't start yet

Label photo and community food entries, and the Grafana review board
(`docs/plans/food-data-import.md`, "Future work"). Coaching (v2).

## At the end: the Opson website

No domain yet; build it to deploy anywhere and switch domains without code
changes. Marketing pages, privacy and terms built very similar to how Downcard
(the Cards project) does its site. A waitlist signup that lands in a database
you can contact people from, with abuse protection and a way to export it.
Link to or reuse the published `legal/` pages; the waitlist needs its own
privacy wording.

## Needs you

- GitHub › Settings › Actions › General: allow GitHub Actions to create pull
  requests, or release-please keeps failing.
- Photo estimate will need an `ANTHROPIC_API_KEY` on the server.
- `/design-login` in a `claude` terminal so the Claude Design connector works.
- Set the `CATALOG_DATABASE_URL` repo secret (prod pooler URL) for the monthly
  USDA refresh.
- Default palette: the icon is Espresso's accent; the app defaults to Pine.
- Before public launch: a dietitian sets `GOAL_FLOOR_KCAL` and the maximum
  deficit; a clinician reviews everything marked `TODO(clinician)`; check the
  helpline and provider links marked `TODO(launch)`.
