-- ============================================================
-- V124: Password reset via email OTP
-- ============================================================

CREATE TABLE IF NOT EXISTS public.password_reset_otp (
    otp_id                  uuid         NOT NULL DEFAULT uuid_generate_v4(),
    user_id                 uuid         NOT NULL,
    email                   varchar      NOT NULL,
    code_hash               text         NOT NULL,
    otp_expires_at          timestamptz  NOT NULL,
    attempt_count           integer      NOT NULL DEFAULT 0,
    verified_at             timestamptz  NULL,
    reset_token_hash        text         NULL,
    reset_token_expires_at  timestamptz  NULL,
    consumed_at             timestamptz  NULL,
    created_at              timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT password_reset_otp_pkey PRIMARY KEY (otp_id)
);

-- Efficient lookup of the latest active OTP by email
CREATE INDEX IF NOT EXISTS idx_password_reset_otp_email
    ON public.password_reset_otp (email, created_at DESC);

-- Unique index for reset token hash lookup (partial — only rows that have a token)
CREATE UNIQUE INDEX IF NOT EXISTS ux_password_reset_otp_token_hash
    ON public.password_reset_otp (reset_token_hash)
    WHERE reset_token_hash IS NOT NULL;
