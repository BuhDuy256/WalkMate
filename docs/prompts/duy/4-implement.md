1. Tầng Core Domain (Dành cho code của Bước 2):

Định nghĩa các Aggregate Roots, Entities, Value Objects.
Định nghĩa Lifecycle Enum (VD: PENDING, ACTIVE...).
Code các hàm State Transitions, ném lỗi (Domain Exceptions) ngay ở tầng Domain nếu cố tình thực hiện transition sai logic hoặc vi phạm Invariant. (Ở bước này, code hoàn toàn thuần túy, không dính dáng gì đến framework, database hay framework HTTP).
2. Tầng Data / Persistence (Dành cho code của Bước 3):

Chạy script Database Migration để tạo table (walk_session, walk_trace...).
Cài đặt các DB Constraints (Unique Index, Check Constraints) mà bạn đã vạch ra để chặn Invariants tầng cuối.
Code Repository Interfaces và Implementation xử lý lưu DB (kèm lock concurrency phòng Stress Test).
3. Tầng Application / Use Cases (Dành cho code của Bước 1 & Stress Test Bước 2):

Viết các Services (ví dụ: StartWalkSessionUseCase, EndWalkSessionUseCase...).
Gọi Repository lưu Aggregate. Sử dụng Transaction để đảm bảo tính ACID, đảm bảo bắt lỗi fail/retry theo đúng kịch bản thiết kế Stress Test.
4. Tầng Presentation (API/Controllers):

Expose các Use Case thành REST/GraphQL Endpoints...
5. Viết Tests (Dành cho Invariants & Stress Test):

Biến các kịch bản Stress Test và Invariants thành Integration Tests hoặc Unit Tests (Vd: Gọi hàm Pay đồng thời 2 lần xem Optimistic Locking đã ném lỗi chuẩn chưa).