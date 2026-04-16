# Core Domain Lifecycles & Scheduling System

## 1. WalkIntent

The **WalkIntent** domain declares a user's availability and intent to participate in a walk at a specific time and hotspot.

### Lifecycle Stages

- **OPEN:** The intent is newly created and actively looking for a match. Nó tham gia vào việc duy trì chặn trùng lịch trên toàn hệ thống thời gian.
- **MATCHING:** A `MatchProposal` has been generated. The intent is "soft-locked" (đánh dấu matching lock) để không bị Match Engine tìm thấy nữa, nhưng vẫn tham gia chặn trùng lặp thời gian.
- **CONSUMED:** A `WalkSession` has been successfully created. This is a terminal state. Trách nhiệm duy trì chặn thời gian được chuyển giao hoàn toàn sang `WalkSession`.
- **CANCELLED:** The user manually withdrew the intent. Terminal state.
- **EXPIRED:** The valid time window passed without a confirmed match. Terminal state.

### Transitions

| From         | To            | Trigger                                                                      |
| :----------- | :------------ | :--------------------------------------------------------------------------- |
| **OPEN**     | **MATCHING**  | System creates a `MatchProposal` from public matching (inline on create or async/internal retry matching). |
| [None]       | **MATCHING**  | Private invite flow atomically creates paired intents and proposal `PENDING`. |
| **MATCHING** | **OPEN**      | Public `MatchProposal` is REJECTED or EXPIRED (user continues searching). |
| **MATCHING** | **CANCELLED** | Private-invite `MatchProposal` is REJECTED or EXPIRED (close private intents, do not publicize). |
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
| [None]      | **PENDING**   | Public matching finds compatible intents, or private invite flow creates proposal. |
| **PENDING** | **CONFIRMED** | Both participants flag as accepted.               |
| **PENDING** | **REJECTED**  | At least one participant flags as rejected.       |
| **PENDING** | **EXPIRED**   | Proposal validity duration elapses.               |

---

## 3. WalkSession

The **WalkSession** domain governs the real-world execution and path tracing (Strava-like).

### Lifecycle Stages

- **PENDING:** Session created, waiting for participants to arrive at the Hotspot and activate.
- **ACTIVE:** Both participants have activated the session (Path tracing in progress).
- **COMPLETED:** The walk reached its destination or time limit.
- **CANCELLED:** Manual cancellation prior to start hoặc auto-cancel khi `PENDING` quá TTL cấu hình.
- **ABORTED:** Session terminated due to reported issues or emergency.

### Transitions

| From        | To            | Trigger                                    |
| :---------- | :------------ | :----------------------------------------- |
| [None]      | **PENDING**   | `MatchProposal` transitions to CONFIRMED.  |
| **PENDING** | **ACTIVE**    | Mutual activation (both participants press Arrive). |
| **PENDING** | **CANCELLED** | Manual cancel or session exceeds `pending-ttl` policy. |
| **ACTIVE**  | **COMPLETED** | Goal reached or time elapsed.              |
| **ACTIVE**  | **ABORTED**   | Manual report or safety incident.          |

---

## 4. Time Overlap Invariant (Domain Service Approach)

Để đảm bảo quy tắc **Không chọn trùng khung giờ** (Một người không thể ở hai nơi trong cùng một khoảng thời gian), hệ thống loại bỏ bảng `UserSchedule` tập trung và thay thế bằng Domain Service chuyên biệt đóng vai trò Nguồn Sự Thật.

### Logic

- **Nguồn dữ liệu:** Domain Service sẽ truy vấn đồng thời trên 2 bảng để kiểm tra:
  - Bảng `WalkIntent` (với các trạng thái `OPEN`, `MATCHING`).
  - Bảng `WalkSession` (với các trạng thái `PENDING`, `ACTIVE`).
- **Validation:** Trước khi cho phép tạo mới hoặc dời lịch trình, hệ thống gọi Service để xác nhận không có bất kỳ dòng nào thỏa mãn: `MAX(Existing_Start, New_Start) < MIN(Existing_End, New_End)` thuộc về user đó.
- **Hand-off (Bàn giao):** Khi Intent chuyển từ `MATCHING` sang `CONSUMED`, Intent này sẽ "nhả" chặn thời gian, đồng thời `WalkSession` mới ngay lập tức "kế thừa" khoảng thời gian này thông qua Atomic Transaction, đảm bảo việc khóa thời gian liền mạch hoàn toàn mà không bị hở.

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

### 4. Hậu quả của CANCELLED/ABORTED (Reputation System)

Vì app của bạn là WalkMate (gặp người lạ), sự tin tưởng là quan trọng nhất.

- **Vấn đề:** Nếu một User thường xuyên hủy (`CANCELLED`) hoặc giữa chừng `ABORTED` mà không có lý do chính đáng.
- **Giải pháp:** \* Cần một bảng **UserReputation** (hoặc Karma điểm).
  - Mỗi khi Session kết thúc với trạng thái `CANCELLED` hoặc `ABORTED`, hệ thống tự động cập nhật tín hiệu uy tín của User gây lỗi.
  - Khi điểm quá thấp, Matching Engine sẽ không ưu tiên ghép đôi họ nữa (dù Intent vẫn `OPEN`).

### 5. Race Condition (Tranh chấp dữ liệu)

Đây là lỗi kỹ thuật hay gặp ở các app dạng Grab:

- **Vấn đề:** Hai thao tác khác nhau (ví dụ: Match Engine sinh Proposal và User chủ động Cancel Intent) diễn ra cùng lúc ở mức mili giây trên chung 1 bản ghi.
- **Giải pháp:** Sử dụng **Optimistic Locking (Versioning)** kết hợp với Database Transaction. Bất kỳ bản ghi nào trong `WalkIntent`, `MatchProposal`, và `WalkSession` đều có trường `version` định kỳ tăng dần. Nếu version từ Memory/Request gửi xuống DB khác với version hiện trạng trong DB, transaction sẽ bị từ chối xác nhận (fail-fast), tránh ghi đè dữ liệu sai.
