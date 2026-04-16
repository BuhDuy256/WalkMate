-- ============================================================
-- V4: OAuth support — Google Sign-In
-- ============================================================
-- Strategy: A2 (merge & keep both providers).
--   • Existing LOCAL accounts keep password_hash and can still log in with password.
--   • When a LOCAL user signs in with Google for the first time, provider_subject
--     is populated; they can then use either method.
--   • Brand-new Google users get provider = 'GOOGLE', provider_subject = <Google sub>.
-- ============================================================

-- 1. Extend the auth_provider enum with GOOGLE
ALTER TYPE auth_provider ADD VALUE IF NOT EXISTS 'GOOGLE';

-- 2. Add provider_subject column (Google's stable "sub" claim)
--    Nullable — LOCAL-only accounts will always have NULL here.
ALTER TABLE public.user_account
    ADD COLUMN IF NOT EXISTS provider_subject varchar NULL;

-- 3. Partial unique index: one Google account → one WalkMate account.
--    Scoped to rows where provider_subject is set so that multiple LOCAL
--    accounts (all with provider_subject = NULL) are not mistakenly deduplicated.
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_account_provider_subject
    ON public.user_account (provider_subject)
    WHERE provider_subject IS NOT NULL;