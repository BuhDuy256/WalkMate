# Matching Flow — Quyết định Kiến trúc (ADR)

Tài liệu này ghi lại các quyết định kiến trúc quan trọng của lần tái cấu trúc
**Matching Flow** nhằm chống *double-booking* (một người bị xếp trùng hai cuộc đi bộ
trong cùng một khung giờ) giữa **ghép công khai** và **lời mời trực tiếp**.

Mỗi mục được viết theo khuôn khổ ADR (Architecture Decision Record):

- **Bối cảnh** — vấn đề cần giải quyết.
- **Quyết định** — lựa chọn đã triển khai.
- **Phương án đã cân nhắc** — những cách khác đã xem xét và lý do loại bỏ.
- **Đánh đổi** — cái giá phải trả của quyết định.
- **Hệ quả** — điều ràng buộc đối với mã nguồn sau này.

Tài liệu giải thích cơ chế hoạt động chi tiết nằm ở
[`matching-flow-progress.md`](./matching-flow-progress.md) (Giải thích).
Bản kế hoạch gốc là `implementation-plan-matching-flow-v2.md`.

---

## ADR-001 — Khóa lịch cá nhân bằng PostgreSQL *advisory lock*

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Chống double-booking không thể dựa vào việc khóa riêng từng hàng `walk_intent`,
vì kiểm tra trùng lặp phải quét **nhiều bảng cùng lúc** (`walk_intent` + `walk_session`)
và phải đúng cả khi người dùng **chưa có bất kỳ hàng nào** (trường hợp phantom —
không có hàng để `SELECT ... FOR UPDATE` bám vào). Hai luồng ghi — tạo intent công khai
và tạo lời mời riêng tư — có thể đua nhau tạo slot trùng.

**Quyết định**

Dùng `pg_advisory_xact_lock(key)` để **khóa toàn bộ lịch của một user** như một mutex.
Key là số 64-bit được gấp từ UUID của user:

```
key = NAMESPACE * 31 + uuid.mostSignificantBits
key = key * 31 + uuid.leastSignificantBits
```

`NAMESPACE` là hằng số 64-bit cố định (mang ý nghĩa "WALKMATE" trong chú thích mã
nguồn) để khóa lịch không bao giờ va chạm với người dùng advisory-lock khác trong
cùng database.

**Phương án đã cân nhắc**

- *Chỉ `SELECT ... FOR UPDATE` trên intent rows* — không đủ, vì phantom và vì overlap
  check trải trên hai bảng.
- *`EXCLUDE ... USING gist` (exclusion constraint)* — đúng về lý thuyết nhưng khó biểu
  diễn overlap `[start, end)` trên **hai** bảng khác nhau, đồng thời không tự nhiên
  tương tác với các trạng thái (chỉ khóa "active" chứ không phải mọi hàng).
- *Khóa phân tán kiểu Redis / bảng khóa trong DB* — thêm dependency, thêm bước mở/đóng
  khóa tường minh, dễ rò rỉ khóa khi process chết.

**Đánh đổi**

- Advisory lock là *transaction-scoped*: tự nhả khi commit/rollback, không cần `unlock`
  tường minh → loại bỏ cả lớp bug "quên mở khóa".
- Đây là **mutex hợp tác**, không phải constraint: chỉ an toàn khi *mọi* writer tuân thủ
  gọi `lockUsers` trước khi chạm lịch. Bỏ sót một writer là mất toàn bộ đảm bảo.
- Collision key (hai user gấp về cùng một số 64-bit) chỉ làm hai user bị serialize lẫn
  nhau (giảm throughput), không bao giờ phá vỡ tính đúng đắn.

**Hệ quả**

Mọi writer tạo/giải phóng slot lịch **bắt buộc** gọi `UserCalendarLockManager.lockUsers(...)`
trước khi đọc-ghi lịch, trong cùng transaction.

---

## ADR-002 — Thứ tự khóa chuẩn (canonical lock order)

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Một writer có thể cần khóa lịch của **hai** người (cả hai intent) cộng với các hàng
intent/proposal. Nếu hai transaction khóa cùng một tập tài nguyên theo thứ tự khác nhau
sẽ deadlock.

**Quyết định**

Mọi writer thống nhất một thứ tự duy nhất:

1. **User advisory lock** — sắp xếp theo chuỗi UUID (tăng dần), loại bỏ trùng lặp.
2. **Intent row lock** — `SELECT ... FOR UPDATE`, sắp theo `intent_id` (thứ tự từ điển).
3. **Proposal row lock** — `SELECT ... FOR UPDATE OF mp` (chỉ khóa `match_proposal`,
   không khóa intent/hotspot được JOIN vào).
