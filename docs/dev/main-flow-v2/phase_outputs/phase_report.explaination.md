### 1. Vấn đề 1: Lỗi "gọi hồn" bảng dữ liệu đã bị xóa (Stale Code / Migration Mismatch)
**Bản chất:** Trong quá trình nâng cấp DB (bản migration V104), bạn đã xóa bảng `follow_relation` (theo dõi) và thay thế bằng một cấu trúc mới là bảng `friendship` (bạn bè). Tuy nhiên, tầng Repository (`SocialJdbcRepository`) vẫn còn sót lại các hàm cũ (như `follow()`, `unfollow()`) đang trỏ trực tiếp bằng câu query SQL vào cái bảng đã bị xóa kia.

**Hậu quả:** Nếu có bất kỳ luồng code nào gọi vào các hàm này, hệ thống sẽ lập tức "crash" và quăng lỗi Database Exception (Table not found). 
*Lưu ý của doc:* Hiện tại hệ thống chưa nổ vì ở Phase 1 chưa có API nào gọi đến các hàm follow này. Nó là một "quả bom nổ chậm", bạn có thể phớt lờ nó ở Phase 1, nhưng bắt buộc phải dọn dẹp trước khi làm các tính năng Social thực sự.

---

### 2. Vấn đề 2: Lỗ hổng logic "thuật phân thân" (Thiếu kiểm tra Overlapping Session)
**Bản chất:** Khi user gọi API tạo một yêu cầu đi dạo mới (`createIntent()`), hệ thống cần đảm bảo user này không bị "phân thân" (có 2 hoạt động ở cùng 1 khung giờ). Hiện tại code mới chỉ check: *"User này có yêu cầu (Intent) nào khác trùng giờ không?"*. Nó **quên mất** việc gọi hàm `hasOverlappingActiveSession()` để check xem: *"User này có đang nằm trong một Chuyến đi (Session) nào thực tế đang diễn ra trùng giờ không?"*.

**Hậu quả:** Hệ thống bị lủng logic (vi phạm luật I-1). Một user có thể đang trong một chuyến đi dạo (Session = ACTIVE), nhưng vẫn tạo thành công một yêu cầu tìm người đi dạo mới cho chính khung giờ đó.

---

### 3. Vấn đề 3: Nợ kỹ thuật Test (Chạy bằng niềm tin)
**Bản chất:** Giống hệt Phase 0, toàn bộ Phase 1 được code xong nhưng không có dòng Unit/Integration Test nào bảo vệ. Tài liệu này liệt kê 6 quy tắc nghiệp vụ (Business Rules) cực kỳ phức tạp vừa được thêm vào và yêu cầu Phase 2 bắt buộc phải viết test cho chúng để chống Regression (lỗi hồi quy):
* **Luật TTL:** Lời mời (proposal) phải bị quét rác đúng sau 5 phút.
* **Luật Check-in:** Hàm `recordActivation()` chỉ cho phép user bấm "bắt đầu đi" trong khung giờ châm chước: sớm 10 phút hoặc trễ tối đa 15 phút. Ngoài khung này phải chặn.
* **Luật kết thúc chuyến đi:** Chặn việc user bấm "Kết thúc" (`POST /sessions/{id}/complete`) quá sớm khi chưa đi đủ thời gian tối thiểu.
* **Luật bảo mật (I-7):** Yêu cầu đi dạo đặt ở chế độ `Private` thì chỉ những người được mời mới nhìn thấy hàm `findOpenCandidates()`, người lạ không được thấy.
* **Luật chặn trùng giờ:** Nếu có yêu cầu đang `MATCHING` thì phải chặn tạo mới, nhưng nếu yêu cầu đã `CONSUMED` (đi xong rồi) thì được phép tạo tiếp.