# Core Domain Lifecycles & Scheduling System

## 1. WalkIntent

The **WalkIntent** domain declares a user's availability and intent to participate in a walk at a specific time and hotspot.

### Lifecycle Stages

- **OPEN:** The intent is newly created and actively looking for a match. Nó tham gia vào việc duy trì chặn trùng lịch trên toàn hệ thống thời gian.
  - Chỉ **public intent** mới được ở `OPEN` để tham gia public matching pool.
  - **Private intent** không được publicize lại thành `OPEN` sau khi private proposal bị từ chối hoặc hết hạn.
- **MATCHING:** A `MatchProposal` has been generated. The intent is "soft-locked" (đánh dấu matching lock) để không bị Match Engine tìm thấy nữa, nhưng vẫn tham gia chặn trùng lặp thời gian.
  - Public intent chuyển `OPEN → MATCHING` khi public matching tạo proposal.
  - Private invite flow tạo paired intents trực tiếp ở trạng thái `MATCHING`.
- **CONSUMED:** A `WalkSession` has been successfully created. This is a terminal state. Trách nhiệm duy trì chặn thời gian được chuyển giao hoàn toàn sang `WalkSession`.
- **CANCELLED:** The user manually withdrew the intent, or a private invite was declined/expired before becoming a session. Terminal state.
- **EXPIRED:** The valid time window passed without a confirmed match. Terminal state.

### Transitions

| From         | To            | Trigger                                                                      |
| :----------- | :------------ | :--------------------------------------------------------------------------- |
| **OPEN**     | **MATCHING**  | System creates a `MatchProposal` from public matching (inline on create or async/internal retry matching). |
| [None]       | **MATCHING**  | Private invite flow atomically creates paired private intents and proposal `PENDING`. |
| **MATCHING** | **OPEN**      | Public `MatchProposal` is REJECTED or EXPIRED and the user continues searching. This transition is valid for **public intents only**. |
| **MATCHING** | **CANCELLED** | Private-invite `MatchProposal` is REJECTED or EXPIRED. Both private intents are closed and must not return to `OPEN`. |
| **MATCHING** | **CONSUMED**  | `MatchProposal` becomes CONFIRMED (Session created).                         |
| **MATCHING** | **CANCELLED** | User rejects proposal and chooses to withdraw intent. For private invite, both paired private intents must be cancelled. |
| **MATCHING** | **EXPIRED**   | The valid time window elapses before the intent is consumed. For paired private intents, the whole private pair/proposal must be terminalized and must not unlock back to `OPEN`. |
| **OPEN**     | **CANCELLED** | User manually withdraws the intent.                                          |
| **OPEN**     | **EXPIRED**   | The valid time window elapses.                                               |

### Private Intent Invariants

Private invite has stricter lifecycle rules than public matching:

1. A private invite creates a **paired intent set**:
   - inviter private intent
   - invitee private intent
   - one `MatchProposal` connecting both intents

2. Private intents are created/locked as `MATCHING` immediately and are not part of the public matching pool.

3. A private intent must never be transitioned from `MATCHING` back to `OPEN`.

4. If a private proposal is declined/rejected:
   - `MatchProposal` → `REJECTED`
   - both private `WalkIntent`s → `CANCELLED`

5. If a private proposal expires:
   - `MatchProposal` → `EXPIRED`
   - both private `WalkIntent`s → `CANCELLED`

6. If the private walk time window expires before confirmation/session creation:
   - affected private intent(s) must move to a terminal state such as `EXPIRED` or `CANCELLED`
   - the paired private intent must also be terminalized
   - no private intent may be unlocked back to `OPEN`

7. If code detects a proposal where only one side is private and the other side is public, this is an invariant violation. The system should fail fast or log a critical error rather than silently treating it as public matching.

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
| [None]      | **PENDING**   | Public matching finds compatible intents, or private invite flow creates proposal. |
| **PENDING** | **CONFIRMED** | Both participants flag as accepted.               |
| **PENDING** | **REJECTED**  | At least one participant flags as rejected.       |
| **PENDING** | **EXPIRED**   | Proposal validity duration elapses.               |

### Public vs Private Proposal Resolution

A `MatchProposal` can be resolved differently depending on whether it belongs to public matching or private invite.

