-- Phase 0 Migration A: extend report_status enum + add resolution columns to session_report

-- Step 1: extend report_status enum with APPROVED and REJECTED
ALTER TYPE report_status ADD VALUE IF NOT EXISTS 'APPROVED';
ALTER TYPE report_status ADD VALUE IF NOT EXISTS 'REJECTED';

-- Step 2: add new columns to session_report
ALTER TABLE session_report
    ADD COLUMN IF NOT EXISTS applied_trust_delta INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS resolved_by          UUID         REFERENCES user_account(user_id),
    ADD COLUMN IF NOT EXISTS resolved_at          TIMESTAMP WITHOUT TIME ZONE,
    ADD COLUMN IF NOT EXISTS resolution_note      TEXT;
