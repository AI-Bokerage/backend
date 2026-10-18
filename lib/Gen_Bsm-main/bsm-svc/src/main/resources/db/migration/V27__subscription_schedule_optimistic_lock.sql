-- Add optimistic locking to subscription_schedules.
-- Without @Version, two concurrent scheduler pods could both read a PENDING
-- schedule, both execute the action, and both write EXECUTED status — causing
-- double-execution (e.g. two downgrades applied, two cancellations fired).
-- Adding a version column allows JPA @Version to detect and reject the second
-- concurrent write with ObjectOptimisticLockingFailureException.
ALTER TABLE subscription_schedules
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
