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

Người dùng xác định:

- Time (Now hoặc Scheduled)
- Walk Purpose
- Snapshot Location
- Matching constraints

WalkIntent tồn tại độc lập với WalkSession.

---

## 4.2 Matching

Hệ thống:

- So khớp theo Time
- So khớp Purpose
- Áp dụng constraints
- Kiểm tra Block
- Ghép ngẫu nhiên

Matching chỉ tạo **MatchSuggestion**, chưa tạo Session.

---

## 4.3 Mutual Confirmation (Value Boundary)

Khi:

- Hai bên đồng ý thời gian và địa điểm

→ Domain Service tạo **WalkSession ở trạng thái PENDING**

Đây là **VALUE REALIZATION BOUNDARY**.

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

- Ít nhất 1 người nhấn “Start Walk”
- GPS tracking bật
- Có thể:
  - Complete → COMPLETED
  - Report no-show → NO_SHOW
  - Auto-complete (planned duration / safety limit)

---

## 5.4 COMPLETED

- Walk thành công
- Unlock Rating
- Trigger Achievement
- Terminal & immutable

---

## 5.5 NO_SHOW

- Auto sau grace period
- Hoặc user report trong 15 phút đầu
- Áp dụng penalty
- Terminal & immutable

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

## Minimum Duration

- ≥ 5 phút để được COMPLETE

## Safety Limit

- Auto complete sau 4 giờ

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

Không áp dụng cho Direct Invite.

Server kiểm tra Block trước khi tạo MatchSuggestion.

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

- Không tạo MatchSuggestion
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

Push được kích hoạt bởi Domain Events:

## Coordination Phase

- MatchSuggestion created
- Mutual confirmation

## Lifecycle Phase

- WalkSessionCreated
- Activated
- Cancelled
- No-show
- Completed
- New message

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

Report gắn với:

- session_id

NO_SHOW mở dispute window 24h.

Terminal state không bị mutate.
Correction thông qua compensation event.

---

# 16. Analytics & Automated Monitoring

Theo dõi:

- No-show rate
- Cancellation patterns
- Rating distribution

Có thể:

- Giảm ưu tiên matching
- Hạn chế Quick Match

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
