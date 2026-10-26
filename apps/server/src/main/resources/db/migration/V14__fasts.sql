-- ─────────────────────────────────────────────────────────────────────────────
-- V14: fasts (0.2, behind the client's release.fasting flag).
--
-- One row per fast, keyed by the local day it began: a fast starts when the
-- eating window closes, so there is at most one per evening, and two devices
-- that both start it write the same row. ended_at is null until someone ends
-- it (End fast, logging food, the timer going off); reaching the goal is
-- derived on the device and never written.
--
-- Safety rules the table backs up: no goal over 16 hours and no fast longer
-- than 16 hours (DiaryRules checks the same, with a readable rejection).
-- TODO(clinician): confirm the 16 hour cap.
--
-- Additive only, so 0.1 clients are untouched: they never send a fast, and
-- /v1/sync only returns fasts to a request that accepts them. Written to be
-- safe to run twice.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS fasts (
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    day               DATE NOT NULL,
    started_at        TIMESTAMPTZ NOT NULL,
    ended_at          TIMESTAMPTZ,
    goal_hours        SMALLINT CHECK (goal_hours BETWEEN 1 AND 16),
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (user_id, day),
    CHECK (ended_at IS NULL OR (ended_at >= started_at AND ended_at <= started_at + INTERVAL '16 hours'))
);

CREATE INDEX IF NOT EXISTS fasts_user_seq_idx ON fasts (user_id, server_seq);

DROP TRIGGER IF EXISTS fasts_bump_server_seq ON public.fasts;
CREATE TRIGGER fasts_bump_server_seq BEFORE INSERT OR UPDATE ON public.fasts
    FOR EACH ROW EXECUTE FUNCTION public.bump_server_seq();

-- The server connects as the owner; RLS with no policies is what stops the
-- anon key reading the table if a client is ever pointed at it directly.
ALTER TABLE public.fasts ENABLE ROW LEVEL SECURITY;
