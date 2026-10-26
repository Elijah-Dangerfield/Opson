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
-- Open Food Facts. A btree on lower(name) answers "products named exactly
-- this" without the trigram recheck, so an exact name is always a candidate
-- however many other products share its words. Search caps the other match
-- kinds (PostgresFoodCatalogRepository.OFF_SEARCH_SQL). The reload copies
-- every off_foods index definition onto the next table, so this one is rebuilt
-- each month like the rest; its name starts with off_foods_ for the swap.
--
-- Additive and idempotent: IF NOT EXISTS throughout. Each build reads its
-- whole table once; on foods that takes seconds and briefly holds back writes
-- (barcode caching, popularity), never reads.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE INDEX IF NOT EXISTS foods_generic_search_idx ON foods USING gin (search) WHERE tier = 1;
CREATE INDEX IF NOT EXISTS foods_generic_name_trgm_idx ON foods USING gin (lower(name) extensions.gin_trgm_ops) WHERE tier = 1;

CREATE INDEX IF NOT EXISTS off_foods_name_idx ON off_foods (lower(name));
