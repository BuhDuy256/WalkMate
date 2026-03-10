# 📘 WALKMATE – FULL CONCEPTUAL SCHEMA (FINAL ALIGNED VERSION)

---

# I. Bounded Contexts

```text
1. Identity & Access
2. User Profile
3. Social Graph
4. Presence
5. Walk Coordination (Intent–Intent Matching)
6. Walk Lifecycle (Session)
7. Communication
8. Trust & Reputation
9. Reporting & Dispute
10. Gamification
11. Notification
12. AI Personalization
```

---

# II. Identity & Access Context

## Aggregate: UserAccount (AR)

### Entity: UserAccount

- user_id
- email (unique)
- phone (unique)
- password_hash
- provider
- status (ACTIVE / SUSPENDED / DELETED)
- created_at
- last_login_at

### Invariants

- 1 identity = 1 account
- Email/Phone unique

---

# III. User Profile Context

## Aggregate: UserProfile (AR)

### Entity

- user_id
- full_name
- gender
- date_of_birth
- avatar_url
- bio
- public_mode (BOOLEAN)
- created_at

### Sub-Entity: ProfileTag

- tag_id
- user_id
- tag_type
- created_at

### Invariants

- Profile chỉ tồn tại nếu có UserAccount
- public_mode = FALSE → không tạo WalkIntent

---

# IV. Social Graph Context (Updated)

❌ Bỏ Friendship
✅ Dùng Following + Block

---

## Aggregate: FollowRelation

- follow_id
- follower_id
- followee_id
- created_at

### Invariants

- Không duplicate pair
- Following là one-way

---

## Aggregate: BlockRelation

- block_id
- blocker_id
- blocked_id
- created_at

### Invariants

- Nếu block tồn tại → không match, không create session

---

# V. Presence Context

(Phục vụ Quick Mode)

## Aggregate: UserPresence

- user_id
- status (ONLINE / OFFLINE)
- availability (AVAILABLE / UNAVAILABLE)
- quick_mode (BOOLEAN)
- last_active_at
- expires_at

### Invariants

- quick_mode auto expire sau X phút
- Nếu app background → availability = UNAVAILABLE

---

# VI. Walk Coordination Context

(Pre-value phase)

---

## Aggregate: WalkIntent (AR)

### Entity

- intent_id
- user_id
- location_lat
- location_lng
- time_window_start
- time_window_end
- purpose
- matching_constraints
- status (OPEN / EXPIRED / CANCELLED / CONSUMED)
- created_at
- expires_at

### Invariants

- time_window_end > time_window_start
- 1 user không có 2 OPEN intent overlap
- Intent chỉ match khi status = OPEN
- Block check trước khi match

---

## Aggregate: MatchProposal

### Entity

- proposal_id
- intent_A_id
- intent_B_id
- proposed_time_start
- proposed_time_end
- proposed_location_lat
- proposed_location_lng
- status (PENDING / ACCEPTED_BY_A / ACCEPTED_BY_B / CONFIRMED / REJECTED / EXPIRED)
- created_at
- expires_at

### Invariants

- 2 Intent phải = OPEN
- 1 Intent chỉ có 1 proposal PENDING
- Khi CONFIRMED:
  → Domain Service create WalkSession
  → Intent status = CONSUMED

---

# VII. Walk Lifecycle Context (Execution Phase)

## Aggregate: WalkSession (AR)

### Entity

- session_id
- user1_id
- user2_id
- scheduled_start_time
- scheduled_end_time
- actual_start_time
- actual_end_time
- status (PENDING / ACTIVE / COMPLETED / NO_SHOW / CANCELLED)
- created_at
- source_intent_id_A
- source_intent_id_B

---

## 🔴 Core Invariant

Không tồn tại 2 session:

- Cùng user
- time window overlap
- status ∈ (PENDING, ACTIVE)

---

## State Machine (Enforced by Aggregate)

