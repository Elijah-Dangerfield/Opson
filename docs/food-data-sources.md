# Food data sources: final recommendation

Researched 2026-10-26. Licences were read on the official pages linked below,
except where a row says otherwise.

## The recommendation

1. **Import Open Food Facts in full, kept in its own table.** It is the only
   large non-US source of packaged, barcoded products, and it covers almost
   every market. Keep it separate from the USDA rows so its share-alike
   licence can't reach the rest of the catalog.
2. **Add the open national tables for generic foods.** They cover Canada, the
   UK, France, Germany, Sweden, Denmark and Japan, and each allows commercial
   use and transformation with attribution.
3. **Hold the sources whose terms forbid changing the data, or force
   share-alike on what we build.**
4. **Skip the paid APIs as data sources.** Their caching limits don't fit a
   catalog we own. Keep Nutritionix as the optional paid barcode fallback
   that's already wired up.
5. **Add a Credits screen** (More → Support) listing every source with its
   required attribution line.

## Open Food Facts, measured

Downloaded the full CSV export on 2026-10-26 (1.5 GB compressed).

- **4,837,151 products in total. 3,303,905 usable:** energy, protein, carbs
  and fat per 100 g, values within physical limits (macros 0 to 100 g, energy
  at most 900 kcal, macros summing to at most 101 g), a valid 8 to 14 digit
  barcode, and a name.
- **Usable products by country:**

| Country | Products | Country | Products |
|---|---:|---|---:|
| France | 882,557 | Belgium | 80,633 |
| United States | 774,242 | Australia | 65,666 |
| Spain | 245,938 | Netherlands | 59,199 |
| Germany | 235,064 | Ireland | 59,185 |
| Italy | 216,192 | Japan | 40,771 |
| United Kingdom | 150,101 | Brazil | 25,785 |
| Canada | 111,044 | Sweden | 24,755 |
| Switzerland | 86,271 | Mexico | 12,446 |

  A product can be tagged with several countries, so the column doesn't sum
  to the total.
- **US overlap:** keep USDA Branded (manufacturer labels, tier 2) as the
  answer for a barcode both sources have. Open Food Facts fills in the rest
  as tier 4.
- **Size:** estimated at 1 to 1.5 GB in Postgres with indexes. Measured at
  2.1 GB (below). Pro includes 8 GB per project.
- **Refresh:** a full monthly reload, because the daily deltas don't record
  deletions. Keep the live barcode lookup as a fallback for products newer
  than the last dump.

### Measured, 2026-10-26: dev blocked, so on a dev-sized Postgres

The load into `opson-dev` stopped after 418,000 rows: Supabase switched the
database to read-only when it reached 679 MB (391 MB before the load). That
fits the Free plan's 500 MB database cap, or a disk far smaller than the
table. The staging table was dropped and dev was writable again within
minutes. **Dev needs a bigger plan or disk before it can hold Open Food
Facts.** So the numbers below come from a local Postgres 17 with dev's
settings (1 GB of memory, `shared_buffers` 256 MB, `work_mem` 3.5 MB,
`random_page_cost` 1.1, JIT off, one parallel worker) on a laptop SSD.
Sizes carry over exactly. Latency on Supabase's disks will be no better.

- **Rows:** 3,290,890 of the export's 4,837,151. 3,303,905 pass the filter;
  12,113 of those have a placeholder barcode (mostly zeros) and 902 share a
  barcode with a newer product.
- **Size:** 2,123 MB. Table 1,583 MB (about 480 bytes a row), indexes 540 MB:
  trigram on the name 190, primary key 133, barcode 131, full text 81,
  countries 6. A monthly reload holds two copies for a while, and Supabase
  lets WAL grow to 4 GB (`max_wal_size`), so a reload wants about 6 GB free
  on top of the live table. Size the disk before loading: a load writes
  faster than disk autoscaling reacts, and at 95% Supabase goes read-only.
- **Load time:** 229 s locally, of which about 20 s is the index builds and
  the rest reading the 1.5 GB export. Over dev's session pooler rows went in at about
  4,600 a second, so a full load there takes about 12 minutes plus the index
  builds.
- **Search:** 290 queries (common foods, brands, misspellings, 1 to 3 letter
  prefixes, French, German, Spanish, Italian, Dutch, Swedish and Japanese
  names) through the server's search statement aimed at `off_foods`, ranked
  with the caller's country first, LIMIT 25. Server time from
  `EXPLAIN ANALYZE`, after a warm-up pass. `scripts/bench_off_search.py` has
  the list and the statement.

