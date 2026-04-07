-- ============================================================
-- V5: Visibility mode, account status extension, OTP support
-- ============================================================

-- 1. Extend account_status enum with SUSPENDED and BANNED
ALTER TYPE account_status ADD VALUE IF NOT EXISTS 'SUSPENDED';
ALTER TYPE account_status ADD VALUE IF NOT EXISTS 'BANNED';

-- 2. Extend auth_provider enum with PHONE (for OTP-based auth)
ALTER TYPE auth_provider ADD VALUE IF NOT EXISTS 'PHONE';

-- 3. Make email nullable — phone-only users will have email = NULL
ALTER TABLE public.user_account ALTER COLUMN email DROP NOT NULL;

-- 4. Add visibility_mode column (PUBLIC = visible to matching; PRIVATE = hidden)
--    Default PUBLIC preserves existing user visibility.
ALTER TABLE public.user_account
    ADD COLUMN IF NOT EXISTS visibility_mode VARCHAR(10) NOT NULL DEFAULT 'PUBLIC';

-- 5. Create otp_record table for phone+OTP authentication challenges
CREATE TABLE IF NOT EXISTS public.otp_record (
    otp_id        uuid         NOT NULL DEFAULT uuid_generate_v4(),
    phone         varchar      NOT NULL,
    code_hash     text         NOT NULL,
    expires_at    timestamptz  NOT NULL,
    used          boolean      NOT NULL DEFAULT false,
    attempt_count integer      NOT NULL DEFAULT 0,
    created_at    timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT otp_record_pkey PRIMARY KEY (otp_id)
);

-- Index for efficient lookup of the latest OTP by phone
CREATE INDEX IF NOT EXISTS idx_otp_record_phone
    ON public.otp_record (phone, expires_at DESC);
