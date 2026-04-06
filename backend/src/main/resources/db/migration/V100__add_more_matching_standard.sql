-- ==============================================================================
-- 1. MIGRATIONS CHO BẢNG WALK_INTENT
-- Bổ sung các cột phục vụ điều hướng (Routing) và hiển thị (Display) 
-- đã được lôi ra khỏi matching_constraints JSONB.
-- ==============================================================================

-- Thêm cờ xác định đây là lời mời riêng tư
ALTER TABLE public.walk_intent 
ADD COLUMN IF NOT EXISTS is_private boolean NOT NULL DEFAULT false;

-- Thêm ID của người bạn được mời (Nếu is_private = true)
ALTER TABLE public.walk_intent 
ADD COLUMN IF NOT EXISTS invited_friend_id uuid NULL;

-- Thêm ForeignKey cho invited_friend_id (Tùy chọn nhưng khuyên dùng)
-- Lưu ý: PostgreSQL không hỗ trợ ADD CONSTRAINT IF NOT EXISTS trực tiếp, 
-- nên ta dùng khối lệnh an toàn (DO block)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'walk_intent_invited_friend_fkey'
    ) THEN
        ALTER TABLE public.walk_intent
        ADD CONSTRAINT walk_intent_invited_friend_fkey 
        FOREIGN KEY (invited_friend_id) REFERENCES public.user_account (user_id) ON DELETE SET NULL;
    END IF;
END $$;

-- Thêm lời nhắn hiển thị trên UI
ALTER TABLE public.walk_intent 
ADD COLUMN IF NOT EXISTS description text NULL;

-- Thêm thời gian cập nhật để track việc user sửa đổi Intent
ALTER TABLE public.walk_intent 
ADD COLUMN IF NOT EXISTS updated_at timestamp NOT NULL DEFAULT now();


-- ==============================================================================
-- 2. MIGRATIONS CHO BẢNG WALK_SESSION
-- Bổ sung trường lưu trữ dữ liệu GPS (Strava tracing) mà schema mới đang thiếu
-- ==============================================================================

-- Thêm đường dẫn (URL) lưu file GeoJSON/KML chứa lịch sử vẽ đường đi trên bản đồ
ALTER TABLE public.walk_session 
ADD COLUMN IF NOT EXISTS path_data_url varchar NULL;


-- ==============================================================================
-- 3. MIGRATIONS CHO BẢNG MATCH_PROPOSAL (Tùy chọn)
-- Bổ sung trường version cho Optimistic Locking giống như các bảng khác
-- ==============================================================================

-- Thêm cột version để đồng bộ kiến trúc chống Race Condition với Intent và Session
ALTER TABLE public.match_proposal 
ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0 CHECK (version >= 0);

-- Thêm trạng thái 'MATCHING' vào ENUM intent_status
-- Đặt nó đứng ngay sau trạng thái 'OPEN' cho đúng thứ tự logic vòng đời
ALTER TYPE public.intent_status ADD VALUE IF NOT EXISTS 'MATCHING' AFTER 'OPEN';