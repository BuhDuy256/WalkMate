-- 1. Xóa bảng chat_message trước (vì nó chứa khóa ngoại trỏ vào chat_room)
DROP TABLE IF EXISTS public.chat_message CASCADE;

-- 2. Xóa bảng chat_room
DROP TABLE IF EXISTS public.chat_room CASCADE;