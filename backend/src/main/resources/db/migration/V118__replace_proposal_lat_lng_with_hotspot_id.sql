-- V118: Replace match_proposal.proposed_location_lat/lng with hotspot_id FK
-- The meeting location is always the matched hotspot, so storing redundant
-- coordinates was denormalised.  Backfill from intent_id_a's hotspot.

ALTER TABLE public.match_proposal ADD COLUMN hotspot_id uuid;

UPDATE public.match_proposal mp
SET hotspot_id = wi.hotspot_id
FROM public.walk_intent wi
WHERE wi.intent_id = mp.intent_id_a;

ALTER TABLE public.match_proposal ALTER COLUMN hotspot_id SET NOT NULL;

ALTER TABLE public.match_proposal
    ADD CONSTRAINT match_proposal_hotspot_id_fkey
    FOREIGN KEY (hotspot_id) REFERENCES public.hotspot(id);

ALTER TABLE public.match_proposal DROP COLUMN proposed_location_lat;
ALTER TABLE public.match_proposal DROP COLUMN proposed_location_lng;
