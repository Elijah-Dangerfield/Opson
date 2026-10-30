-- ─────────────────────────────────────────────────────────────────────────────
-- V33: does a food's energy add up to its macros? (docs/food-data-sources.md,
-- "Energy math: does every food add up".)
--
-- Three columns on every table that holds a food's nutrition (`foods` and
-- `off_foods`; there is no other):
--   energy_consistent   true when the stated kcal is what the macros give,
--                       within a label's rounding; false when it is not; null
--                       when it can't be judged (energy or a macro unknown,
--                       or energy with no macros and no alcohol figure).
--   energy_gap_kcal     stated minus the macros' energy, per 100 g (or 100 ml),
--                       against the counting method nearest the stated value.
--                       Null when energy or a macro is unknown.
--   energy_checked_at   when the row was last judged. Null means never: the
--                       rows already loaded until scripts/check_food_energy.py
--                       reaches them.
--
-- The rule is food_energy_check() below, and the docs say it in words. It
-- judges the food as the catalog serves it: the stored row with a reviewer's
-- live correction laid over it (V29), so fixing a food clears its flag. It
-- does not apply the served energy guard (FoodEnergy in the server): the guard
-- patches a broken row on the way out, and this column is what says the row
-- is broken.
--
-- Kept current by triggers rather than in each writer, since rows come from
-- four importers, the barcode cache, approvals and corrections:
--   - a BEFORE INSERT / UPDATE OF the nutrient columns on `foods` and
--     `off_foods` (so a popularity bump costs nothing);
--   - an AFTER trigger on `food_corrections` that re-judges the food it names.
-- The Open Food Facts reload builds `off_foods_next` with LIKE, which copies
-- no triggers, so scripts/import_off_foods.py copies this table's triggers
-- onto it; the reload therefore arrives judged.
--
-- Adding nullable columns without a default is a catalog-only change, so this
-- migration takes its locks for milliseconds and rewrites nothing. The rows
-- already loaded are judged afterwards, in committed batches, by
-- scripts/check_food_energy.py through food_energy_backfill() (apps/server/
-- README.md has the command). Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE foods ADD COLUMN IF NOT EXISTS energy_consistent BOOLEAN;
ALTER TABLE foods ADD COLUMN IF NOT EXISTS energy_gap_kcal REAL;
ALTER TABLE foods ADD COLUMN IF NOT EXISTS energy_checked_at TIMESTAMPTZ;
ALTER TABLE off_foods ADD COLUMN IF NOT EXISTS energy_consistent BOOLEAN;
ALTER TABLE off_foods ADD COLUMN IF NOT EXISTS energy_gap_kcal REAL;
ALTER TABLE off_foods ADD COLUMN IF NOT EXISTS energy_checked_at TIMESTAMPTZ;

-- The rule, on numbers alone. Everything per 100 g (or ml) except `serving`,
-- the amount one serving holds in the same unit (null when unknown).
--
-- Energy from the macros, at Atwater's general factors: protein 4, carbs 4,
-- fat 9, alcohol 7 kcal a gram. Labels count carbohydrate two ways, so three
-- candidates are tried and the one nearest the stated energy wins:
--   general      4 x carbs, whatever is inside them;
--   US total     carbs include fiber and sugar alcohols: fiber at 2, polyols
--                at 2.4, the rest at 4;
--   EU           carbs exclude fiber (counted apart at 2) and include
--                polyols (2.4).
-- Without fiber or polyols the three are one number.
--
-- Judged at a serving when its size is known, since that is where a label
-- rounds; per 100 otherwise. The stated and the macros' energy may differ by
-- 15 kcal or 12% of the stated energy, whichever is larger (LabelChecks'
-- tolerance, which the app's label form shares: energy rounded to 5 or 10,
-- each macro to the gram, and the specific factors some foods use). A serving
-- stated under 5 kcal is a label's "0" (the FDA allows it below 5), so there
-- the macros must also come in within 5 kcal: the zero-calorie energy drink
-- with 2 g of carbs a can is caught, as the energy guard catches it.
--
-- Energy with protein, carbs and fat all 0 and no alcohol figure can't be
-- judged (spirits look like this), so it stays null unless it adds up anyway.
CREATE OR REPLACE FUNCTION public.food_energy_check(
    kcal DOUBLE PRECISION,
    protein DOUBLE PRECISION,
    carbs DOUBLE PRECISION,
    fat DOUBLE PRECISION,
    fiber DOUBLE PRECISION,
    polyols DOUBLE PRECISION,
    alcohol DOUBLE PRECISION,
    serving DOUBLE PRECISION,
    OUT consistent BOOLEAN,
    OUT gap_kcal REAL
)
    LANGUAGE plpgsql
    IMMUTABLE
    PARALLEL SAFE
    SET search_path = ''