| Country preferred | p50 | p95 | p99 | Slowest |
|---|---:|---:|---:|---:|
| United States | 66 ms | 1,251 ms | 5,705 ms | 12.1 s (`b`) |
| United States, queries of 3+ characters | 61 ms | 543 ms | 2,815 ms | |
| France | 59 ms | 1,306 ms | 6,655 ms | 11.5 s |

**Too slow for the current search statement.** It fetches and scores every
row a prefix matches before it sorts, and on 3.3 million rows a common word
matches tens of thousands: `milk` reaches 44,000 rows on 35,000 heap pages
and takes 650 to 870 ms, where the index scans themselves take 20 ms. The slow cases:

- **Short prefixes** (`b`, `ch`, `co`, `sa`, `mi`): 1 to 12 s. One or two
  letters match hundreds of thousands of names. `m&m` is the same problem,
  two one-letter words.
- **Common words** (`milk`, `cheese`, `chicken`, `chocolate`, `butter`): 0.6
  to 1.2 s, and `chocolat` and `mars` 1.6 to 2.9 s because they prefix-match
  `chocolate` and `marshmallow` too.
- **Misspellings with no direct match** (`cheeerios`, `parmesan chese`,
  `bananna`): 0.4 to 1.8 s in the trigram fallback, which rechecks every
  name sharing a trigram.

Section 3 has to bound the candidates before scoring rather than add
indexes: a minimum query length for prefix matching, a cap on rows per match
tier taken in index order, or a smaller hot subset ranked by `scans` (Open
Food Facts' scan count, kept on every row for this). Re-run the bench after
that change, and on dev once it has room.

### Measured again, 2026-10-26: bounded search (0.2)

Same local Postgres 17 with dev's current settings (`shared_buffers` 320 MB,
`work_mem` 3.5 MB, `effective_cache_size` 960 MB, `random_page_cost` 1.1, JIT
off, one parallel worker), in Docker with 1 GB of memory and 2 CPUs, on a
laptop shared with other builds (load average 7 to 30 during the runs, so
treat single slow queries as noise). Same 290 queries, LIMIT 25.

What changed (`OpenFoodFactsSearch` in the server, mirrored by
`scripts/bench_off_search.py`):

- **The reload writes rows most-scanned first**, so the table's physical
  order is popularity, and a capped bitmap scan returns the most-scanned
  matches first. The load takes 362 s instead of 229 s: the export is
  spilled to temp files by scan count before anything is sent.
- **Queries under 3 letters and digits skip Open Food Facts** (16 of the 290;
  the catalog still answers them).
- **Candidates are capped before scoring**: exact names up to 100 (a btree
  on `lower(name)`), word-prefix matches up to 300, both again among products
  sold in the caller's country (a GIN on `countries_tags, search`), and a
  typo fallback on trigram word similarity up to 50, only when the rest find
  fewer than 25. At most about 850 rows are scored, whatever the query.
- **Two indexes added** (V15): 200 MB, so `off_foods` is 2.3 GB with indexes.

| | p50 | p95 | p99 | Slowest |
|---|---:|---:|---:|---:|
| Before: unbounded, United States | 101 ms | 2,180 ms | 7,906 ms | 13.2 s (`m&m`) |
| Bounded, United States | 25 ms | 56 ms | 87 ms | 241 ms |
| Bounded, France | 23 ms | 60 ms | 88 ms | 115 ms |

The "before" row is this setup's own measurement of the old statement; it
reads slower than the earlier table above because this container is
smaller.

**Quality.** `--compare` runs both statements with the same ranking and
counts how much of the unbounded top 10 the bounded one keeps: 88.5% for the
United States, 91.7% for France, with the same first result 89.4% and 95.3%
of the time. Where they differ the bounded result is usually the more popular
product: the unbounded statement can reach products nobody scanned, and the
caps keep the most-scanned. The typo fallback is better bounded than
unbounded (`cheeerios` now finds Cheerios, not Cheerwine), and the lowest
overlaps are prefixes still being typed (`cho`, `che`, `chi`).

