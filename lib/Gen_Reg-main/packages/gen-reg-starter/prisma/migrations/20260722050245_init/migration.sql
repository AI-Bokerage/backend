-- CreateEnum
CREATE TYPE "SignupState" AS ENUM ('STARTED', 'EMAIL_VERIFIED', 'PROVISIONING', 'ACTIVE', 'PROVISION_FAILED', 'ABANDONED');

-- CreateTable
CREATE TABLE "signup_session" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "email" VARCHAR(255) NOT NULL,
    "company_name" VARCHAR(255),
    "full_name" VARCHAR(255),
    "phone" VARCHAR(32),
    "source" VARCHAR(64),
    "referral_code" VARCHAR(64),
    "utm_source" VARCHAR(128),
    "utm_medium" VARCHAR(128),
    "utm_campaign" VARCHAR(128),
    "desired_subdomain" VARCHAR(63) NOT NULL,
    "state" "SignupState" NOT NULL DEFAULT 'STARTED',
    "email_verification_token_hash" VARCHAR(255),
    "email_verified_at" TIMESTAMPTZ(6),
    "resume_token_hash" VARCHAR(255),
    "auth_user_id" UUID NOT NULL,
    "provisioning_job_id" UUID,
    "provisioned_tenant_id" UUID,
    "last_provisioning_error" TEXT,
    "expires_at" TIMESTAMPTZ(6) NOT NULL,
    "created_at" TIMESTAMPTZ(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updated_at" TIMESTAMPTZ(6) NOT NULL,

    CONSTRAINT "signup_session_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "signup_session_email_verification_token_hash_idx" ON "signup_session"("email_verification_token_hash");

-- CreateIndex
CREATE INDEX "signup_session_resume_token_hash_idx" ON "signup_session"("resume_token_hash");