AS $$
DECLARE
    f DOUBLE PRECISION := greatest(coalesce(fiber, 0), 0);
    p DOUBLE PRECISION := greatest(coalesce(polyols, 0), 0);
    base DOUBLE PRECISION;
    nearest DOUBLE PRECISION;
    candidate DOUBLE PRECISION;
    amount DOUBLE PRECISION := CASE WHEN serving > 0 THEN serving ELSE 100 END;
    stated DOUBLE PRECISION;
    off_by DOUBLE PRECISION;
BEGIN
    IF kcal IS NULL OR protein IS NULL OR carbs IS NULL OR fat IS NULL THEN
        RETURN;
    END IF;
    base := 4 * protein + 9 * fat + 7 * greatest(coalesce(alcohol, 0), 0);
    FOREACH candidate IN ARRAY ARRAY[
        base + 4 * carbs,
        base + 4 * greatest(0, carbs - f - p) + 2 * f + 2.4 * p,
        base + 4 * greatest(0, carbs - p) + 2.4 * p + 2 * f
    ] LOOP
        IF nearest IS NULL OR abs(kcal - candidate) < abs(kcal - nearest) THEN
            nearest := candidate;
        END IF;
    END LOOP;
    gap_kcal := round((kcal - nearest)::numeric, 1)::real;
    stated := kcal * amount / 100;
    off_by := abs(kcal - nearest) * amount / 100;
    consistent := off_by <= CASE WHEN stated < 5 THEN 5 ELSE greatest(15, 0.12 * stated) END;
    IF NOT consistent AND protein = 0 AND carbs = 0 AND fat = 0 AND alcohol IS NULL THEN
        consistent := NULL;
    END IF;
END;
$$;

-- A serving's amount, as the server reads it (FoodEnergy.servingAmount): the
-- first portion's grams, or the millilitres its label names ("1 can (473 ml)").
CREATE OR REPLACE FUNCTION public.food_serving_amount(portions JSONB)
    RETURNS DOUBLE PRECISION
    LANGUAGE sql
    IMMUTABLE
    PARALLEL SAFE
    SET search_path = ''
AS $$
    SELECT CASE
        WHEN jsonb_typeof(portions -> 0 -> 'grams') = 'number' AND (portions -> 0 ->> 'grams')::double precision > 0
            THEN (portions -> 0 ->> 'grams')::double precision
        ELSE nullif(replace(substring(portions -> 0 ->> 'label'
                 FROM '(?i)(\d+(?:[.,]\d+)?)\s*(?:ml|milliliters?|millilitres?)\y'), ',', '.'), '')::double precision
    END
$$;

-- A stored food, judged as it is served: its live correction (V29) over it.
-- A correction sets kcal, protein, carbs and fat per 100, and a serving that
-- goes first.
CREATE OR REPLACE FUNCTION public.food_energy_judge(
    food_id UUID,
    kcal DOUBLE PRECISION,
    protein DOUBLE PRECISION,
    carbs DOUBLE PRECISION,
    fat DOUBLE PRECISION,
    fiber DOUBLE PRECISION,
    micros JSONB,
    portions JSONB,
    OUT consistent BOOLEAN,
    OUT gap_kcal REAL
)
    LANGUAGE sql
    STABLE
    SET search_path = ''
