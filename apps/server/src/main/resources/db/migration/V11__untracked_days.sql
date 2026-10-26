-- ─────────────────────────────────────────────────────────────────────────────
-- V11: untracked days and a day's feeling, both on day_records.
--
-- untracked: the person marked the day as one they are not tracking (a
-- holiday, a wedding). Trends shows it as a gap, never as a low day. Older
-- clients never send it, so it defaults to false and their days stay tracked.
--
-- feeling: Day close's optional "How did today feel?" (steady, busy, tired,
-- good, hard), as the client's wire value. Like meal feelings it is free text
-- here and validated for length only (DiaryRules); only the person sees it,
-- and no analytics view reads it.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE day_records
    ADD COLUMN untracked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN feeling   TEXT;

-- Meals per day is the one view that reads a day's logging as a measure of
-- the day. Someone who marks a holiday untracked and still logs a coffee has
-- not had a one-meal day, so untracked days leave the distribution. The
-- activity views (daily_active_loggers, the retention cohorts) stay as they
-- are: logging on an untracked day is still using the app, and that is all
-- they count. Same columns as V9, so CREATE OR REPLACE keeps grafana_ro's
-- grant.
CREATE OR REPLACE VIEW analytics.meals_per_day_distribution AS
WITH untracked AS (
    SELECT user_id, day
    FROM public.day_records
    WHERE NOT deleted AND untracked
),
slots AS (
    SELECT user_id, day, slot
    FROM public.log_entries
    WHERE NOT deleted AND slot <> 'uncategorized'
    UNION
    SELECT d.user_id, d.day, s.key
    FROM public.day_records d
    CROSS JOIN LATERAL jsonb_each(d.ate_no_details) AS s (key, value)
    WHERE NOT d.deleted AND s.value = 'true'::jsonb AND s.key <> 'uncategorized'
),
per_day AS (
    SELECT s.user_id, s.day, count(*) AS meals
    FROM slots s
    WHERE NOT EXISTS (SELECT 1 FROM untracked u WHERE u.user_id = s.user_id AND u.day = s.day)
    GROUP BY s.user_id, s.day
)
SELECT day, meals AS meals_per_day, count(*) AS person_days
FROM per_day
GROUP BY day, meals;
