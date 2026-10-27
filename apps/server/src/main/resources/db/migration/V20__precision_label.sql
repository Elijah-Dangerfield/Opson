-- ─────────────────────────────────────────────────────────────────────────────
-- V20: "From label" as a precision on log entries (design sync 4, the food
-- detail's How sure: Estimate, Weighed, From label).
--
-- V8 wrote the check inline, so Postgres named it log_entries_precision_check.
-- Widening it is a catalog change plus one read of the table to validate.
-- Clients before 0.2 never send 'label', and read one they pull as an
-- estimate (Precision.fromWire falls back), so nothing they do changes.
--
-- Idempotent: the drop and the add are both guarded, so running it by hand
-- before Flyway sees it is safe.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE log_entries DROP CONSTRAINT IF EXISTS log_entries_precision_check;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'log_entries_precision_check') THEN
        ALTER TABLE log_entries ADD CONSTRAINT log_entries_precision_check
            CHECK (precision IN ('weighed', 'estimate', 'named', 'label'));
    END IF;
END $$;
