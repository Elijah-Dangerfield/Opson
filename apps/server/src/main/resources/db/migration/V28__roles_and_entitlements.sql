-- ─────────────────────────────────────────────────────────────────────────────
-- V28: roles, per-account entitlement grants, and the trail of both.
--
-- Entitlements are named capabilities (`photo_estimate`, `share_friends`, …).
-- The server resolves an account's set as the union of
--   1. `entitlements.everyone` (remote config, seeded below),
--   2. the bundles its role brings (`entitlements.roles`, e.g. builder → pro),
--   3. its own grants here, of one entitlement or of a whole bundle
--      (`entitlements.bundles`, e.g. pro → [...]),
-- minus its revocations here. Pro is a bundle, not a flag on the account, so a
-- feature can move between Free and Pro, or to one person, with no release.
--
-- `user_roles` holds the few accounts that are more than a member: owner,
-- admin, moderator, builder. No row means member. What each role may do is in
-- code (Role.permissions); which bundles it brings is config.
--
-- `entitlement_grants` rows are never deleted by the admin API, only marked
-- removed, so the history of what someone was given stays readable. A revoke
-- names one entitlement (taking away a bundle is removing its grant). `source`
-- is a short tag for where a grant came from: manual, founding, builder, comp,
-- and later app_store / play from verified purchases.
--
-- `access_audit` records every role and grant change with the row before and
-- after, as `app_config_audit` does for config. Edits to the `entitlements.*`
-- flags themselves are config edits and are audited there.
--
-- All three belong to the account: deleting it deletes them (cascade), as with
-- every other user-owned table. Nobody is given a role here; the owner's role is
-- set after deploy through the admin API (apps/server/README.md, "Roles and
-- entitlements"), so no account id or email is ever committed.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS user_roles (
    user_id    UUID        PRIMARY KEY REFERENCES auth.users (id) ON DELETE CASCADE,
    role       TEXT        NOT NULL CHECK (role IN ('owner', 'admin', 'moderator', 'builder')),
    granted_by TEXT        NOT NULL CHECK (char_length(granted_by) <= 200),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reason     TEXT        CHECK (char_length(reason) <= 500)
);

CREATE INDEX IF NOT EXISTS user_roles_role_idx ON user_roles (role);

ALTER TABLE user_roles ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS entitlement_grants (
    id         UUID        PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    kind       TEXT        NOT NULL CHECK (kind IN ('entitlement', 'bundle')),
    key        TEXT        NOT NULL CHECK (key ~ '^[a-z][a-z0-9_]{0,63}$'),
    effect     TEXT        NOT NULL DEFAULT 'grant' CHECK (effect IN ('grant', 'revoke')),
    source     TEXT        NOT NULL DEFAULT 'manual' CHECK (source ~ '^[a-z][a-z0-9_]{0,31}$'),
    reason     TEXT        CHECK (char_length(reason) <= 500),
    granted_by TEXT        NOT NULL CHECK (char_length(granted_by) <= 200),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ,
    removed_at TIMESTAMPTZ,
    removed_by TEXT        CHECK (char_length(removed_by) <= 200),
    CHECK (effect = 'grant' OR kind = 'entitlement')
);

-- The hot read: one account's grants still in force, on every gated request.
CREATE INDEX IF NOT EXISTS entitlement_grants_user_active_idx
    ON entitlement_grants (user_id) WHERE removed_at IS NULL;

ALTER TABLE entitlement_grants ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS access_audit (
    id           UUID        PRIMARY KEY,
    at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor        TEXT        NOT NULL,
    action       TEXT        NOT NULL,
    user_id      UUID        NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    before_jsonb JSONB,
    after_jsonb  JSONB
);

CREATE INDEX IF NOT EXISTS access_audit_user_at_idx ON access_audit (user_id, at DESC);
CREATE INDEX IF NOT EXISTS access_audit_at_idx ON access_audit (at DESC);

ALTER TABLE access_audit ENABLE ROW LEVEL SECURITY;

-- The policy, as config. Photo estimates are free for everyone today, so they go
-- in `everyone` and nothing changes on deploy. An environment that already has
-- a row keeps it.
INSERT INTO app_config_values (path, value_jsonb) VALUES
    ('entitlements.everyone', '["photo_estimate"]'::jsonb),
    ('entitlements.bundles',  '{"pro": ["photo_estimate", "share_professional", "share_friends", "adaptive_burn", "long_range_reports", "recipe_import"]}'::jsonb),
    ('entitlements.roles',    '{"owner": ["pro"], "admin": ["pro"], "builder": ["pro"]}'::jsonb)
ON CONFLICT (path) DO NOTHING;
