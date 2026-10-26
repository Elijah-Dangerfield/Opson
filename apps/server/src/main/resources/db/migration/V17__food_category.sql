-- ─────────────────────────────────────────────────────────────────────────────
-- V17: a small, normalised category on catalog foods, for the Logger's
-- Restaurant and Beverages tabs (docs/plans/screens/search-options.md).
--
-- Filled from USDA's own categories by scripts/import_usda_foods.py (SR Legacy
-- and Foundation `food_category`, the FNDDS WWEIA category, Branded
-- `branded_food_category`), and for rows already loaded by
-- scripts/backfill_food_categories.py. Null means "none of these", which is
-- most foods. Open Food Facts rows (here and in off_foods) stay null.
--
--   restaurant  fast food and restaurant dishes
--   beverage    drinks, including juice, milk, coffee, tea and alcohol
--   baby_food   baby food and infant formula
--   supplement  protein powders, vitamins and other supplements
--
-- No index: search only filters on it after the name and full-text indexes
-- have narrowed the rows to the matches, as with tier (V10).
--
-- Additive and idempotent, so it can be run by hand before Flyway sees it
-- (dev got it that way, as with V12). Adding a nullable column with no
-- default is a catalog-only change; the CHECK reads the table once.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE foods ADD COLUMN IF NOT EXISTS category TEXT;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'foods_category_check') THEN
        ALTER TABLE foods ADD CONSTRAINT foods_category_check
            CHECK (category IN ('restaurant', 'beverage', 'baby_food', 'supplement'));
    END IF;
END
$$;
