# Core Domain Lifecycles & Scheduling System

## 1. WalkIntent

The **WalkIntent** domain declares a user's availability and intent to participate in a walk at a specific time and hotspot.

### Lifecycle Stages

- **OPEN:** The intent is newly created and actively looking for a match. It blocks the time overlap in `UserSchedule`.
- **MATCHING:** A `MatchProposal` has been generated. The intent is "soft-locked" to prevent multiple simultaneous proposals. It remains blocking the time overlap.
- **CONSUMED:** A `WalkSession` has been successfully created. This is a terminal state. The responsibility of blocking the time overlap is handed over to the `WalkSession`.
- **CANCELLED:** The user manually withdrew the intent. Terminal state.
- **EXPIRED:** The valid time window passed without a confirmed match. Terminal state.

### Transitions

| From         | To            | Trigger                                                                      |
| :----------- | :------------ | :--------------------------------------------------------------------------- |
| **OPEN**     | **MATCHING**  | System generates a `MatchProposal` (PENDING).                                |
| **MATCHING** | **OPEN**      | `MatchProposal` is REJECTED or EXPIRED (User chooses to continue searching). |
| **MATCHING** | **CONSUMED**  | `MatchProposal` becomes CONFIRMED (Session created).                         |
| **MATCHING** | **CANCELLED** | User rejects proposal and chooses to withdraw intent.                        |
| **OPEN**     | **CANCELLED** | User manually withdraws the intent.                                          |
| **OPEN**     | **EXPIRED**   | The valid time window elapses.                                               |

---

## 2. MatchProposal

The **MatchProposal** domain coordinates the negotiation between two `WalkIntents`.

### Lifecycle Stages

- **PENDING:** Awaiting acceptance from both involved users.
- **CONFIRMED:** Both users accepted. This triggers `WalkSession` creation and moves `WalkIntent` to `CONSUMED`.
- **REJECTED:** One or both users declined the proposal.
- **EXPIRED:** The proposal window closed before mutual consensus.

### Transitions

| From        | To            | Trigger                                           |
| :---------- | :------------ | :------------------------------------------------ |
| [None]      | **PENDING**   | Match Engine finds two compatible `OPEN` intents. |
| **PENDING** | **CONFIRMED** | Both participants flag as accepted.               |
| **PENDING** | **REJECTED**  | At least one participant flags as rejected.       |
| **PENDING** | **EXPIRED**   | Proposal validity duration elapses.               |

---

## 3. WalkSession

The **WalkSession** domain governs the real-world execution and path tracing (Strava-like).

### Lifecycle Stages

- **PENDING:** Session created, waiting for participants to arrive at the Hotspot and activate.
- **ACTIVE:** Both participants successfully activated the session (Path tracing in progress).
- **COMPLETED:** The walk reached its destination or time limit.
- **NO_SHOW:** Only one participant activated within the window.
- **CANCELLED:** Manual cancellation prior to start or zero activation.
- **ABORTED:** Session terminated due to reported issues or emergency.

### Transitions

| From        | To            | Trigger                                    |
| :---------- | :------------ | :----------------------------------------- |
| [None]      | **PENDING**   | `MatchProposal` transitions to CONFIRMED.  |
| **PENDING** | **ACTIVE**    | Mutual activation within the valid window. |
| **PENDING** | **NO_SHOW**   | One-sided activation timeout.              |
| **PENDING** | **CANCELLED** | Manual cancel or no one activates.         |
| **ACTIVE**  | **COMPLETED** | Goal reached or time elapsed.              |
| **ACTIVE**  | **ABORTED**   | Manual report or safety incident.          |

---

## 4. UserSchedule (Centralized Invariant Source)

To ensure the **Time Overlap Invariant** (One user cannot be in two places at once), the system utilizes a centralized `UserSchedule` table.

### Logic

