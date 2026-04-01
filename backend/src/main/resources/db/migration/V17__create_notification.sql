-- Phase 11: Notification feed
-- Creates the notification_status PG enum and the notification table.

CREATE TYPE notification_status AS ENUM ('PENDING', 'SENT', 'READ');

CREATE TABLE notification (
    notification_id UUID                NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID                NOT NULL REFERENCES user_account(user_id),
    type            VARCHAR(64)         NOT NULL,
    payload         JSONB               NOT NULL DEFAULT '{}',
    status          notification_status NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    read_at         TIMESTAMPTZ,

    CONSTRAINT pk_notification PRIMARY KEY (notification_id)
);

-- Index for efficient per-user feed queries (newest first)
CREATE INDEX idx_notification_user_created ON notification (user_id, created_at DESC);
