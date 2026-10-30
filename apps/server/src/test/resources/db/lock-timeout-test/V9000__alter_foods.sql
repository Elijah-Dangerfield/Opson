-- MigrationLockTimeoutTest: a catalog change like V33's, which needs an exclusive lock on foods.
ALTER TABLE foods ADD COLUMN IF NOT EXISTS lock_timeout_probe BOOLEAN;
