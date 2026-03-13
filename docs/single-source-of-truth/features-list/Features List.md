Tuyệt. Dưới đây là **WALKMATE – FINAL PROPOSAL (Aligned 100% với DDD State Machine)**.
Thiết kế này tuân thủ đúng boundary:

- **Coordination Phase → WalkIntent aggregate**
- **Lifecycle Phase → WalkSession aggregate (5 states: PENDING, ACTIVE, COMPLETED, NO_SHOW, CANCELLED)**
- Session chỉ được tạo sau **mutual confirmation** (Value Realization Boundary)
- Không còn MATCHED/CONFIRMED là trạng thái của WalkSession

---

# WALKMATE – DOMAIN-ALIGNED PROPOSAL (16 GROUP FEATURES)

---

# 1. Xác thực & Bảo mật

- Đăng nhập / Đăng ký:
  - Google
  - Số điện thoại (OTP)
  - Email & mật khẩu

- Khôi phục mật khẩu
- Đăng xuất
- Quản lý phiên đăng nhập
- Mã hóa dữ liệu nhạy cảm

---

# 2. Hồ sơ người dùng & Quyền riêng tư

## 2.1 Public Profile

Hiển thị khi tham gia coordination:

- Tên hiển thị
- Ảnh đại diện
- Giới tính
- Độ tuổi
- Nhãn sở thích (Pet, Âm nhạc, Yên tĩnh…)

## 2.2 Private Profile

Chỉ hiển thị nội bộ:

- Email
- Số điện thoại
- Lịch sử đăng nhập

## 2.3 Public / Private Mode

- Public Mode: Có thể tham gia matching
- Private Mode: Không xuất hiện trong coordination phase

---

# 3. Định vị & Trace Path

## 3.1 Coordination Phase – Snapshot Location

- Khi tạo WalkIntent:
  - Hệ thống lấy snapshot GPS

- Không tracking liên tục trong giai đoạn coordination

## 3.2 Lifecycle Phase – Realtime Tracking

Khi WalkSession ở trạng thái **ACTIVE**:

- Cập nhật GPS định kỳ
- Đồng bộ vị trí 2 người
- Hiển thị marker & polyline

## 3.3 Trace Persistence

Chỉ lưu trace khi:

- WalkSession = COMPLETED

Lưu:

- Tọa độ
- Tổng quãng đường
- Tổng thời gian

---

# 4. Coordination Phase (WalkIntent Aggregate)

WalkIntent là aggregate quản lý giai đoạn trước khi giá trị được hiện thực hóa.

---

## 4.1 Tạo WalkIntent
Người dùng xác định lịch trình:
- Scheduled Time (Dự kiến thời gian bắt đầu và kết thúc; có thể bắt đầu từ thời điểm hiện tại)
- Walk Purpose
- Snapshot Location
- Matching constraints (Khoảng cách, Giới tính, Tuổi)

Lưu ý kiến trúc:
- Không tồn tại cơ chế "Quick Match".
- Nếu người dùng chọn thời điểm hiện tại (startTime = currentTime), đây vẫn là một Scheduled WalkIntent bình thường.
- WalkIntent luôn đi qua lifecycle chuẩn và không được bypass MatchProposal.

WalkIntent tồn tại độc lập với WalkSession và mang trạng thái OPEN.

---

## 4.2 Matching
Hệ thống:
- So khớp theo Scheduled Time window (bao gồm cả trường hợp startTime = currentTime)
- So khớp Location và Purpose
- Áp dụng constraints
- Kiểm tra Block

Matching luôn tạo ra **MatchProposal** ở trạng thái PENDING.

Không có cơ chế auto-create WalkSession.
Không có cơ chế bỏ qua bước xác nhận song phương.

---

## 4.3 Mutual Confirmation (Value Boundary)

Khi cả hai bên xác nhận (acceptedByA == true và acceptedByB == true):
→ MatchProposal chuyển sang CONFIRMED.
→ Domain Service khóa WalkIntents liên quan.
→ Kiểm tra lại Invariants (không trùng lặp lịch, intent vẫn OPEN).
→ Tạo **WalkSession ở trạng thái PENDING**.
→ Các WalkIntents liên quan chuyển sang CONSUMED.

Lưu ý:
Không có trường hợp tạo WalkSession nếu chưa CONFIRMED.
Không tồn tại đường đi trực tiếp từ WalkIntent sang WalkSession.

---

# 5. WalkSession Aggregate (Lifecycle Phase)

WalkSession chỉ quản lý lifecycle của commitment.

## 5.1 Trạng thái hợp lệ (5 states)

- PENDING
- ACTIVE
- COMPLETED (terminal)
- NO_SHOW (terminal)
- CANCELLED (terminal)

Terminal states là immutable.

---

## 5.2 PENDING

- Sau mutual confirmation
- Chờ activation trong grace period
- Có thể:
  - Activate → ACTIVE
  - Cancel → CANCELLED
  - Timeout → NO_SHOW (auto)

---

## 5.3 ACTIVE

- Chỉ xảy ra khi CẢ HAI người đã nhấn “Start Walk” trong activation window.
- GPS tracking bật.
- Chat vẫn mở.

