-- V115: Add CANCELLED value to friend_status enum.
--
-- Semantic distinction maintained going forward:
--   PENDING   = request awaiting a response from the addressee
--   ACCEPTED  = both users are friends
--   DECLINED  = addressee explicitly rejected the request
--   CANCELLED = requester withdrew the pending request themselves
--
-- The ADD VALUE IF NOT EXISTS guard makes this idempotent on re-run.
-- AFTER 'DECLINED' positions the new value at the end of the enum (cosmetic only).
-- No existing rows are affected — no current row carries CANCELLED.

ALTER TYPE public.friend_status ADD VALUE IF NOT EXISTS 'CANCELLED' AFTER 'DECLINED';
