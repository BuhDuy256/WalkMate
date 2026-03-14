# Phân tích Use Cases: Walk Intent (Đồng bộ Giao diện & Domain SSOT)

Tài liệu này áp dụng quy trình 8 bước trong `1-use-cases-detection.workflow.md` để xác định các Use Case cho domain **Walk Intent** (Yêu cầu tìm bạn đi bộ). Tài liệu đã được điều chỉnh và **đồng bộ hóa nghiêm ngặt** với Single Source of Truth (SSOT) từ danh sách tính năng `Features List.md`, UI "Create Walking Intent" và tài liệu **Domain Lifecycle & Invariants**.

## Sự cam kết kiến trúc từ SSOT đối với Walk Intent
*   **Không có "Quick Match"**: Mọi yêu cầu đi bộ dù được đặt cho "thời điểm hiện tại" (`startTime = currentTime`) vẫn được đối xử như một **Scheduled WalkIntent** bình thường, đi qua vòng đời tiêu chuẩn.
*   **Độc lập với Session**: `WalkIntent` tồn tại riêng biệt với `WalkSession` và mang trạng thái `OPEN` khi vừa khởi tạo. Việc ghép cặp sẽ do `MatchProposal` đảm nhiệm.
*   **Lifecycle chuẩn**: Trạng thái cơ bản của một Intent sẽ gồm: `DRAFT`, `OPEN` (Đang chờ ghép cặp), `CONSUMED` (Đã ghép cặp thành công), `CANCELLED` (Người dùng tự hủy), và `EXPIRED` (Hết hạn thời gian dự kiến mà chưa có ai ghép thành công).

---

## 📍 1. Màn hình: Home - Create Walking Intent
_(Từ trạng thái sửa `DRAFT` chuyển sang trạng thái chính thức `OPEN` - User thiết lập thời gian, địa điểm, mục đích, sở thích để tìm bạn)_

**1️⃣ Bắt đầu từ Actor:**
- **User** (Walker).

**2️⃣ Hỏi: Actor muốn đạt được mục tiêu gì?**
- Tạo và xác nhận gửi một yêu cầu tìm kiếm bạn đi bộ với các tiêu chí/mong muốn cá nhân (khoảng cách, giờ giấc, loại hình đi bộ).
- Gỡ bỏ yêu cầu tìm kiếm nếu đổi ý định, không muốn đi bộ và tìm bạn nữa.

**3️⃣ Viết Use Case dưới dạng hành động (đã map Domain Lifecycle):**
- `SubmitWalkIntent` (Map với nút "Find a Buddy" trên UI - đổi tên thay vì Create chung chung)
- `CancelWalkIntent`

**4️⃣ Kiểm tra: Use Case có tạo ra thay đổi trong hệ thống không?**
- `SubmitWalkIntent`: Validate thời điểm lập lịch ở tương lai hợp lệ và chuyển entity `WalkIntent` từ bản nháp sang trạng thái `OPEN` (với ràng buộc DB khoá trùng lập lịch I-1). Không trigger tạo WalkSession hay bypass quá trình match. Lưu Record xuống DB. Event hệ thống match engine sẽ tự rà tìm người phù hợp.
- `CancelWalkIntent`: Chuyển trạng thái của entity `WalkIntent` từ `OPEN` sang `CANCELLED`. Phát ra sự kiện để huy động hệ thống nếu đang vướng luồng Match. Bỏ Intent khỏi hàng đợi tìm kiếm của Match Engine.

**5️⃣ Xác định System Boundary:**
- System lắng nghe request "Submit" và "Cancel" từ UI, validate các Invariants bảo vệ Domain (như I-1 Không trùng lặp thời gian mở, I-6 thời gian gửi là tương lai logic). Xử lý các thay đổi state.

**6️⃣ Viết User Story:**
- As a user, I want to submit a structured walk intent with my preferred time, radius, and walk type So that the system validates invariants and transitions it to OPEN to begin matching me.
- As a user, I want to safely cancel my open walk intent So that its terminal state changes to CANCELLED and I am correctly removed from the matching pool.

