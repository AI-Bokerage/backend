-- AlterEnum
-- This migration adds more than one value to an enum.
-- With PostgreSQL versions 11 and earlier, this is not possible
-- in a single migration. This can be worked around by creating
-- multiple migrations, each migration adding only one value to
-- the enum.


ALTER TYPE "SignupState" ADD VALUE 'PLAN_SELECTED';
ALTER TYPE "SignupState" ADD VALUE 'PAYMENT_PENDING';
ALTER TYPE "SignupState" ADD VALUE 'PAYMENT_SUCCEEDED';

-- AlterTable
ALTER TABLE "signup_session" ADD COLUMN     "checkout_session_id" VARCHAR(255),
ADD COLUMN     "payment_customer_id" VARCHAR(255),
ADD COLUMN     "payment_provider" VARCHAR(16),
ADD COLUMN     "selected_billing_cycle" VARCHAR(16),
ADD COLUMN     "selected_plan_code" VARCHAR(64);

-- CreateIndex
CREATE INDEX "signup_session_checkout_session_id_idx" ON "signup_session"("checkout_session_id");
