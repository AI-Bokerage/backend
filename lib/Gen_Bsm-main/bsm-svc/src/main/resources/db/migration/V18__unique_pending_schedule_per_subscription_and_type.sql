-- V18: Prevent duplicate PENDING schedules of the same type for the same subscription.
-- Uses a partial unique index so completed/cancelled schedules are not affected.
CREATE UNIQUE INDEX IF NOT EXISTS uq_one_pending_schedule_per_subscription_type
    ON subscription_schedules (subscription_id, action_type)
    WHERE status = 'PENDING';
