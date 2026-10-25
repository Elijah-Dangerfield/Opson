-- The reference table: one row per user, keyed by the Supabase auth user id
-- (the JWT `sub` claim).
--
-- No FK to auth.users yet — that's added in V2 once auth is wired, so this
-- migration stands alone and Testcontainers needs no auth schema to run it.
-- See apps/server/README.md → Database.
CREATE TABLE profiles (
    user_id      UUID PRIMARY KEY,
    display_name TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Display names are unique. The repository attempts the write and treats the
-- unique violation (SQLSTATE 23505) as the arbiter rather than pre-checking.
CREATE UNIQUE INDEX profiles_display_name_uq ON profiles (display_name);
