# Food data import: work packet

For the agent doing the work. The research and licence reasoning live in
[../food-data-sources.md](../food-data-sources.md). Read it first. This file
says what to build, in what order, and what done looks like.

## Ground rules

- **Dev first, always.** Load `opson-dev`, measure size and search latency,
  then prod. Prod credentials are in the gitignored `apps/server/.env.prod`.
  Never print them.
- **Share-alike sources get their own tables.** Open Food Facts (ODbL) and
  AFCD (CC BY-SA) must never be merged into the USDA or national-table rows.
  Joining them at query time is fine. Copying rows across tables is not.
- **Every row carries its source, source version and tier** (1 reference,
  2 label, 3 licensed, 4 community), as the USDA rows do today
  (`V10__food_tiers.sql`).
- Downloads go in their own empty directory. Run Python that reads them with
  `python3 -I`.
- If a prod load or deploy is blocked by the auto-mode classifier, hand the
  user the command. Don't route around it.

## 1. Open Food Facts

- Importer modelled on `scripts/import_usda_foods.py`. Same batching, commit
  per batch, `--psql` mode, unit tests in `scripts/`.
- Source: the CSV export (`products.csv.gz`, about 1.5 GB). Stream it with the
  stdlib `csv` module. Parquet needs tools we don't have.
- Filter as in the measurement (3,303,905 usable of 4.8M):
  - energy-kcal, protein, carbs and fat per 100 g all present
  - 0 to 900 kcal, each macro 0 to 100 g, macros summing to at most 101 g
  - barcode of 8 to 14 digits, non-empty name
- Keep `countries_tags` on each row. Search ranking needs it.
- Own table, for example `off_foods`, tier 4, `verified = false`.
- Monthly **full** reload (deltas don't record deletions). Swap tables
  atomically so search never sees a half-loaded table. Extend the existing
  monthly refresh workflow, which needs the `CATALOG_DATABASE_URL` repo
  secret set (see `docs/HANDOFF.md`).
- Done when: dev has the table, size and p95 search latency are written into
  `food-data-sources.md`, then prod has it.

## 2. National tables (tier 1)

One importer per source, or one importer with a per-source adapter. Each goes
into the generic catalog with its source and version on every row.

| Source | Country | Notes |
|---|---|---|
| Canadian Nutrient File 2026 | CA | |
| BLS 4.0 | DE | |
| Livsmedelsverket | SE | |
| Ciqual 2025 | FR | |
| CoFID | GB | |
| Frida 5.5 | DK | |
| MEXT 8th ed. | JP | |
| Fineli | FI | CSV zips from fineli.fi open data |
| Matvaretabellen | NO | `/api/en/foods.json` |
| AFCD | AU | **Own table** (share-alike). Licence is a click-through on the FSANZ download page, so the user downloads the file and hands it over. |
| MyPyramid Food Raw Data | US | Optional. USDA, CC BY 4.0, about 1,000 foods with common portions, last updated 2014. Useful only for its portion sizes. Skip if FNDDS portions already cover them. |

Not yet: Korea (licence unconfirmed), and NZ, NEVO, IFCT and PhilFCT
(waiting on email replies). Don't import these until `food-data-sources.md`
says they're cleared.

Every source needs its nutrient codes mapped to our nutrient keys, and its
units normalised per 100 g. Write a test per source with a few known foods.

## 3. API

Files: `apps/server/src/main/kotlin/com/opson/server/domain/Foods.kt`,
`routes/FoodDto.kt`, `PostgresFoodCatalogRepository`, and their tests.

- **Barcode lookup:** USDA Branded first, then Open Food Facts, then the live
  barcode fallback. Global, never filtered by country.
- **Name search:** all tables, ranked. Boost rows matching the user's country
  (phone region, with a settings override), then tier, then text match.
  Rank by country, never filter by it.
- **Each result says where it came from:** the response carries the source
  and its attribution key, so the app can show "Open Food Facts" on a result.
- Measure search latency on dev with OFF loaded before shipping. Add indexes
  if p95 regresses.

## 4. App attribution

- A **Credits** screen under More → Support, listing every source with its
  required line. Exact wording per source is in `food-data-sources.md`. The
  Open Food Facts line is: "Contains data from Open Food Facts, available
  under the Open Database License", with a link.
- On a food's detail, show a small source line ("Source: Open Food Facts").
  Required for OFF and AFCD; do it for all sources for consistency.
- Add the country override to Settings if it isn't there yet.
- Run `:apps:screenshots` and update previews.

## 5. Website (nightjarlabs.llc)

The site is the Astro repo at `~/Workspace/nightjar`. Legal text is written
in this repo under `legal/` and synced there by pull request (see
`nightjar/docs/legal-sync.md`). Follow the same pattern.

- [ ] `legal/credits.md` in this repo, mirroring the in-app Credits screen,
      synced to `nightjarlabs.llc/opson/credits`. Check that the sync
      workflow picks up a third file, and extend it if not.
- [ ] A **data downloads** page for the share-alike tables (Open Food Facts
      derived, AFCD derived). ODbL needs our derived database offered under
      ODbL. Only these tables, never user data or other tables.
- [ ] Host the dumps somewhere that handles GB files: Supabase Storage or a
      GitHub release, not Firebase Hosting. Regenerate after each monthly
      reload and link the latest from the page, with the licence and date.
- [ ] Link Credits and downloads from the site footer or the Opson app page.

## 6. Before public launch

- Counsel or Open Food Facts confirms the ODbL boundary (email sent; check
  for a reply).
- Re-check the pending permission emails and update `food-data-sources.md`.

## Looked at and rejected (2026-10-26)

| Candidate | Why not |
|---|---|
| Kaggle `rajeshbosak/hello1` | 21 KB, licence "Unknown", no stated source. |
| Mendeley `cvdw2ybshp` | A calorie-labelling experiment's results, 130 subjects. Not food composition. |
| Nutrola "open dataset" | Claims 500k foods under CC BY-SA, but no GitHub repo by that name exists, so it can't be downloaded or checked. Its stated sources are ones we already import. Revisit only if a real download appears. |
| PhysioNet CGMacros | Glucose-monitor study with per-meal totals for 45 people. CC BY-NC-SA, non-commercial. |
| GWU Himmelfarb guide | A list of links. No food composition database on it. |
