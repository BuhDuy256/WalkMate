Danh sach van de: 
1. Hiện tại thì findMatch trả về người valid nhất => Theo như thiết kế thì khi tìm được Match thì phải tạo ra Proposal và tùy vào User quết định Accept hay Pass thì update status => Phải sinh ra domain "Proposal". => Hàm findMatch của intent hiện tại chưa có logic tạo ra proposal.
2. Hiện tại thì chưa rõ là response của findMatch trả về User mà nó tìm được có đúng ko hay là trả về proposal. 
Hiện tại thì /intents/ đang trả về user valid. Cân nhắc 2 options là trả về "proposal" cho /intents/ hoặc di chuyển cái findMatch sang /proposal/ API. Hoặc có cách thiết kế khác. 

Dựa trên các tài liệu **Invariants**, **Lifecycle** và bản **Research** của bạn, đây là bản tóm tắt thiết kế chuẩn xác nhất để bạn ra quyết định triển khai cho WalkMate.

---

## 1. Summary Flow (Hành động & Trạng thái)

Hệ thống sẽ vận hành theo mô hình **System-Driven Matching** (Hệ thống tự ghép đôi) thay vì người dùng tự đi tìm nhau.

| Bước | Chủ thể | Hành động | Trạng thái Hệ thống |
| :--- | :--- | :--- | :--- |
| **1. Khởi tạo** | User A & B | Tạo Intent (Thời gian, Địa điểm, Bộ lọc). | `WalkIntent`: **OPEN**. |
| **2. Quét đối tượng** | Hệ thống | Chạy Worker quét các Intent có Time Overlap $\ge Min\_Duration$. | Kiểm tra Invariant **I-2** (Cùng Hotspot, cùng OPEN). |
| **3. Đề xuất** | Hệ thống | Chọn cặp có Scoring cao nhất và tạo đề nghị. | `MatchProposal`: **PENDING**. |
| **4. Thông báo** | Hệ thống | Gửi Push/Email cho cả A và B. | Cả 2 Intent vẫn **OPEN** nhưng gắn link tới Proposal. |
| **5. Phản hồi** | User A & B | Nhấn **Confirm** (Chấp nhận) hoặc **Pass** (Từ chối). | Nếu 1 người Pass: Proposal thành **REJECTED**. |
| **6. Chốt đơn** | Hệ thống | Thực hiện Giao dịch nguyên tử (Atomic Transaction) khi cả 2 cùng Confirm. | `MatchProposal`: **CONFIRMED**; `WalkIntent`: **CONSUMED**; `WalkSession`: **PENDING**. |

---

## 2. Thiết kế API chuẩn hóa

Bạn nên tách biệt rõ ràng giữa việc "Quản lý mong muốn" (Intent) và "Quản lý giao kèo" (Proposal).

### A. Nhóm API Proposals (Dành cho User phản hồi)
* **`GET /api/v1/proposals/active?intentId={id}`**: 
    * Dùng để App "poll" hoặc lấy dữ liệu hiển thị khi User nhấn vào thông báo.
    * Trả về: Thông tin đối phương (Partner Profile) và trạng thái của Proposal.
* **`PATCH /api/v1/proposals/{proposalId}/respond`**:
    * **Payload**: `{ "action": "ACCEPT" | "REJECT" }`
    * **Logic**: Cập nhật cờ `accepted` của user đó trong Proposal.

### B. Nhóm API Sessions (Dành cho việc thực hiện đi bộ)
* **`GET /api/v1/sessions/active`**: Lấy thông tin phiên đi bộ hiện tại sau khi đã khớp lệnh thành công.
* **`POST /api/v1/sessions/{sessionId}/activate`**: Để cả 2 kích hoạt khi đến điểm hẹn (Invariant **S-3**).

---

## 3. Các quyết định kỹ thuật "Sinh tử" (Critical Decisions)

Dựa trên các Invariants, bạn cần đảm bảo các logic "cứng" sau trong Code:

1.  **Atomic Transaction (P-3):** Đây là phần quan trọng nhất. Khi người cuối cùng nhấn Confirm, Backend phải dùng Transaction để:
    * Khóa (Lock) cả 2 bản ghi Intent.
    * Kiểm tra chắc chắn cả 2 vẫn đang ở trạng thái `OPEN`.
    * Tạo `WalkSession` và đổi trạng thái Intent sang `CONSUMED` cùng một lúc.
2.  **Xử lý Proposal "mồ côi" (P-5):** Nếu User A đang có một Proposal `PENDING` nhưng họ đột ngột Cancel Intent của chính mình, hệ thống phải tự động chuyển Proposal đó sang `EXPIRED` hoặc `REJECTED` ngay lập tức.
3.  **Cách tính Time Overlap (Database Level):** Hãy áp dụng công thức `(Start_A < End_B) AND (Start_B < End_A)` trực tiếp vào câu Query để hệ thống không bị chậm khi số lượng Intent tăng lên.

---

## 4. Tư vấn về Trải nghiệm UI (Bottom Sheet)

Vì bạn lo ngại về việc UI hiện tại chỉ có một Bottom Sheet "Find Match", tôi đề xuất:
* Khi User nhấn **Find Match**: Hệ thống tạo Intent và ngay lập tức chạy Worker tìm kiếm.
* **Nếu thấy người phù hợp ngay:** Hiển thị Profile người đó trên Bottom Sheet với nút "Gửi lời mời" (Thực chất là tạo Proposal PENDING).
* **Nếu không thấy ai:** Hiển thị "Chúng tôi sẽ thông báo cho bạn khi có người phù hợp" và đóng Bottom Sheet.

**Quyết định cuối cùng của bạn nên là:** Chuyển từ việc trả về `User` sang trả về `MatchProposal`. Mọi logic sau đó sẽ tự động chạy trơn tru theo đúng bản thiết kế Invariants bạn đã dày công soạn thảo.

Bạn có muốn tôi giúp viết chi tiết logic cho **Atomic Transaction (P-3)** bằng ngôn ngữ lập trình bạn đang dùng không?