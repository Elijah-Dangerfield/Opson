-- ─────────────────────────────────────────────────────────────────────────────
-- V13: indexes for the 0.2 search (docs/plans/search-quality.md).
--
-- Generic foods first. Search now reads the USDA generic rows (tier 1, about
-- 13,000) on their own before it decides whether Branded rows (430,000) can
-- reach the results at all: a short query with plenty of generic matches never
-- touches them. These partial indexes are the generic rows' copy of the two
-- search indexes from V7, so that first read costs a few milliseconds instead
-- of a scan of every match across the whole table.
--
-- Open Food Facts. Search caps how many rows of each kind of match it scores
-- (OpenFoodFactsSearch in the server). Two indexes keep the caps from losing
-- what matters:
--   off_foods_name_idx, a btree on lower(name): products named exactly the
--     query, without the trigram recheck, so an exact name is always a
--     candidate however many other products share its words;
--   off_foods_country_search_idx, GIN on (countries_tags, search): products
--     sold in the caller's country that match the query, found directly
--     rather than by walking the country's matches among all the others.
-- About 200 MB together at 3.3 million rows. The reload copies every off_foods
-- index definition onto the next table, so these are rebuilt each month like
-- the rest; their names start with off_foods_ for the swap.
--
-- Additive and idempotent: IF NOT EXISTS throughout. Each build reads its
-- whole table once; on foods that takes seconds and briefly holds back writes
-- (barcode caching, popularity), never reads. off_foods is empty until the
-- first Open Food Facts load, which builds them itself.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS foods_generic_search_idx ON foods USING gin (search) WHERE tier = 1;
CREATE INDEX IF NOT EXISTS foods_generic_name_trgm_idx ON foods USING gin (lower(name) extensions.gin_trgm_ops) WHERE tier = 1;

CREATE INDEX IF NOT EXISTS off_foods_name_idx ON off_foods (lower(name));
CREATE INDEX IF NOT EXISTS off_foods_country_search_idx ON off_foods USING gin (countries_tags, search);
