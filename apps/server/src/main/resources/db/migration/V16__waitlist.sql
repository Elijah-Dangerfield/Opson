-- ─────────────────────────────────────────────────────────────────────────────
-- V16: the website waitlist (docs/plans/website.md).
--
-- One row per address that asked to hear when Opson can be downloaded,
-- written only by the server through POST /v1/waitlist:
--   email            stored lower-cased; unique ignoring case, so a second
--                    sign-up is the same row and the route can answer 204
--                    either way without revealing who is on the list
--   source           which page or campaign, `^[a-z0-9-]{1,32}$`, or null
--   unsubscribed_at  set when someone asks for no more email but the row is
--                    kept; the export leaves these out. A removal request
--                    deletes the row instead (the site promises deletion).
--
-- Nothing else about the person is kept: no IP, no user agent, no user id.
--
-- Additive and idempotent (IF NOT EXISTS, CREATE OR REPLACE), so running it
-- twice, or against a database that got it by hand first, changes nothing.
-- RLS on with no policies, as on every table: only the server (the owner)
-- reads or writes it.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS waitlist (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email            TEXT NOT NULL CHECK (char_length(email) <= 254),
    source           TEXT CHECK (source ~ '^[a-z0-9-]{1,32}$'),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    unsubscribed_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS waitlist_email_lower_key ON waitlist (lower(email));
CREATE INDEX IF NOT EXISTS waitlist_created_at_idx ON waitlist (created_at);

ALTER TABLE waitlist ENABLE ROW LEVEL SECURITY;

-- Sign-ups per day for the Pulse board. A count only: no email, no source.
CREATE OR REPLACE VIEW analytics.waitlist_daily AS
SELECT
    (created_at AT TIME ZONE 'UTC')::date AS day,
    count(*) AS signups
FROM public.waitlist
GROUP BY 1;

-- V9's default privileges already cover views created by the migration role;
-- granted explicitly so a database where someone else ran this still works.
GRANT SELECT ON analytics.waitlist_daily TO grafana_ro;