Có thể:
- Complete → COMPLETED (khi đạt minimum duration)
- Auto-complete (khi đạt planned duration hoặc safety limit)

Không tồn tại cơ chế report no-show trong ACTIVE.
ACTIVE không thể chuyển sang NO_SHOW bằng hành động đơn phương.

---

## 5.4 COMPLETED

- Walk thành công
- Unlock Rating
- Trigger Achievement
- Terminal & immutable

---

## 5.5 NO_SHOW

- Xảy ra tự động khi activation window kết thúc mà:
  - Không đủ hai người activate.
- Áp dụng penalty theo policy.
- Terminal & immutable.

Không tồn tại NO_SHOW từ ACTIVE.

---

## 5.6 CANCELLED

- Chỉ từ PENDING
- Tiered penalty theo thời điểm
- Terminal & immutable

---

# 6. Activation & Completion Rules

## Activation Window
- Từ Start_Time -15 phút
- Đến Start_Time +30 phút

Session chỉ được chuyển sang ACTIVE nếu cả hai activate trong khoảng này.

## Minimum Duration
- ≥ 5 phút để được COMPLETE.
- Không được COMPLETE nếu chưa đạt minimum duration.

## Safety Limit
- ACTIVE sẽ tự động COMPLETE khi đạt giới hạn an toàn (ví dụ 4 giờ).

---

# 7. Chat (Bounded by WalkSession)

Chat chỉ tồn tại trong Lifecycle Phase.

| State     | Chat             |
| --------- | ---------------- |
| PENDING   | Mở               |
| ACTIVE    | Mở               |
| COMPLETED | Đóng (read-only) |
| CANCELLED | Đóng             |
| NO_SHOW   | Đóng             |

Chat Room gắn với session_id.

Không tồn tại chat ngoài WalkSession.

---

# 8. Matching Constraints

Người dùng có thể thiết lập:

- Giới tính mong muốn
- Độ tuổi mong muốn

Áp dụng trong Coordination Phase.

Không áp dụng cho Direct Invite. Tuy nhiên, Direct Invite không bypass Invariant:
- Vẫn phải thỏa mãn time overlap.
- Vẫn phải thỏa mãn trạng thái OPEN của WalkIntent.
- Vẫn phải đi qua MatchProposal → CONFIRMED → WalkSession.

Server kiểm tra Block trước khi tạo MatchProposal.

---

# 9. Following & Block

## 9.1 Following

- Thực hiện sau COMPLETED
- Không tạo Session
- Không tạo chat riêng
- Tăng trọng số trong matching
- Cho phép Direct Invite

## 9.2 Block

Nếu Block:

- Không tạo MatchProposal
- Không tạo WalkSession
- Không chat

---

# 10. Scheduled Matching vs Scheduled Direct Invite

## 10.1 Scheduled Matching

- WalkIntent có Time trong tương lai
- Hệ thống ghép random
- Sau confirmation → tạo WalkSession (PENDING)

## 10.2 Scheduled Direct Invite

- Gửi lời mời cụ thể cho Following / Completed partner
- Sau mutual confirmation → tạo WalkSession (PENDING)

Cả hai đều đi qua Coordination Phase trước khi vào Lifecycle Phase.

---

# 11. Notification System

Push được kích hoạt bởi Domain Events phản ánh chính xác Lifecycle:

## Coordination Phase
- MatchProposalCreated (PENDING)
- MatchProposalConfirmed
- MatchProposalRejected
- MatchProposalExpired

## Lifecycle Phase
- WalkSessionCreated (state = PENDING)
- WalkSessionActivated (state = ACTIVE)
- WalkSessionCancelled
- WalkSessionNoShow (do thiếu activation trong activation window)
- WalkSessionCompleted
- New chat message

Không tồn tại event "Quick Match".
Không tồn tại event auto-create session.

Server là nguồn duy nhất phát sự kiện.

---

# 12. Rating System

Chỉ xuất hiện khi:

- WalkSession = COMPLETED

- 1–5 sao

- Nhãn mô tả

Ảnh hưởng:

- TrustScore aggregate
- Matching weight

---

# 13. Session History

Lưu:

- session_id
- Scheduled time
- Actual start/end
- Duration
- Trace path
- Final state

Chỉ COMPLETED được tính thành tích.

---

# 14. Gamification

Badge dựa trên:

- Tổng km
- Số COMPLETED session
- Số 5 sao
- Streak

Chỉ tính COMPLETED.

---

# 15. Reporting & Dispute

Report gắn với session_id.

Dispute chỉ áp dụng cho COMPLETED hoặc NO_SHOW.

Dispute không làm thay đổi state của WalkSession.
Correction được xử lý thông qua compensation event (ví dụ điều chỉnh TrustScore), không mutate state.

---

# 16. Analytics & Automated Monitoring

Theo dõi:

- No-show rate
- Cancellation patterns
- Rating distribution

Có thể:

- Giảm ưu tiên matching
- Hạn chế tạo WalkIntent mới trong cùng time window

Thực hiện tự động qua Domain Events.

---

# Kiến trúc tổng thể

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
