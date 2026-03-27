Perfect. Below is **WALKMATE – FINAL PROPOSAL (100% Aligned with DDD State Machine)**.
This design strictly adheres to boundaries:

- **Coordination Phase → WalkIntent aggregate**
- **Lifecycle Phase → WalkSession aggregate (5 states: PENDING, ACTIVE, COMPLETED, NO_SHOW, CANCELLED)**
- Session is created only after **mutual confirmation** (Value Realization Boundary)
- MATCHED/CONFIRMED are no longer states of WalkSession

---

# WALKMATE – DOMAIN-ALIGNED PROPOSAL (16 GROUP FEATURES)

---

# 1. Authentication & Security

- Login / Sign up:
  - Google
  - Phone number (OTP)
  - Email & password

- Password recovery
- Logout
- Session management
- Sensitive data encryption

---

# 2. User Profile & Privacy

## 2.1 Public Profile

Displayed when participating in coordination:

- Display name
- Profile picture
- Gender
- Age
- Interest tags (Pet, Music, Quiet…)

## 2.2 Private Profile

Internal display only:

- Email
- Phone number
- Login history

## 2.3 Public / Private Mode

- Public Mode: Can participate in matching
- Private Mode: Does not appear in coordination phase

---

# 3. Location & Trace Path

## 3.1 Coordination Phase – Snapshot Location

- When creating WalkIntent:
  - System captures GPS snapshot

- No continuous tracking during coordination phase

## 3.2 Lifecycle Phase – Realtime Tracking

When WalkSession is in **ACTIVE** state:

- GPS updates at regular intervals
- Synchronize both users' locations
- Display markers & polyline

## 3.3 Trace Persistence

Trace is saved only when:

- WalkSession = COMPLETED

Saved data:

- Coordinates
- Total distance
- Total duration

---

# 4. Coordination Phase (WalkIntent Aggregate)

WalkIntent is the aggregate managing the phase before value realization.

---

## 4.1 Creating WalkIntent

User defines the schedule:
- Scheduled Time (Expected start and end time; can start from current time)
- Walk Purpose
- Snapshot Location
- Matching constraints (Distance, Gender, Age)

Architectural notes:
- No "Quick Match" mechanism exists.
- If user selects current time (startTime = currentTime), it is still a normal Scheduled WalkIntent.
- WalkIntent always goes through standard lifecycle and cannot bypass MatchProposal.

WalkIntent exists independently from WalkSession with OPEN status.

---

## 4.2 Matching

The system:
- Matches by Scheduled Time window (including cases where startTime = currentTime)
- Matches Location and Purpose
- Applies constraints
- Checks for Blocks

Matching always creates **MatchProposal** in PENDING state.

No auto-create WalkSession mechanism.
No mechanism to skip mutual confirmation step.

---

## 4.3 Mutual Confirmation (Value Boundary)

When both parties confirm (acceptedByA == true AND acceptedByB == true):
→ MatchProposal transitions to CONFIRMED.
→ Domain Service locks related WalkIntents.
→ Re-check Invariants (no schedule conflicts, intent still OPEN).
→ Create **WalkSession in PENDING state**.
→ Related WalkIntents transition to CONSUMED.

Notes:
- No case for creating WalkSession without CONFIRMED.
- No direct path from WalkIntent to WalkSession.

---

# 5. WalkSession Aggregate (Lifecycle Phase)

WalkSession only manages the lifecycle of commitment.

## 5.1 Valid States (5 states)

- PENDING
- ACTIVE
- COMPLETED (terminal)
- NO_SHOW (terminal)
- CANCELLED (terminal)

Terminal states are immutable.

---

## 5.2 PENDING

- After mutual confirmation
- Waiting for activation within grace period
- Can:
  - Activate → ACTIVE
  - Cancel → CANCELLED
  - Timeout → NO_SHOW (auto)

---

## 5.3 ACTIVE

- Only occurs when BOTH users have clicked "Start Walk" within activation window.
- GPS tracking enabled.
- Chat still open.

Can:
- Complete → COMPLETED (when minimum duration reached)
- Auto-complete (when planned duration or safety limit reached)

No mechanism to report no-show during ACTIVE.
ACTIVE cannot transition to NO_SHOW by unilateral action.

---

## 5.4 COMPLETED

- Successful walk
- Unlock Rating
- Trigger Achievement
- Terminal & immutable

