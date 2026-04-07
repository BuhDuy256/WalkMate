-- GAP-11: Per-intent exclude list (X-3)
-- Stores the set of user IDs that this intent's owner has explicitly rejected.
-- The matching engine filters out any candidate whose user_id appears in this array.
ALTER TABLE public.walk_intent
    ADD COLUMN IF NOT EXISTS excluded_user_ids uuid[] NOT NULL DEFAULT '{}';
