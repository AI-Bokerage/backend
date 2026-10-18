-- CRIT-1: Add optimistic-locking version column to dunning_attempts.
-- Without this column two scheduler pods can both fetch the same PENDING attempt,
-- both call the payment provider, and produce a double charge.
-- DEFAULT 0 ensures existing rows are valid on the first read.
ALTER TABLE dunning_attempts
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
