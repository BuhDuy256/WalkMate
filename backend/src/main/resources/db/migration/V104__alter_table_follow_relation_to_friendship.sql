-- ==============================================================================
-- MIGRATIONS: CONVERT FOLLOW TO FRIENDSHIP (PRIVACY-FIRST MODEL)
-- ==============================================================================

-- 1. Xóa bỏ bảng follow_relation cũ
DROP TABLE IF EXISTS public.follow_relation CASCADE;

-- 2. Tạo kiểu ENUM cho trạng thái kết bạn
-- PENDING: Đang chờ đồng ý
-- ACCEPTED: Đã là bạn bè (Có thể mời Private Intent)
-- DECLINED: Đã từ chối
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'friend_status') THEN
        CREATE TYPE public.friend_status AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED');
    END IF;
END $$;

-- 3. Tạo bảng friendship mới
CREATE TABLE IF NOT EXISTS public.friendship (
    friendship_id  uuid           NOT NULL DEFAULT gen_random_uuid(),
    requester_id   uuid           NOT NULL, -- Người chủ động gửi lời mời
    addressee_id   uuid           NOT NULL, -- Người nhận lời mời
    status         friend_status  NOT NULL DEFAULT 'PENDING',
    
    -- Optimistic Locking cho DDD
    version        bigint         NOT NULL DEFAULT 0 CHECK (version >= 0),
    
    created_at     timestamp      NOT NULL DEFAULT now(),
    updated_at     timestamp      NOT NULL DEFAULT now(),

    CONSTRAINT friendship_pkey PRIMARY KEY (friendship_id),
    
    -- Ràng buộc 1: Không được kết bạn với chính mình
    CONSTRAINT chk_not_self_friend CHECK (requester_id <> addressee_id),
    
    -- Ràng buộc 2: Đảm bảo duy nhất một cặp quan hệ giữa 2 người
    -- (Tránh việc A gửi cho B 2 lời mời cùng lúc)
    CONSTRAINT friendship_unique_pair UNIQUE (requester_id, addressee_id),
    
    -- Khóa ngoại kết nối với bảng user_account
    CONSTRAINT friendship_requester_fkey FOREIGN KEY (requester_id) 
        REFERENCES public.user_account (user_id) ON DELETE CASCADE,
    CONSTRAINT friendship_addressee_fkey FOREIGN KEY (addressee_id) 
        REFERENCES public.user_account (user_id) ON DELETE CASCADE
);

-- 4. Đánh Index để truy vấn danh sách bạn bè nhanh hơn
CREATE INDEX IF NOT EXISTS idx_friendship_requester ON public.friendship(requester_id);
CREATE INDEX IF NOT EXISTS idx_friendship_addressee ON public.friendship(addressee_id);
CREATE INDEX IF NOT EXISTS idx_friendship_status    ON public.friendship(status);