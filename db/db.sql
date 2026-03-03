PRAGMA foreign_keys = ON;

CREATE TABLE users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE intents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL,
    walk_type TEXT NOT NULL,
    start_at DATETIME NOT NULL,
    flex_minutes INTEGER NOT NULL CHECK (flex_minutes IN (30, 60)),
    window_start DATETIME NOT NULL,
    window_end DATETIME NOT NULL,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    radius_m INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('OPEN', 'LOCKED', 'CANCELLED')),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE proposals (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    requester_user_id INTEGER NOT NULL,
    requester_intent_id INTEGER NOT NULL,
    target_user_id INTEGER NOT NULL,
    target_intent_id INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('PROPOSED', 'CONFIRMED', 'EXPIRED', 'CANCELLED')),
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (requester_user_id) REFERENCES users(id),
    FOREIGN KEY (requester_intent_id) REFERENCES intents(id),
    FOREIGN KEY (target_user_id) REFERENCES users(id),
    FOREIGN KEY (target_intent_id) REFERENCES intents(id)
);

CREATE TABLE sessions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    proposal_id INTEGER NOT NULL UNIQUE,
    user_a_id INTEGER NOT NULL,
    user_b_id INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    scheduled_start_at DATETIME NOT NULL,
    started_at DATETIME,
    ended_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (proposal_id) REFERENCES proposals(id),
    FOREIGN KEY (user_a_id) REFERENCES users(id),
    FOREIGN KEY (user_b_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX idx_intents_one_active_per_user 
ON intents(user_id) 
WHERE status IN ('OPEN', 'LOCKED');

CREATE UNIQUE INDEX idx_proposals_one_active_requester 
ON proposals(requester_user_id) 
WHERE status IN ('PROPOSED', 'CONFIRMED');

CREATE UNIQUE INDEX idx_proposals_one_active_target 
ON proposals(target_user_id) 
WHERE status IN ('PROPOSED', 'CONFIRMED');

CREATE UNIQUE INDEX idx_sessions_one_active_user_a 
ON sessions(user_a_id) 
WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

CREATE UNIQUE INDEX idx_sessions_one_active_user_b 
ON sessions(user_b_id) 
WHERE status IN ('CONFIRMED', 'IN_PROGRESS');

CREATE UNIQUE INDEX idx_proposals_one_active_per_requester_intent 
ON proposals(requester_intent_id) 
WHERE status IN ('PROPOSED', 'CONFIRMED');

CREATE UNIQUE INDEX idx_proposals_one_active_per_target_intent 
ON proposals(target_intent_id) 
WHERE status IN ('PROPOSED', 'CONFIRMED');

CREATE INDEX idx_intents_matching 
ON intents(status, walk_type, window_start, window_end) 
WHERE status = 'OPEN';

CREATE INDEX idx_intents_user_status 
ON intents(user_id, status);

CREATE INDEX idx_proposals_status 
ON proposals(status);

CREATE INDEX idx_sessions_status 
ON sessions(status);
