# Core Domain Lifecycles

## WalkIntent

The WalkIntent domain declares a user's availability at a specific time and location.

### Lifecycle Stages

*   **DRAFT:** The intent is incomplete and not visible for matching.
*   **OPEN:** The intent is eligible for proposals and blocks the time overlap invariant.
*   **CONSUMED:** A WalkSession was created using this intent. It no longer blocks the time overlap invariant.
*   **CANCELLED:** The user manually withdrew the intent before session creation.
*   **EXPIRED:** The time window passed without session creation.

### Transitions

| From | To | Trigger |
| :--- | :--- | :--- |
| DRAFT | OPEN | User submits. System validates `time_window_start` is safely in the future. |
| OPEN | CONSUMED | A related MatchProposal becomes CONFIRMED. |
| OPEN | CANCELLED | User manually withdraws the intent. |
| OPEN | EXPIRED | The valid time window elapses (safely row-locked prior to update). |

## MatchProposal

The MatchProposal domain coordinates the synchronization negotiation between two WalkIntents. Mutual acceptance is tracked via internal properties.

### Lifecycle Stages

*   **PENDING:** The proposal awaits confirmation from involved users.
*   **CONFIRMED:** Both users have accepted the proposal. This state triggers immediate WalkSession creation.
*   **REJECTED:** One or both users explicitly declined the proposal.
*   **EXPIRED:** The confirmation time window elapsed before mutual consensus was reached.

### Transitions

| From | To | Trigger |
| :--- | :--- | :--- |
| [None] | PENDING | The system generates a MatchProposal for two OPEN WalkIntents. |
| PENDING | CONFIRMED | Both users flag the proposal as accepted. |
| PENDING | REJECTED | At least one user flags as rejected, or a Saga dictates rejection (`WalkIntentCancelled`). |
| PENDING | EXPIRED | Proposal validity duration elapses, or a Saga dictates expiration (`WalkIntentExpired`). |

## WalkSession

The WalkSession domain manages the real-world execution of the scheduled walk and governs value realization.

### Lifecycle Stages

*   **PENDING:** The session awaits mutual activation by the participants.
*   **ACTIVE:** Both participants successfully activated the session within the valid activation window.
*   **COMPLETED:** The ACTIVE session reached its end time or satisfied the completion threshold.
*   **NO_SHOW:** Only one participant activated the session within the activation window.
*   **CANCELLED:** The session was cancelled prior to or during the activation window, or neither participant activated it.
*   **ABORTED:** The ACTIVE session was prematurely terminated by a user due to an emergency, injury, or safety concern before the completion threshold.

### Transitions

| From | To | Trigger |
| :--- | :--- | :--- |
| [None] | PENDING | A MatchProposal transitions to CONFIRMED. |
| PENDING | ACTIVE | Both users activate the session within the valid window. |
| PENDING | NO_SHOW | One user activates, but the other does not act within the valid window. |
| PENDING | CANCELLED | User manually cancels before or during the window, or no users activate within the window. |
| ACTIVE | COMPLETED | The walk end time is reached or the completion threshold is met. |
| ACTIVE | ABORTED | A user initiates premature termination for safety or emergency reasons. |
