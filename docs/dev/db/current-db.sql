-- WARNING: This schema is for context only and is not meant to be run.
-- Table order and constraints may not be valid for execution.

CREATE TABLE public.badge (
  badge_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  name character varying NOT NULL UNIQUE,
  description text,
  icon_url text,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT badge_pkey PRIMARY KEY (badge_id)
);
CREATE TABLE public.block_relation (
  block_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  blocker_id uuid NOT NULL,
  blocked_id uuid NOT NULL,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT block_relation_pkey PRIMARY KEY (block_id),
  CONSTRAINT block_relation_blocker_id_fkey FOREIGN KEY (blocker_id) REFERENCES public.user_account(user_id),
  CONSTRAINT block_relation_blocked_id_fkey FOREIGN KEY (blocked_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.dispute_case (
  dispute_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  opened_by uuid NOT NULL,
  status USER-DEFINED NOT NULL DEFAULT 'OPEN'::dispute_status,
  resolution_action text,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  expires_at timestamp without time zone NOT NULL,
  CONSTRAINT dispute_case_pkey PRIMARY KEY (dispute_id),
  CONSTRAINT dispute_case_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT dispute_case_opened_by_fkey FOREIGN KEY (opened_by) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.flyway_schema_history (
  installed_rank integer NOT NULL,
  version character varying,
  description character varying NOT NULL,
  type character varying NOT NULL,
  script character varying NOT NULL,
  checksum integer,
  installed_by character varying NOT NULL,
  installed_on timestamp without time zone NOT NULL DEFAULT now(),
  execution_time integer NOT NULL,
  success boolean NOT NULL,
  CONSTRAINT flyway_schema_history_pkey PRIMARY KEY (installed_rank)
);
CREATE TABLE public.friendship (
  friendship_id uuid NOT NULL DEFAULT gen_random_uuid(),
  requester_id uuid NOT NULL,
  addressee_id uuid NOT NULL,
  status USER-DEFINED NOT NULL DEFAULT 'PENDING'::friend_status,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  created_at timestamp without time zone NOT NULL DEFAULT now(),
  updated_at timestamp without time zone NOT NULL DEFAULT now(),
  CONSTRAINT friendship_pkey PRIMARY KEY (friendship_id),
  CONSTRAINT friendship_requester_fkey FOREIGN KEY (requester_id) REFERENCES public.user_account(user_id),
  CONSTRAINT friendship_addressee_fkey FOREIGN KEY (addressee_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.hotspot (
  id uuid NOT NULL DEFAULT gen_random_uuid(),
  name character varying NOT NULL,
  lat double precision NOT NULL,
  lng double precision NOT NULL,
  CONSTRAINT hotspot_pkey PRIMARY KEY (id)
);
CREATE TABLE public.match_proposal (
  proposal_id uuid NOT NULL DEFAULT gen_random_uuid(),
  intent_id_a uuid NOT NULL,
  intent_id_b uuid NOT NULL,
  proposed_start_time timestamp without time zone NOT NULL,
  proposed_end_time timestamp without time zone NOT NULL,
  proposed_location_lat double precision NOT NULL,
  proposed_location_lng double precision NOT NULL,
  accepted_by_a boolean NOT NULL DEFAULT false,
  accepted_by_b boolean NOT NULL DEFAULT false,
  status USER-DEFINED NOT NULL DEFAULT 'PENDING'::proposal_status,
  created_at timestamp without time zone NOT NULL DEFAULT now(),
  expires_at timestamp without time zone NOT NULL,
  confirmed_at timestamp without time zone,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  CONSTRAINT match_proposal_pkey PRIMARY KEY (proposal_id),
  CONSTRAINT match_proposal_intent_id_a_fkey FOREIGN KEY (intent_id_a) REFERENCES public.walk_intent(intent_id),
  CONSTRAINT match_proposal_intent_id_b_fkey FOREIGN KEY (intent_id_b) REFERENCES public.walk_intent(intent_id)
);
CREATE TABLE public.matching_preference_model (
  user_id uuid NOT NULL,
  weight_time_overlap double precision DEFAULT 1.0,
  weight_interest double precision DEFAULT 1.0,
  weight_behavior double precision DEFAULT 1.0,
  weight_distance double precision DEFAULT 1.0,
  last_trained_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT matching_preference_model_pkey PRIMARY KEY (user_id),
  CONSTRAINT matching_preference_model_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.notification (
  notification_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  type character varying NOT NULL,
  payload jsonb,
  status USER-DEFINED NOT NULL DEFAULT 'PENDING'::notification_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  read_at timestamp without time zone,
  CONSTRAINT notification_pkey PRIMARY KEY (notification_id),
  CONSTRAINT notification_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.otp_record (
  otp_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  phone character varying NOT NULL,
  code_hash text NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  used boolean NOT NULL DEFAULT false,
  attempt_count integer NOT NULL DEFAULT 0,
  created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT otp_record_pkey PRIMARY KEY (otp_id)
);
CREATE TABLE public.profile_tag (
  tag_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  tag_name character varying NOT NULL CHECK (length(TRIM(BOTH FROM tag_name)) > 0),
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT profile_tag_pkey PRIMARY KEY (tag_id),
  CONSTRAINT profile_tag_user_profile_fkey FOREIGN KEY (user_id) REFERENCES public.user_profile(user_id)
);
CREATE TABLE public.refresh_token (
  token_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  user_id uuid NOT NULL,
  token_value text NOT NULL UNIQUE,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  device_id character varying NOT NULL,
  expires_at timestamp with time zone NOT NULL,
  revoked boolean NOT NULL DEFAULT false,
  CONSTRAINT refresh_token_pkey PRIMARY KEY (token_id),
  CONSTRAINT refresh_token_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.review_tag_master (
  tag_id uuid NOT NULL DEFAULT gen_random_uuid(),
  tag_name character varying NOT NULL UNIQUE,
  tag_type character varying,
  CONSTRAINT review_tag_master_pkey PRIMARY KEY (tag_id)
);
CREATE TABLE public.session_point_chunks (
  chunk_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  chunk_index integer NOT NULL CHECK (chunk_index >= 0),
  polyline text NOT NULL,
  timestamps bytea,
  elevations bytea,
  point_count integer NOT NULL CHECK (point_count > 0),
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  user_id uuid NOT NULL,
  CONSTRAINT session_point_chunks_pkey PRIMARY KEY (chunk_id),
  CONSTRAINT session_point_chunks_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id),
  CONSTRAINT session_point_chunks_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id)
);
CREATE TABLE public.session_report (
  report_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  reporter_id uuid NOT NULL,
  reported_user_id uuid NOT NULL,
  reason character varying NOT NULL,
  evidence_url text,
  status USER-DEFINED NOT NULL DEFAULT 'OPEN'::report_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  category USER-DEFINED,
  CONSTRAINT session_report_pkey PRIMARY KEY (report_id),
  CONSTRAINT session_report_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT session_report_reporter_id_fkey FOREIGN KEY (reporter_id) REFERENCES public.user_account(user_id),
  CONSTRAINT session_report_reported_user_id_fkey FOREIGN KEY (reported_user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.session_state_change_log (
  log_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  from_status USER-DEFINED,
  to_status USER-DEFINED NOT NULL,
  changed_by uuid,
  reason text,
  changed_at timestamp without time zone NOT NULL DEFAULT now(),
  CONSTRAINT session_state_change_log_pkey PRIMARY KEY (log_id),
  CONSTRAINT session_state_change_log_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT session_state_change_log_changed_by_fkey FOREIGN KEY (changed_by) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.trust_score (
  user_id uuid NOT NULL,
  score integer NOT NULL DEFAULT 100 CHECK (score >= 0 AND score <= 1000),
  total_sessions integer NOT NULL DEFAULT 0,
  completed_sessions integer NOT NULL DEFAULT 0,
  cancelled_sessions integer NOT NULL DEFAULT 0,
  no_show_sessions integer NOT NULL DEFAULT 0,
  aborted_sessions integer NOT NULL DEFAULT 0,
  last_updated timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  total_distance_km numeric NOT NULL DEFAULT 0 CHECK (total_distance_km >= 0::numeric),
  CONSTRAINT trust_score_pkey PRIMARY KEY (user_id),
  CONSTRAINT trust_score_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.user_account (
  user_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  email character varying UNIQUE CHECK (email::text ~* '^[A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,}$'::text),
  phone character varying UNIQUE,
  password_hash text,
  provider USER-DEFINED NOT NULL DEFAULT 'LOCAL'::auth_provider,
  status USER-DEFINED NOT NULL DEFAULT 'ACTIVE'::account_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_login_at timestamp without time zone,
  trust_score integer NOT NULL DEFAULT 0,
  total_points integer NOT NULL DEFAULT 0,
  fcm_token text,
  provider_subject character varying,
  visibility_mode character varying NOT NULL DEFAULT 'PUBLIC'::character varying,
  last_active_at timestamp without time zone DEFAULT now(),
  CONSTRAINT user_account_pkey PRIMARY KEY (user_id)
);
CREATE TABLE public.user_badge (
  user_id uuid NOT NULL,
  badge_name character varying NOT NULL,
  awarded_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_badge_pkey PRIMARY KEY (user_id, badge_name),
  CONSTRAINT user_badge_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.user_embedding (
  user_id uuid NOT NULL,
  vector_data USER-DEFINED NOT NULL,
  last_updated timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_embedding_pkey PRIMARY KEY (user_id),
  CONSTRAINT user_embedding_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.user_profile (
  user_id uuid NOT NULL,
  full_name character varying NOT NULL,
  gender USER-DEFINED,
  date_of_birth date CHECK (date_of_birth IS NULL OR date_of_birth < (CURRENT_DATE - '13 years'::interval)),
  avatar_url text,
  bio text,
  search_radius integer DEFAULT 5000 CHECK (search_radius > 0 AND search_radius <= 50000),
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT user_profile_pkey PRIMARY KEY (user_id),
  CONSTRAINT user_profile_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.walk_intent (
  intent_id uuid NOT NULL DEFAULT gen_random_uuid(),
  hotspot_id uuid NOT NULL,
  user_id uuid NOT NULL,
  time_window_start timestamp without time zone NOT NULL,
  time_window_end timestamp without time zone NOT NULL,
  matching_constraints jsonb NOT NULL DEFAULT '{}'::jsonb,
  status USER-DEFINED NOT NULL DEFAULT 'OPEN'::intent_status,
  created_at timestamp without time zone NOT NULL DEFAULT now(),
  expires_at timestamp without time zone NOT NULL,
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  is_private boolean NOT NULL DEFAULT false,
  invited_friend_id uuid,
  description text,
  updated_at timestamp without time zone NOT NULL DEFAULT now(),
  excluded_user_ids ARRAY NOT NULL DEFAULT '{}'::uuid[],
  CONSTRAINT walk_intent_pkey PRIMARY KEY (intent_id),
  CONSTRAINT walk_intent_invited_friend_fkey FOREIGN KEY (invited_friend_id) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_intent_hotspot_id_fkey FOREIGN KEY (hotspot_id) REFERENCES public.hotspot(id),
  CONSTRAINT walk_intent_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.walk_review (
  review_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  reviewer_id uuid NOT NULL,
  reviewee_id uuid NOT NULL,
  rating_stars integer NOT NULL CHECK (rating_stars >= 1 AND rating_stars <= 5),
  comment text,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT walk_review_pkey PRIMARY KEY (review_id),
  CONSTRAINT walk_review_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT walk_review_reviewer_id_fkey FOREIGN KEY (reviewer_id) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_review_reviewee_id_fkey FOREIGN KEY (reviewee_id) REFERENCES public.user_account(user_id)
);
CREATE TABLE public.walk_review_tag_map (
  review_id uuid NOT NULL,
  tag_id uuid NOT NULL,
  CONSTRAINT walk_review_tag_map_pkey PRIMARY KEY (review_id, tag_id),
  CONSTRAINT walk_review_tag_map_review_fkey FOREIGN KEY (review_id) REFERENCES public.walk_review(review_id),
  CONSTRAINT walk_review_tag_map_tag_fkey FOREIGN KEY (tag_id) REFERENCES public.review_tag_master(tag_id)
);
CREATE TABLE public.walk_session (
  session_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  proposal_id uuid NOT NULL,
  user_id_a uuid NOT NULL,
  user_id_b uuid NOT NULL,
  meeting_point_lat double precision NOT NULL,
  meeting_point_lng double precision NOT NULL,
  scheduled_start timestamp without time zone NOT NULL,
  scheduled_end timestamp without time zone NOT NULL,
  status USER-DEFINED NOT NULL DEFAULT 'PENDING'::walk_session_status,
  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at timestamp without time zone,
  ended_at timestamp without time zone,
  user_a_activated_at timestamp without time zone,
  user_b_activated_at timestamp without time zone,
  cancellation_reason character varying,
  cancelled_by uuid,
  abort_reason character varying CHECK (abort_reason IS NULL OR (abort_reason::text = ANY (ARRAY['SAFETY_CONCERN'::character varying, 'EMERGENCY'::character varying, 'PARTNER_MISCONDUCT'::character varying, 'OTHER'::character varying]::text[]))),
  version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
  user_a_distance_km numeric NOT NULL DEFAULT 0 CHECK (user_a_distance_km >= 0::numeric),
  user_a_duration_seconds bigint NOT NULL DEFAULT 0 CHECK (user_a_duration_seconds >= 0),
  source_intent_id_a uuid,
  source_intent_id_b uuid,
  path_data_url character varying,
  user_a_status USER-DEFINED NOT NULL DEFAULT 'PENDING'::walk_session_status,
  user_b_status USER-DEFINED NOT NULL DEFAULT 'PENDING'::walk_session_status,
  user_a_ended_at timestamp without time zone,
  user_b_ended_at timestamp without time zone,
  user_b_distance_km numeric NOT NULL DEFAULT 0 CHECK (user_b_distance_km >= 0::numeric),
  user_b_duration_seconds bigint NOT NULL DEFAULT 0 CHECK (user_b_duration_seconds >= 0),
  CONSTRAINT walk_session_pkey PRIMARY KEY (session_id),
  CONSTRAINT walk_session_proposal_id_fkey FOREIGN KEY (proposal_id) REFERENCES public.match_proposal(proposal_id),
  CONSTRAINT walk_session_user_id_a_fkey FOREIGN KEY (user_id_a) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_session_user_id_b_fkey FOREIGN KEY (user_id_b) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_session_cancelled_by_fkey FOREIGN KEY (cancelled_by) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_session_source_a_fkey FOREIGN KEY (source_intent_id_a) REFERENCES public.walk_intent(intent_id),
  CONSTRAINT walk_session_source_b_fkey FOREIGN KEY (source_intent_id_b) REFERENCES public.walk_intent(intent_id)
);