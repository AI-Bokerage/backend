-- E1.2: Expose plan tier through PPM so BSM can perform tier comparison without
-- reading its local plan catalog. Nullable — existing plans default to NULL until
-- backfilled; BSM treats NULL as "unknown tier" and skips the tier guard.
ALTER TABLE ppm_plans
    ADD COLUMN IF NOT EXISTS tier VARCHAR(30) NULL;
