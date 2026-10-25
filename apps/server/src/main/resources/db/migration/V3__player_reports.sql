-- V3: Player reports — the trust-and-safety table behind an in-app
-- "Report a user" action. Google Play's UGC policy requires a report path
-- for apps with user-visible content (display names among strangers); this
-- is where those reports land.
--
-- One row per report. A reporter can file more than one report against the
-- same user (e.g. in different contexts), so there is no unique constraint
-- on the pair; the rate limit on the route is what bounds abuse. `context`
-- is a free-form nullable tag for where the report happened (a room code, a
-- chat id, a post id — whatever the app's surfaces are); `reason` is the
-- reporter's optional free text; `reason_categories` is a comma-joined list
-- of canonical category keys (harassment, spam, …) picked in the report UI.
-- Comma-joined TEXT rather than an array/child table: the volume is low and
-- a moderator filters with a LIKE, so more machinery isn't earned.
--
-- FK to auth.users ON DELETE CASCADE: deleting either account drops the
-- reports it is a party to.
--
-- CHECK forbids self-reports at the DB level so an application bug can't
-- persist one; the route rejects them with 400 first.
--
-- No auto-ban and no moderation-review UI — a human reads these later. The
-- (reported_user_id, created_at DESC) index is for that future review
-- ("show me the recent reports against this user").

CREATE TABLE player_reports (
    id                BIGSERIAL PRIMARY KEY,
    reporter_user_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    reported_user_id  UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    context           TEXT,
    reason            TEXT,
    reason_categories TEXT,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT player_reports_no_self CHECK (reporter_user_id <> reported_user_id)
);

CREATE INDEX player_reports_reported_idx
    ON player_reports (reported_user_id, created_at DESC);
