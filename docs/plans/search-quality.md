# Search quality (0.2, wave 1)

Server and scripts only. Three problems found in the 0.1 app, one plan each.
0.1.0 is in App Review against prod's `/v1/foods/search`, so the response
shape stays byte-for-byte the same, migrations are additive and idempotent,
and nothing here touches prod. The owner deploys after review.

## Where things live

| What | Where |
|---|---|
| Search statement and ranking | `SEARCH_SQL` and `SearchTerms` in `apps/server/.../data/PostgresFoodCatalogRepository.kt` |
| Route | `GET /v1/foods/search` in `routes/FoodRoutes.kt`, DTOs in `routes/FoodDto.kt` |
| Default serving | the client logs `portions.firstOrNull()` (`libraries/foods/.../FoodAmount.kt`). The order of `foods.portions` is the default, and that order comes from the importer (`scripts/import_usda_foods.py`, FDC `seq_num`) |
| Open Food Facts | `off_foods` (V12), `scripts/import_off_foods.py`, `scripts/bench_off_search.py` |

## 1. Ranking

**Cause.** "brown rice" puts Branded "Crispy Sushi Rice Patties" (brand "Rice
Browns") first. Its brand words `rice` and `browns` are all query words, so
`brand_hit` is true, and the guard that stops a brand outranking a food only
looks for a generic food whose *whole name or first segment* is the query.
"Rice, brown, cooked" is neither, so the brand rule wins.

**Change.**
- A query only names a brand when no generic food (tier 1) has every query
  word in its name. "brown rice", "peanut butter", "greek yogurt" are foods;
  "cheerios", "chobani greek yogurt", "coke zero" are brands.
- Within a match tier, a row whose *name* carries the query words beats one
  that only matches through its brand. Full text over name + brand stays the
  candidate filter; the ranking asks the name first.
- Generic before Branded stays as is (`tier > 1` after the brand rule).

**Relevance suite first.** About 60 queries a person types (staples, dishes,
brands, misspellings, short prefixes), each with the foods its top 3 must
hold and, where it matters, what must not be there. It lives in
`apps/server/src/test/resources/search-relevance.tsv` and runs as
`SearchRelevanceTest` against a Testcontainers Postgres seeded with the
committed USDA generic seed plus a committed Branded fixture: the Branded rows
from dev that compete for those queries (public domain). The test prints the
hit rate and fails below a floor. Before and after numbers go in the report
and in `docs/food-data-sources.md`.

## 2. Default servings

**Cause.** The client takes the first portion. The importer keeps FDC's
`seq_num` order, which for small items starts with one piece: "1 berry" (2 g),
"1 grape", "1 almond".

**Change.** The importer orders portions so the first is a sensible default:
a labelled or typical serving (cup, medium piece, container, NLEA serving,
slice, oz) over a single tiny unit, never a piece under a grams floor (about
15 g) when something bigger exists. The rest keep their order, so every
portion is still offered. One function, `order_portions`, with tests for
berries, nuts, grains, candies and drinks.

- Fix the importer and regenerate the committed seed (the release URLs are
  pinned, so only the portion order changes).
- `scripts/backfill_default_portions.py`: applies the same function to rows
  already loaded, only rewriting rows whose order changes. Idempotent. Run on
  dev only; the prod command goes in the report, not run.
- The client is unchanged: 0.1 already reads the first portion.

## 3. Open Food Facts search speed

**Cause.** The statement fetches and scores every row a prefix matches before
it sorts: tens of thousands for common words, hundreds of thousands for one
or two letters, and the trigram fallback rechecks every name sharing a
trigram.

**Change.** Bound the candidates before scoring, measured on a local Postgres
17 with dev's settings and the full 3.29M-row export:
- no Open Food Facts matching for queries under 3 characters;
- candidates per match kind capped and taken from the most-scanned products
  first (the reload writes rows ordered by `scans`, so the index order of a
  capped scan is popularity), then scored;
- the trigram fallback on a tighter threshold, or dropped for OFF, whichever
  the numbers support.
Target: p99 under 300 ms at 3.3M rows. Result quality checked against the
same queries before and after.

**Wiring.** OFF results join the response behind a server config flag,
`FOOD_SEARCH_OFF_ENABLED`, default off, so 0.1's results don't change. OFF
rows rank after generic and Branded rows for the same match, and keep the
same response shape (`source: "off"`, tier 4, not verified). `GET
/v1/foods/{id}` answers OFF ids too when the flag is on.

**Dev.** Load OFF into dev only if free space stays under 60% used during and
after the load. Check `pg_database_size` and the disk first, stop if it
climbs.

## Done means

This file committed first. Relevance suite before/after and benchmark numbers
in the report and in `docs/food-data-sources.md`. Server tests and
`python3 -I -m unittest discover -s scripts -p 'test_*.py'` pass.