| Proposal Type | Resolution Event | Proposal State | Intent Resolution |
| :------------ | :--------------- | :------------- | :---------------- |
| Public matching | User passes/rejects but continues searching | `REJECTED` | Both public intents return `MATCHING → OPEN`. Caller should exclude the rejected partner to avoid immediate re-pairing. |
| Public matching | User cancels/withdraws | `REJECTED` | Caller intent `MATCHING → CANCELLED`; partner intent `MATCHING → OPEN`. |
| Public matching | Proposal expires | `EXPIRED` | Both public intents return `MATCHING → OPEN`. |
| Private invite | Invitee declines | `REJECTED` | Both private intents `MATCHING → CANCELLED`. |
| Private invite | Inviter cancels private invite | `REJECTED` | Both private intents `MATCHING → CANCELLED`. |
| Private invite | Proposal expires | `EXPIRED` | Both private intents `MATCHING → CANCELLED`. |
| Any proposal | Both users accept | `CONFIRMED` | Both intents `MATCHING → CONSUMED`, then `WalkSession` is created. |

**Key invariant:** `MATCHING → OPEN` is a public matching recovery transition only. It must never be used for private invite intents.

---

## 3. WalkSession

The **WalkSession** domain governs the real-world execution and path tracing (Strava-like).

### Lifecycle Stages

- **PENDING:** Session created, waiting for participants to arrive at the Hotspot and activate.
- **ACTIVE:** At least one participant has activated. Each participant owns an independent `user_a_status` / `user_b_status`. The global `ACTIVE` state is set as soon as the first participant arrives — the second participant may still be `PENDING`.
- **COMPLETED:** Both participants have reached a terminal state and at least one is `COMPLETED`. Path tracing and points are earned per participant independently.
- **CANCELLED:** Manual cancellation before either participant activates, or auto-cancel when `PENDING` exceeds the configured TTL.

### Transitions

| From        | To            | Trigger                                    |
| :---------- | :------------ | :----------------------------------------- |
| [None]      | **PENDING**   | `MatchProposal` transitions to CONFIRMED.  |
| **PENDING** | **ACTIVE**    | First participant presses Arrive (independent activation — second may follow later). |
| **PENDING** | **CANCELLED** | Manual cancel or session exceeds `pending-ttl` policy. |
| **ACTIVE**  | **COMPLETED** | Goal reached or time elapsed.              |

### Personal State vs Global State (State Invariant Table)

Each participant has an independent **PERSONAL STATE** (`user_a_status` / `user_b_status`). The **GLOBAL STATE** (`status`) is a derived value computed from the two personal states. **Individual user actions must always be validated against the user's own PERSONAL STATE, never against GLOBAL STATE.**

| `user_a_status`    | `user_b_status`    | Derived `global status` |
| :----------------- | :----------------- | :---------------------- |
| PENDING            | PENDING            | **PENDING**             |
| ACTIVE             | PENDING            | **ACTIVE**              |
| PENDING            | ACTIVE             | **ACTIVE**              |
| ACTIVE             | ACTIVE             | **ACTIVE**              |
| COMPLETED          | ACTIVE             | **ACTIVE**              |
| ACTIVE             | COMPLETED          | **ACTIVE**              |
| COMPLETED          | COMPLETED          | **COMPLETED**           |
| COMPLETED          | NO_SHOW            | **COMPLETED**           |
| NO_SHOW            | COMPLETED          | **COMPLETED**           |
| NO_SHOW            | NO_SHOW            | **COMPLETED**           |

**Key invariant:** GLOBAL STATE transitions to COMPLETED only when **both** participants are in a terminal state (`COMPLETED` or `NO_SHOW`). A session where User A has COMPLETED but User B is still ACTIVE has GLOBAL STATE = ACTIVE. User B must be able to call "Complete" even when GLOBAL STATE already shows their partner finished.

---

## 4. Time Overlap Invariant (Domain Service Approach)

Để đảm bảo quy tắc **Không chọn trùng khung giờ** (Một người không thể ở hai nơi trong cùng một khoảng thời gian), hệ thống loại bỏ bảng `UserSchedule` tập trung và thay thế bằng Domain Service chuyên biệt đóng vai trò Nguồn Sự Thật.

### Logic

- **Nguồn dữ liệu:** Domain Service sẽ truy vấn đồng thời trên 2 bảng để kiểm tra:
  - Bảng `WalkIntent` (với các trạng thái `OPEN`, `MATCHING`).
  - Bảng `WalkSession` (với các trạng thái `PENDING`, `ACTIVE`).
- **Validation:** Trước khi cho phép tạo mới hoặc dời lịch trình, hệ thống gọi Service để xác nhận không có bất kỳ dòng nào thỏa mãn: `MAX(Existing_Start, New_Start) < MIN(Existing_End, New_End)` thuộc về user đó.
- **Hand-off (Bàn giao):** Khi Intent chuyển từ `MATCHING` sang `CONSUMED`, Intent này sẽ "nhả" chặn thời gian, đồng thời `WalkSession` mới ngay lập tức "kế thừa" khoảng thời gian này thông qua Atomic Transaction, đảm bảo việc khóa thời gian liền mạch hoàn toàn mà không bị hở.