**In search.** Off by default: `FOOD_SEARCH_OFF_ENABLED=true` adds these
rows to `/v1/foods/search` with the same response shape (`source: "off"`,
tier 4, not verified), after every generic food that matched directly and
after Branded rows of the same match, products sold in the caller's country
(`X-Country-Code`) first. `/v1/foods/{id}` answers their ids when it is on.
Describe never uses them.

**Not loaded on dev yet.** A first load writes the 2.3 GB table plus about
as much WAL again (dev's `max_wal_size` is 4 GB) on top of the 1.4 GB dev
already uses (392 MB of data, 960 MB of WAL), which could pass 60% of the
12 GB disk at its peak, and the disk itself can't be read from SQL. Load it
with the disk graph open in the Supabase dashboard, and stop if it passes
60%.

### Measured on dev, 2026-10-27: where a typed search spent its time

Dev now has the full load (3.29M rows) and `FOOD_SEARCH_OFF_ENABLED` on, on
Supabase's micro instance (256 MB `shared_buffers`, so `off_foods` at 2.3 GB
is mostly read from disk). `EXPLAIN (ANALYZE, BUFFERS)` of the statements
search runs, read-only:

- **The catalog statement scored every Branded match.** "chicken" read and
  ranked 14,630 rows (0.9 s), "cheese" 32,491 (2.2 s). Most of them could
  never reach the page: generic matches rank first, and a Branded row only
  jumps them when the query names its brand. Now only rows whose brand
  carries a query word are read when generic matches fill the page: 0.12 s
  and 0.21 s, same rows in the same order (14 queries, limits 25 and 50).
- **Open Food Facts went back to the table by id for each of up to 850
  candidates**, a random read each when cold. Carrying ctids halves its cold
  reads: about 370 ms to 190 ms on fresh words.
- **The two statements ran one after the other**; they now run at once.
- **The phone retried a 503 three times**, up to about 23 s.