---

## 5.5 NO_SHOW

- Occurs automatically when activation window ends with:
  - Insufficient activations from both users.
- Apply penalty according to policy.
- Terminal & immutable.

No NO_SHOW from ACTIVE state.

---

## 5.6 CANCELLED

- Only from PENDING
- Tiered penalty based on timing
- Terminal & immutable

---

# 6. Activation & Completion Rules

## Activation Window
- From Start_Time -15 minutes
- To Start_Time +30 minutes

Session transitions to ACTIVE only if both users activate within this window.

## Minimum Duration
- ≥ 5 minutes to be COMPLETED.
- Cannot COMPLETE if minimum duration not reached.

## Safety Limit
- ACTIVE automatically COMPLETES when safety limit reached (e.g., 4 hours).

---

# 7. Chat (Bounded by WalkSession)

Chat exists only in Lifecycle Phase.

| State     | Chat               |
| --------- | ------------------ |
| PENDING   | Open               |
| ACTIVE    | Open               |
| COMPLETED | Closed (read-only) |
| CANCELLED | Closed             |
| NO_SHOW   | Closed             |

Chat Room is bound to session_id.

No chat exists outside WalkSession.

---

# 8. Matching Constraints

Users can set:

- Desired gender
- Desired age

Applied during Coordination Phase.

Not applied to Direct Invite. However, Direct Invite does not bypass Invariants:
- Must still satisfy time overlap.
- Must still satisfy OPEN status of WalkIntent.
- Must still go through MatchProposal → CONFIRMED → WalkSession.

Server checks Block before creating MatchProposal.

---

# 9. Following & Block

## 9.1 Following

- Performed after COMPLETED
- Does not create Session
- Does not create private chat
- Increases matching weight
- Enables Direct Invite

## 9.2 Block

If blocked:

- Cannot create MatchProposal
- Cannot create WalkSession
- Cannot chat

---

# 10. Scheduled Matching vs Scheduled Direct Invite

## 10.1 Scheduled Matching

- WalkIntent has Time in the future
- System matches randomly
- After confirmation → create WalkSession (PENDING)

## 10.2 Scheduled Direct Invite

- Send specific invitation to Following / Completed partner
- After mutual confirmation → create WalkSession (PENDING)

Both go through Coordination Phase before entering Lifecycle Phase.

---

# 11. Notification System

Push notifications are triggered by Domain Events that precisely reflect Lifecycle:

## Coordination Phase
- MatchProposalCreated (PENDING)
- MatchProposalConfirmed
- MatchProposalRejected
- MatchProposalExpired

## Lifecycle Phase
- WalkSessionCreated (state = PENDING)
- WalkSessionActivated (state = ACTIVE)
- WalkSessionCancelled
- WalkSessionNoShow (due to insufficient activation in activation window)
- WalkSessionCompleted
- New chat message

No "Quick Match" event exists.
No auto-create session event exists.

Server is the only source of events.

---

# 12. Rating System

Only appears when:

- WalkSession = COMPLETED

- 1–5 stars

- Descriptive tags

Impact:

- TrustScore aggregate
- Matching weight

---

# 13. Session History

Saves:

- session_id
- Scheduled time
- Actual start/end
- Duration
- Trace path
- Final state

Only COMPLETED counts toward achievements.

---

# 14. Gamification

Badges based on:

- Total km
- Number of COMPLETED sessions
- Number of 5-star ratings
- Streak

Only COMPLETED sessions counted.

---

# 15. Reporting & Dispute

Report is bound to session_id.

Dispute only applies to COMPLETED or NO_SHOW.

Dispute does not change WalkSession state.
Correction is handled through compensation events (e.g., TrustScore adjustment), not state mutation.

---

# 16. Analytics & Automated Monitoring

Track:

- No-show rate
- Cancellation patterns
- Rating distribution

Can:

- Reduce matching priority
- Restrict new WalkIntent creation in same time window

Performed automatically through Domain Events.

---

# Overall Architecture

Coordination Phase
(WalkIntent Aggregate)

→ Mutual Confirmation
→ Domain Service
→ Create WalkSession (PENDING)

Lifecycle Phase
(WalkSession Aggregate)

PENDING → ACTIVE → COMPLETED
        ↘ NO_SHOW
        ↘ CANCELLED
