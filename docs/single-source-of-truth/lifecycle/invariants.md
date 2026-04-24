# Các Ràng buộc Bất biến của WalkMate (SSOT)

Tài liệu này quy định các quy tắc nghiệp vụ cốt lõi nhằm đảm bảo tính toàn vẹn của dữ liệu trong toàn bộ hệ thống WalkMate. Mọi hành động thay đổi trạng thái phải kiểm tra và thỏa mãn các điều kiện này.

---

## 1. Ràng buộc đối với WalkIntent (Nhu cầu)

- **I-1 Cấm chồng lấn thời gian (Cập nhật):** Vì đã bỏ bảng UserSchedule, logic kiểm tra chồng lấn (Overlap) phải thực hiện truy vấn đồng thời trên bảng `WalkIntent` (status `OPEN`, `MATCHING`) và `WalkSession` (status `PENDING`, `ACTIVE`) của cùng một người dùng.
- **I-2 Điều kiện ghép đôi:** Một `MatchProposal` có thể khởi tạo theo 2 đường hợp lệ: (a) Public matching: cả hai `WalkIntent` đang `OPEN` và tương thích hotspot/khung giờ; (b) Private invite: hệ thống atomically tạo cặp intent cho sender/receiver rồi đưa vào `MATCHING` để gắn với proposal `PENDING`.
- **I-3 Quy trình tiêu thụ (Consumption):** Một `WalkIntent` chỉ có thể chuyển sang trạng thái `CONSUMED` từ trạng thái `MATCHING`. Luồng chuyển đổi trực tiếp từ `OPEN` sang `CONSUMED` bị nghiêm cấm để đảm bảo tính an toàn của giao dịch.
- **I-4 Khóa ghép đôi (Matching Lock):** Khi một Intent chuyển sang trạng thái `MATCHING`, nó phải được đánh dấu để loại khỏi kết quả của Matching Engine.
- **I-5 Lan tỏa hết hạn:** Khi một `WalkIntent` chuyển sang `EXPIRED`, tất cả các `MatchProposal` liên quan đang ở trạng thái `PENDING` phải tự động chuyển sang `EXPIRED`.
- **I-6 Trạng thái cuối bất biến:** Các trạng thái `CONSUMED`, `CANCELLED`, và `EXPIRED` là trạng thái cuối. Không được phép có bất kỳ sự thay đổi trạng thái nào sau khi đã đạt đến các mức này.
- **I-7 Tính riêng tư (Bổ sung):** Nếu `is_private = true`, Intent này tuyệt đối không được xuất hiện trong kết quả tìm kiếm công khai. Nó chỉ có thể được kết nối thông qua `invited_friend_id`. Với private invite, intent phía receiver do hệ thống tạo cũng bị ràng buộc tương tự và không bao giờ được đẩy sang waitlist `OPEN` công khai.

---

## 2. Ràng buộc đối với MatchProposal (Lời mời)

- **P-1 Bối cảnh khởi tạo:** Lời mời có thể được tạo từ 2 ngữ cảnh hợp lệ: (a) Match Engine ghép hai intent `OPEN`; (b) luồng private invite tạo proposal ngay trong transaction tạo intent. Sau khi tạo proposal, cả hai intent phải ở `MATCHING`.
- **P-2 Điều kiện xác nhận (Confirmation):** Lời mời chỉ được chuyển sang `CONFIRMED` khi và chỉ khi cả hai phía đã ở trạng thái accepted (bao gồm cả auto-accept của sender trong private invite flow) VÀ cả hai Intent vẫn đang ở trạng thái `MATCHING`.
- **P-3 Giao dịch nguyên tử (Cập nhật):** Việc chuyển lời mời sang `CONFIRMED` phải là một Atomic Transaction bao gồm:
  1. Kiểm tra trạng thái `MATCHING` của 2 Intent.
  2. Tạo `WalkSession` (Copy/Snapshot dữ liệu từ Intent sang).
  3. Tạo Chat Room ảo trên MongoDB (Sử dụng `session_id` làm khóa).
  4. Chuyển cả 2 Intent sang trạng thái `CONSUMED`.
- **P-4 Giới hạn thời gian chờ (Timeout):** Giữ nguyên tối đa 5 phút. Nếu quá hạn mà chưa đủ 2 bên chấp nhận, lời mời chuyển sang `EXPIRED` và xóa đánh dấu Matching Lock. Hậu quả intent theo ngữ cảnh: public matching thì trả về `OPEN`; private invite thì đóng intent private về `CANCELLED` (không public hóa).
- **P-5 Tính duy nhất của Session:** Một lời mời chỉ được phép tạo ra tối đa một `WalkSession`.

---

## 3. Ràng buộc đối với WalkSession (Chuyến đi)

> **PERSONAL STATE vs GLOBAL STATE** — `user_a_status` / `user_b_status` là trạng thái cá nhân của từng participant. `status` (global) là trạng thái dẫn xuất, được tính tự động từ hai trạng thái cá nhân qua `deriveGlobalStatus()`. **Mọi validation hành động của người dùng (activate, complete...) phải kiểm tra PERSONAL STATE của người dùng đó, không được block dựa trên GLOBAL STATE.** GLOBAL STATE chỉ dùng cho mục đích query lịch sử và hiển thị tổng quan.


