-- ─────────────────────────────────────────────────────────────────────────────
-- V22: which builds each person runs, and the `support` schema the admin
-- console's user lookup and Grafana's Users and User detail boards read.
--
-- user_installs: one row per (user, install, build), stamped by the server
-- the first and the latest time that build made a signed-in request. It is
-- the version history the admin lookup shows, and the install ids the User
-- detail board uses to find someone's app events in Loki. Written by
-- PostgresUserInstallRepository, at most every few minutes per row.
--
-- `support` is the one place a dashboard sees a single person. `analytics`
-- (V9) stays aggregate-only, and its rule is unchanged. The line here is
-- different and just as structural: these views carry an account's id, its
-- email and display name (the owner's CRM, decided 2026-10-27 in
-- docs/decisions.md), counts per day, timestamps and the settings someone
-- chose, and nothing they wrote or measured. No food name, amount, nutrient,
-- weight, water, note, feeling, check-in answer, onboarding answer, target or
-- fast length appears in any view. A column that would show one is a review
-- blocker, as it is for `analytics`.
--
-- Views run with their owner's rights, so grafana_ro reads them without a
-- grant on public or auth. Additive and idempotent: IF NOT EXISTS and
-- CREATE OR REPLACE throughout, so this can also be applied by hand.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS user_installs (
    user_id       UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    -- '' when the request carried no X-Install-Id (an install not yet hydrated).
    install_id    TEXT        NOT NULL,
    platform      TEXT        NOT NULL,
    app_version   TEXT        NOT NULL,
    build_number  INTEGER     NOT NULL,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, install_id, app_version, build_number)
);

CREATE INDEX IF NOT EXISTS user_installs_last_seen_idx ON user_installs (last_seen_at);

ALTER TABLE public.user_installs ENABLE ROW LEVEL SECURITY;

CREATE SCHEMA IF NOT EXISTS support;

-- One row per account: who it is, what it runs, how much it has logged.
CREATE OR REPLACE VIEW support.users AS
WITH latest_install AS (
    SELECT DISTINCT ON (user_id) user_id, platform, app_version, build_number, last_seen_at
    FROM public.user_installs
    ORDER BY user_id, last_seen_at DESC
),
installs AS (
    SELECT user_id, min(first_seen_at) AS first_seen_at, count(DISTINCT install_id) AS installs
    FROM public.user_installs
    GROUP BY user_id
),
logs AS (
    SELECT user_id,
           count(*) AS entries,
           count(DISTINCT day) AS days_logged,
           min(day) AS first_log_day,
           max(day) AS last_log_day,
           max(client_updated_at) AS last_entry_change_at
    FROM public.log_entries
    WHERE NOT deleted
    GROUP BY user_id
)
SELECT
    u.id AS user_id,
    u.email,
    coalesce(u.is_anonymous, false) AS is_guest,
    u.created_at AS joined_at,
    u.last_sign_in_at,
    p.display_name,
    coalesce(s.settings ->> 'preset', s.settings -> 'numbers' ->> 'preset') AS preset,
    li.platform,
    li.app_version,
    li.build_number,
    li.last_seen_at,
    i.first_seen_at,
    coalesce(i.installs, 0) AS installs,
    coalesce(l.entries, 0) AS entries,
    coalesce(l.days_logged, 0) AS days_logged,
    l.first_log_day,
    l.last_log_day,
    greatest(l.last_entry_change_at, s.client_updated_at) AS last_synced_change_at
FROM auth.users u
LEFT JOIN public.profiles p ON p.user_id = u.id
LEFT JOIN public.user_settings s ON s.user_id = u.id AND NOT s.deleted
LEFT JOIN latest_install li ON li.user_id = u.id
LEFT JOIN installs i ON i.user_id = u.id
LEFT JOIN logs l ON l.user_id = u.id;

-- Every build every install of an account has run, newest first in use.
CREATE OR REPLACE VIEW support.user_installs AS
SELECT user_id, install_id, platform, app_version, build_number, first_seen_at, last_seen_at
FROM public.user_installs;

-- Entries per person per diary day, by how they were added. Counts only.
CREATE OR REPLACE VIEW support.user_daily_logging AS
SELECT
    user_id,
    day,
    count(*) AS entries,
    count(DISTINCT slot) FILTER (WHERE slot <> 'uncategorized') AS meals,
    count(*) FILTER (WHERE method = 'search') AS search,
    count(*) FILTER (WHERE method = 'barcode') AS barcode,
    count(*) FILTER (WHERE method = 'describe') AS describe,
    count(*) FILTER (WHERE method = 'saved') AS saved,
    count(*) FILTER (WHERE method = 'copy') AS copy,
    count(*) FILTER (WHERE method = 'recent') AS recent,
    count(*) FILTER (WHERE method = 'favorite') AS favorite,
    count(*) FILTER (WHERE method = 'custom') AS custom,
    count(*) FILTER (WHERE method = 'quick_number') AS quick_number,
    count(*) FILTER (WHERE method = 'ate_no_details') AS ate_no_details,
    count(*) FILTER (WHERE precision = 'weighed') AS weighed,
    count(*) FILTER (WHERE precision = 'estimate') AS estimated,
    count(*) FILTER (WHERE precision = 'named') AS named_only,
    -- V21: amounts read off a nutrition label.
    count(*) FILTER (WHERE precision = 'label') AS from_label
