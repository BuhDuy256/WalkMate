Việc thiết kế một hệ thống **Trust Score (Điểm Tín Nhiệm)** từ con số không là một thách thức đáng kể, đặc biệt khi bạn đang hướng tới mô hình AI Matching. Dữ liệu đầu vào thô sơ (chỉ Email/Password) sẽ tạo ra vấn đề "Cold Start" (Khởi động lạnh) cho AI, và nếu thiếu một vòng đời điểm số rõ ràng, hệ thống sẽ rơi vào trạng thái hỗn loạn.

Dưới đây là một bản đặc tả kiến trúc toàn diện (Architectural Specification) để giải quyết cả hai vấn đề này, sử dụng nguyên tắc *Progressive Disclosure* (Hé lộ dần dần) cho UI và hệ thống điểm theo hướng sự kiện (Event-driven scoring).

---

### 1. Giải quyết vấn đề "Cold Start" (Thiếu dữ liệu Profile)

Bạn là người ưa chuộng sự tối giản và ghét các luồng thao tác rườm rà. Việc ép người dùng điền một form dài 10 trang ngay khi đăng ký là một rào cản lớn (high friction). Thay vào đó, hãy áp dụng chiến lược **Just-in-Time Onboarding (Cung cấp thông tin đúng lúc)**:

* **Tách biệt Account và Profile:** Khi Sign Up, họ chỉ tạo `user_account`. Bảng `user_profile` có thể chứa giá trị `NULL` cho `gender` và `bio`.
* **The "First Intent" Interceptor:** Lần đầu tiên người dùng nhấn nút "Create Walk Intent", hệ thống sẽ chặn luồng (intercept) và hiển thị một Popup tối giản: *"Để AI tìm được người đồng hành phù hợp nhất, hãy cho chúng tôi biết một chút về bạn"*. Lúc này, bạn mới yêu cầu nhập `Gender` và chọn 3 `profile_tag`.
* **Default Broad Matching:** Nếu họ bằng cách nào đó vẫn lách qua được, Engine mặc định sẽ gán `weight_interest = 0` (Bỏ qua sở thích) và chỉ match thuần túy dựa trên *Thời gian* và *Địa điểm* (giai đoạn 1 của hệ thống hiện tại).

---

### 2. Đặc tả Vòng đời Hệ thống Trust Score (SSOT)

Dựa trên tài liệu `invariants.md` (Điều khoản X-4), vòng đời của Trust Score phải được chia làm 2 giai đoạn riêng biệt để đảm bảo sự tách biệt về trách nhiệm (Low Coupling).

**Đề xuất điều chỉnh Database:** Trong file `current-db.sql`, `trust_score` mặc định là `100` (Max `1000`). Mức 100 là quá thấp để bắt đầu (như bị điểm F). Hãy điều chỉnh `DEFAULT 500` làm điểm trung bình (Neutral baseline).

#### Giai đoạn 1: System-Driven Adjustment (Ngay lập tức)
Hành vi này được trigger tự động bởi các chuyển đổi trạng thái của `WalkSession` (Objective metrics).

* **Session COMPLETED (+ Điểm tích cực):** Khi một user chuyển trạng thái sang `COMPLETED`, cộng ngay một lượng điểm nhỏ (ví dụ: +5 điểm). Điều này khuyến khích sự ổn định.
* **Session CANCELLED (Trừ điểm theo Penalty Curve):** * Hủy trước 24h: Trừ rất nhẹ hoặc không trừ (-0 điểm).
    * Hủy trước 2h: Trừ trung bình (-15 điểm).
    * Hủy sau khi đã `ACTIVE` (Hủy ngang giữa chừng): Trừ nặng (-30 điểm).
* **NO_SHOW (Hình phạt tối đa):** Do luồng auto-noshow đang tắt (S-4), trạng thái này được cập nhật thủ công qua `session_report`. Nếu một người bị report No-show và report được duyệt (Status = RESOLVED), trừ cực nặng (-100 điểm).

#### Giai đoạn 2: Review-Driven Adjustment (Hậu kiểm)
Hành vi này được trigger khi có bản ghi mới trong bảng `walk_review` (Subjective metrics). Đây là nguồn cấp dữ liệu cực tốt cho mô hình AI sau này.

Sử dụng trọng số phi tuyến tính cho rating:
$$Score\Delta = (Rating - 3) \times K$$
*(Trong đó K là hằng số khuếch đại. Ví dụ K = 10: 5 sao được +20đ, 4 sao được +10đ, 3 sao +0đ, 2 sao -10đ, 1 sao -20đ).*

---

### 3. Tích hợp Trust Score vào AI Matching Engine

Khi bạn chuyển sang AI Matching, Trust Score không chỉ là một con số để trưng bày. Nó sẽ can thiệp vào tầng `MatchingCommandService`:

1.  **Phân tầng (Tiering):** Chia User thành các Tier:
    * *Elite (800-1000):* Ưu tiên match với Elite khác. Nếu có xung đột request, Elite được xử lý trước.
    * *Standard (300-799):* Luồng xử lý bình thường.
    * *Restricted (Dưới 300):* Thuật toán AI sẽ áp dụng *Soft Block*. Bị giới hạn số lượng Intent mở cùng lúc và không bao giờ được match với nhóm Elite.
2.  **Định tuyến tham số:** Điểm Trust Score được đưa vào Vector không gian của bảng `user_embedding` như một feature cốt lõi để tính khoảng cách (Cosine Similarity) giữa hai ứng viên.

Để giúp bạn hình dung rõ hơn về cách các thông số này tác động đến quỹ đạo (trajectory) của một tài khoản, tôi đã chuẩn bị một công cụ mô phỏng động dưới đây. Bạn có thể tự do điều chỉnh các biến số để kiểm tra ngưỡng an toàn của hệ thống.
```

Hãy đảm bảo việc cập nhật điểm này được bọc trong một Database Transaction cùng với trường `version` (X-5) để ngăn chặn hoàn toàn lỗi Race Condition khi nhiều request (ví dụ: vừa hoàn thành session, vừa nhận được review) xảy ra đồng thời.