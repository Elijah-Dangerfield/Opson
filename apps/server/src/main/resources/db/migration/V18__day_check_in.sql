-- ─────────────────────────────────────────────────────────────────────────────
-- V18: Day close's check-in on day_records (design sync 2, release.checkIn).
--
-- check_in: the client's JSON for hunger through the day, energy, last night's
-- sleep and any moods, each optional ({"hunger":"steady","moods":["calm"]}).
-- Like the day's feeling it is opaque here and validated for length only
-- (DiaryRules); only the person sees it, and no analytics view reads it.
-- Older clients never send it, so it stays null for them.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE day_records
    ADD COLUMN IF NOT EXISTS check_in TEXT;