FROM public.log_entries
WHERE NOT deleted
GROUP BY user_id, day;

-- What else someone did on a day, as yes/no and counts: closed it, checked in,
-- tagged how meals felt, weighed in, fasted. Never what they answered or
-- measured.
CREATE OR REPLACE VIEW support.user_daily_marks AS
SELECT
    user_id,
    day,
    bool_or(closed) AS closed,
    bool_or(checked_in) AS checked_in,
    sum(meals_tagged)::integer AS meals_tagged,
    sum(weigh_ins)::integer AS weigh_ins,
    bool_or(fasted) AS fasted
FROM (
    SELECT user_id, day, closed_at IS NOT NULL AS closed, check_in IS NOT NULL AS checked_in,
           0 AS meals_tagged, 0 AS weigh_ins, false AS fasted
    FROM public.day_records WHERE NOT deleted
    UNION ALL
    SELECT user_id, day, false, false, 1, 0, false
    FROM public.meal_feelings WHERE NOT deleted
    UNION ALL
    SELECT user_id, (measured_at AT TIME ZONE 'UTC')::date, false, false, 0, 1, false
    FROM public.weigh_ins WHERE NOT deleted
    UNION ALL
    SELECT user_id, day, false, false, 0, 0, true
    FROM public.fasts WHERE NOT deleted
) marks
GROUP BY user_id, day;

-- The choices someone made in Settings, key by key from an allowlist. Targets,
-- onboarding answers, favourite foods, units and meal times stay out.
CREATE OR REPLACE VIEW support.user_settings AS
SELECT
    user_id,
    coalesce(settings ->> 'preset', settings -> 'numbers' ->> 'preset') AS preset,
    coalesce(settings -> 'numbers' -> 'overrides', '{}'::jsonb) <> '{}'::jsonb
        OR (settings ->> 'progressPhotos') IS NOT NULL AS adjusted,
    coalesce(settings -> 'numbers' -> 'overrides', '{}'::jsonb)::text AS visibility_overrides,
    (settings -> 'numbers' ->> 'pauseBeforeMore')::boolean AS pause_before_more,
    to_timestamp((settings -> 'numbers' ->> 'changedAtEpochMs')::double precision / 1000) AS numbers_changed_at,
    changed_at AS settings_changed_at,
    client_updated_at AS settings_synced_at,
    settings ->> 'theme' AS theme,
    settings ->> 'textSize' AS text_size,
    settings ->> 'tone' AS tone,
    settings ->> 'macroMode' AS macro_mode,
    settings ->> 'ringMode' AS ring_mode,
    (settings ->> 'mealGroups')::boolean AS meal_groups,
    (settings ->> 'askTags')::boolean AS ask_tags,
    (settings ->> 'checkIn')::boolean AS check_in,
    (settings ->> 'fasting')::boolean AS fasting,
    (settings ->> 'remindersChosen')::boolean AS reminders_chosen,
    (settings ->> 'waterReminders')::boolean AS water_reminders,
    settings ->> 'searchSort' AS search_sort,
    settings ->> 'searchDatabase' AS search_database,
    settings ->> 'weekStart' AS week_start
FROM public.user_settings
WHERE NOT deleted;

-- Same access rule as `analytics`: grafana_ro and the owner, nobody else.
REVOKE ALL ON SCHEMA support FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA support FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA support FROM anon';
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA support FROM anon';
        EXECUTE 'REVOKE ALL ON TABLE public.user_installs FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON SCHEMA support FROM authenticated';
        EXECUTE 'REVOKE ALL ON ALL TABLES IN SCHEMA support FROM authenticated';
        EXECUTE 'REVOKE ALL ON TABLE public.user_installs FROM authenticated';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'grafana_ro') THEN
        CREATE ROLE grafana_ro NOLOGIN;
    END IF;
END;
$$;

GRANT USAGE ON SCHEMA support TO grafana_ro;
GRANT SELECT ON ALL TABLES IN SCHEMA support TO grafana_ro;
ALTER DEFAULT PRIVILEGES IN SCHEMA support GRANT SELECT ON TABLES TO grafana_ro;
