-- ============================================================
-- V10: Create session_state_change_log for auditing every
--      session status transition (user-initiated or scheduler).
-- ============================================================

CREATE TABLE IF NOT EXISTS session_state_change_log (
    id           BIGSERIAL            PRIMARY KEY,
    session_id   UUID                 NOT NULL REFERENCES walk_session(session_id) ON DELETE CASCADE,
    from_status  walk_session_status,
    to_status    walk_session_status  NOT NULL,
    changed_by   UUID                 REFERENCES user_account(user_id),
    reason       TEXT,
    changed_at   TIMESTAMP            NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_session_log_session_id ON session_state_change_log (session_id);
CREATE INDEX IF NOT EXISTS idx_session_log_changed_at ON session_state_change_log (changed_at DESC);
