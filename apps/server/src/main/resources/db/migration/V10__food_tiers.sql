-- ─────────────────────────────────────────────────────────────────────────────
-- V10: source tiers, the verified flag, and the new catalog sources
-- (docs/system-design.md §5, §7, §8 and the 2026-10-26 decision in §15).
--
-- Every food carries the tier of the source it came from, best first:
--   1 reference  USDA Foundation, SR Legacy, FNDDS (generic, lab or survey data)
--   2 label      USDA Branded Foods (the manufacturer's nutrition label)
--   3 licensed   paid sources we cache under a licence (Nutritionix, later NCCDB)
--   4 community  Open Food Facts, and later foods people submit
-- `verified` is true for tiers 1 to 3: someone accountable stands behind the
-- numbers. Search ranks verified and lower tiers first within a match tier.
--
-- The tier follows from the source, so a trigger fills it in and the loaders
-- never have to agree on a mapping: the committed USDA seed predates these
-- columns and still loads unchanged. `verified` is only defaulted, so a later
-- review step can verify a community food without changing its source.
--
-- New sources: `usda_branded` (scripts/import_usda_foods.py --branded) and
-- `nutritionix` (cached only when NUTRITIONIX_CACHE_RESULTS is on). A barcode
-- lookup answered by Nutritionix is recorded as outcome `nutritionix`.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE foods DROP CONSTRAINT foods_source_check;
ALTER TABLE foods ADD CONSTRAINT foods_source_check
    CHECK (source IN ('usda_foundation', 'usda_sr', 'usda_fndds', 'usda_branded', 'nutritionix', 'off'));

CREATE FUNCTION public.food_source_tier(source TEXT) RETURNS SMALLINT
    LANGUAGE sql
    IMMUTABLE
    SET search_path = ''
AS $$
    SELECT CASE source
        WHEN 'usda_foundation' THEN 1
        WHEN 'usda_sr' THEN 1
        WHEN 'usda_fndds' THEN 1
        WHEN 'usda_branded' THEN 2
        WHEN 'nutritionix' THEN 3
        ELSE 4
    END::smallint
$$;

ALTER TABLE foods ADD COLUMN tier SMALLINT;
ALTER TABLE foods ADD COLUMN verified BOOLEAN;
UPDATE foods SET tier = public.food_source_tier(source), verified = public.food_source_tier(source) <= 3;
ALTER TABLE foods ALTER COLUMN tier SET NOT NULL;
ALTER TABLE foods ALTER COLUMN verified SET NOT NULL;
ALTER TABLE foods ADD CONSTRAINT foods_tier_check CHECK (tier BETWEEN 1 AND 4);

-- On insert, fill whatever the writer left out. When an upsert moves a row to
-- another source (an Open Food Facts product that turns up in Branded Foods),
-- the tier and the default verified flag follow the new source.
CREATE FUNCTION public.foods_set_tier() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        NEW.tier := coalesce(NEW.tier, public.food_source_tier(NEW.source));
        NEW.verified := coalesce(NEW.verified, NEW.tier <= 3);
    ELSIF NEW.source IS DISTINCT FROM OLD.source THEN
        NEW.tier := public.food_source_tier(NEW.source);
        NEW.verified := NEW.tier <= 3;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER foods_set_tier
    BEFORE INSERT OR UPDATE OF source ON foods
    FOR EACH ROW EXECUTE FUNCTION public.foods_set_tier();

-- No index on tier: it has four values, and search only orders by it after
-- the name and full-text indexes have narrowed the rows to the matches.

ALTER TABLE barcode_lookups DROP CONSTRAINT barcode_lookups_outcome_check;
ALTER TABLE barcode_lookups ADD CONSTRAINT barcode_lookups_outcome_check
    CHECK (outcome IN ('catalog', 'off', 'nutritionix', 'miss', 'error'));
