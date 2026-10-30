-- ─────────────────────────────────────────────────────────────────────────────
-- V34: recipes, and the ones people share (behind the client's release.recipes).
--
-- A recipe is ingredients with amounts for the whole batch, how many servings
-- it makes, and optional notes. It syncs like any diary row (keyed by the
-- client's id, last write wins on client_updated_at, server_seq for the pull),
-- and /v1/sync only returns recipes to a request that accepts `recipe`, so
-- older clients never see one. `items` is the client's array of ingredient
-- snapshots, the same shape as a saved meal's; `saved_from` is the share code
-- of the recipe it was saved from, so a copy of someone else's stays marked
-- as theirs.
--
-- Sharing is server state on the same row, written only by the publish routes
-- and the admin console, never by sync: `public_code` (8 characters, kept when
-- the owner stops sharing so a printed QR code works again if they share
-- again), `public_state` (`private`, `live`, `hidden` while reports wait for a
-- person, `removed` by a moderator) and whether to show the owner's display
-- name. Changing them bumps server_seq like any update, so the owner's other
-- phones learn a recipe is shared.
--
-- `recipe_reports` are reports on a shared recipe. Three open ones from
-- different people hide it until someone looks.
--
-- Owned by the account: deleting it deletes its recipes and its reports
-- (cascade), and a recipe's reports go with the recipe. RLS on with no
-- policies, as on every table. The analytics views carry counts only.
-- Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS recipes (
    id                UUID PRIMARY KEY,
    user_id           UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    name              TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 200),
    servings          REAL NOT NULL CHECK (servings > 0 AND servings <= 1000),
    items             JSONB NOT NULL DEFAULT '[]'::jsonb,
    notes             TEXT CHECK (char_length(notes) <= 4000),
    -- The share code this one was saved from, when someone saved another person's recipe.
    saved_from        TEXT CHECK (saved_from ~ '^[2-9a-z]{8}$'),
    client_updated_at TIMESTAMPTZ NOT NULL,
    server_seq        BIGINT NOT NULL DEFAULT nextval('public.sync_seq'),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    -- When the server first saw it. Sync never writes it.
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    public_code       TEXT UNIQUE CHECK (public_code ~ '^[2-9a-z]{8}$'),
    public_state      TEXT NOT NULL DEFAULT 'private' CHECK (public_state IN ('private', 'live', 'hidden', 'removed')),
    public_show_name  BOOLEAN NOT NULL DEFAULT FALSE,
    published_at      TIMESTAMPTZ,
    removed_at        TIMESTAMPTZ,
    removed_by        TEXT CHECK (char_length(removed_by) <= 100),
    removed_reason    TEXT CHECK (char_length(removed_reason) <= 500),
    save_count        INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS recipes_user_seq_idx ON recipes (user_id, server_seq);
CREATE INDEX IF NOT EXISTS recipes_live_name_trgm_idx ON recipes USING gin (lower(name) extensions.gin_trgm_ops)
    WHERE public_state = 'live' AND NOT deleted;

DROP TRIGGER IF EXISTS recipes_bump_server_seq ON public.recipes;
CREATE TRIGGER recipes_bump_server_seq BEFORE INSERT OR UPDATE ON public.recipes
    FOR EACH ROW EXECUTE FUNCTION public.bump_server_seq();

ALTER TABLE public.recipes ENABLE ROW LEVEL SECURITY;

CREATE TABLE IF NOT EXISTS recipe_reports (
    id          UUID PRIMARY KEY,
    recipe_id   UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    user_id     UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    reason      TEXT NOT NULL CHECK (reason IN ('wrong_nutrition', 'unsafe', 'offensive', 'spam', 'other')),
    note        TEXT CHECK (char_length(note) <= 1000),
    status      TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'upheld', 'dismissed')),
    decided_at  TIMESTAMPTZ,
    decided_by  TEXT CHECK (char_length(decided_by) <= 100)
);

-- One open report per person per recipe: a second tap is the same report.
CREATE UNIQUE INDEX IF NOT EXISTS recipe_reports_open_uq ON recipe_reports (recipe_id, user_id) WHERE status = 'open';
CREATE INDEX IF NOT EXISTS recipe_reports_user_idx ON recipe_reports (user_id);

ALTER TABLE public.recipe_reports ENABLE ROW LEVEL SECURITY;

-- Recipes made and first shared per day, for the Pulse board. Counts only.
CREATE OR REPLACE VIEW analytics.recipes_daily AS
SELECT day, sum(made)::bigint AS made, sum(shared)::bigint AS shared
FROM (
    SELECT (created_at AT TIME ZONE 'UTC')::date AS day, 1 AS made, 0 AS shared FROM public.recipes
    UNION ALL
    SELECT (published_at AT TIME ZONE 'UTC')::date, 0, 1 FROM public.recipes WHERE published_at IS NOT NULL
) e
GROUP BY day;

-- Where shared recipes stand now, and how many reports wait. Counts only.
CREATE OR REPLACE VIEW analytics.recipe_sharing AS
SELECT
    count(*) FILTER (WHERE NOT deleted) AS recipes,
    count(*) FILTER (WHERE public_state = 'live' AND NOT deleted) AS live,
    count(*) FILTER (WHERE public_state = 'hidden' AND NOT deleted) AS hidden,
    count(*) FILTER (WHERE public_state = 'removed') AS removed,
    coalesce(sum(save_count) FILTER (WHERE public_state = 'live' AND NOT deleted), 0) AS saves,
    (SELECT count(*) FROM public.recipe_reports WHERE status = 'open') AS open_reports,
    (SELECT min(created_at) FROM public.recipe_reports WHERE status = 'open') AS oldest_open_report_at
FROM public.recipes;

GRANT SELECT ON analytics.recipes_daily TO grafana_ro;
GRANT SELECT ON analytics.recipe_sharing TO grafana_ro;
