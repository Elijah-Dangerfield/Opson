-- ─────────────────────────────────────────────────────────────────────────────
-- V32: Builders by application (docs/decisions.md, 2026-10-29, "Builders apply").
--
-- V31 let anyone who passed the gates join Builders on their own. Now they
-- apply, and the owner accepts or declines in the admin console. Accepting is
-- what writes `builder_members` and sets the `builder` role, so nobody becomes
-- a Builder without a person saying yes. Builders who joined under V31 keep
-- their row and their role.
--
-- `builder_applications` is one row per application, kept after it is
-- decided so a second application can be read against the first. At most one
-- is pending per account (a partial unique index); a declined one can be
-- followed by another once BuilderRules.REAPPLY_AFTER has passed. What they
-- wrote about why they want to help (`motivation`) is free text the owner
-- reads in the console and nothing else does: the Grafana views leave it out.
-- `shopping` and `region` are choices from short lists the server checks
-- (region decides which label format they will mostly photograph). `via` is
-- where they applied from (`more`, `prompt`, `report`, or `join` for an app
-- from before applications, whose Join now applies).
--
-- Owned by the account (cascade), like every other per-person table. RLS on
-- with no policies, as on every table. Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS builder_applications (
    id               UUID        PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    status           TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'declined', 'withdrawn')),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    terms_version    TEXT        NOT NULL CHECK (char_length(terms_version) <= 32),
    photo_retention  BOOLEAN     NOT NULL DEFAULT FALSE,
    motivation       TEXT        CHECK (char_length(motivation) <= 500),
    shopping         TEXT        CHECK (char_length(shopping) <= 16),
    region           TEXT        CHECK (char_length(region) <= 16),
    via              TEXT        CHECK (char_length(via) <= 16),
    decided_at       TIMESTAMPTZ,
    decided_by       TEXT        CHECK (char_length(decided_by) <= 200),
    decision_note    TEXT        CHECK (char_length(decision_note) <= 500),
    withdrawn_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS builder_applications_one_pending ON builder_applications (user_id) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS builder_applications_queue_idx ON builder_applications (created_at) WHERE status = 'pending';
CREATE INDEX IF NOT EXISTS builder_applications_user_idx ON builder_applications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS builder_applications_decided_idx ON builder_applications (decided_at DESC) WHERE decided_at IS NOT NULL;

ALTER TABLE builder_applications ENABLE ROW LEVEL SECURITY;

-- ── Views ───────────────────────────────────────────────────────────────────

-- Applications per day: sent, and how they ended. Counts only.
CREATE OR REPLACE VIEW analytics.builder_applications_daily AS
SELECT day, sum(sent) AS sent, sum(accepted) AS accepted, sum(declined) AS declined, sum(withdrawn) AS withdrawn
FROM (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day, 1 AS sent, 0 AS accepted, 0 AS declined, 0 AS withdrawn FROM public.builder_applications
    UNION ALL
    SELECT (decided_at AT TIME ZONE 'UTC')::date, 0, 1, 0, 0 FROM public.builder_applications WHERE status = 'accepted' AND decided_at IS NOT NULL
    UNION ALL
    SELECT (decided_at AT TIME ZONE 'UTC')::date, 0, 0, 1, 0 FROM public.builder_applications WHERE status = 'declined' AND decided_at IS NOT NULL
    UNION ALL
    SELECT (withdrawn_at AT TIME ZONE 'UTC')::date, 0, 0, 0, 1 FROM public.builder_applications WHERE withdrawn_at IS NOT NULL
) a
GROUP BY day;

-- The queue, oldest first, for the Builders board: the choices and how long each has waited,
-- never what anyone wrote.
CREATE OR REPLACE VIEW support.builder_applications AS
SELECT
    a.id,
    a.user_id,
    a.status,
    a.created_at,
    a.decided_at,
    a.decided_by,
    a.region,
    a.shopping,
    a.via,
    a.photo_retention,
    a.motivation IS NOT NULL AS wrote_motivation,
    u.created_at AS account_created_at,
    extract(epoch FROM (coalesce(a.decided_at, now()) - a.created_at)) / 3600 AS hours_waiting
FROM public.builder_applications a
JOIN auth.users u ON u.id = a.user_id;

GRANT SELECT ON analytics.builder_applications_daily TO grafana_ro;
GRANT SELECT ON support.builder_applications TO grafana_ro;
