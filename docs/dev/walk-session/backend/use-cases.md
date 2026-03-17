# Phân tích Use Cases: Walk Session (Đồng bộ Giao diện & Domain SSOT)

Tài liệu này áp dụng quy trình 8 bước trong `1-use-cases-detection.workflow.md` để xác định các Use Case cho domain **Walk Session**. Tài liệu đã được điều chỉnh và **đồng bộ hóa nghiêm ngặt** với Single Source of Truth (SSOT) từ phân tích Lifecycle, State Machine, và Invariants của hệ thống.

## Sự khác biệt trọng yếu giữa UI và Domain State
*   **UI có trạng thái PAUSED**, nhưng **Domain Backend (theo SSOT) KHÔNG CÓ state `PAUSED`** cho Aggregate `WalkSession`. Vòng đời SSOT chỉ gồm: `PENDING`, `ACTIVE`, `COMPLETED`, `CANCELLED`, `NO_SHOW`, `ABORTED`.
*   Vì vậy, các hành động như **Pause/Resume trên UI** không làm thay đổi State chính của `WalkSession`. Chúng chỉ là các event ghi nhận thời gian tính toán cho Tracker, Session ở backend vẫn đang ở trạng thái `ACTIVE`.
*   Tên các hành động từ UI cũng đã được đổi chuẩn hóa thành các Use Case Map chính xác với Model Backend (`StartWalkSession` => `ActivateWalkSession`).

---

## 📍 1. Màn hình: Walk Session - Init
_(Trạng thái `PENDING` - chờ hai người cùng xác nhận tham gia bắt đầu ở khung cửa sổ thời gian lịch trình)_

**1️⃣ Bắt đầu từ Actor:**
- **User** (Walker).

**2️⃣ Hỏi: Actor muốn đạt được mục tiêu gì?**
- Xác nhận bắt đầu chuyến đi đã được xếp lịch với Partner trong khung giờ.
- Hủy bỏ không đi chuyến đi này nữa trước khi nó bắt đầu.

**3️⃣ Viết Use Case dưới dạng hành động (đã map Domain):**
- `ActivateWalkSession` (Thay cho `StartWalkSession` trên UI)
- `CancelWalkSession`

**4️⃣ Kiểm tra: Use Case có tạo ra thay đổi trong hệ thống không?**
- `ActivateWalkSession`: Chuyển trạng thái sang `ACTIVE` (chỉ khi cả 2 đều đã activate trong khung giờ cho phép theo `S-3`, `S-4`). Bắt đầu tính giờ tracking.
- `CancelWalkSession`: Thay đổi trạng thái sang `CANCELLED` (`S-6`). Hủy mọi tác vụ chờ.

**5️⃣ Xác định System Boundary:**
- System lắng nghe request từ UI, lock record để validate với các invariant của WalkSession, sau đó đổi State.

**6️⃣ Viết User Story:**
- As a user, I want to activate (start) my walk session So that the system transitions it to ACTIVE and signals I am ready.
- As a user, I want to cancel my walk session prior to starting by providing a valid reason So that I can abort the walk safely.

---

## 🏃‍♂️ 2. Màn hình: Walk Session - Progress & Paused 
_(Trạng thái `ACTIVE` - user đang đi bộ, kể cả rẽ vào nghỉ ngơi cũng tính là đang trong Session)_

**1️⃣ Bắt đầu từ Actor:**
- **User** (Walker).

**2️⃣ Hỏi: Actor muốn đạt được mục tiêu gì?**
- Kết thúc chuyến đi giữa chừng (hủy) vì sự cố an toàn/khẩn cấp lúc đang đi.
- Hoàn thành chuyến đi một cách bình thường khi đã đủ thời gian quy định tối thiểu.

**3️⃣ Viết Use Case dưới dạng hành động:**
- `CompleteWalkSession` (Map với nút "End Walk" trên UI khi đủ điều kiện bình thường)
- `AbortWalkSession` (Map với nút "End Walk" trên UI khi hủy sự cố khẩn cấp)
- _(Lưu ý: `PauseTracking` và `ResumeTracking` không phải Use Case làm thay đổi trạng thái Lifecycle, mà là Domain Event cho module Tracking tính toán Calo/Quãng đường)._