4. **Ghi dữ liệu** (mutation).

Đọc khám phá (participants, trạng thái) luôn chạy **trước** khi khóa; sau khi khóa,
mọi thứ được **reload và revalidate** một lần nữa.

**Phương án đã cân nhắc**

- *Khóa theo thứ tự truy vấn tự nhiên* — tạo cạnh ngược, deadlock.
- *Khóa toàn cục một key duy nhất* — đơn giản nhưng serialize toàn bộ hệ thống, mất hết
  song song.

**Đánh đổi**

- Chi phí là **đọc hai lần** (một lần discovery, một lần sau khi khóa) và phải viết lại
  toàn bộ logic revalidate. Đổi lại là **không có cạnh ngược → không deadlock** giữa các
  writer tuân thủ thứ tự.
- Sắp theo chuỗi UUID (không phải bit) để thứ tự nhất quán và dễ đọc/debug.

**Hệ quả**

Bất kỳ writer mới nào trong tương lai cũng phải đi theo đúng 4 bước trên; đây là điểm
duy nhất cần kiểm tra khi review để đảm bảo không tái phát deadlock.

---

## ADR-003 — Retry có giới hạn với transaction tươi mỗi lần thử

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Khi hai writer đua nhau, một bên có thể thua ở bước khóa/ghi (deadlock, lock timeout,
trạng thái đổi giữa chừng). Ta cần thử lại nhưng **không được** thử lại bên trong
transaction đã thất bại (transaction đã bị đánh dấu rollback-only).

**Quyết định**

`MatchingTransactionExecutor` bọc `TransactionTemplate`; **mỗi lần thử là một transaction
hoàn toàn mới**. Chỉ retry khi gặp:

- SQLSTATE `40P01` (deadlock detected),
- SQLSTATE `55P03` (lock_not_available / lock timeout),
- SQLSTATE `40001` (serialization failure),
- `RetryableConcurrencyException` (trạng thái đổi giữa chừng — cần chạy lại từ đầu).

**Lỗi nghiệp vụ (`DomainException`) không bao giờ được retry** và luôn ném ra ngay.

**Phương án đã cân nhắc**

- *Retry trong cùng transaction* — sai về bản chất (transaction đã rollback-only).
- *Retry vô hạn* — có thể vòng lặp vô tận khi có contention kéo dài.
- *Retry mọi exception* — che giấu lỗi nghiệp vụ thật, gây spam log và độ trễ vô ích.

**Đánh đổi**

- Thêm độ phức tạp: phải phân loại lỗi chính xác, nếu phân loại sai (ném `DomainException`
  thay vì `RetryableConcurrencyException` ở chỗ cần retry) sẽ mất đi tính sẵn sàng.
- Backoff luỹ thừa có jitter; mỗi lần thử áp `SET LOCAL lock_timeout` / `statement_timeout`
  để tránh treo. Giá trị mặc định (`max-attempts=3`, `lock-timeout=2000ms`,
  `statement-timeout=5000ms`, backoff 100→1000ms) là *đặt theo đúng đắn*, chưa đo tải thật.

**Hệ quả**

Khi cạn số lần thử, lỗi cuối cùng (conflict/DataAccess) được ném ra và framework ánh xạ
thành HTTP 500 — client có thể tự retry (xem ADR-011).

---

## ADR-004 — Tách `RetryableConcurrencyException` khỏi `DomainException`

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Cần một tín hiệu riêng để báo "trạng thái đã đổi giữa chừng, hãy đọc lại và thử lại",
khác hẳn "dữ liệu không hợp lệ về nghiệp vụ".

**Quyết định**

Tạo `RetryableConcurrencyException extends RuntimeException` dùng riêng cho trường hợp
này (ví dụ: candidate không còn `OPEN` khi đã khóa được hàng). Nó được executor coi là
retryable; `DomainException` thì không.

**Phương án đã cân nhắc**

- *Tái dùng `DomainException` + cờ* — trộn lẫn hai khái niệm, dễ retry nhầm lỗi nghiệp vụ.

**Đánh đổi**

- Yêu cầu người viết code phải nhớ ném đúng loại ở đúng chỗ (điểm dễ sai). Đổi lại là
  ranh giới retry rất rõ ràng và dễ test.

---

## ADR-005 — Lời mời riêng tư chạy atomic, auto-accept dùng chung internal workflow

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Lời mời riêng tư tạo **hai intent**, một **proposal**, và tự động accept phía người gửi.
Nếu mỗi bước ghi trong một transaction riêng, một lỗi giữa chừng sẽ để lại dữ liệu dở
dang (intent mồ côi, proposal không có đủ hai bên).

