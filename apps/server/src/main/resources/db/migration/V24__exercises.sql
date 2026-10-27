-- ─────────────────────────────────────────────────────────────────────────────
-- V24: exercise logged by hand (behind the client's release.exercise flag).
--
-- One row per entry, keyed by the client's id. kcal is what the entry counts
-- in Burned, worked out on the device (MET, length and the latest weigh-in)
-- or typed, and stored so a later weigh-in never rewrites a past day. The
-- activity is a key from the client's list or `other`, with a name for the
-- latter. See docs/research/energy-expenditure.md.
--
-- The checks back up DiaryRules, which refuses the same rows with a readable
-- message: at most a day long, at most 5000 kcal.
--
-- Additive only, so 0.1 clients are untouched: they never send an exercise,
-- and /v1/sync only returns exercises to a request that accepts them. Written
-- to be safe to run twice.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS exercises (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    day               DATE NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    activity          TEXT NOT NULL CHECK (length(activity) BETWEEN 1 AND 40),
    name              TEXT CHECK (name IS NULL OR length(name) <= 80),
    minutes           SMALLINT NOT NULL CHECK (minutes BETWEEN 1 AND 1440),
    kcal              INTEGER NOT NULL CHECK (kcal BETWEEN 0 AND 5000),
    kcal_typed        BOOLEAN NOT NULL DEFAULT FALSE,
    not_on_watch      BOOLEAN NOT NULL DEFAULT FALSE,
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS exercises_user_seq_idx ON exercises (user_id, server_seq);

DROP TRIGGER IF EXISTS exercises_bump_server_seq ON public.exercises;
CREATE TRIGGER exercises_bump_server_seq BEFORE INSERT OR UPDATE ON public.exercises
    FOR EACH ROW EXECUTE FUNCTION public.bump_server_seq();

-- As on every table since V7, and on the early ones since V19: RLS with no
-- policies. The server connects as the owner, which RLS does not restrict;
-- this is what stops the anon key reading the table through PostgREST.
ALTER TABLE public.exercises ENABLE ROW LEVEL SECURITY;
