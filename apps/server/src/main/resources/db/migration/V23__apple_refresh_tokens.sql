-- ─────────────────────────────────────────────────────────────────────────────
-- V21: the Sign in with Apple refresh token per account, kept only so that
-- deleting the account can revoke it (App Review asks this of every app that
-- offers Sign in with Apple and account deletion).
--
-- The app sends Apple's one-use authorization code right after signing in, and
-- the server swaps it for this token. With the server's Apple key it yields
-- only a fresh Apple identity token, nothing Sign in with Apple didn't already
-- give us. Still a credential, so no analytics view reads it, and RLS is on
-- with no policies like every other table.
--
-- The row goes with the account (ON DELETE CASCADE), so DELETE /v1/me reads
-- it before deleting the auth user.
--
-- Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS apple_refresh_tokens (
    user_id       UUID PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    refresh_token TEXT NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

ALTER TABLE apple_refresh_tokens ENABLE ROW LEVEL SECURITY;