**Quyết định**

Toàn bộ luồng mời riêng tư chạy trong **một transaction** duy nhất, retry như một khối
thống nhất. Bước auto-accept phía người gửi gọi
`MatchingCommandService.acceptProposalInternal(...)` — workflow nội bộ dùng chung — **bên
trong** transaction đang mở, và **không bao giờ retry độc lập**.

**Phương án đã cân nhắc**

- *Mỗi bước ghi transaction riêng + bù trừ* — phức tạp, cần saga/compensation, dễ sai.
- *Auto-accept gọi `acceptProposal` (bản có retry)* — sẽ mở transaction mới lồng nhau và
  retry độc lập, phá vỡ tính atomic.

**Đánh đổi**

- Transaction dài hơn (ôm cả hai intent + proposal + accept) nên giữ khóa lâu hơn một chút.
- FCM/notification được gửi trong transaction nên trong trường hợp retry hiếm gặp có thể
  gửi trùng lặp — chỉ là vấn đề thẩm mỹ, không ảnh hưởng đúng đắn (xem ADR-012).

**Hệ quả**

`acceptProposalInternal` phải luôn được gọi **trong một transaction đang hoạt động**; nó
không tự mở transaction.

---

## ADR-006 — Giữ OCC (version guard) trên proposal như *defence-in-depth*

**Trạng thái:** Chấp nhận.

**Bối cảnh**

`MatchProposalJdbcRepository.save` vốn dùng conditional UPDATE có kiểm tra `version`
(optimistic concurrency). Sau khi mọi writer đều khóa proposal row trước khi ghi, guard
này gần như không bao giờ kích hoạt.

**Quyết định**

**Giữ nguyên** version guard. Nó đóng vai trò backstop: nếu sau này có một writer mới
quên khóa hàng, OCC vẫn chặn được lost update thay vì âm thầm ghi đè.

**Đánh đổi**

- Gần như không tốn gì (một predicate `version = ?` trong UPDATE); rủi ro duy nhất là
  guard có thể bắn nhầm nếu có ai đó cố ý bỏ qua thứ tự khóa, nhưng đó lại chính là tín
  hiệu báo lỗi mong muốn.

---

## ADR-007 — Không thêm *expected-version* cho upsert `walk_intent`

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Kế hoạch gốc cho phép "row-lock discipline **hoặc** conditional UPDATE có expected
version". `walk_intent` dùng upsert không có predicate version.

**Quyết định**

Chọn **row-lock discipline**: mọi writer đều reload intent qua `findByIdForUpdate` trước
khi mutate, nên không cần thêm expected-version vào đường INSERT/UPDATE.

**Phương án đã cân nhắc**

- *Thêm conditional UPDATE* — đổi đường ghi đang được nhiều writer dùng chung chỉ để có
  một đảm bảo mà row-lock đã cung cấp.

**Đánh đổi**

- Tiết kiệm được thay đổi rủi ro trên đường ghi dùng chung; đổi lại là an toàn phụ thuộc
  hoàn toàn vào discipline ở tầng service (phải luôn reload dưới khóa).

---

## ADR-008 — Unique index trên `walk_session(proposal_id)` (I-04)

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Một proposal chỉ được phép sinh ra **tối đa một** `walk_session`. Index cũ
`idx_walk_session_proposal_id` không unique nên về lý thuyết cho phép hai session trùng
nếu có double-accept đồng thời.

**Quyết định**

Migration `V125__enforce_session_proposal_unique.sql` thay bằng
`CREATE UNIQUE INDEX uq_walk_session_proposal_id ON walk_session(proposal_id)`, sau một
bước preflight kiểm tra dữ liệu trùng và fail loudly nếu có.

**Đánh đổi**

- Preflight khiến migration từ chối chạy nếu đã tồn tại dữ liệu trùng (phải dọn trước);
  đây là hành vi mong muốn — phát hiện sớm hơn là âm thầm bỏ sót.

**Hệ quả**

Database trở thành **tầng chặn cuối** cho bất biến "một session/proposal", độc lập với
độ chính xác của logic khóa ở tầng application.

---

## ADR-009 — Không dùng unique index cho cặp proposal PENDING

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Bất biến "mỗi intent chỉ tham gia **tối đa một** proposal `PENDING`" không thể biểu diễn
bằng một index unique trên cặp `(intent_id_a, intent_id_b)`: cùng một intent có thể xuất
hiện ở `intent_id_a` của proposal này và `intent_id_b` của proposal khác.

**Quyết định**