- **S-1 Nguồn gốc hợp lệ:** Một chuyến đi chỉ được tạo ra từ một lời mời (`MatchProposal`) đã ở trạng thái `CONFIRMED`.
- **S-2 Kích hoạt độc lập (Cập nhật):** Mỗi người tham gia chuyển sang trạng thái `ACTIVE` ngay khi họ xác nhận đến điểm hẹn (`user_a_status` hoặc `user_b_status` = `ACTIVE`). Trạng thái toàn cục của Session được xác lập là `ACTIVE` ngay khi **ít nhất một** người đã kích hoạt. Không cần chờ đợi cả hai.
- **S-3 Dọn dẹp Session PENDING theo TTL (Cập nhật):** Session `PENDING` không được treo vô hạn. Nếu quá TTL cấu hình, hệ thống tự động chuyển sang `CANCELLED`. Giá trị hiện tại lấy từ cấu hình `walkmate.session.pending-ttl` (mặc định `PT24H`, có thể override theo môi trường).
- **S-4 Chính sách No-show (Cập nhật):** Luồng tự động chuyển `NO_SHOW` theo khung giờ kích hoạt đã tắt trong runtime hiện tại. `NO_SHOW` được giữ như trạng thái lịch sử/khả năng mở rộng, nhưng không còn là transition mặc định của scheduler.
- **S-5 Hoàn thành độc lập (Cập nhật):** Mỗi người tham gia có thể hoàn thành (`user_a_status` / `user_b_status` = `COMPLETED`) độc lập sau khi đi tối thiểu 5 phút tính từ thời điểm họ tự kích hoạt. Trạng thái toàn cục chuyển sang `COMPLETED` chỉ khi **cả hai** người đã đạt trạng thái cuối và ít nhất một người là `COMPLETED`. Điều này đảm bảo người đến đúng giờ vẫn nhận được điểm ngay cả khi đối tác không đến.
- **S-6 Giới hạn an toàn:** Một chuyến đi không được phép ở trạng thái `ACTIVE` quá 4 tiếng. Sau thời gian này, hệ thống sẽ tự động đóng Session để đảm bảo an toàn.
- **S-7 Ràng buộc Chat (Bổ sung):** Người dùng chỉ có quyền gửi tin nhắn vào MongoDB khi `WalkSession` đang ở trạng thái `PENDING` hoặc `ACTIVE`. Khi Session chuyển sang `COMPLETED` hoặc `CANCELLED`, quyền ghi (Write) vào Chat của `session_id` đó phải bị khóa ngay lập tức.
- **S-8 Tính nhất quán của Snapshot (Bổ sung):** Một khi Session đã được tạo, các thông tin `scheduled_start`, `scheduled_end` và `meeting_point` là bất biến (Immutable), không thay đổi theo sự biến động của dữ liệu gốc ở bảng Intent hay Hotspot.

---

## 4. Ràng buộc xuyên suốt (Cross-Aggregate)

- **X-1 Nguồn sự thật (Xóa bỏ/Thay thế):** Xóa bỏ ràng buộc dựa trên bảng `UserSchedule`. Thay thế bằng: Logic Overlap Check tập trung tại Domain Service. Mọi hành động tạo Intent hoặc Session đều phải gọi qua Service này để kiểm tra chéo giữa bảng `WalkIntent` và `WalkSession`.
- **X-2 Bàn giao trách nhiệm lịch trình (Hand-off):** Khi Intent chuyển sang `CONSUMED`, bản ghi lịch trình tương ứng phải được cập nhật ngay lập tức để trỏ tới `WalkSession` mới tạo. Tuyệt đối không được xóa và tạo mới để tránh làm mất "khóa" thời gian (Time Lock).
- **X-3 Danh sách loại trừ (Exclude List):** Nếu User A từ chối lời mời từ User B, hệ thống phải cập nhật danh sách loại trừ của Intent đó để Matching Engine không ghép cặp hai người này lại trong cùng một yêu cầu.
- **X-4 Hệ thống uy tín (2 giai đoạn):**
  1. **Session-outcome update (ngay lập tức):** Khi Session đi vào trạng thái kết thúc đang dùng trong runtime (`COMPLETED`, `CANCELLED`), hệ thống cập nhật tín hiệu uy tín nền tảng để phục vụ matching.
  2. **Review-based adjustment (hậu kiểm):** Khi có review (UC-31), hệ thống áp dụng điều chỉnh theo `rating_stars` và tính lại `trustScore` hiển thị.
- **X-5 Versioning (Bổ sung):** Mọi hành động cập nhật trạng thái trên `WalkIntent`, `MatchProposal`, và `WalkSession` phải kiểm tra trường `version` (Optimistic Locking). Nếu `version` ở DB khác với `version` ở Request, hệ thống phải từ chối thao tác để tránh xung đột dữ liệu khi 2 người dùng thao tác cùng lúc.