**4️⃣ Kiểm tra sự thay đổi trong hệ thống:**
- `CompleteWalkSession`: Thay đổi từ `ACTIVE` sang `COMPLETED` (`S-7`). Lưu các kết quả tracking tổng hợp (Distance, Calories, Duration).
- `AbortWalkSession`: Thay đổi từ `ACTIVE` sang `ABORTED` (`S-10`). Ghi nhận lý do khẩn cấp/an toàn.

**6️⃣ Viết User Story:**
- As a user, I want to end my walk session normally after the required minimum time So that the system marks it as COMPLETED and rewards me.
- As a user, I want to abort my walk session immediately So that the system marks it as ABORTED due to an emergency.

---

## 🤖 3. Các Use Cases của System (Cron/Background Job)
*(Các Actor ẩn không nằm trên giao diện UI nhưng bắt buộc phải có để tuân thủ SSOT Invariants)*

**1️⃣ Bắt đầu từ Actor:**
- **System** (Cron Job / Schedulers).

**2️⃣ Hỏi: Actor muốn đạt được mục tiêu gì?**
- Đóng cửa sổ Check-in, định đoạt những người đến và cố tình vắng mặt.
- Ép kết thúc các cuộc đi bộ bị treo/quên quá số giờ quy định để dọn rác.

**3️⃣ Viết Use Case dưới dạng hành động:**
- `ProcessSessionActivationWindow`
- `ForceCompleteOverdueSession`

**4️⃣ Kiểm tra sự thay đổi trong hệ thống:**
- `ProcessSessionActivationWindow`: Chuyển `PENDING` -> `NO_SHOW` (`S-5`) nếu 1 người đến, hoặc đổi -> `CANCELLED` (`S-6`) nếu 0 người đến vào lúc hết hạn. (Yêu cầu Lock DB chống race condition cực mạnh).
- `ForceCompleteOverdueSession`: Chuyển `ACTIVE` -> `COMPLETED` (`S-9`) nếu session quá số giờ tối đa quy định (vd >4h) để tránh zombie session.

---

## 🏁 4. Màn hình khác quanh Session: End (Walk Complete)
*(Tương tác với Sub-domains khác như Review/Report sau Session)*

**1️⃣ Bắt đầu từ Actor:**
- **User** (Walker).

**2️⃣ Hỏi: Actor muốn đạt được mục tiêu gì?**
- Đánh giá partner.
- Báo cáo partner (Icon Report trên UI).

**3️⃣ Viết Use Case dưới dạng hành động:**
- `RateWalkSession`
- `ReportWalkPartner`

*(Lưu ý: Hai Use Case này thao tác khi WalkSession đã vào Terminal State như COMPLETED/ABORTED. Mặc dù thao tác trên cùng Context UI nhưng bản chất tạo Record ở module Review).*

---

## 🛡️ 8️⃣ Tổng duyệt (Rule 8)
**Danh sách trích xuất đầy đủ được Map thẳng hàng với SSOT Domain:**

**A. User-Triggered (WalkSession State Mutating):**
1. `ActivateWalkSession` (từ `PENDING` -> `ACTIVE` chờ người còn lại)
2. `CancelWalkSession` (từ `PENDING` -> `CANCELLED`)
3. `CompleteWalkSession` (từ `ACTIVE` -> `COMPLETED` nếu đủ chuẩn thời gian)
4. `AbortWalkSession` (từ `ACTIVE` -> `ABORTED` nếu do khẩn cấp)

**B. System-Triggered (WalkSession State Mutating):**
5. `ProcessSessionActivationWindow` (`PENDING` -> `NO_SHOW` hoặc `CANCELLED` khi đóng cửa sổ thời gian)
6. `ForceCompleteOverdueSession` (`ACTIVE` -> `COMPLETED` dọn rác zombie session)

**C. Related Sub-domain Actions (Review/Report):**
7. `RateWalkSession` 
8. `ReportWalkPartner`

**Validate:**
Bảng phân tích này đã đập tan góc nhìn "hời hợt" thuần UI ban đầu. Toàn bộ hành động đã kết nối tuyệt đối không lệch một li với **Lifecycle và Invariants SSOT**, giúp anh em Backend và Mobile có chung một tiếng nói, không chế ra các Entity/State lạc loài như "PAUSED" ở tầng Database Core!
