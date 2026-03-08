# 📘 WalkMate – Feature Groups (16 Groups Required)

---

# 1️⃣ Authentication & Identity Management

Bao gồm:

- Đăng ký bằng Email
- Đăng ký bằng Số điện thoại
- Đăng nhập Email/Password
- Đăng nhập Google
- OTP xác thực
- Khôi phục mật khẩu
- Đăng xuất
- Khóa tài khoản

---

# 2️⃣ Account Security & Privacy Controls

Bao gồm:

- Đổi mật khẩu
- Xác minh email
- Cài đặt quyền riêng tư
- Ẩn hồ sơ khỏi matching
- Xóa tài khoản
- Lịch sử đăng nhập

---

# 3️⃣ User Profile Management

Bao gồm:

- Cập nhật thông tin cá nhân
- Ảnh đại diện
- Bio
- Giới tính
- Ngày sinh

---

# 4️⃣ Interest & Preference Configuration

Bao gồm:

- Chọn nhãn sở thích (Tag)
- Thiết lập bán kính tìm kiếm
- Thiết lập mục đích đi bộ
- Bộ lọc giới tính
- Bộ lọc độ tuổi

---

# 5️⃣ Location & Map Integration

Bao gồm:

- Chọn địa điểm đi bộ
- Lưu tọa độ GPS
- Hiển thị bản đồ
- Kiểm tra khoảng cách giữa 2 Intent
- Xác minh điều kiện địa lý hợp lệ

---

# 6️⃣ Walk Intent Creation & Management

Bao gồm:

- Tạo WalkIntent
- Chỉnh sửa Intent
- Hủy Intent
- Tự động expire Intent
- Kiểm tra time window hợp lệ
- Không cho phép 2 Intent trùng time window

---

# 7️⃣ Scheduled Discovery Matching Engine

Bao gồm:

- Phát hiện overlap giữa 2 Intent
- Lọc theo filter
- Kiểm tra block list
- Tạo MatchProposal
- Expire proposal tự động

---

# 8️⃣ Match Proposal Negotiation

Bao gồm:

- Accept proposal
- Reject proposal
- Timeout proposal
- Xử lý trạng thái ACCEPTED_BY_A / ACCEPTED_BY_B
- Trigger WalkSession khi CONFIRMED

---

# 9️⃣ Walk Session Lifecycle Management

Bao gồm:

- Tạo WalkSession (PENDING)
- Activate
- Complete
- Cancel
- No-show
- Enforce invariant:
  - Không cho phép 2 session trùng time window với status ∈ (PENDING, ACTIVE)

---

# 🔟 Chat & Communication

Bao gồm:

- Chat trước giờ hẹn
- Chat khi ACTIVE
- Chat tự đóng
- Lưu lịch sử chat
- Gợi ý mở lời AI (không tự gửi)

---

# 1️⃣1️⃣ Review & Rating System

Bao gồm:

- Đánh giá 1–5 sao
- Gắn tag tích cực
- 1 review / user / session
- Chỉ áp dụng cho COMPLETED
- Không áp dụng cho NO_SHOW

---

# 1️⃣2️⃣ Trust Score & Reliability System

Bao gồm:

- Cộng điểm khi hoàn thành
- Trừ điểm khi hủy
- Trừ điểm khi no-show
- Không cho chỉnh sửa thủ công
- Phân tầng uy tín

---

# 1️⃣3️⃣ Badge & Achievement System

Bao gồm:

- Badge số km
- Badge số phiên hoàn thành
- Badge số 5 sao
- Không tính session NO_SHOW
- Không revoke trừ admin

---

# 1️⃣4️⃣ Social Graph & Relationship Management

Bao gồm:

- Danh sách bạn bè
- Favorite user
- Block user
- Ưu tiên match với bạn bè
- Lịch sử đi cùng nhau

---

# 1️⃣5️⃣ Reporting & Moderation System

Bao gồm:

- Báo cáo người dùng
- Đính kèm bằng chứng
- Admin xử lý
- Khóa tài khoản
- Theo dõi vi phạm no-show

---

# 1️⃣6️⃣ AI Personalization & Matching Intelligence

Bao gồm:

### AI Matching (Scheduled Discovery)

- User Embedding
- Compatibility Scoring (Intent–Intent)
- Behavior similarity
- TrustScore weighting
- Time overlap scoring

### AI Smart Suggestions

- Gợi ý mở lời
- Gợi ý “smart label” (cùng tốc độ, cùng thói quen)
- Phân tích pattern lịch sử
- Async retraining sau mỗi session

---

# ✅ Kiểm tra lại yêu cầu giảng viên

✔ 16 Feature Groups rõ ràng
✔ Mỗi group có ≥ 4 tính năng con
✔ Không phải app hệ thống mặc định
✔ Có AI feature trong luồng chính
✔ Có domain complexity
✔ Có lifecycle logic
✔ Có matching + negotiation + execution

---

# 🎯 Kết luận

Giờ dự án của bạn:

- Đủ 16 nhóm tính năng
- Có AI rõ ràng
- Có DDD structure
- Có invariant phức tạp
- Có logic concurrency
- Có hệ thống reputation
- Có moderation