---

## 🤖 2. Các Use Cases của System (Cron/Background Job / Match Engine Saga)
*(Các Actor ẩn không nằm trên giao diện UI nhưng điều phối Lifecycle của Intent tự động)*

**1️⃣ Bắt đầu từ Actor:**
- **System** (Cron Job / Schedulers / Orchestrator Saga).

**2️⃣ Hỏi: Actor muốn đạt được mục tiêu gì?**
- Tiêu thụ (Consume) Intent một lần duy nhất khi quá trình ghép cặp Match Proposal đạt thoả thuận thành công, khoá cứng lại để bảo vệ nguyên tắc ghép đơn lẻ.
- Ngưng tìm người ghép cặp cho các yêu cầu đã bị lố thời gian (Schedule Time) quá lâu mà không có ai đáp ứng, nhằm dọn rác hệ thống (zombie intent).

**3️⃣ Viết Use Case dưới dạng hành động:**
- `ConsumeWalkIntent` 
- `ExpireWalkIntent`

**4️⃣ Kiểm tra sự thay đổi trong hệ thống:**
- `ConsumeWalkIntent`: Được kích hoạt tự động (Saga) khi một MatchProposal xác nhận. Hệ thống kiểm tra và chuyển trạng thái `WalkIntent` từ `OPEN` sang `CONSUMED`. Đã tiêu thụ độc quyền (I-3), dỡ bỏ lock check trùng lịch I-1. Giao quyền lại cho WalkSession hoàn toàn.
- `ExpireWalkIntent`: Hệ thống tự động chuyển trạng thái `WalkIntent` từ `OPEN` sang `EXPIRED`. Đặc thù yêu cầu cơ chế DB row lock mạnh mẽ (`SELECT ... FOR UPDATE SKIP LOCKED` - I-7) nhằm chống lại race condition nếu lúc đó proposal đang Consume.

**6️⃣ Viết User Story:**
- As the system orchestrator, I want to safely consume matched walk intents So that they transition to CONSUMED and are perfectly isolated to a single walk session.
- As the system scheduler, I want to safely expire and close open walk intents that have passed their window So that no conflicts emerge with active proposals and the pool is kept clean.

---

## 🛡️ 8️⃣ Tổng duyệt (Rule 8)

**Danh sách trích xuất đầy đủ được Map thẳng hàng với SSOT Domain - Walk Intent:**

**A. User-Triggered (WalkIntent State Mutating):**
1. `SubmitWalkIntent` (Từ `DRAFT` -> `OPEN`, bảo vệ I-1 GiST và I-6)
2. `CancelWalkIntent` (Từ `OPEN` -> `CANCELLED` terminal state khi user ngưng tìm)

**B. System-Triggered (WalkIntent State Mutating):**
3. `ConsumeWalkIntent` (Từ `OPEN` -> `CONSUMED` khi Proposal xác nhận, cơ chế độc quyền I-3)
4. `ExpireWalkIntent` (Từ `OPEN` -> `EXPIRED` dọn rác zombie intent lố giờ, bảo vệ lock trễ I-7)

**Validate:**
Bảng Use Case cho Walk Intent hiện tại đã được đồng nhất hoá 1-1 với file State Transition `domain-lifecycle-stresstest.md`. Dựa trên nguyên tắc không bypass MatchProposal và không có tính năng "Quick Match" khác lạ, toàn bộ hành động tạo lập và huỷ trên giao diện (UI) bản chất chỉ thao tác chuyển đổi state trên một bản ghi yêu cầu rỗng đằng sau. Rule phân rã này thể hiện chuẩn mực "Tìm bạn đi bộ" giữ đúng vai trò là một **yêu cầu đầu vào độc lập** và ngăn ngừa hoàn toàn các khe hở concurrency.
