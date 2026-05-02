-- Phase 0 Migration B: add role column to user_account for admin RBAC

ALTER TABLE user_account
    ADD COLUMN IF NOT EXISTS role VARCHAR NOT NULL DEFAULT 'USER';

-- To promote an account to admin, run manually after deployment:
-- UPDATE user_account SET role = 'ADMIN' WHERE email = '<admin_email>';
