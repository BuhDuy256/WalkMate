# Architectural Proposal: Separating WalkSession Lifecycle per Participant

## 1. Problem Statement
The current `WalkSession` domain maintains a single, shared `status` for both participants. This leads to a UI conflict: according to Invariant `S-2`, a session only becomes `ACTIVE` when **both** users have arrived. Consequently, if User A arrives first, they are blocked from starting their walk and path tracing until User B arrives.

## 2. Proposed Solution: The "Independent Participant State" Model
We will decouple the lifecycle of each participant by introducing individual statuses within the `WalkSession` aggregate. This allows each user to transition from `PENDING` to `ACTIVE` (and eventually `COMPLETED`) independently, while the global session status represents the overall match state.

### 2.1 Domain Logic Changes (`WalkSession.java`)
We will track the status, distance, and duration for each participant separately:

- **New Attributes**:
    - `userAStatus`, `userBStatus` (using existing `SessionStatus` enum).
    - `userAStartedAt`, `userBStartedAt`.
    - `userAEndedAt`, `userBEndedAt`.
    - `userADistanceKm`, `userBDistanceKm`.
    - `userADurationSeconds`, `userBDurationSeconds`.

- **Updated Behavior**:
    - `recordActivation(userId)`: Sets the specific user's status to `ACTIVE` and their `startedAt` timestamp. 
    - **Global Status Update**: The global `status` is set to `ACTIVE` if *at least one* participant is `ACTIVE`.
    - `complete(userId)`: Sets the specific user's status to `COMPLETED` and their `endedAt` timestamp.
    - **Global Status Update**: The global `status` transitions to `COMPLETED` only when *both* participants have reached a terminal state (COMPLETED, CANCELLED, or NO_SHOW).

### 2.2 Database Schema Update
The `walk_session` table will be updated to store these individual metrics. We recommend a single-table approach for simplicity and performance in the current "A/B" matching model.

```sql
-- Track independent statuses
ALTER TABLE public.walk_session
  ADD COLUMN user_a_status walk_session_status NOT NULL DEFAULT 'PENDING',
  ADD COLUMN user_b_status walk_session_status NOT NULL DEFAULT 'PENDING';

-- Track individual timings
ALTER TABLE public.walk_session
  RENAME COLUMN started_at TO user_a_started_at;
ALTER TABLE public.walk_session
  RENAME COLUMN ended_at TO user_a_ended_at;
ALTER TABLE public.walk_session
  ADD COLUMN user_b_started_at timestamp without time zone,
  ADD COLUMN user_b_ended_at timestamp without time zone;

-- Track individual metrics
ALTER TABLE public.walk_session
  RENAME COLUMN total_distance_km TO user_a_distance_km;
ALTER TABLE public.walk_session
  RENAME COLUMN total_duration_seconds TO user_a_duration_seconds;
ALTER TABLE public.walk_session
  ADD COLUMN user_b_distance_km numeric NOT NULL DEFAULT 0,
  ADD COLUMN user_b_duration_seconds bigint NOT NULL DEFAULT 0;
```

### 2.3 Refined Invariants
- **S-2 (Independent Activation)**: A participant enters the `ACTIVE` state and may begin path tracing as soon as they signal arrival. The shared match is considered `ACTIVE` if at least one participant is walking.
- **S-5 (Independent Completion)**: The minimum 5-minute walk duration is enforced per participant. A participant can finish and earn points even if their partner is still walking or never showed up.

## 3. Impact on System Components

### 3.1 Backend (DDD-lite)
- **Domain**: `WalkSession` entity will contain the logic to derive the global `status` from individual participant states.
- **Presentation**: `WalkSessionResponse` will be expanded to include `user_a_status` and `user_b_status`, allowing the UI to render the correct buttons for the local user.

### 3.2 Frontend (MVVM)
- **TrackingViewModel**: Will now observe the local user's status within the session to determine when to start/stop the `WalkTrackerService`.
- **UI**: If the local user is `ACTIVE` but the partner is still `PENDING`, the UI will show "Walking (Waiting for partner...)" instead of being stuck on the arrival screen.

## 4. Conclusion
By separating the participant lifecycles, we resolve the blocking UI conflict and provide a smoother user experience, especially in cases of partner delays or no-shows, while still preserving the data integrity of the joint walk session.
