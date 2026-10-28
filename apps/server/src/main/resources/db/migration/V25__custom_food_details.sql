-- ─────────────────────────────────────────────────────────────────────────────
-- V25: a custom food's weight, serving sizes and full label (Edit a copy).
--
-- serving_grams is what one serving weighs, once someone weighed it; with it
-- the food can be logged in grams. sizes is a JSON array of {label, grams},
-- one of each unit with its weight ("tbsp" is 15 g). micros holds the rest of
-- the label per serving, keyed like log_entries.micros (`sodium_mg`,
-- `saturated_fat_g`).
--
-- Additive and nullable, so 0.1 clients are untouched. They never send these
-- fields, and an upsert from them leaves whatever is stored alone (see
-- PostgresSyncRepository), so an old phone editing a food does not wipe what
-- a new one added. Written to be safe to run twice.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE custom_foods ADD COLUMN IF NOT EXISTS serving_grams REAL;
ALTER TABLE custom_foods ADD COLUMN IF NOT EXISTS sizes JSONB;
ALTER TABLE custom_foods ADD COLUMN IF NOT EXISTS micros JSONB;