AS $$
    SELECT c.consistent, c.gap_kcal
    FROM (SELECT (SELECT fields FROM public.food_corrections fc
                  WHERE fc.food_id = food_energy_judge.food_id
                    AND fc.reverted_at IS NULL AND fc.superseded_by IS NULL) AS fields) live,
         public.food_energy_check(
             coalesce((live.fields ->> 'kcal')::double precision, kcal),
             coalesce((live.fields ->> 'protein')::double precision, protein),
             coalesce((live.fields ->> 'carbs')::double precision, carbs),
             coalesce((live.fields ->> 'fat')::double precision, fat),
             fiber,
             (micros ->> 'sugar_alcohols_g')::double precision,
             (micros ->> 'alcohol_g')::double precision,
             CASE WHEN live.fields ->> 'servingLabel' IS NOT NULL
                 THEN public.food_serving_amount(jsonb_build_array(jsonb_build_object(
                          'label', live.fields ->> 'servingLabel', 'grams', live.fields -> 'servingGrams')))
                 ELSE public.food_serving_amount(portions)
             END
         ) c
$$;

-- Shared by `foods` and `off_foods`: the columns it reads have the same names on both.
CREATE OR REPLACE FUNCTION public.food_energy_check_row() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    SELECT j.consistent, j.gap_kcal INTO NEW.energy_consistent, NEW.energy_gap_kcal
    FROM public.food_energy_judge(NEW.id, NEW.kcal_100g, NEW.protein_100g, NEW.carbs_100g, NEW.fat_100g,
                                  NEW.fiber_100g, NEW.micros, NEW.portions) j;
    NEW.energy_checked_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS foods_energy_check ON foods;
CREATE TRIGGER foods_energy_check
    BEFORE INSERT OR UPDATE OF kcal_100g, protein_100g, carbs_100g, fat_100g, fiber_100g, micros, portions ON foods
    FOR EACH ROW EXECUTE FUNCTION public.food_energy_check_row();

-- The name carries the off_foods_ prefix like everything else on the table.
DROP TRIGGER IF EXISTS off_foods_energy_check ON off_foods;
CREATE TRIGGER off_foods_energy_check
    BEFORE INSERT OR UPDATE OF kcal_100g, protein_100g, carbs_100g, fat_100g, fiber_100g, micros, portions ON off_foods
    FOR EACH ROW EXECUTE FUNCTION public.food_energy_check_row();

-- Re-judge one food, in whichever table holds it, without touching its nutrients.
CREATE OR REPLACE FUNCTION public.food_energy_refresh(target UUID) RETURNS void
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    UPDATE public.foods t
    SET (energy_consistent, energy_gap_kcal, energy_checked_at) = (
        SELECT j.consistent, j.gap_kcal, now()
        FROM public.food_energy_judge(t.id, t.kcal_100g, t.protein_100g, t.carbs_100g, t.fat_100g,
                                      t.fiber_100g, t.micros, t.portions) j)
    WHERE t.id = target;
    UPDATE public.off_foods t
    SET (energy_consistent, energy_gap_kcal, energy_checked_at) = (
        SELECT j.consistent, j.gap_kcal, now()
        FROM public.food_energy_judge(t.id, t.kcal_100g, t.protein_100g, t.carbs_100g, t.fat_100g,
                                      t.fiber_100g, t.micros, t.portions) j)
    WHERE t.id = target;
END;
$$;

CREATE OR REPLACE FUNCTION public.food_corrections_energy_refresh() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        PERFORM public.food_energy_refresh(OLD.food_id);
    END IF;
    IF TG_OP <> 'DELETE' AND (TG_OP = 'INSERT' OR NEW.food_id IS DISTINCT FROM OLD.food_id) THEN
        PERFORM public.food_energy_refresh(NEW.food_id);
    END IF;
    RETURN NULL;
END;
$$;

DROP TRIGGER IF EXISTS food_corrections_energy_refresh ON food_corrections;
CREATE TRIGGER food_corrections_energy_refresh
    AFTER INSERT OR UPDATE OR DELETE ON food_corrections
    FOR EACH ROW EXECUTE FUNCTION public.food_corrections_energy_refresh();

