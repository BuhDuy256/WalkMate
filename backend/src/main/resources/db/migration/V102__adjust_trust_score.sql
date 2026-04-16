-- ==============================================================================
-- MIGRATIONS: CLEAN UP USER_ACCOUNT TABLE
-- Xóa các cột thống kê dư thừa để chuẩn hóa dữ liệu theo kiến trúc tách biệt.
-- ==============================================================================

-- 1. Xóa cột completed_sessions
ALTER TABLE public.user_account 
DROP COLUMN IF EXISTS completed_sessions;

-- 2. Xóa cột total_distance_km
ALTER TABLE public.user_account 
DROP COLUMN IF EXISTS total_distance_km;

-- Thêm cột tổng quãng đường vào bảng thống kê chi tiết
ALTER TABLE public.trust_score 
ADD COLUMN IF NOT EXISTS total_distance_km numeric(10, 2) NOT NULL DEFAULT 0;

-- Ràng buộc để đảm bảo quãng đường không bao giờ âm
ALTER TABLE public.trust_score 
ADD CONSTRAINT chk_total_distance_positive CHECK (total_distance_km >= 0);