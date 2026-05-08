CREATE TABLE public.walk_post (
  post_id       uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id    uuid NOT NULL,
  author_id     uuid NOT NULL,

  caption       text,
  visibility    character varying NOT NULL DEFAULT 'PUBLIC',

  show_companion  boolean NOT NULL DEFAULT true,
  show_route_map  boolean NOT NULL DEFAULT false,
  show_stats      boolean NOT NULL DEFAULT true,

  distance_km        numeric NOT NULL DEFAULT 0 CHECK (distance_km >= 0),
  duration_seconds   bigint  NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0),
  points_earned      integer NOT NULL DEFAULT 0 CHECK (points_earned >= 0),

  route_preview_url  text,

  created_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at    timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT walk_post_pkey PRIMARY KEY (post_id),
  CONSTRAINT walk_post_session_fkey
    FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT walk_post_author_fkey
    FOREIGN KEY (author_id) REFERENCES public.user_account(user_id),
  CONSTRAINT walk_post_unique_author_session
    UNIQUE (session_id, author_id),
  CONSTRAINT walk_post_visibility_check
    CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'))
);

CREATE INDEX idx_walk_post_author_created
    ON public.walk_post (author_id, created_at DESC);

CREATE INDEX idx_walk_post_author_visibility_created
    ON public.walk_post (author_id, visibility, created_at DESC);
