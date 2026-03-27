-- Kiểm tra và tạo Type intent_status nếu chưa có
DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'intent_status') THEN
        CREATE TYPE intent_status AS ENUM ('OPEN', 'MATCHED', 'CANCELLED', 'EXPIRED');
    END IF;
END $$;

-- Xóa bảng cũ nếu nó không giống cấu trúc (vì bản cũ dùng location_lat/lng thay vì hotspot_id)
-- Nếu bạn muốn giữ data, hãy bỏ dòng DROP này và dùng ALTER TABLE.
DROP TABLE IF EXISTS walk_intent CASCADE;

CREATE TABLE IF NOT EXISTS walk_intent (
    intent_id           UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    hotspot_id          UUID            NOT NULL REFERENCES hotspot(id),
    user_id             UUID            NOT NULL REFERENCES user_account(user_id),
    time_window_start   TIMESTAMP       NOT NULL,
    time_window_end     TIMESTAMP       NOT NULL,
    matching_constraints JSONB          NOT NULL DEFAULT '{}',
    status              intent_status   NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMP       NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMP       NOT NULL,
    version             BIGINT          NOT NULL DEFAULT 0 CHECK (version >= 0),

    CONSTRAINT walk_intent_time_valid CHECK (time_window_end > time_window_start)
);

-- Các index nên dùng CREATE INDEX IF NOT EXISTS
CREATE INDEX IF NOT EXISTS idx_walk_intent_hotspot_status ON walk_intent (hotspot_id, status);
CREATE INDEX IF NOT EXISTS idx_walk_intent_user_id ON walk_intent (user_id);
CREATE INDEX IF NOT EXISTS idx_walk_intent_constraints ON walk_intent USING GIN (matching_constraints);