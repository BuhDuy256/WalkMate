# 📜 WalkSession Feature Declaration
**Status:** READY FOR ENGINEERING
**Phase:** 4 (Engineering Blueprint)

## 1. Business Context
The WalkSession domain manages the real-world execution of a scheduled walk between two Matched Users. It governs the lifecycle from waiting for both participants to activate the session, through the active physical duration, to handling terminations (Completed, Aborted, or Cancelled).

## 2. UI ↔ Backend Authority Override
Because visual designs (Figma) can be rigid, this document serves as the **Single Source of Truth** for the Backend Implementation. It explicitly overrides and clarifies UI behavior as follows:

*   **Override 1: The "Pause" Illusion:** The Figma design shows a `PAUSED` state with a "Pause Walk" button. **Backend Directive:** The backend will completely ignore this. The state machine will remain `ACTIVE`. The pause feature is strictly a client-side visual feature to stop the pedometer/local timer.
*   **Override 2: The Missing "Cancel" Button:** The Figma `Init` screen lacks a "Cancel Walk" button. **Backend Directive:** The `CancelSessionCommand` API Endpoint MUST be implemented. The UI team is required to add this button later. The backend must enforce invariant `S-6` (allowing cancellation prior to or during the activation window) regardless of the current UI state.
*   **Override 3: The Missing "Abort Reason" Prompt:** The Figma design for ending a walk early does not show a reason prompt. **Backend Directive:** The `EmergencyAbortCommand` API Endpoint MUST accept a required `reason` payload (e.g., "Injury", "Safety"). If the client hits "End Walk" before the 5-minute threshold, they must hit this endpoint. The UI team will be instructed to intercept that click and show a prompt later.

## 3. Strict State Transitions
| Current State | Target State | Trigger Scenario |
| :--- | :--- | :--- |
| `PENDING` | `ACTIVE` | Both users fire `ActivateSessionCommand` within the activation grace period. |
| `PENDING` | `NO_SHOW` | System Cron Job fires exactly at the end of the activation grace period, detecting only 1 user activated. |
| `PENDING` | `CANCELLED` | System Cron Job fires (0 users activated) OR user fires `CancelSessionCommand` manually. |
| `ACTIVE` | `COMPLETED` | User fires `EndSessionCommand` (elapsed time > threshold) OR System Cron Job forcibly ends walk exceeding safety limits. |
| `ACTIVE` | `ABORTED` | User fires `EmergencyAbortCommand` (elapsed time < threshold, accompanied by a valid reason payload). |

*Note: The terminal states (`COMPLETED`, `NO_SHOW`, `CANCELLED`, `ABORTED`) are immutable.*

## 4. Required Backend Capabilities (API Canvas)

### User Triggers (Commands)
1.  **`ActivateSessionCommand`**
    *   **Action:** Sets the calling user's status to ready.
    *   **Concurrency:** Must use optimistic locking (versioning) or row-level `SELECT FOR UPDATE` to prevent race conditions during the final milliseconds of the activation window.
    *   **Payload:** `sessionId`, `userId`, `clientTimestamp`
2.  **`CancelSessionCommand`**
    *   **Action:** Gracefully cancels the walk *prior to or during* the activation window.
    *   **Concurrency:** Must handle idempotency if both users attempt to cancel simultaneously.
    *   **Payload:** `sessionId`, `userId`, `cancellationReason` (Optional)
3.  **`EndSessionCommand`**
    *   **Action:** Completes an `ACTIVE` walk (only permitted if `elapsedTime >= MIN_DURATION_THRESHOLD`).
    *   **Concurrency/Idempotency:** Must gracefully return a `200 OK` (no-op) if the session was recently marked `COMPLETED` by the safety cron job due to exact time overlap.
    *   **Payload:** `sessionId`, `userId`
4.  **`EmergencyAbortCommand`**
    *   **Action:** Aborts an `ACTIVE` walk before the duration threshold. Requires an `AbortReason` Enum payload.
    *   **Payload:** `sessionId`, `userId`, `reason` (Enum: INJURY, SAFETY, ENVIRONMENT, OTHER), `details` (Optional string)
5.  **`UpdateSessionTelemetryCommand` / WebSocket**
    *   **Action:** Live syncs metrics during the `ACTIVE` phase.
    *   **Concurrency:** High-throughput endpoint. Avoid locking the central session row for every step count update; consider a Redis buffer or a dedicated telemetry sub-table.
    *   **Payload:** `sessionId`, `userId`, `steps`, `distanceMeters`, `calories`

### System Triggers (Background Workers/Crons)
1.  **`SweepExpiredActivationsWorker`**
    *   **Action:** Runs frequently (e.g., every 10 seconds). Finds rows where `status='PENDING'` AND `(scheduled_start_time + activation_grace_period) < NOW()`. 
    *   **Concurrency/Locking:** MUST execute `SELECT ... FOR UPDATE SKIP LOCKED` to prevent deadlocks and ensure no overlap with inflight `ActivateSessionCommand` requests.
    *   **Resolution:** If `user1_activated` XOR `user2_activated` is true -> `NO_SHOW`. If neither -> `CANCELLED`.
2.  **`SweepMaxDurationWalksWorker`**
    *   **Action:** Runs frequently. Finds rows where `status='ACTIVE'` AND `actual_start_time < NOW() - MAX_SAFETY_DURATION` (e.g., 4 hours). 
    *   **Concurrency/Locking:** `SELECT ... FOR UPDATE SKIP LOCKED`. Forcibly transitions to `COMPLETED`.

### Read Models (Queries)
1.  **`GetActiveSessionStateQuery`**
    *   **Action:** Returns the hydrated session model for the UI, including partner network location if available. 
2.  **`GetWalkSummaryQuery`**
    *   **Action:** Aggregates final step count, distance, duration, and calories for the terminal UI screen.

---

## 5. Engineering Readiness
This declaration resolves all ambiguities. The UI limitations have been successfully decoupled from the Domain layer.

**Ready for Phase 5 (The Backend Development Prompt):** `BackendDevelopmentPrompt.md` can now be triggered safely against this blueprint.
