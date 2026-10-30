# Search ranking: what we have, what we could do, what we built

Owner, 2026-10-30: "I searched 'strawberry' and one of the first results was
strawberry milk." Also: a food someone keeps picking for a word should rise for
them; recently tracked foods should be near the top; and a long tail of what
they log should be on the phone so they can log offline. What already works and
must stay: "protein brownie" put their recently logged custom food first and
said why.

## How search works today

**Server** (`apps/server/.../data/PostgresFoodCatalogRepository.kt`). One SQL
statement over `foods` (USDA Foundation, SR Legacy, FNDDS, Branded, NEVO,
approved submissions), and a second over `off_foods` merged after it when Open
Food Facts is on (`OpenFoodFactsSearch`). Candidates come from a name prefix
`LIKE` (trigram index) and full text over name and brand (`simple` tsvector,
prefix tsquery), with a trigram fallback for typos. Rows are ranked by rules,
not a score: direct before typo, a name match before a brand-only match, a
named brand first, generic before Branded, then match tier (exact, head,
whole-word prefix, full text, similar), verified, source tier, whole words, a
"plain form" flag (raw, NFS, plain, whole, brewed, cooked), similarity,
`popularity` (incremented by the sync for every logged entry of the food) and
name length. Answers are cached per query for 5 minutes in memory
(`SearchResultCache`). `SearchRelevanceTest` checks the top 3 of 78 queries
against the committed USDA seed plus a Branded fixture.

**Client** (`features/logger/impl`). `FoodSearchViewModel` debounces 250 ms and
asks `FoodCatalog.search`, which keeps the last 32 answers for 5 minutes and,
offline, falls back to a substring `LIKE` over `food_cache` (catalog foods this
device logged, unbounded). `FoodSearchStateBuilder` puts the person's matching
custom foods first, then the server's order. With the Most recent sort,
foods from their history that match go first too. Rows say "Your usual" when
the food is among the 20 logged most this month, and with the search options
on a "Your food" badge marks custom foods. That is the protein brownie case.

## Why "strawberry" showed strawberry milk

`SearchTerms.variantsOf` made plurals by adding an s. `strawberry` became
`strawberry | strawberrys`, so "Strawberries, raw" was neither an exact nor a
head match, and the prefix tsquery `strawberry:*` does not reach
`strawberries` either. The generic fruit only matched through trigram
similarity, the last tier, while "Strawberry milk, NFS" was a whole-word prefix
match. Same for every word ending in a consonant and y: blueberry, raspberry,
cherry. The fixtures reproduce it; dev's database is not reachable from the
repo's dev config, so it was not run there.

## Ideas, with costs

| Idea | For | Against | Effort |
|---|---|---|---|
| English plural rules (y/ies, ch/es, o/oes) | Root cause of the report; cheap; helps every source | None found | S |
| Head-noun matching, query used as a modifier ranks lower | The tiers already do it once the head match works: "Strawberries, raw" is a head match, "Strawberry milk" a prefix | A separate modifier penalty would fight the brand rules | done by the fix |
| Prefer the plain form among head matches: fewest descriptor segments ("Rice, cooked, NFS" before "Rice, fried, NFS") | Cheap, general, USDA names are built that way | Heuristic; a dish can be short | S |
| Typed-so-far prefix beats a later word ("ban" is Bananas before "Peppers, banana") | Search is as-you-type | Loses to a whole word ("toast" is "Melba toast" before "Toaster pastries"), so it sits after whole words | S |
| Drop "Oil" as a category prefix | `almonds` stops offering almond oil | `olive oil` must still work (it does, as a two-segment head) | XS |
| Generic and whole-food preference for single words | Already the rule: generic before Branded | | done |
| Name length and complexity penalty | Length is already the last tiebreak; segment count added above | | done |
| Category priors (fruit for a fruit word, beverage for "milk") | Would separate milk from milkshake | Needs a category on every generic row and a query classifier | M |
| Global popularity from logs | `foods.popularity` already counts logs; real signal once there are users | Rich get richer; small counts are noise today | already a tiebreak; promote later |
| Per-query click-through on the server | Best signal there is | Stores what people search next to what they ate; against the privacy stance unless aggregated and thresholded | M, later, needs a privacy decision |
| Per-person learned boosts (query to chosen food, decayed) | Exactly the owner's ask; private when it stays on the phone | Cold start; another store to clear on sign-out | M |
| Recency and frequency of the person's own logs | The biggest win for repeat logging | Can push a one-off ahead of the obvious food, so it needs a threshold | S on the client |
| Synonyms (eggplant/aubergine, soda/pop, courgette) | Real misses for non-US English | Needs a curated list per locale | S per list, later |
| Typos | Trigram rescue exists and the suite covers it | | done |
| Brand intent | "A query names a brand" rule exists | | done |
| Learning to rank | Combines all signals properly | Needs labelled data or click logs we do not keep | L, later |
| Embeddings or an LLM reranker | Semantic matches | No backend AI in v1; on-device models are heavy | no |
| Offline evaluation with graded labels, MRR and NDCG | Tells better from different; guards regressions | Labels are ours, so keep them honest and written for intent | S |

