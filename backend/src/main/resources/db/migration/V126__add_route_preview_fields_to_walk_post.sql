-- Route preview columns added to walk_post.
-- route_preview_path  : Supabase Storage object path for deletion on post delete.
-- route_preview_status: lifecycle of the preview generation task.

ALTER TABLE public.walk_post
    ADD COLUMN IF NOT EXISTS route_preview_path   TEXT,
    ADD COLUMN IF NOT EXISTS route_preview_status VARCHAR(20) NOT NULL DEFAULT 'PENDING';

ALTER TABLE public.walk_post
    ADD CONSTRAINT walk_post_route_preview_status_check
        CHECK (route_preview_status IN ('PENDING', 'READY', 'NO_ROUTE', 'FAILED'));
