-- ─────────────────────────────────────────────────────────────────────────────
-- V19: row level security on the tables created before V7.
--
-- V7 onward enable RLS on every table they create, and the review checklist
-- asks for it on every table, but V1 to V6 predate the rule: profiles,
-- player_reports and the four app_config tables have it off. On Supabase,
-- tables the postgres role creates in `public` are granted to the anon and
-- authenticated roles by default, so with RLS off anyone holding the app's
-- anon key could read every user's display name through PostgREST, and write
-- app_config_values: switch on maintenance mode, or raise the minimum
-- supported version, for everyone.
--
-- Enabled with no policies, as on every other table. The server connects as
-- the owner, which RLS does not restrict, and the analytics views run with
-- their owner's rights, so nothing the server or Grafana reads changes.
--
-- flyway_schema_history is left alone: Flyway holds a lock on it on another
-- connection while a migration runs, so altering it here would deadlock.
--
-- Additive and idempotent: enabling RLS that is already on is a no-op.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.player_reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_config_values ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_config_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_config_audit ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_config_manifest ENABLE ROW LEVEL SECURITY;
