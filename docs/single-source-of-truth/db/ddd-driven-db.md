# 📘 Full Conceptual Schema – Scheduled Discovery (Intent–Intent Matching)

---

# I. Bounded Contexts

```text
1. Identity & Access
2. User Profile
3. Social Graph
4. Walk Coordination (Intent–Intent Matching)
5. Walk Lifecycle (Session)
6. Trust & Reputation
7. AI Personalization
```

Không có Realtime Matching.

---

# II. Identity & Access Context

## Aggregate: UserAccount (Aggregate Root)

### Entity: UserAccount

- user_id
- email
- phone
- password_hash
- provider
- status (ACTIVE / SUSPENDED / DELETED)
- created_at
- last_login_at

### Invariants

- Email unique
- Phone unique
- 1 account = 1 identity

---

# III. User Profile Context

## Aggregate: UserProfile (Aggregate Root)

### Entity: UserProfile

- user_id
- full_name
- gender
- date_of_birth
- avatar_url
- bio
- search_radius
- preferences

### Sub-Entities

- ProfileTag
  - tag_type
  - created_at

### Invariants

- Profile chỉ tồn tại nếu có UserAccount

---

# IV. Social Graph Context

## Aggregate: Friendship

### Entity: Friendship

- friendship_id
- user1_id
- user2_id
- status (ACTIVE / BLOCKED)
- favorite_flag_user1
- favorite_flag_user2
- created_at

### Invariants

- 1 cặp user chỉ có 1 friendship
- BLOCKED → không được match

---

# V. Walk Coordination Context (Intent–Intent Matching)

Đây là nơi diễn ra discovery.

---

## Aggregate: WalkIntent (Aggregate Root)

### Entity: WalkIntent

- intent_id
- user_id
- location_lat
- location_lng
- time_window_start
- time_window_end
- purpose
- match_filter
- status (OPEN / MATCHED / CONFIRMED / EXPIRED / CANCELLED)
- created_at
- expires_at

### Invariants

- Intent phải có time_window_end > time_window_start
- 1 user không được có 2 OPEN intent có time_window overlap
- Intent chỉ match nếu status = OPEN
- Intent expire nếu quá expires_at

---

## Aggregate: MatchProposal

### Entity: MatchProposal

- proposal_id
- intent_id_A
- intent_id_B
- proposed_time_start
- proposed_time_end
- proposed_location
- status (PENDING / ACCEPTED_BY_A / ACCEPTED_BY_B / CONFIRMED / REJECTED / EXPIRED)
- created_at
- expires_at

### Invariants

- Cả 2 intent phải ở trạng thái OPEN
- Một intent chỉ có tối đa 1 proposal PENDING tại một thời điểm
- Khi CONFIRMED → trigger WalkSession creation
- Khi 1 intent bị CANCELLED/EXPIRED → proposal auto EXPIRED

---

# VI. Walk Lifecycle Context

Đây là execution phase.

---

## Aggregate: WalkSession (Aggregate Root)

### Entity: WalkSession

- session_id
- user1_id
- user2_id
- scheduled_start_time
- scheduled_end_time
- actual_start_time
- actual_end_time
- status (PENDING / ACTIVE / COMPLETED / NO_SHOW / CANCELLED)
- created_at
- source_intent_id_A (nullable)
- source_intent_id_B (nullable)

---

## 🔴 Invariant QUAN TRỌNG (đã sửa)

Thay vì:

```text
1 user chỉ có 1 session ACTIVE hoặc PENDING
```

Đổi thành:

```text
1 user không được có 2 WalkSession
có time window overlap
với status ∈ (PENDING, ACTIVE)
```

Formal:

Không tồn tại S1, S2 sao cho:

- S1.user = U
- S2.user = U
- S1 ≠ S2
- S1.time overlaps S2.time
- S1.status ∈ (PENDING, ACTIVE)
- S2.status ∈ (PENDING, ACTIVE)

Nếu không overlap → hoàn toàn hợp lệ.

---

## Sub-Aggregate: ChatRoom

### Entity: ChatRoom

- chat_room_id
- session_id
- open_at
- close_at
- status (OPEN / CLOSED)

### Invariants

- ChatRoom mở khi session PENDING (trước giờ hẹn X phút) hoặc ACTIVE
- ChatRoom đóng khi session vào terminal state

---

# VII. Trust & Reputation Context

---

## Aggregate: WalkReview

### Entity

- review_id
- session_id
- reviewer_id
- reviewee_id
- rating_stars
- tags
- created_at

### Invariants

- Chỉ tạo khi session COMPLETED
- 1 user chỉ review 1 lần per session

---

## Aggregate: TrustScore

### Entity

- user_id
- score
- last_updated

Score thay đổi theo:

- Completion (+)
- Cancellation (-)
- No-show (-)

---

## Aggregate: Badge

### Entity: Badge

- badge_id
- name
- condition_type
- condition_value

### Entity: UserBadge

- user_id
- badge_id
- earned_at

---

# VIII. Reporting Context

## Aggregate: Report

- report_id
- reporter_id
- reported_user_id
- session_id (nullable)
- reason
- evidence_url
- status

---

# IX. AI Personalization Context

AI chỉ ranking Intent–Intent.

---

## Aggregate: UserEmbedding

- user_id
- vector_data
- last_updated

---

## Aggregate: MatchingPreferenceModel

- user_id
- w1_time_overlap
- w2_distance
- w3_interest_similarity
- w4_behavior_similarity

---

## AI Responsibilities

AI tính:

```text
Score(IntentA, IntentB)
```

AI không:

- Tạo session
- Thay đổi trạng thái
- Áp penalty

---

# X. Updated Event-Driven Flow (Intent–Intent + Non-overlap Rule)

```text
User A tạo WalkIntent
User B tạo WalkIntent
↓
MatchingService detect overlap
↓
AI ranking
↓
MatchProposal created
↓
Hai bên ACCEPT
↓
SessionService.createSession()
    → load all existing PENDING/ACTIVE sessions của user
    → check time overlap
    → nếu conflict → reject
    → nếu ok → create WalkSession
↓
Lifecycle execution
```

---

# XI. Aggregate Roots

```text
UserAccount
UserProfile
Friendship
WalkIntent
MatchProposal
WalkSession
WalkReview
TrustScore
Badge
Report
UserEmbedding
```

---

# XII. Điều quan trọng nhất (phiên bản corrected)

Mô hình này:

- Là Intent–Intent matching
- Không duplicate lifecycle
- WalkSession là single source of truth
- Cho phép nhiều session tương lai
- Cấm session trùng thời gian
- Invariant chính xác với domain thực tế
- AI chỉ ranking
- Dễ maintain và evolve

---

# 🎯 Kết luận

Giờ đây thiết kế:

✔ Phù hợp Scheduled Discovery
✔ Phù hợp Intent–Intent
✔ Không quá restrictive
✔ Không artificial limit user
✔ Domain invariant chính xác hơn
