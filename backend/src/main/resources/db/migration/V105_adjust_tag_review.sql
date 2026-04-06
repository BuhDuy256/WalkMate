-- 1. Tạo bảng danh mục Tag (Chỉ lưu tên các tag mẫu)
CREATE TABLE IF NOT EXISTS public.review_tag_master (
    tag_id     uuid         NOT NULL DEFAULT gen_random_uuid(),
    tag_name   varchar(50)  NOT NULL, -- Ví dụ: 'Đúng giờ', 'Nhiệt tình'
    tag_type   varchar(20)  NULL,     -- Gợi ý: 'POSITIVE' hoặc 'NEGATIVE'
    
    CONSTRAINT review_tag_master_pkey PRIMARY KEY (tag_id),
    CONSTRAINT review_tag_master_name_unique UNIQUE (tag_name)
);

-- 2. Xóa bảng cũ bị sai logic
DROP TABLE IF EXISTS public.review_tag CASCADE;

-- 3. Tạo bảng trung gian (Junction Table)
-- Đây chính là nơi nối tag_id với review_id
CREATE TABLE IF NOT EXISTS public.walk_review_tag_map (
    review_id  uuid  NOT NULL,
    tag_id     uuid  NOT NULL,
    
    CONSTRAINT walk_review_tag_map_pkey PRIMARY KEY (review_id, tag_id),
    CONSTRAINT walk_review_tag_map_review_fkey FOREIGN KEY (review_id) 
        REFERENCES public.walk_review (review_id) ON DELETE CASCADE,
    CONSTRAINT walk_review_tag_map_tag_fkey FOREIGN KEY (tag_id) 
        REFERENCES public.review_tag_master (tag_id) ON DELETE CASCADE
);