Thực thi bất biến bằng **khóa + revalidate dưới khóa** (kiểm tra `findPendingByIntentId`
cho cả hai bên sau khi đã `FOR UPDATE`), thay vì index.

**Đánh đổi**

- An toàn nằm ở logic ứng dụng (không có constraint DB để chặn một bug trong tương lai),
  đổi lại tránh được việc ép một bất biến phức tạp vào một index không đủ khả năng biểu
  diễn.

---

## ADR-010 — Writer vòng đời session không dùng advisory lock

**Trạng thái:** Chấp nhận (độ lệch so với kế hoạch).

**Bối cảnh**

Các thao tác activate / cancel / complete / no-show của `walk_session` chỉ **giải phóng**
một reservation đã có, không tạo reservation mới trùng lặp.

**Quyết định**

Không áp advisory lock cho chúng. Việc **tạo** session (reservation duy nhất được tạo từ
writer session) nằm bên trong `acceptProposal`, vốn đã được khóa đầy đủ.

**Đánh đổi**

- Giảm phạm vi thay đổi và tránh khóa không cần thiết; đổi lại là một quy ước ngầm: nếu
  sau này thêm writer session nào **tạo** slot mới, nó phải được đưa vào giao thức khóa.

---

## ADR-011 — Không thêm mã lỗi mới cho retry exhaustion

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Khi cạn số lần retry, cần trả về lỗi gì đó cho client.

**Quyết định**

Ném nguyên lỗi cuối cùng (`RetryableConcurrencyException` hoặc `DataAccessException`),
để framework ánh xạ thành HTTP 500. Không thêm mã lỗi domain mới; client có thể tự retry.

**Đánh đổi**

- API không phân biệt được "tạm thời bận" với "lỗi thật" ở tầng HTTP; đổi lại executor
  tách bạch hoàn toàn khỏi bảng mã lỗi domain, giảm coupling.

---

## ADR-012 — Notification/FCM giữ trong transaction (outbox để dành Phase 6)

**Trạng thái:** Chấp nhận (độ lệch so với kế hoạch).

**Bối cảnh**

Kế hoạch coi transactional outbox / durable delivery / idempotency là phạm vi tuỳ chọn.

**Quyết định**

Giữ nguyên `NotificationPublisher` gửi trong transaction. FCM push có thể, trong trường
hợp retry hiếm gặp, bắn trùng — chấp nhận vì đây là vấn đề thẩm mỹ (notification trùng),
không phải vấn đề đúng đắn (dữ liệu vẫn nhất quán).

**Đánh đổi**

- Không có đảm bảo *exactly-once* cho notification; đổi lại không phải xây outbox +
  reconciliation cho phần core.

---

## ADR-013 — Overlap là interval `[start, end)` (bao gồm đầu, loại trừ cuối)

**Trạng thái:** Xác nhận.

**Bối cảnh**

Cần thống nhất ngữ nghĩa overlap để test và logic khớp nhau.

**Quyết định**

Overlap dùng predicate hiện có `time_window_start < :end AND time_window_end > :start`
— hai khung giờ **kề sát nhau** (kết thúc đúng lúc bắt đầu) không bị coi là trùng.

**Hệ quả**

Toàn bộ test concurrency dựa trên ngữ nghĩa này (kể cả test "adjacent windows" xác nhận
hai cửa sổ liền kề không overlap).

---

## ADR-014 — Kiểm thử hai instance bằng hai Spring context trong cùng process

**Trạng thái:** Chấp nhận.

**Bối cảnh**

Cần chứng minh chống double-booking **khi có hai instance ứng dụng** cùng trỏ một database.

**Quyết định**

Test hai instance dùng **hai Spring context độc lập** (hai connection pool + transaction
manager riêng) trong cùng một process JVM.

**Đánh đổi**

- Không phải hai OS process thật; nhưng về ngữ nghĩa khóa là **tương đương** vì mọi khóa
  đều nằm ở tầng database (advisory lock / row lock là DB-scoped), không phụ thuộc JVM.

---

## Độ lệch so với kế hoạch (tổng hợp)

1. **ADR-010** — session lifecycle writers không advisory-lock (chỉ giải phóng slot).
2. **ADR-009** — không thêm index unique cho cặp PENDING proposal.
3. `findOrCreateProposal` trả về proposal PENDING đã tồn tại **trước** khi kiểm tra
   `OPEN` — sửa race khi một concurrent match đã chuyển intent của caller sang MATCHING.
4. **ADR-011** — retry exhaustion không thêm mã lỗi mới.
5. **ADR-012** — notification giữ trong transaction, outbox để dành Phase 6.
