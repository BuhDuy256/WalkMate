-- V119: Replace walk_session.meeting_point_lat/lng with hotspot_id FK
-- Depends on V118 (match_proposal.hotspot_id must exist first).
-- Backfill from the proposal's hotspot_id.

ALTER TABLE public.walk_session ADD COLUMN hotspot_id uuid;

UPDATE public.walk_session ws
SET hotspot_id = mp.hotspot_id
FROM public.match_proposal mp
WHERE mp.proposal_id = ws.proposal_id;

ALTER TABLE public.walk_session ALTER COLUMN hotspot_id SET NOT NULL;

ALTER TABLE public.walk_session
    ADD CONSTRAINT walk_session_hotspot_id_fkey
    FOREIGN KEY (hotspot_id) REFERENCES public.hotspot(id);

ALTER TABLE public.walk_session DROP COLUMN meeting_point_lat;
ALTER TABLE public.walk_session DROP COLUMN meeting_point_lng;