### Private Intent Scheduling Rule

Private intents still block the user's schedule while they are `MATCHING`.

However, once a private invite is declined, rejected, expired, or otherwise terminalized, both paired private intents must leave the blocking set by moving to a terminal state:

- `CANCELLED`
- or `EXPIRED`

Private intents must not remain or return to `OPEN`, because `OPEN` participates in schedule blocking and may create stale private availability that prevents users from creating new intents in the same time window.

# Mốt số lưu ý

### 1. Trạng thái "Chờ đợi đơn phương" (Partial Acceptance)

Trong `MatchProposal`, khi User A bấm **Accept** nhưng User B vẫn đang **Pending**:

- **Vấn đề:** User A bị khóa cứng ở trạng thái `MATCHING`. Nếu User B cứ để đó không trả lời (treo máy), User A sẽ bị "giam cầm" thời gian mà không được match với ai khác.
- **Giải pháp:** Cần có một **Proposal Timeout** rất ngắn (ví dụ 2-5 phút).
  - Với **public matching**, nếu quá thời gian mà một bên chưa trả lời, hệ thống tự động chuyển Proposal sang `EXPIRED` và đẩy cả hai public intents quay lại `OPEN`.
  - Với **private invite**, nếu quá thời gian mà người được mời chưa trả lời, hệ thống tự động chuyển Proposal sang `EXPIRED` và chuyển cả hai private intents sang `CANCELLED`. Private invite không được quay lại `OPEN`.

### 2. Độ trễ giữa Proposal và Start Time (The "Last Minute" Risk)

- **Kịch bản:** User tạo Intent đi dạo lúc 17:00. Đến 16:55 hệ thống mới tìm thấy Match và tạo Proposal.
- **Vấn đề:** Thời gian xác nhận (Confirm) có thể kéo dài lấn sang cả giờ bắt đầu đi dạo.
- **Giải pháp:** Cần có logic **Auto-Expire Intent**: Nếu còn 5 phút nữa là đến giờ đi dạo mà Intent vẫn chưa chuyển sang `CONSUMED` (tức là chưa có Session), hãy tự động chuyển nó sang `EXPIRED`. Đừng để User đợi đến sát giờ hoặc quá giờ mới báo không tìm được bạn.
  - Với public intent, auto-expire chuyển intent sang `EXPIRED`.
  - Với paired private intents, auto-expire phải terminalize toàn bộ private pair/proposal. Không được unlock partner private intent về `OPEN`.

### 3. Vấn đề "Thay đổi ý định" (Edit Intent)

- **Vấn đề:** Khi Intent đang ở trạng thái `MATCHING` (đã có Proposal), User có được sửa thời gian hoặc Hotspot không?
- **Giải pháp:** **Cấm sửa khi đã MATCHING.**

### 4. Hậu quả của CANCELLED (Reputation System)

Vì app của bạn là WalkMate (gặp người lạ), sự tin tưởng là quan trọng nhất.

- **Vấn đề:** Nếu một User thường xuyên hủy (`CANCELLED`) không có lý do chính đáng.
- **Giải pháp:** Cần một bảng **UserReputation** (hoặc Karma điểm).
  - Mỗi khi Session kết thúc với trạng thái `CANCELLED`, hệ thống tự động cập nhật tín hiệu uy tín của User gây lỗi.
  - Khi điểm quá thấp, Matching Engine sẽ không ưu tiên ghép đôi họ nữa (dù Intent vẫn `OPEN`).
  - Private invite decline/cancel có thể cần policy riêng để tránh phạt reputation sai ngữ cảnh. Ví dụ: decline lời mời riêng tư trước khi session được tạo không nhất thiết bị xem là hành vi xấu.

### 5. Race Condition (Tranh chấp dữ liệu)

Đây là lỗi kỹ thuật hay gặp ở các app dạng Grab:

- **Vấn đề:** Hai thao tác khác nhau (ví dụ: Match Engine sinh Proposal và User chủ động Cancel Intent) diễn ra cùng lúc ở mức mili giây trên chung 1 bản ghi.
- **Giải pháp:** Sử dụng **Optimistic Locking (Versioning)** kết hợp với Database Transaction. Bất kỳ bản ghi nào trong `WalkIntent`, `MatchProposal`, và `WalkSession` đều có trường `version` định kỳ tăng dần. Nếu version từ Memory/Request gửi xuống DB khác với version hiện trạng trong DB, transaction sẽ bị từ chối xác nhận (fail-fast), tránh ghi đè dữ liệu sai.