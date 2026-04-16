-- 1. Thêm trường 'last_active_at' vào user_account để track dấu vết người dùng
-- Chúng ta giữ lại trường này vì nó cực kỳ quan trọng để biết user có "ngáp" hay không
ALTER TABLE public.user_account 
ADD COLUMN IF NOT EXISTS last_active_at timestamp DEFAULT now();

-- 2. "Khai tử" bảng user_presence
-- Dùng CASCADE để nó tự động dọn dẹp các ràng buộc liên quan (nếu có)
DROP TABLE IF EXISTS public.user_presence CASCADE;