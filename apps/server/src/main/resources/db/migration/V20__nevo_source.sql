-- ─────────────────────────────────────────────────────────────────────────────
-- V20: NEVO, the Dutch food composition table (RIVM), as a catalog source
-- (docs/food-data-sources.md, "Replies to the permission requests").
--
-- `nevo` rows are generic foods with lab data, so they are tier 1 like
-- USDA's generic tables, and search reads them with those (V15's partial
-- indexes cover tier 1). scripts/import_nevo_foods.py loads them, one row
-- per NEVO-code.
--
-- `source_version` is the release a row came from ("2025/9.0" for NEVO).
-- Null for rows loaded before it existed; only the NEVO importer writes it
-- for now.
--
-- RIVM's terms ask for its citation wherever NEVO foods appear, so search
-- only returns them to a client that shows it (`credits=nevo`, see
-- FoodRoutes). Nothing here enforces that; the server does.
--
-- Additive and idempotent, so it can be run by hand before Flyway sees it.
-- A nullable column with no default is a catalog-only change. The new CHECK
-- reads the table once, as V10's did (about a second at 445,000 rows),
-- holding back writes and searches meanwhile. `food_source_tier` is replaced
-- in place, and the trigger that calls it (V10) picks it up.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE foods ADD COLUMN IF NOT EXISTS source_version TEXT;

ALTER TABLE foods DROP CONSTRAINT IF EXISTS foods_source_check;
ALTER TABLE foods ADD CONSTRAINT foods_source_check
    CHECK (source IN ('usda_foundation', 'usda_sr', 'usda_fndds', 'usda_branded', 'nutritionix', 'off', 'nevo'));

CREATE OR REPLACE FUNCTION public.food_source_tier(source TEXT) RETURNS SMALLINT
    LANGUAGE sql
    IMMUTABLE
    SET search_path = ''
AS $$
    SELECT CASE source
        WHEN 'usda_foundation' THEN 1
        WHEN 'usda_sr' THEN 1
        WHEN 'usda_fndds' THEN 1
        WHEN 'nevo' THEN 1
        WHEN 'usda_branded' THEN 2
        WHEN 'nutritionix' THEN 3
        ELSE 4
    END::smallint
$$;
