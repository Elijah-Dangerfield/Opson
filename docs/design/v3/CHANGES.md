# Design v3: what changed and what we build

`docs/design/v3/` holds the owner's Claude Design export of
`Friendly Tracking Screens.dc.html` (v3, 2026-10-26): one image per screen
(downscaled to 390 pt wide) and `manifest.json` with per-screen notes and the
global rules. **v3 supersedes the earlier handoff in `docs/design/handoff/`
wherever they disagree.** Full-resolution images are in `design-sync/design/`
in the main checkout after `./scripts/design_sync.main.kts import`.

Read the image and the manifest notes for every screen you touch. This file is
the summary and the scope call, not a substitute.

## Global rules (apply everywhere)

- Sub-screens use an **Android top app bar** (back arrow and title), not
  "‹ More" text links. This is `PageHeader` in `:libraries:ui`; it changes
  once, there.
- **Touch targets are at least 48 dp.**
- **Bars stop at 100%.** Over a target never shows overflow colour or a
  warning. No red anywhere.
- **Visibility changes live only in Modes and numbers**, behind the pause.
  Content screens never get a one-tap toggle that shows more numbers (no
  weight picker on Weight, no "Show vitamins" that applies directly, no
  "Turn on" for scores). They may link to Modes.
- Bottom nav: Today, Trends, centre +, Foods, More, with the Material pill
  indicator on the selected tab.
- Meal colours only tell meals apart.

## Changes by screen (0.1 builds these)

| Area | Change |
|---|---|
| Onboarding | Welcome shows the plan before setup (see the pricing note below). Segmented progress bar plus a back arrow. A new optional **statement screen** ("numbers have been hard") that never asks about a diagnosis and unlocks the gentler presets. The mode preview lists what you'll see and what stays out of view. **Account is the last, optional step.** "Skip and start logging" lands on Today in **Full numbers** and skips the account step; returning users sign in from a link on Welcome. |
| Today | Plain header: ‹ day label › with the date under it, calendar icon, ⋮ menu. **No + in the header.** The summary is **one card, no pager, no rings**: eaten with the range as a dashed box on a bar, then macros, "Expended so far" (0.2) and a Nutrients link. Empty meals collapse to one row offering yesterday's version. **Close the day** is a labeled card after the meals and a ⋮ item. Day menu groups: the day (Close, This day's nutrients), copying (Copy yesterday, Copy to tomorrow), notes and untracked (Add a note, **Mark as untracked**), layout (**Edit Today's cards**, which holds Hide water), Clear this day (two taps). After adding food, Today scrolls to the meal, outlines it briefly, and shows **Undo for 6 s**. Planning goes **one day ahead**. Delayed keeps counts visible and opens at the reveal time. Explorer gets a food-bridge card. Mindful has no banner. |
| Logger | Opens on **search**. The meal picker is a **dropdown in the sheet title** ("Add to Dinner ⌄"), defaulting to the time of day, with the day on the right. **No totals strip, no Favorites row.** Method chips: Photo (0.2, hidden), Describe, Yesterday's, Saved, Ate no details, Just a number. **Describe lives inside the sheet**; the separate Log screen with tabs is gone. Chips hide while typing. A no-results search gets a real empty state with two actions that carry the search term. The Amount sheet gets **"Pick and keep going"**. |
| Day close | The big-day reset card sits on the hero colour. Tomorrow shows the range, not a target. A committed state follows either button, and records nothing. Blind never shows the big-day card. |
| Trends | 1W / 1M / 3M covers **the whole page**. Today's partial bar is marked so it isn't read as a low day. Blind leads with the recap meal-dot grid; Nutrients, Expended and Weight cards are removed in Blind. A **Coast weekly-check card** replaces the old Today banner. |
| Nutrients | The **Nutrition scores page is cut.** "Show vitamins" goes through Modes. The **week average is the default when opened from Trends**. |
| Patterns (new) | Opened from Recap. Meal timing dots with the usual window tinted, meal size (removed in Blind), hunger before meals (when tags are on), and new foods. Plain facts, no advice. |
| Weight | **The visibility switch moves to Modes.** The trend is the large number and today's reading the small one. Soft shows a slope word (steady, rising slowly, falling slowly) and a line. Hidden masks the weigh-in input. No change readout. |
| Modes | A **"1 change" badge** on the preset, and changed rows marked in the matrix. **Reveal time** becomes a setting instead of a fixed 9:00 pm. Targets moves to More. In the pause sheet, **"Keep things as they are" is the primary button**. |
| Targets | Each end of the range has its own stepper (50 kcal steps) and a lock icon where it can't go lower. A **goal style picker**: Range (default), A set deficit (Full numbers and Sprint only; Today shows the result, "Goal about 1,870", never "−300"; a maximum deficit is a `TODO(dietitian)` next to the floor), and Different by day (up to four groups of days, each with its own range). |
| Foods | Create a meal and Add a food are side-by-side tiles. **Saved meals come before Your foods**, then Recent. Create a meal's "Add a food" opens the logger in pick-for-this-meal mode, so there's one search UI. Add a food: every field is optional; Nutrient focus asks only protein and fiber; Blind shows no number fields. |
| More | Three groups with icons: how the app treats you, optional extras, help and plan. Subtitles never show amounts. Fasting is shown only in Full numbers and Sprint. |
| Support | Delete everything is a **modal dialog**. The safe choice is the accent button, and Delete stays neutral. |

## Not in 0.1

- 0.2: Reminders and the reminder notification, Integrations and Health,
  Widgets and watch, Fasting, Photo estimate, barcode scan, the Expended row.
- v2: Coaching (sharing with a coach, the coach's client list, client overview
  and settings, the coach's 4-tab bar).

## Pricing on Welcome

v3 shows the plan before setup, and a Plus price. 0.1 has no in-app purchase,
and a price with no way to buy it gets rejected by App Review. So 0.1 shows the
free plan and what it includes, and lists Plus as "Coming later" with no price.
Swap in the real price when the purchase flow exists.