## Decided

Built now, because each one is cheap, testable and fixes something the owner saw:

1. **Server ranking fixes**: the plural rules (server and the Python copy in
   `scripts/bench_off_search.py` the fixture script uses), "Oil" out of the
   category prefixes, fewest descriptor segments among exact and head matches,
   and a typed-prefix tiebreak in full text after whole words. Same response
   shape, no migration, no new query parameters, so installed builds just get
   better answers.
2. **Graded evaluation**: `search-eval.tsv` (58 queries: staples including
   strawberry, banana, chicken breast, rice, egg, milk and apple, two-word
   foods, brands, typos, prefixes) and `SearchEvaluationTest`, which prints
   MRR, NDCG@10, good first rows and modifiers in the top 3, writes
   `build/reports/search-eval.txt`, and fails below floors. The relevance
   suite gains strawberry, blueberry, cherry, tomato, rice and almonds.
3. **Personal ranking on the phone**: what someone picks for a query is kept
   on the device (query, food, a decayed weight), never sent and never logged.
   Search puts the person's own foods first when they mean something: a
   favorite, their own custom food, a food they picked twice for this query,
   or one they logged twice or within the last two weeks. The rest keep the
   server's order.
4. **The why**: one hint on the row, the same everywhere in results:
   "Favorite", "Logged 3 times" (or "Logged once"), "Your food".
5. **Offline long tail**: the last 500 distinct foods the person logged, read
   from the diary with each one's last amount and full nutrients, are
   searchable offline and log through the diary outbox like any other entry.
   The catalog cache on the phone is capped at 500 foods besides favorites,
   its offline search matches words the way the server does, and the catalog
   details of long-tail foods logged on another device are fetched in the
   background so their detail opens offline too.

Later, and why:

- Promote `popularity` once counts mean something; today they are mostly 0.
- Server-side click-through only with an explicit privacy decision.
- Synonyms per locale, category priors, then learning to rank once there is
  data to learn from.
- Syncing learned picks between a person's devices (through settings sync),
  if people ask; today they stay on the phone that learned them.

## Measured

Fixtures (USDA generic seed plus the Branded fixture), 2026-10-30:

| | MRR | NDCG@10 | Good first | Modifiers in top 3 | Relevance suite |
|---|---|---|---|---|---|
| Before | 0.914 | 0.822 | 90% | 0.31 | 78 of 83 |
| After | 0.991 | 0.890 | 98% | 0.07 | 83 of 83 |

`strawberry` went from "Strawberry milk, NFS" first (RR 0) to "Strawberries,
raw" first; blueberry, raspberry and cherry the same; `rice` from "Rice, fried,
NFS" to "Rice, cooked, NFS". Search time on the fixtures is unchanged within
noise (p50 about 13 ms, p95 about 35 ms). What is still weak: `rice` keeps
fried rice second, `chicken` opens with ground chicken, and `straw` puts the
mushroom first because "straw" is a whole word there.

Run it: `./gradlew :apps:server:test --tests '*SearchEvaluationTest*' -i`.
