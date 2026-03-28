-- =========================================================================
-- MIGRATION: Kích hoạt RLS và cấu hình Supabase Realtime cho Chat
-- MỤC TIÊU:
-- 1. Chặn toàn bộ quyền GHI (INSERT, UPDATE, DELETE) từ Client.
-- 2. Chỉ cho phép Client ĐỌC (SELECT) tin nhắn thuộc về WalkSession của họ.
-- 3. Đưa bảng chat_message vào luồng phát sóng Realtime của Supabase.
-- =========================================================================

-- 1. Bật Row Level Security (RLS) cho cả hai bảng
ALTER TABLE public.chat_room ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.chat_message ENABLE ROW LEVEL SECURITY;

-- 2. Xóa các policy cũ (nếu có) để tránh xung đột
DROP POLICY IF EXISTS "Participants can view their chat rooms" ON public.chat_room;
DROP POLICY IF EXISTS "Participants can view messages in their chat rooms" ON public.chat_message;

-- 3. Tạo Policy cho Chat Room: Chỉ được phép SELECT nếu user là 1 trong 2 người tham gia
CREATE POLICY "Participants can view their chat rooms"
ON public.chat_room
FOR SELECT
USING (
    EXISTS (
        SELECT 1 FROM public.walk_session ws
        WHERE ws.session_id = chat_room.session_id
        AND (ws.user1_id = auth.uid() OR ws.user2_id = auth.uid())
    )
);

-- 4. Tạo Policy cho Chat Message: Chỉ được phép SELECT nếu user có quyền vào Chat Room tương ứng
CREATE POLICY "Participants can view messages in their chat rooms"
ON public.chat_message
FOR SELECT
USING (
    EXISTS (
        SELECT 1 FROM public.chat_room cr
        JOIN public.walk_session ws ON cr.session_id = ws.session_id
        WHERE cr.chat_room_id = chat_message.chat_room_id
        AND (ws.user1_id = auth.uid() OR ws.user2_id = auth.uid())
    )
);

-- 5. Đưa bảng chat_message vào kênh Supabase Realtime
-- Bắt buộc để Client Android có thể lắng nghe sự kiện INSERT từ bảng này
ALTER PUBLICATION supabase_realtime ADD TABLE public.chat_message;

-- 6. Tối ưu hóa hiệu năng (Index cho việc query tin nhắn nhanh hơn khi load lại phòng chat)
CREATE INDEX IF NOT EXISTS idx_chat_message_room_created
ON public.chat_message(chat_room_id, created_at DESC);