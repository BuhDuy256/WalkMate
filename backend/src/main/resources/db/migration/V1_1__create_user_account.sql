-- ============================================================
-- V1.1: Create user_account table and its PostgreSQL enum types.
-- Must run before V2 (walk_intent has FK to user_account).
-- ============================================================

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'auth_provider') THEN
        CREATE TYPE auth_provider AS ENUM ('LOCAL', 'GOOGLE');
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'account_status') THEN
        CREATE TYPE account_status AS ENUM ('ACTIVE', 'SUSPENDED', 'BANNED');
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS user_account (
    user_id       UUID           PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(255)   NOT NULL UNIQUE,
    phone         VARCHAR(20),
    provider      auth_provider  NOT NULL DEFAULT 'LOCAL',
    status        account_status NOT NULL DEFAULT 'ACTIVE',
    password_hash VARCHAR(72)    NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    last_login_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_user_account_email ON user_account (email);
