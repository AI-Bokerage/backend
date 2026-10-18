-- E1.4.4: Drop BSM plan catalog tables.
-- PPM-SVC is now the sole source of plan/version truth.

-- Step 1: Drop FK from subscription_schedules → plan_versions
ALTER TABLE subscription_schedules
    DROP CONSTRAINT IF EXISTS fk_subscription_schedules_target_plan_version;

-- Step 2: Drop FKs from subscription_history → plan_versions
ALTER TABLE subscription_history
    DROP CONSTRAINT IF EXISTS fk_subscription_history_from_plan_version;
ALTER TABLE subscription_history
    DROP CONSTRAINT IF EXISTS fk_subscription_history_to_plan_version;

-- Step 3: Drop FK from subscriptions → plan_versions and make column nullable
ALTER TABLE subscriptions
    DROP CONSTRAINT IF EXISTS fk_subscriptions_plan_version;
ALTER TABLE subscriptions
    ALTER COLUMN plan_version_id DROP NOT NULL;

-- Step 4: Drop FK from plan_versions → plans
ALTER TABLE plan_versions
    DROP CONSTRAINT IF EXISTS fk_plan_versions_plan;

-- Step 5: Drop indexes on plan_versions
DROP INDEX IF EXISTS idx_plan_versions_plan_id;

-- Step 6: Drop plan_versions table (CASCADE handles any remaining dependencies)
DROP TABLE IF EXISTS plan_versions CASCADE;

-- Step 7: Drop plans table
DROP TABLE IF EXISTS plans CASCADE;