PENDING → ACTIVE → COMPLETED
    ↘ CANCELLED
    ↘ NO_SHOW

Terminal: COMPLETED / NO_SHOW / CANCELLED (immutable)

---

# VIII. Communication Context

## Aggregate: ChatRoom

- chat_room_id
- session_id
- status (OPEN / CLOSED)
- open_at
- close_at

### Invariants

- OPEN khi session.status ∈ (PENDING, ACTIVE)
- CLOSED khi terminal state

---

## Entity: ChatMessage

- message_id
- chat_room_id
- sender_id
- content
- created_at

---

# IX. Trust & Reputation Context

---

## Aggregate: WalkReview

- review_id
- session_id
- reviewer_id
- reviewee_id
- rating_stars
- tags
- created_at

### Invariants

- Session must be COMPLETED
- 1 review per user per session

---

## Aggregate: TrustScore

- user_id
- score
- last_updated

Updated via Domain Events:

- Completion (+)
- Cancellation (-)
- No-show (-)

---

# X. Reporting & Dispute Context

---

## Aggregate: SessionReport

- report_id
- session_id
- reporter_id
- reported_user_id
- reason
- evidence_url
- status (OPEN / RESOLVED / REJECTED)
- created_at

---

## Aggregate: DisputeCase

- dispute_id
- session_id
- opened_by
- status (OPEN / RESOLVED / CLOSED)
- resolution_action
- created_at
- expires_at (24h window)

### Invariants

- NO_SHOW → auto allow dispute 24h
- Terminal session state không mutate
- Compensation via TrustScore adjustment

---

# XI. Gamification Context

---

## Aggregate: Badge

- badge_id
- name
- condition_type
- condition_value

---

## Aggregate: UserBadge

- user_id
- badge_id
- earned_at

---

# XII. Notification Context

(Event-driven)

## Aggregate: Notification

- notification_id
- user_id
- type
- payload
- status (PENDING / SENT / FAILED)
- created_at

Triggered by Domain Events:

- WalkSessionCreated
- Activated
- Cancelled
- Completed
- No-show
- NewMessage
- MatchProposalCreated

---

# XIII. AI Personalization Context

AI chỉ ranking Intent–Intent.

---

## Aggregate: UserEmbedding

- user_id
- vector_data
- last_updated

---

## Aggregate: MatchingPreferenceModel

- user_id
- weight_time_overlap
- weight_interest
- weight_behavior
- weight_distance

AI compute:

Score(IntentA, IntentB)

AI không:

- mutate state
- create session
- apply penalty

---

# XIV. Event Flow (Full Aligned)

User tạo WalkIntent
↓
MatchingService detect overlap
↓
AI ranking
↓
MatchProposal
↓
Mutual ACCEPT
↓
SessionService.createSession()
 → Check overlap invariant
 → Create WalkSession (PENDING)
↓
Lifecycle execution
↓
Domain events
↓
TrustScore / Notification / Badge

---

# XV. Aggregate Roots (Final List)

```text
UserAccount
UserProfile
FollowRelation
BlockRelation
UserPresence
WalkIntent
MatchProposal
WalkSession
ChatRoom
WalkReview
TrustScore
SessionReport
DisputeCase
Badge
Notification
UserEmbedding
```

---

# XVI. Alignment Check

| Feature               | Align |
| --------------------- | ----- |
| WalkIntent separation | ✅    |
| 5-state lifecycle     | ✅    |
| No overlap invariant  | ✅    |
| Following model       | ✅    |
| Presence Quick Mode   | ✅    |
| Trace path            | ✅    |
| Dispute window        | ✅    |
| Notification          | ✅    |
| AI ranking only       | ✅    |
| No radius matching    | ✅    |

---

# Final Result

Schema này:

- Align 100% feature list
- Align 100% DDD state machine
- Clean aggregate boundary
- Production-ready modeling
- Không còn legacy tư duy
