# 🎯 AI System – Domain-Aligned Design (Final Version)

---

# I. AI's Role in the System

AI in WalkMate does not replace the random matching mechanism.

AI only serves to:

- Fine-tune priority order within the set of valid candidates
- Personalize user experience
- Increase probability of COMPLETED sessions
- Suggest conversation topics after a session is created

AI does **NOT have permission to**:

- Create WalkSession
- Change aggregate state
- Apply penalties
- Override domain invariants

AI only provides scores and suggestions.

---

# II. AI Matching – Intent-Level Personalization

AI operates during the Coordination Phase (WalkIntent).

---

## 1️⃣ Scope of Operation

AI runs only when:

- WalkIntent.status = OPEN
- UserPresence.availability = AVAILABLE
- No BlockRelation exists
- Candidate already satisfies basic matching conditions:
  - Time window overlap
  - WalkPurpose matches
  - Matching constraints are valid

AI only ranks within the set of valid candidates.

---

# III. Data Sources AI Uses

AI does not access state machine directly.
AI reads from read models / projections.

---

## 1️⃣ Interaction History

- WalkIntent already ACCEPTED
- MatchProposal already CONFIRMED
- Session COMPLETED

---

## 2️⃣ Experience Quality

- 5-star ratings
- Following relationships
- Completion frequency

---

## 3️⃣ Actual Behavior (Lifecycle-based)

From COMPLETED WalkSession:

- Average walk duration
- Average movement speed (derived from trace)
- No-show rate
- Cancellation rate

---

## 4️⃣ Activity Patterns

- Typical active time windows
- Typical WalkIntent creation time
- Typical session activation time

---

# IV. AI Model – 3 Steps Following DDD Standards

---

## Step 1 – User Embedding

Aggregate: UserEmbedding

The system builds a numeric vector:

```text
Vector(User) =
[
  time_preference_pattern,
  average_walk_duration,
  average_speed,
  favorite_tags_distribution,
  reliability_score,
  acceptance_pattern,
  cancellation_pattern
]
```

Embedding is updated when:

- Session COMPLETED
- Rating created
- Follow added
- No-show occurs

Embedding does not directly affect aggregates.

---

## Step 2 – Compatibility Scoring

AI calculates:

```text
Score(IntentA, IntentB) =
w1 * InterestSimilarity +
w2 * TimeOverlapStrength +
w3 * BehavioralSimilarity +
w4 * ReliabilityFactor
```

Where:

- InterestSimilarity → tag similarity
- TimeOverlapStrength → time window overlap strength
- BehavioralSimilarity → speed + duration + patterns
- ReliabilityFactor → TrustScore

Weights are adjusted based on:

- Accept/reject history
- 5-star ratings
- Following relationships

AI only returns scores.
MatchingService decides the suggestion order.

---

## Step 3 – Context Awareness

AI analyzes current context:

- Weather
- Location type (park, mall, etc.)
- Time of day

For example:

- If user typically chooses indoor locations when raining
  → Prioritize Intent near indoor locations

AI only adjusts ranking, it does not create proposals.

---

# V. Cold Start Strategy

During initialization phase (insufficient data):

AI fallback:

- Match using:
  - Time overlap
  - Purpose match
  - Distance proximity (geo-hash level, not continuous radius scanning)

After 3–5 COMPLETED sessions:

- Enable UserEmbedding personalization

---

# VI. Reliability-Aware Matching

AI prioritizes:

- People with high COMPLETED rates
- Few NO_SHOW instances
- Few last-minute cancellations

However, AI does not apply penalties itself.
Penalties are still handled by WalkSession aggregate + TrustScore.

---

# VII. AI Conversation Assistant (Chat Suggestion)

Scope:

- Only operates when WalkSession.status ∈ (PENDING, ACTIVE)
- Only UI suggestions
- Not stored in domain aggregate

---

## Data Sources

AI uses:

1️⃣ Shared tags
2️⃣ Context (weather, location)
3️⃣ Recent achievements
4️⃣ WalkPurpose

---

## 3S Framework

AI scans:

- Shared Interests
- Situational Context
- Session Goal

---

## Example Suggestions

### Based on Interests

"You both love walking with pets. Which route do you usually take in this park?"

### Based on Context

"What a beautiful day today! Should we walk the entire lake loop?"

### Based on Goal

"My goal today is 2km in 20 minutes. Want to give it a try?"

---

## UX Flow

Match confirmed
→ WalkSession created
→ ChatRoom OPEN
→ AI displays 2–3 suggestions
→ User taps to send

AI does not send messages automatically.

---

# VIII. AI Boundary Protection

AI does NOT:

- Call WalkSession.activate()
- Call WalkSession.complete()
- Create WalkSession
- Change TrustScore

AI only:

- Reads projection
- Computes scores
- Emits suggestions

---

# IX. Event-Driven Integration

AI reacts to Domain Events:

- WalkSessionCompleted
- PartnerNoShowReported
- SessionCancelled
- WalkReviewCreated
- FollowRelationCreated

Projection service updates embedding.

---

# X. Alignment Check

| Rule                          | Aligned |
| ----------------------------- | ------- |
| Does not change state machine | ✅       |
| Does not create session       | ✅       |
| Only ranks                    | ✅       |
| Based on COMPLETED            | ✅       |
| Does not break invariants     | ✅       |
| Chat only suggestions         | ✅       |
| Cold start defined            | ✅       |
| Reliability-aware             | ✅       |

---

# XI. Summary

AI in WalkMate:

- Does not replace matching
- Does not violate domain boundaries
- Does not interfere with lifecycle
- Does not mutate aggregates
- Only personalizes ranking and experience

Coordination remains in WalkIntent
Lifecycle remains in WalkSession
AI is just a personalization layer.
