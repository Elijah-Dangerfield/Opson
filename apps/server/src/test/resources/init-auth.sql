-- Minimal stub of Supabase's auth.users, created before Flyway runs so the V2
-- foreign key has something to point at. The real table (owned by Supabase Auth)
-- has dozens more columns; we only need `id` (for the FK) and `is_anonymous`
-- (read by auth-aware queries). Production uses the real table and never runs
-- this script.
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id           UUID PRIMARY KEY,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    -- Read by the ban gate (ModerationRepository). Supabase's dashboard/Admin
    -- API sets this on the real table; the stub needs it so moderation reads
    -- work against local/test Postgres.
    banned_until TIMESTAMPTZ
);
