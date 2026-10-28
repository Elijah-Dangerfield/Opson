-- ─────────────────────────────────────────────────────────────────────────────
-- V27: which of the submitter's own foods a submission came from.
--
-- Adding a packaged food saves it to the person's foods (a `custom_foods` row
-- the app made) and sends a copy for review (V25). The app marks its own copy
-- "Unverified" while the review is pending, and needs the link back to do
-- that on another phone or after a reinstall, so the app now sends the custom
-- food's id with the submission and reads it back from
-- `GET /v1/foods/submissions`.
--
-- No foreign key: the custom food syncs on its own schedule and may reach the
-- server after the submission, or never (deleted on the phone first). The
-- column is only ever read back by the same account, alongside the status.
-- Additive and idempotent; old submissions keep it null.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE food_submissions ADD COLUMN IF NOT EXISTS custom_food_id UUID;

CREATE INDEX IF NOT EXISTS food_submissions_user_created_idx ON food_submissions (user_id, created_at DESC);