Search over HTTP, in process on a laptop against dev (so each database
round trip is about 18 ms instead of Fly's 1 ms), warm, limit 25:

| Query | Before | After | Repeated within 5 min |
|---|---:|---:|---:|
| chicken | 1.3 to 1.6 s | 0.29 s | 2 ms |
| cheese | 2.4 to 3.1 s | 0.36 to 0.45 s | 2 ms |
| chic | 1.4 to 1.8 s | 0.37 to 0.54 s | 2 ms |
| chi | 0.50 to 0.62 s | 0.26 to 0.35 s | 1 ms |
| greek yogurt | 0.33 to 0.40 s | 0.27 s | 3 ms |
| nutella | 0.27 to 0.37 s | 0.25 s | 3 ms |
| egg | 0.15 to 0.24 s | 0.12 s | 2 ms |
| coke | 0.12 to 0.20 s | 0.08 s | 2 ms |
| c | 0.64 to 2.0 s | 0.55 to 1.1 s | 2 ms |

Still slowest: one- and two-letter queries ("c" ranks 7,800 generic foods),
which the server and the phone now keep for five minutes, and cold
Open Food Facts pages. A bigger Supabase instance would hold `off_foods` in
memory; that is the next lever if cold searches still feel slow.

## Search ranking and default servings (0.2)

**Ranking.** A relevance suite of 78 queries (staples, dishes, brands,
misspellings, prefixes typed so far) runs as `SearchRelevanceTest` against the
generic seed plus a committed Branded fixture. Before the change 67 of 78
held their expected top 3 (85.9%); after, 78 of 78. A held-out set of 44
queries written before running it, never used to tune: 40 of 44 before, 42
of 44 after. On a full local copy of dev's catalog (445,414 foods) search got
faster too, because Branded rows are only read when they can reach the
results: p50 52 to 37 ms, p95 281 to 269 ms, slowest 963 to 385 ms.

**Plurals and plain forms (2026-10-30).** `strawberry` showed "Strawberry
milk" first because the plural rule only added an s, so "Strawberries, raw"
never matched as the food. English plural rules, the plain form among head
matches and a graded evaluation (`SearchEvaluationTest`, 58 queries): MRR 0.914
to 0.991, NDCG@10 0.822 to 0.890, relevance suite 78 to 83 of 83. Details in
`docs/plans/search-ranking.md`.

**Default servings.** The app logs a food's first portion. 1,318 of the
13,324 generic foods had a crumb or a bulk measure first ("1 berry", "1 fl
oz" of coffee, "1 cup" of almonds at 828 kcal); they now start with a typical
serving. The seed is regenerated and dev was backfilled (1,318 rows; a second
run changes nothing).

### Licence: ODbL, kept contained

- **Required in the app:** "Contains data from Open Food Facts, available
  under the Open Database License", with a link, on a credits screen and
  wherever its foods appear. In the Logger (`release.searchOptions`) each of
  its rows carries an "Open Food Facts" badge and that sentence sits under
  any results that include one; the Foods tab has the credit line. Still
  missing: the Credits screen with the link.
- **Keep it in its own table.** A database built from Open Food Facts is a
  Derivative Database. If it is "publicly used", it has to be offered under
  ODbL. Mixing Open Food Facts rows into the USDA table risks that applying
  to the whole merged catalog.
- **Don't ship a bulk copy inside the app.** Serving search results from our
  server is the lower-risk reading. A bundled database is likelier to count
  as public conveyance.
- **Unverified:** Open Food Facts' own reuse FAQ wouldn't load. The boundary
  above comes from the ODbL text
  (https://opendatacommons.org/licenses/odbl/1-0/), not a ruling from them.
  Have counsel or Open Food Facts confirm before public launch.

## Zero-calorie labels (2026-10-29)

**What happened.** Scanning Monster Energy Zero Ultra (UPC 070847012474)
showed 0 kcal where the can says 10. Traced on the real data:

- **USDA Branded answered**, as tier 2 always beats Open Food Facts. Its
  record (FoodData Central 2680587) is the label's "Calories 0" for an 8 fl oz
  (240 ml) serving, which a US label may print for anything under 5 kcal,
  stored as 0 kcal per 100 ml beside 0.83 g of carbs.
- **Open Food Facts had it right**: 2 kcal per 100 ml (`energy-kcal_100g`),
  a 473 ml can, so about 9.5 kcal. Its parsing was never the problem.
- **The serving made it worse.** Branded keeps an ml serving without grams,
  and the app dropped it, so the only amount on offer was 100 g. Even a right
  per-100 value would have read 2.

**The fix**, on the server, for rows already loaded (no reimport):

- An energy guard (`FoodEnergy`, apps/server/README.md) checks every served
  food's calories against its macros at a serving. A 0 the macros contradict
  is served as the macros' energy held under 5 kcal a serving: the Monster can
  reads 10. No energy at all is worked out from the macros or stays unknown,
  never 0. Calories with no macros (spirits) are flagged, not changed.
- A scan prefers a lower tier whose energy holds up over a Branded row the
  guard caught out.
- An ml serving on a food whose numbers are per 100 ml is served with its ml
  as the amount, so "1 can" can be logged.
- Each caught food is filed once for review, where a person can correct it
  for good (`food_corrections`).
- The Open Food Facts importer now converts kJ when a label gives no kcal,
  instead of dropping the product, and the live lookup reads
  `energy-kj_100g` too.

**How common.** In a sample of 658 Branded drinks and syrups from FoodData
Central's search (zero-sugar energy drinks and sodas, sugar-free syrups), 284
are 0 kcal and 44 of those have macros that give more than 5 kcal a serving.
Every Monster Ultra flavour is one (0 kcal, 0.83 g carbs, 8 fl oz). Most of
the rest are sweetened with erythritol or other sugar alcohols (Reed's Zero
Sugar sodas list 5 g of carbs per 100 ml), whose carbs give almost no energy,
which is why the estimate is capped at 5 kcal a serving rather than taken
from 4/4/9 alone. Count the whole catalog on a database with the Branded
load:

```sql
SELECT count(*) FROM foods
WHERE kcal_100g = 0 AND 4 * protein_100g + 4 * carbs_100g + 9 * fat_100g > 5;
```

That is per 100 g, so it undercounts drinks with a larger serving. The
energy check below counts them at their serving.

## Energy math: does every food add up (2026-10-30)

Every row in `foods` and `off_foods` now says whether its calories match its
macros (V33): `energy_consistent` (true, false, or null when it can't be
judged), `energy_gap_kcal` (stated minus the macros' energy, per 100 g or ml)
and `energy_checked_at`. It is information only. Nothing is hidden, ranked or
changed by it; the served energy guard above is separate and unchanged.

**The rule** (`food_energy_check()` in V33):

1. **Energy from the macros**, at Atwater's general factors: protein 4,
   carbs 4, fat 9, alcohol 7 kcal a gram. Labels count carbohydrate two ways,
   so three sums are tried and the one nearest the stated energy is used:
   all carbs at 4; US "total carbohydrate" with fiber at 2 and sugar
   alcohols at 2.4 inside it; EU carbohydrate with polyols at 2.4 inside it
   and fiber outside at 2. Without fiber or polyol figures the three are the
   same number. Fiber is `fiber_100g`; polyols and alcohol come from
   `micros.sugar_alcohols_g` and `micros.alcohol_g` when a source has them.
2. **At a serving**, when the first portion's size is known (grams, or the
   ml its label names), because that is where a label rounds; per 100
   otherwise.
3. **Tolerance**: 15 kcal or 12% of the stated energy, whichever is larger.
   That is the label form's tolerance (`LabelChecks`, and `MacroEnergyCheck`
   in the app): energy is printed to the nearest 5 or 10 kcal and each macro
   to the gram, which alone can move a serving by about 13 kcal, and some
   foods use specific factors (nuts, whole grains). A serving stated under
   5 kcal is a label's "0", which the FDA allows only below 5, so there the
   macros must come in within 5 kcal too. This is the Monster Energy case.
4. **Null** when energy or protein, carbs or fat is unknown, or when protein,
   carbs and fat are all 0 with no alcohol figure but the energy isn't
   (spirits look like this, and so do errors; neither can be told apart).
5. **As served**: a reviewer's live correction (V29) is laid over the row
   first, so correcting a food clears its flag and reverting brings it back.

**Kept current** by triggers: a new row, a change to a row's numbers or
portions, and a correction re-judge the food, so the importers, the barcode
cache and approvals need no code of their own. The Open Food Facts reload
copies the trigger onto its staging table, so each reload arrives judged.
Rows loaded before V33 are judged by `scripts/check_food_energy.py` (command
in apps/server/README.md).

**What it finds.** On the committed USDA generic seed (13,324 foods): 13,066
add up, 200 don't and 58 can't be judged. Nearly all of the 200 are alcoholic
drinks and cocktails, because no importer stores alcohol yet; the rest are
foods whose specific factors sit far from 4/4/9 (cocoa powder, oat bran,
lecithin). Storing alcohol (USDA nutrient 221, NEVO `ALC`, Open Food Facts
`alcohol_100g`) as `micros.alcohol_g` would clear most of them. Branded and
Open Food Facts have not been measured; the Food catalog board shows them
once the script has run.

## Generic national tables: import

| Source | Region | Foods | Licence | Attribution |
|---|---|---:|---|---|
| Canadian Nutrient File 2026 | Canada | 5,993 | Open Government Licence – Canada 2.0 ([dataset](https://open.canada.ca/data/en/dataset/1b6139bd-ed7e-4043-bc28-ff00e10f3109)) | "Contains information licensed under the Open Government Licence – Canada." |
| BLS 4.0 | Germany | not counted | CC BY 4.0 ([download](https://blsdb.de/download)) | Max Rubner-Institut (2025), BLS 4.0, DOI 10.25826/Data20251217-134202-0 |
| Livsmedelsverket | Sweden | ~2,500 | CC BY 4.0 ([open data](https://www.livsmedelsverket.se/en/about-us/open-data/food-composition-data/)) | "Livsmedelsverkets Livsmedelsdatabas version YYYY-MM-DD" |
| Ciqual 2025 | France | 3,484 | Etalab Open Licence 2.0 ([Zenodo](https://zenodo.org/records/17550133)) | Cite source and version date |
| CoFID | UK | not stated | Open Government Licence v3.0 ([gov.uk](https://www.gov.uk/government/publications/composition-of-foods-integrated-dataset-cofid)) | OGL attribution statement |
| Frida 5.5 | Denmark | ~1,370 | CC BY 4.0 (from search summaries; the Frida site didn't load) | DTU Food, Frida 5.5 |
| MEXT 8th edition | Japan | ~2,500 | No standard licence; apps may use it with a citation (from a third-party summary of MEXT's Q&A) | Cite MEXT |
| Fineli | Finland | 4,232 | CC BY 4.0, verified on [fineli.fi Open Data](https://fineli.fi/fineli/en/avoin-data) | "Finnish Institute for Health and Welfare, Fineli" |
| Matvaretabellen | Norway | ~2,000 | Open API; the [API page](https://www.matvaretabellen.no/api/) invites caching and asks only that Matvaretabellen be cited as the source | "Matvaretabellen" |
| Korea integrated food nutrient data | South Korea | large (ingredients, dishes, processed foods, chain-restaurant menus) | Government standard dataset on [data.go.kr](https://www.data.go.kr/data/15100064/standard.do); the page features a calorie app built on it, but shows no licence field. **Confirm the licence before importing.** | Ministry of Food and Drug Safety, RDA, NIFS |

All of these go in as tier 1 (reference), each with its source and version on
every row. They cover about 20,000 foods in total. This is the same shape of
importer as the USDA one.

## NEVO (Netherlands): imported

`scripts/import_nevo_foods.py` loads NEVO-online 2025/9.0 (2,328 foods in
the release) into `foods` as source `nevo`, tier 1, under RIVM's written
permission (Replies, below). Not loaded on dev or prod yet; the release
checklist has the step.

- **Getting the file.** RIVM hands out the download link only after its
  conditions of use are accepted on the
  [request form](https://www.rivm.nl/en/dutch-food-composition-database/use-of-nevo-online/request-dataset),
  so the importer never fetches it on its own. Download it there, then pass
  the file (or the zip it comes in) with `--file`, or the link with `--url`.
  Other projects that import it describe `NEVO2025_v9.0.csv`, pipe-separated
  with decimal commas, which is what the importer reads; the real file
  hasn't been through it yet.
- **The command.** Counts only, then a load:

  ```bash
  python3 -I scripts/import_nevo_foods.py --file ~/Downloads/NEVO2025_v9.0.csv
  CATALOG_DATABASE_URL=... python3 -I scripts/import_nevo_foods.py --file ~/Downloads/NEVO2025_v9.0.csv --psql
  ```

  It needs V20 on the database and checks for it first. Re-running changes
  no row.
- **Amounts unchanged.** Every value is copied as published; only decimal
  commas become points. Each nutrient column is matched on its code and its
  unit, and a unit other than ours stops the run.
- **Left out:** the "Foods for special nutritional use" group (infant and
  toddler formula, per 100 ml), and any other row not given per 100 g.
- **Names:** the English name, with NEVO's `w`, `wo` and `av` written out
  ("Apple with skin average"). `foods` has nowhere to keep an alias, so the
  Dutch name isn't stored, and a Dutch query won't find these foods until
  it does.
- **Citation:** the search row's badge ("NEVO 2025/9.0, RIVM"), the amount
  sheet's source line and the Foods tab's credit carry it. The importer
  refuses any release other than the one the app cites. Search only returns
  NEVO foods to an app that sends `credits=nevo`, so 0.1, which shows no
  citation, never sees them, and Describe never picks them.

| Our key | NEVO | Note |
|---|---|---|
| kcal | ENERCC (kcal) | |
| protein | PROT (g) | |
| carbs | CHO (g) | Available carbohydrate: fibre is not included, unlike USDA's |
| fat | FAT (g) | |
| fiber | FIBT (g) | |
| sugars_g | SUGAR (g) | |
| saturated_fat_g | FASAT (g) | |
| trans_fat_g | FATRS (g) | |
| cholesterol_mg | CHORL (mg) | |
| sodium_mg, potassium_mg, calcium_mg, iron_mg, magnesium_mg, phosphorus_mg, zinc_mg, copper_mg | NA, K, CA, FE, MG, P, ZN, CU (mg) | |
| selenium_ug | SE (µg) | |
| vitamin_a_rae_ug | VITA_RAE (µg) | |
| vitamin_c_mg | VITC (mg) | |
| vitamin_d_ug | VITD (µg) | |
| vitamin_e_mg | TOCPHA (mg) | Alpha-tocopherol, as USDA; not VITE |
| vitamin_k_ug | VITK1 (µg) | Phylloquinone, as USDA; not VITK (K1 + K2) |
| thiamin_mg, riboflavin_mg, niacin_mg, vitamin_b6_mg | THIA, RIBF, NIA, VITB6 (mg) | Niacin preformed, not NIAEQ |
| vitamin_b12_ug | VITB12 (µg) | |
| folate_dfe_ug | FOLFD (µg) | Only when FOLAC (folic acid) is 0, where DFE is the food folate exactly. Otherwise left out: DFE would have to be calculated |
| added_sugars_g, manganese_mg, choline_mg | none | NEVO doesn't give them |

NEVO has no household portions, so the app weighs these foods in grams.

## Hold

| Source | Why |
|---|---|
| New Zealand FOODfiles | Its terms say "must not modify any part", and our normalisation is a modification. |
| Spain BEDCA, FAO regional tables, Italy CREA, China | No open licence found or not checked. FAO items are often CC BY-NC-SA, which rules out commercial use. |
| India IFCT, Philippines PhilFCT | Copyrighted with no open licence. PhilFCT isn't downloadable, and FNRI declined a raw-data request. |

## Added in the final review (2026-10-26)

- **Korea** is the strongest addition. Besides generic foods it carries menu
  items from Korean chains (Domino's, Pizza Hut, Papa John's and others),
  which nothing else we have covers.
- **Restaurant menus are the biggest remaining gap** for the US and Europe.
  NYC MenuStat (96 US chains, about 65,000 items) is open but stopped
  updating in 2019. The better answer is hand-curating the top chains from
  their published nutrition pages, later.
- **Requested permission** on 2026-10-26. Replies are below; NZ FOODfiles,
  India IFCT and the Open Food Facts share-alike question haven't answered.
- **Ask next:** Brazil TBCA (commercial use needs the coordinators' OK),
  for Latin America.
- **Dropped:** Switzerland's Open Food Repo has shut down and redirects to
  Open Food Facts, which the dump already covers.

## Replies to the permission requests (2026-10-27)

- **Dutch NEVO: yes.** RIVM confirmed in writing that storing NEVO values
  converted to our format is fine as long as the amounts don't change, and that
  a free app with a later paid tier for unrelated features is fine if NEVO data
  stays free. Their one caution: "foods for special nutritional use" (infant
  and toddler formula) are per 100 ml with no density, so leave those out.
  Cite "Based on data from NEVO online version 2025/9.0, RIVM, Bilthoven".
  Imported: see "NEVO (Netherlands): imported" above.
- **Philippines PhilFCT: not now.** FNRI can't license it yet and has no
  CSV, Excel or API; they're building a data platform (i.fnri) and will
  announce access on their Facebook page. Only the printed book is for sale.
  Stays on Hold; watch for i.fnri.
- **NCCDB (University of Minnesota): paid licence, files in Excel, no API.**
  Prices are usually for a 2-year term:
  - All 178 nutrients: $8,620.
  - The 38 standard nutrients: $3,420.
  - À la carte: $2,045 plus $180 per nutrient.
  - **Startup offer:** a 1-year Standard Nutrients licence at half price,
    $1,710, for development, testing and launch.
  - Add-ons include nutrients per common portion ($2,570 standard, $6,470
    all), food group files ($4,390) and NCC-to-USDA code links ($660 to $790).
  - Licensing takes at least 3 weeks. Contact: nccdb-licensing@umn.edu (Jenny).
  - If it goes ahead, the startup Standard package plus the USDA links is the
    natural first buy.

## Skip

- **Edamam, FatSecret, Spoonacular:** API-only, with caching limits below
  custom tiers. Pricing came from third-party blogs, not the vendors' pages.
- **Nutritionix:** the free tier is non-commercial. Paid tiers are expensive,
  so keep it as the optional barcode fallback it already is.
- **Recipe datasets (Recipe1M, RecipeNLG, Food.com scrapes):** scraped, non-commercial or unclear licences, and the nutrition is estimated. Mixed dishes come from FNDDS, the Korean dataset and our own curation.
- **GS1 product data:** not open, and its terms forbid redistribution.
- **Mexico SMAE:** sold, not open.
- **Kaggle "Dietly food sample":** about 5,000 foods with macros only, from
  an unknown source under an unknown licence. Nothing USDA plus Open Food
  Facts doesn't already cover.

## Order of work

1. Open Food Facts importer into its own table, a monthly full refresh, and
   barcode lookups preferring USDA Branded. Load dev, measure, then prod.
2. The Credits screen.
3. CNF, BLS, Livsmedelsverket, Ciqual, CoFID, Frida, MEXT, Fineli, Matvaretabellen and AFCD importers; Korea once its licence is confirmed.
4. Before public launch: counsel or Open Food Facts confirms the ODbL
   containment, and AFCD gets the same share-alike containment (its own table).
