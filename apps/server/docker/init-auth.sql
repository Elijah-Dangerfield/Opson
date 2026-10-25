-- Mounted into the local docker-compose Postgres (see ../docker-compose.yml) so
-- the V2 foreign key to auth.users resolves on a plain Postgres. Mirrors
-- src/test/resources/init-auth.sql. Production points at Supabase, whose real
-- auth.users already exists, so this is dev/test-only.
CREATE SCHEMA IF NOT EXISTS auth;

CREATE TABLE IF NOT EXISTS auth.users (
    id           UUID PRIMARY KEY,
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    -- Read by the ban gate (ModerationRepository). Supabase's dashboard/Admin
    -- API sets this on the real table; the stub needs it so moderation reads
    -- work against local/test Postgres.
    banned_until TIMESTAMPTZ
);
