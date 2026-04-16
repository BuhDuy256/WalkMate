### 1. Vấn đề 1: Lỗi "nhanh tay thì được" (Race Condition) khi Lock dữ liệu
**Bản chất vấn đề:** Khi hệ thống muốn ghép cặp 2 người (Match), nó cần đọc trạng thái của 2 cái `intent` (yêu cầu đi dạo) từ Database lên để xem có rảnh (`OPEN`) không, sau đó mới khóa lại (`lock()`). 
Tuy nhiên, hiện tại code đang dùng hàm đọc thông thường (`findById`) thay vì đọc có khóa chết (`findByIdForUpdate` - hay còn gọi là **Pessimistic Lock**).

**Hậu quả tiềm ẩn:**
* Nếu có 2 luồng (thread) cùng chạy vào hàm `findOrCreateProposal` ở cùng một phần ngàn giây, cả 2 luồng đều sẽ đọc được `intent` đang ở trạng thái `OPEN`. 
* Cả 2 luồng đều đinh ninh rằng mình được quyền ghép cặp, nên cả 2 đều tạo ra `proposal` (lời mời) và cố gắng update `intent` thành trạng thái `MATCHING`. Đây chính là hiện tượng **Race Condition**.

**Tại sao nó chưa "cháy nhà"?**
Bởi vì ở tầng Database, bạn đã khôn khéo tạo một Unique Index chặn việc tạo trùng `proposal`. Luồng nào ghi `proposal` chậm hơn một nhịp sẽ bị Database văng lỗi từ chối. Tuy nhiên, việc hệ thống vẫn cố gắng lưu `intent` với trạng thái `MATCHING` có thể gây đụng độ dữ liệu.
*Lưu ý của doc:* Đây không phải là lỗi mới do Phase 0 sinh ra (code cũ vốn đã không có lock), nhưng đây là "cục nợ kỹ thuật" cần phải trả ở các phase sau bằng cách dùng `findByIdForUpdate`.

---

### 2. Vấn đề 2: Quét rác "bỏ chung một giỏ" (Transaction Scope)
**Bản chất vấn đề:** Bạn có một hàm `sweepExpiredProposals()` chạy ngầm để dọn dẹp các lời mời ghép cặp đã hết hạn. Hiện tại, toàn bộ vòng lặp dọn dẹp hàng loạt lời mời này đang được bọc trong một `@Transactional` duy nhất.

**Hậu quả:**
Giao dịch (Transaction) trong database có tính chất **All-or-Nothing** (Được ăn cả, ngã về không). Giả sử hệ thống quét và dọn dẹp được 99 cái proposal thành công, nhưng đến cái thứ 100 thì xảy ra lỗi (chẳng hạn như dữ liệu bị lỗi, hoặc mất kết nối DB chớp nhoáng). 
Ngay lập tức, **toàn bộ 99 cái đã dọn trước đó sẽ bị Rollback (hoàn tác)** trở lại trạng thái ban đầu. Thành ra công cốc.

**Đánh giá:** Doc có ghi rõ đây là vấn đề về **Robustness (Sự bền bỉ)** chứ không phải sai logic (Correctness). Vì nếu lỗi, 60 giây sau job chạy lại sẽ dọn lại từ đầu. Nhưng về chuẩn thiết kế thực chiến, mỗi `proposal` nên được dọn dẹp trong một Transaction độc lập (dùng `TransactionTemplate`) để "hư cái nào, bỏ cái đó, các cái khác vẫn sống".

---

### 3. Vấn đề 3: Code chạy "bằng niềm tin" (Thiếu Test Coverage)
**Bản chất vấn đề:** Ở Phase 0, bạn đã dọn dẹp lại cấu trúc, viết thêm các logic chuyển đổi trạng thái (state-transitions) và cơ chế chống ghi đè phiên bản (Optimistic Concurrency Control - OCC bằng trường `version`). Tuy nhiên, chưa có một dòng Unit Test hay Integration Test nào được viết ra để chứng minh đống code này chạy đúng.

**Yêu cầu bắt buộc cho Phase 1:** Để đảm bảo khi làm tiếp không đụng chạm làm gãy logic cũ, Phase 1 bắt buộc phải bổ sung Test cho 5 kịch bản (scenario) cốt lõi:
1.  **Chuyển đổi trạng thái:** Phải test được hàm `lock()` / `unlock()` của `WalkIntent` hoạt động đúng luật.
2.  **Cơ chế OCC:** Phải giả lập 2 luồng cùng lưu một `MatchProposal`, và test xem hệ thống có văng đúng lỗi `PROPOSAL_CONCURRENT_MODIFICATION` không.
3.  **Tạo Proposal:** Test hàm `findOrCreateProposal()` đảm bảo cả 2 người đều bị đổi trạng thái sang `MATCHING`.
4.  **Từ chối/Hủy:** Hàm `passProposal()` và `cancelProposal()` phải nhả `intent` của 2 người về lại `OPEN`.
5.  **Job quét dọn:** Test hàm quét rác đảm bảo nó đổi trạng thái `proposal` thành hết hạn và mở khóa lại `intent`.