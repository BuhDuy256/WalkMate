Dưới đây là bảng tổng hợp chiến lược triển khai dự án WalkMate, phân bổ AI (Claude Pro vs. Codex/ChatGPT Free) dựa trên độ khó, kèm theo dự đoán các lỗi rủi ro cao nhất (High-Probability Errors) mà bạn cần "soi" kỹ khi AI trả code về.

### 🗺️ Bảng Phân Bổ Task & Cảnh Báo Rủi Ro

| Phase | Mô tả ngắn gọn | Model khuyên dùng | ⚠️ Lỗi có tỉ lệ xảy ra cao (Cần chú ý) |
| :--- | :--- | :--- | :--- |
| **0. Contract Hardening** | Chuẩn hóa toàn bộ DTO, đổi tên Enum, xử lý xung đột tài liệu. | **Codex / ChatGPT Free** | Quên đổi tên biến ở các file liên quan (chỉ đổi ở class gốc mà quên đổi ở file giao diện UI). |
| **1. Auth & Foundation** | Setup Spring Boot, bảo mật JWT, mã hóa mật khẩu BCrypt, Login/Register. | **Claude Pro** (Tuyệt đối) | Dùng các class bị deprecate (lỗi thời) của Spring Security 3.x (như `WebSecurityConfigurerAdapter`); Lỗi parse JWT Token không khớp secret key. |
| **2. Hotspot** | API lấy danh sách các điểm đi bộ, tính số người đang có mặt. | **Codex / ChatGPT Free** | Viết câu SQL `LEFT JOIN` bị sai logic gom nhóm (`GROUP BY`), dẫn đến đếm sai số lượng `activeWalkerCount`. |
| **3. WalkIntent Core** | Tạo, xem danh sách, hủy Ý định đi bộ. Xử lý múi giờ. | **Claude Pro** (Backend) <br> **Codex** (UI/DTO) | Lỗi lệch múi giờ (Timezone) khi cộng `date` và `timeStart`; Truy vấn kiểm tra trùng lặp thời gian bị sai dấu (`<`, `>`). |
| **4. Matching Engine** | Lõi hệ thống: Thuật toán ghép đôi, Transaction nguyên tử khóa DB để tạo Session. | **Claude Pro** (Tuyệt đối) | **Rất dễ xảy ra:** Lỗi Deadlock (khóa chéo) khi 2 user cùng Accept; Bỏ sót annotation `@Transactional`; Lỗi nuốt Exception khiến DB không rollback khi thất bại. |
| **5. Session Lifecycle** | Máy trạng thái của phiên đi bộ (Pending, Active, Abort). Job chạy ngầm quét trạng thái. | **Claude Pro** | Job `@Scheduled` chạy đè lên nhau sinh lỗi; Tính toán sai khoảng thời gian ân hạn (Grace Window). |
| **6. GPS Tracking Sync** | Đồng bộ tọa độ GPS lên server, nén dữ liệu bằng thuật toán Google Polyline. | **Claude Pro** | Lỗi OutOfMemory nếu xử lý mảng tọa độ quá lớn; Nén/Giải nén Polyline bị sai làm mất độ chính xác (mất số thập phân). |
| **7. Profile CRUD** | Cập nhật hồ sơ, tải ảnh Avatar lên server. | **Codex / ChatGPT Free** | Thiếu validation bắt lỗi tuổi \> 13; Lỗi Android xử lý quyền (Permissions) truy cập thư viện ảnh. |
| **8. Social Graph** | Tính năng Theo dõi (Follow) và Chặn (Block) người dùng. | **Codex / ChatGPT Free** | Quên cập nhật lại câu query `findMatch` ở Phase 4 để loại trừ những người đã bị Block. |
| **9. Post-Session Review** | Đánh giá sao, tính toán lại điểm tin cậy (Trust Score). | **Codex** (UI/DTO) <br> **Claude** (Tính điểm) | Báo lỗi NullPointerException nếu người dùng không viết comment (để trống); Cộng dồn sai điểm Trust Score. |
| **10. Chat (Long-polling)** | Nhắn tin trong phiên đi bộ, tự động gọi API mỗi 3 giây. | **Claude Pro** | **Tràn RAM điện thoại (Memory Leak)** do Frontend không hủy `ScheduledExecutorService` khi user thoát màn hình; App bị giật lag vì load lại toàn bộ tin nhắn. |
| **11. Notifications** | Hệ thống thông báo kéo (Pull API). | **Codex / ChatGPT Free** | UI badge (chấm đỏ) đếm sai số lượng thông báo chưa đọc. |
| **12. Gamification** | Tính điểm kinh nghiệm, cấp phát huy hiệu. | **Codex / ChatGPT Free** | Điều kiện logic `if/else` cấp huy hiệu bị lồng ghép sai, dẫn đến cấp trùng huy hiệu nhiều lần. |

-----

### 🔄 Workflow Chuẩn Chẩn Để Làm Việc Từng File / Từng Model

Để sống sót qua dự án này mà không bị kiệt sức vì fix bug, đây là quy trình 4 bước cho mỗi Phase:

**Bước 1: Thiết kế giao việc (Phân loại Model)**

  * Đọc file Plan của Phase hiện tại. Chia làm 2 nhóm: "Chân tay" (DTO, XML, Mapper, Controller cơ bản) và "Não bộ" (Thuật toán, Transaction, Logic Service).
  * Mở ChatGPT Free/Codex để xử lý nhóm chân tay.

**Bước 2: Xử lý nhóm "Chân tay" (Codex/Free AI)**

  * **Prompt:** *"Tôi đang làm dự án WalkMate (Spring Boot + Android MVVM). Cho tôi file DTO `WalkIntentResponse` bằng Java có các trường sau... và 1 file Android XML layout cho màn hình hiển thị danh sách này."*
  * Copy code vào project. Nếu lỗi import hay thiếu dấu ngoặc, bạn tự sửa tay cho nhanh, đừng hỏi lại AI tốn thời gian.

**Bước 3: Xử lý nhóm "Não bộ" (Claude Pro)**

  * Sử dụng prompt *Context Switching* như đã hướng dẫn ở trên.
  * Đưa cho Claude file Spec, file Handoff của Phase trước, và các DTO bạn vừa tự tạo bằng Codex.
  * **Prompt:** *"Chúng ta bắt đầu Phase 4. Tôi đã chuẩn bị sẵn các DTO. Nhiệm vụ của bạn bây giờ CHỈ LÀ viết file `MatchDomainService.java` xử lý Transaction P-3. Đảm bảo sử dụng khóa `SELECT FOR UPDATE`. Trả về đúng 1 file này."*
  * Copy code của Claude vào máy.

**Bước 4: Review, Run & Fix (Bản thân bạn)**

  * **Kiểm tra mắt:** Đọc lướt xem Claude có bắt các Exception đúng như mô tả rủi ro ở bảng trên không (VD: Có `@Transactional` chưa?).
  * **Build & Run:** Chạy server, dùng Postman/Swagger test luồng API. Chạy Android app.
  * **Fix Bug:** Nếu có lỗi đỏ lòm trong Logcat/Console, copy đúng dòng lỗi đó + dòng code gây lỗi ném lại cho Claude: *"Lỗi Exception X ở dòng Y. Hình như chưa handle trường hợp user bị null. Sửa lại hàm này."*

**Bước 5: Lưu Game (Handoff)**

  * Chạy thành công 100% -\> Yêu cầu Claude xuất file `HANDOFF.md` và `DECISION_LOG.md` -\> Đóng chat, dọn dẹp RAM não bộ, chuẩn bị cho Phase tiếp theo.