-- One batch of the backfill, in key order after `after`. Judges the rows not
-- judged yet, and with `checked_before` also those judged before it (to
-- re-run a changed rule). Returns the last id it looked at, to pass back as
-- `after`, how many rows it looked at (0 means done) and how many it wrote.
-- Each call is one short transaction over at most `batch_size` rows, found
-- through the primary key; scripts/check_food_energy.py loops it.
CREATE OR REPLACE FUNCTION public.food_energy_backfill(
    target REGCLASS,
    after UUID,
    batch_size INTEGER,
    checked_before TIMESTAMPTZ DEFAULT NULL,
    OUT last_id UUID,
    OUT scanned INTEGER,
    OUT updated INTEGER
)
    LANGUAGE plpgsql
    SET search_path = ''
AS $$
BEGIN
    IF target NOT IN ('public.foods'::regclass, 'public.off_foods'::regclass) THEN
        RAISE EXCEPTION 'food_energy_backfill judges public.foods or public.off_foods, not %', target;
    END IF;
    IF batch_size IS NULL OR batch_size < 1 OR batch_size > 50000 THEN
        RAISE EXCEPTION 'batch_size must be between 1 and 50000';
    END IF;
    EXECUTE format($q$
        WITH batch AS (
            SELECT id FROM %1$s WHERE id > $1 ORDER BY id LIMIT $2
        ), done AS (
            UPDATE %1$s t
            SET (energy_consistent, energy_gap_kcal, energy_checked_at) = (
                SELECT j.consistent, j.gap_kcal, now()
                FROM public.food_energy_judge(t.id, t.kcal_100g, t.protein_100g, t.carbs_100g, t.fat_100g,
                                              t.fiber_100g, t.micros, t.portions) j)
            FROM batch
            WHERE t.id = batch.id AND (t.energy_checked_at IS NULL OR t.energy_checked_at < $3)
            RETURNING 1
        )
        SELECT (SELECT id FROM batch ORDER BY id DESC LIMIT 1),
               (SELECT count(*) FROM batch)::int,
               (SELECT count(*) FROM done)::int
    $q$, target)
    INTO last_id, scanned, updated
    USING coalesce(after, '00000000-0000-0000-0000-000000000000'::uuid), batch_size, checked_before;
END;
$$;

-- Grafana. Counts per source, and the foods furthest off. `off_export` is the
-- monthly Open Food Facts table; `off` in `foods` is the one-at-a-time barcode
-- cache. Only approved foods: a pending submission is its submitter's alone.
-- No names, barcodes or stated values, as in every analytics view.
CREATE OR REPLACE VIEW analytics.food_energy_by_source AS
SELECT source, tier,
       count(*) AS foods,
       count(*) FILTER (WHERE energy_consistent) AS consistent,
       count(*) FILTER (WHERE NOT energy_consistent) AS inconsistent,
       count(*) FILTER (WHERE energy_checked_at IS NOT NULL AND energy_consistent IS NULL) AS unknown,
       count(*) FILTER (WHERE energy_checked_at IS NULL) AS unchecked
FROM (
    SELECT source, tier, energy_consistent, energy_checked_at FROM public.foods WHERE review_status = 'approved'
    UNION ALL
    SELECT 'off_export', tier, energy_consistent, energy_checked_at FROM public.off_foods
) f
GROUP BY source, tier;

CREATE OR REPLACE VIEW analytics.food_energy_worst AS
SELECT food_id, source, tier, gap_kcal_per_100, checked_at
FROM (
    SELECT id AS food_id, source, tier, energy_gap_kcal AS gap_kcal_per_100, energy_checked_at AS checked_at
    FROM public.foods WHERE energy_consistent = FALSE AND review_status = 'approved'
    UNION ALL
    SELECT id, 'off_export', tier, energy_gap_kcal, energy_checked_at
    FROM public.off_foods WHERE energy_consistent = FALSE
) f
ORDER BY abs(gap_kcal_per_100) DESC, food_id
LIMIT 200;

GRANT SELECT ON analytics.food_energy_by_source TO grafana_ro;
GRANT SELECT ON analytics.food_energy_worst TO grafana_ro;
