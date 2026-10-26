-- ─────────────────────────────────────────────────────────────────────────────
-- V9: the `analytics` schema Grafana reads, and the read-only role it reads as
-- (docs/system-design.md §9).
--
-- The data underneath is about eating, for people who may be unwell, so the
-- rule is structural rather than a convention: Grafana's role can read this
-- schema and nothing else, and every view here aggregates across users. No
-- view selects a nutrient amount (kcal, macros, micros, grams, weight, water)
-- at any grain, and no view carries a user_id. Adding one that does is a
-- review blocker (docs/practices/review-checklist.md).
--
-- Views run with their owner's rights, so grafana_ro needs no grant on the
-- public tables, and the policy-less RLS on them (V7, V8) does not get in the
-- way of the owner.
--
-- grafana_ro is NOLOGIN. To connect Grafana, create a login role outside of
-- migrations (its password must not live in git) and grant it grafana_ro:
--     CREATE ROLE grafana_opson LOGIN PASSWORD '...' IN ROLE grafana_ro;
-- ─────────────────────────────────────────────────────────────────────────────

CREATE SCHEMA IF NOT EXISTS analytics;

-- Daily active loggers: distinct people with at least one live entry that day.
CREATE VIEW analytics.daily_active_loggers AS
SELECT day, count(DISTINCT user_id) AS active_loggers
FROM public.log_entries
WHERE NOT deleted
GROUP BY day;

-- Entries per day by logging method and precision (Logging dashboard).
CREATE VIEW analytics.entries_by_method_precision AS
SELECT day, method, precision, count(*) AS entries
FROM public.log_entries
WHERE NOT deleted
GROUP BY day, method, precision;

-- How many meals a person logs in a day, as a distribution. A meal is a slot
-- with an entry or an "ate, no details" mark; uncategorized is not a meal.
CREATE VIEW analytics.meals_per_day_distribution AS
WITH slots AS (
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
    SELECT user_id, day, count(*) AS meals
    FROM slots
    GROUP BY user_id, day
)
SELECT day, meals AS meals_per_day, count(*) AS person_days
FROM per_day
GROUP BY day, meals;

-- Days closed per day (Opson Pulse).
CREATE VIEW analytics.days_closed AS
SELECT day, count(*) AS days_closed
FROM public.day_records
WHERE NOT deleted AND closed_at IS NOT NULL
GROUP BY day;

-- People by mode preset, from the one settings key the server reads.
CREATE VIEW analytics.preset_distribution AS
SELECT coalesce(settings ->> 'preset', 'unknown') AS preset, count(*) AS users
FROM public.user_settings
WHERE NOT deleted
GROUP BY 1;

-- Searches per day and how many found nothing.
CREATE VIEW analytics.search_daily AS
SELECT
    (at AT TIME ZONE 'UTC')::date AS day,
    count(*) AS searches,
    count(*) FILTER (WHERE result_count = 0) AS zero_result_searches,
    round(count(*) FILTER (WHERE result_count = 0)::numeric / count(*), 4) AS zero_result_rate
FROM public.food_search_events
GROUP BY 1;

-- What people search for and do not find, last 30 days. Queries carry no user
-- id, but a query only one or two people typed can still be personal, so a
-- query must have been searched at least 3 times to appear.
CREATE VIEW analytics.top_zero_result_queries AS
SELECT query_norm, count(*) AS searches, max(at) AS last_searched_at
FROM public.food_search_events
WHERE result_count = 0 AND at > now() - interval '30 days'
GROUP BY query_norm
HAVING count(*) >= 3
ORDER BY searches DESC, query_norm
LIMIT 100;

-- Barcode lookups per day by where the answer came from.
CREATE VIEW analytics.barcode_outcomes_daily AS
SELECT (at AT TIME ZONE 'UTC')::date AS day, outcome, count(*) AS lookups
FROM public.barcode_lookups
GROUP BY 1, 2;

-- Weekly retention: people grouped by the week of their first log, and how
-- many of them logged in each later week.
CREATE VIEW analytics.weekly_retention_cohorts AS
WITH activity AS (
    SELECT DISTINCT user_id, date_trunc('week', day)::date AS week
    FROM public.log_entries
    WHERE NOT deleted
),
firsts AS (
    SELECT user_id, min(week) AS cohort_week
    FROM activity
    GROUP BY user_id
),
sizes AS (
    SELECT cohort_week, count(*) AS cohort_size
    FROM firsts
    GROUP BY cohort_week
)
SELECT
    f.cohort_week,
    s.cohort_size,
    (a.week - f.cohort_week) / 7 AS week_offset,
    count(*) AS active_users,
    round(count(*)::numeric / s.cohort_size, 4) AS retention
FROM firsts f
JOIN activity a USING (user_id)
JOIN sizes s USING (cohort_week)
GROUP BY f.cohort_week, s.cohort_size, a.week;

-- D1 / D7 / D30: of the people whose first log was on cohort_day, how many
-- logged again exactly 1, 7 and 30 days later.
CREATE VIEW analytics.retention_d1_d7_d30 AS
WITH days AS (
    SELECT DISTINCT user_id, day
    FROM public.log_entries
    WHERE NOT deleted
),
firsts AS (
    SELECT user_id, min(day) AS cohort_day
    FROM days
    GROUP BY user_id
)
SELECT
    f.cohort_day,
    count(*) AS cohort_size,
    count(*) FILTER (WHERE EXISTS (SELECT 1 FROM days d WHERE d.user_id = f.user_id AND d.day = f.cohort_day + 1)) AS d1,
    count(*) FILTER (WHERE EXISTS (SELECT 1 FROM days d WHERE d.user_id = f.user_id AND d.day = f.cohort_day + 7)) AS d7,
    count(*) FILTER (WHERE EXISTS (SELECT 1 FROM days d WHERE d.user_id = f.user_id AND d.day = f.cohort_day + 30)) AS d30
FROM firsts f
GROUP BY f.cohort_day;

-- Nobody but grafana_ro (and the owner) reads analytics. A new schema grants
-- nothing to PUBLIC, but say so explicitly, and strip Supabase's API roles in
-- case a platform default ever changes.
REVOKE ALL ON SCHEMA analytics FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA analytics FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA analytics FROM anon';
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA analytics FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA analytics FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA analytics FROM authenticated';
    END IF;
END;
$$;

-- Roles are cluster-wide, so a second database (or a re-baselined one) may
-- already have it. Guarded so the migration is idempotent.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grafana_ro') THEN
        CREATE ROLE grafana_ro NOLOGIN;
    END IF;
END;
$$;

GRANT USAGE ON SCHEMA analytics TO grafana_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO grafana_ro;
-- Views added by later migrations are readable without another GRANT. The
-- schema only ever holds aggregate views, so this grants nothing else.
ALTER DEFAULT PRIVILEGES IN SCHEMA analytics GRANT SELECT ON TABLES TO grafana_ro;
