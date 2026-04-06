# Các Ràng buộc Bất biến của WalkMate (SSOT)

Tài liệu này quy định các quy tắc nghiệp vụ cốt lõi nhằm đảm bảo tính toàn vẹn của dữ liệu trong toàn bộ hệ thống WalkMate. Mọi hành động thay đổi trạng thái phải kiểm tra và thỏa mãn các điều kiện này.

---

## 1. Ràng buộc đối với WalkIntent (Nhu cầu)

- **I-1 Cấm chồng lấn thời gian:** Tại một thời điểm, một người dùng không được phép có nhiều hơn một bản ghi `ACTIVE` trong bảng `UserSchedule`. Điều này có nghĩa là một Intent mới không được trùng khung giờ với bất kỳ Intent (`OPEN`, `MATCHING`) hoặc Session (`PENDING`, `ACTIVE`) nào khác của cùng người dùng đó.
- **I-2 Điều kiện ghép đôi:** Một `MatchProposal` chỉ được phép khởi tạo khi cả hai `WalkIntent` tham gia đều đang ở trạng thái `OPEN`, có vị trí (Hotspot) phù hợp và khung giờ giao nhau hợp lệ.
- **I-3 Quy trình tiêu thụ (Consumption):** Một `WalkIntent` chỉ có thể chuyển sang trạng thái `CONSUMED` từ trạng thái `MATCHING`. Luồng chuyển đổi trực tiếp từ `OPEN` sang `CONSUMED` bị nghiêm cấm để đảm bảo tính an toàn của giao dịch.
- **I-4 Khóa ghép đôi (Matching Lock):** Khi một Intent chuyển sang trạng thái `MATCHING`, nó phải bị ẩn khỏi bộ máy tìm kiếm (Matching Engine) để ngăn chặn việc một người nhận nhiều lời mời cùng lúc cho cùng một khung giờ.
- **I-5 Lan tỏa hết hạn:** Khi một `WalkIntent` chuyển sang `EXPIRED`, tất cả các `MatchProposal` liên quan đang ở trạng thái `PENDING` phải tự động chuyển sang `EXPIRED`.
- **I-6 Trạng thái cuối bất biến:** Các trạng thái `CONSUMED`, `CANCELLED`, và `EXPIRED` là trạng thái cuối. Không được phép có bất kỳ sự thay đổi trạng thái nào sau khi đã đạt đến các mức này.

---

## 2. Ràng buộc đối với MatchProposal (Lời mời)

- **P-1 Bối cảnh khởi tạo:** Tại thời điểm tạo lời mời, cả hai Intent liên quan bắt buộc phải ở trạng thái `OPEN`. Ngay sau khi tạo, hệ thống phải chuyển cả hai Intent sang `MATCHING`.
- **P-2 Điều kiện xác nhận (Confirmation):** Lời mời chỉ được chuyển sang `CONFIRMED` khi và chỉ khi cả hai người dùng đã nhấn Chấp nhận (Accept) VÀ cả hai Intent vẫn đang ở trạng thái `MATCHING`.
- **P-3 Giao dịch nguyên tử (Atomic Transaction):** Việc chuyển lời mời sang `CONFIRMED` phải nằm trong một giao dịch nguyên tử bao gồm:
  1. Kiểm tra trạng thái `MATCHING` của 2 Intent.
  2. Cập nhật bảng `UserSchedule` (Chuyển từ Intent sang Session).
  3. Tạo `WalkSession`.
  4. Chuyển Intent sang `CONSUMED`.
- **P-4 Giới hạn thời gian chờ (Timeout):** Mỗi lời mời chỉ có hiệu lực trong tối đa 5 phút (hoặc cấu hình hệ thống). Nếu quá thời gian mà chưa đủ 2 bên chấp nhận, lời mời phải chuyển sang `EXPIRED` và trả Intent về `OPEN`.
- **P-5 Tính duy nhất của Session:** Một lời mời chỉ được phép tạo ra tối đa một `WalkSession`.

---

## 3. Ràng buộc đối với WalkSession (Chuyến đi)

- **S-1 Nguồn gốc hợp lệ:** Một chuyến đi chỉ được tạo ra từ một lời mời (`MatchProposal`) đã ở trạng thái `CONFIRMED`.
- **S-2 Kích hoạt song phương:** Chuyến đi chỉ chuyển từ `PENDING` sang `ACTIVE` khi cả hai người dùng kích hoạt (Activate) thành công tại vị trí Hotspot trong khung giờ cho phép.
- **S-3 Khung giờ kích hoạt:** Việc kích hoạt chỉ có hiệu lực trong khoảng: `[Giờ bắt đầu - 10 phút, Giờ bắt đầu + 15 phút]`.
- **S-4 Xử lý No-show (Vắng mặt):** Nếu chỉ có một người kích hoạt khi hết khung giờ chờ, Session chuyển sang `NO_SHOW`. Người không kích hoạt sẽ bị hệ thống ghi nhận điểm xấu (Penalty).
- **S-5 Điều kiện hoàn thành:** Một chuyến đi chỉ được chuyển sang `COMPLETED` từ trạng thái `ACTIVE` sau khi đã diễn ra tối thiểu 5 phút (tránh việc gian lận điểm).
- **S-6 Giới hạn an toàn:** Một chuyến đi không được phép ở trạng thái `ACTIVE` quá 4 tiếng. Sau thời gian này, hệ thống sẽ tự động đóng Session để đảm bảo an toàn.

---

## 4. Ràng buộc xuyên suốt (Cross-Aggregate)

- **X-1 Nguồn sự thật duy nhất (Schedule SSOT):** Bảng `UserSchedule` là nơi duy nhất dùng để kiểm tra chồng lấn thời gian. Logic kiểm tra không được phép truy vấn rời rạc trên các bảng Intent hay Session.
- **X-2 Bàn giao trách nhiệm lịch trình (Hand-off):** Khi Intent chuyển sang `CONSUMED`, bản ghi lịch trình tương ứng phải được cập nhật ngay lập tức để trỏ tới `WalkSession` mới tạo. Tuyệt đối không được xóa và tạo mới để tránh làm mất "khóa" thời gian (Time Lock).
- **X-3 Danh sách loại trừ (Exclude List):** Nếu User A từ chối lời mời từ User B, hệ thống phải cập nhật danh sách loại trừ của Intent đó để Matching Engine không ghép cặp hai người này lại trong cùng một yêu cầu.
- **X-4 Hệ thống uy tín:** Kết quả của Session (`COMPLETED`, `NO_SHOW`, `ABORTED`) phải được cập nhật vào hồ sơ uy tín của người dùng ngay lập tức để phục vụ các lần ghép đôi sau này.
