-- ─────────────────────────────────────────────────────────────────────────────
-- V36: who saved a shared recipe, so a save counts once per person.
--
-- `recipes.save_count` (V35) ranks search and feeds the Pulse board, and every
-- POST /v1/recipes/public/{code}/saves added one, so one person tapping Save
-- again (or a script) could push a recipe up search. A save is now a row here,
-- one per recipe and person, and only the first one adds to save_count. The
-- owner saving their own recipe adds nothing. Counts already made stay.
--
-- Both keys cascade: a recipe deleted takes its saves, and an account deleted
-- takes the saves it made (its recipe keeps the count). RLS on with no
-- policies, like the rest. Additive and idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS recipe_saves (
    recipe_id  UUID NOT NULL REFERENCES recipes (id) ON DELETE CASCADE,
    user_id    UUID NOT NULL REFERENCES auth.users (id) ON DELETE CASCADE,
    saved_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (recipe_id, user_id)
);

-- The account delete's cascade looks saves up by person.
CREATE INDEX IF NOT EXISTS recipe_saves_user_idx ON recipe_saves (user_id);

ALTER TABLE public.recipe_saves ENABLE ROW LEVEL SECURITY;