- **Ownership:** Every `WalkIntent` (OPEN/MATCHING) and `WalkSession` (PENDING/ACTIVE) must have a corresponding **ACTIVE** entry in this table.
- **Validation:** Before creating a new `WalkIntent`, the system checks `UserSchedule` for any **ACTIVE** record where:
  `MAX(Existing_Start, New_Start) < MIN(Existing_End, New_End)`.
- **Hand-off:** When an Intent transitions to `CONSUMED`, the `UserSchedule` record is updated to reflect the new `ref_id` (Session ID) and `ref_type` (SESSION), ensuring a continuous lock on the user's time without double-counting.

# Mốt số lưu ý

### 1. Trạng thái "Chờ đợi đơn phương" (Partial Acceptance)

Trong `MatchProposal`, khi User A bấm **Accept** nhưng User B vẫn đang **Pending**:

- **Vấn đề:** User A bị khóa cứng ở trạng thái `MATCHING`. Nếu User B cứ để đó không trả lời (treo máy), User A sẽ bị "giam cầm" thời gian mà không được match với ai khác.
- **Giải pháp:** \* Cần có một **Proposal Timeout** rất ngắn (ví dụ 2-5 phút).
  - Nếu quá thời gian mà một bên chưa trả lời, hệ thống tự động chuyển Proposal sang `EXPIRED` và đẩy cả hai quay lại `OPEN`.

### 2. Độ trễ giữa Proposal và Start Time (The "Last Minute" Risk)

- **Kịch bản:** User tạo Intent đi dạo lúc 17:00. Đến 16:55 hệ thống mới tìm thấy Match và tạo Proposal.
- **Vấn đề:** Thời gian xác nhận (Confirm) có thể kéo dài lấn sang cả giờ bắt đầu đi dạo.
- **Giải pháp:** Cần có logic **Auto-Expire Intent**: Nếu còn 5 phút nữa là đến giờ đi dạo mà Intent vẫn chưa chuyển sang `CONSUMED` (tức là chưa có Session), hãy tự động chuyển nó sang `EXPIRED`. Đừng để User đợi đến sát giờ hoặc quá giờ mới báo không tìm được bạn.

### 3. Vấn đề "Thay đổi ý định" (Edit Intent)

- **Vấn đề:** Khi Intent đang ở trạng thái `MATCHING` (đã có Proposal), User có được sửa thời gian hoặc Hotspot không?
- **Giải pháp:** **Cấm sửa khi đã MATCHING.**

### 4. Hậu quả của NO_SHOW và ABORTED (Reputation System)

Vì app của bạn là WalkMate (gặp người lạ), sự tin tưởng là quan trọng nhất.

- **Vấn đề:** Nếu một User chuyên gia bấm Accept cho sang chảnh rồi đến giờ lại `NO_SHOW` hoặc giữa chừng `ABORTED` mà không có lý do chính đáng.
- **Giải pháp:** \* Cần một bảng **UserReputation** (hoặc Karma điểm).
  - Mỗi khi Session kết thúc với trạng thái `NO_SHOW` hoặc `ABORTED`, hệ thống tự động trừ điểm uy tín của User gây lỗi.
  - Khi điểm quá thấp, Matching Engine sẽ không ưu tiên ghép đôi họ nữa (dù Intent vẫn `OPEN`).

### 5. Race Condition (Tranh chấp dữ liệu)

Đây là lỗi kỹ thuật hay gặp ở các app dạng Grab:

- **Vấn đề:** Hai Proposal khác nhau cùng nhảy vào "vồ" lấy một `WalkIntent` cùng một lúc ở mức mili giây.
- **Giải pháp:** Sử dụng **Database Transaction** và **Pessimistic Locking** (Khóa bản ghi). Khi một Proposal đang được tạo cho Intent A, phải khóa dòng Intent A đó lại để không có Proposal thứ hai nào "đụng" vào được cho đến khi trạng thái chuyển sang `MATCHING` xong xuôi.
