### Lộ trình 4 Trụ cột cho Backend Engineer (Java / Spring Boot)

#### Trụ cột 1: RDBMS & Transaction (Lõi cơ sở dữ liệu)
Đừng chỉ học cách viết câu lệnh SQL `SELECT`, `UPDATE`. Hãy học cách Database hoạt động dưới nền.
* **Kiến thức trọng tâm:**
    * Tính chất ACID của Transaction.
    * **Transaction Isolation Levels** (Read Uncommitted, Read Committed, Repeatable Read, Serializable) -> *Học cái này xong bạn sẽ tự hiểu tại sao sinh ra lỗi Race Condition.*
    * Cơ chế Index (B-Tree, Hash Index) và cách tối ưu Query.
* **Tài liệu chuẩn mực:** * 📖 Sách: **"High-Performance Java Persistence"** của Vlad Mihalcea. (Bắt buộc phải đọc. Tác giả là người viết ra lõi của Hibernate. Quyển này sẽ giải thích từ DB lên tới Code một cách mượt mà nhất).

#### Trụ cột 2: Concurrency & Multithreading (Xử lý đồng thời)
Hệ thống web sinh ra là để phục vụ nhiều người cùng lúc. Nếu không nắm vững đa luồng, bạn sẽ liên tục gặp bug lúc lên Production (môi trường thực tế) dù test ở máy cá nhân (Local) chạy rất ngon.
* **Kiến thức trọng tâm:**
    * Java Memory Model.
    * Thread Lifecycle, Synchronized blocks, Volatile.
    * Sự khác biệt giữa Optimistic Lock (Version) và Pessimistic Lock (For Update).
* **Tài liệu chuẩn mực:**
    * 📖 Sách: **"Java Concurrency in Practice"** của Brian Goetz. (Đây là cuốn "kinh thánh" của dân làm Java, dù cũ nhưng giá trị lý thuyết không bao giờ lỗi thời).

#### Trụ cột 3: Khống chế Framework (Spring Boot & Hibernate)
Đừng dùng Spring Boot như một chiếc "hộp đen" (Blackbox). Phải hiểu cách nó bọc các khái niệm ở Trụ cột 1 và 2 lại.
* **Kiến thức trọng tâm:**
    * Vòng đời của một Bean (Bean Lifecycle) và Spring Context.
    * Cơ chế AOP (Aspect-Oriented Programming) -> *Cách Spring tạo ra `@Transactional` ngầm dưới code.*
    * Propagation trong `@Transactional` (Khi nào dùng `REQUIRED`, khi nào dùng `REQUIRES_NEW`).
    * Entity State trong Hibernate (Transient, Persistent, Detached).
* **Tài liệu chuẩn mực:**
    * 🎓 Khóa học: Tìm các khóa của **Vlad Mihalcea** hoặc khóa **"Spring Data JPA"** trên Udemy. Học cách họ cấu hình và xử lý N+1 Query problem.

#### Trụ cột 4: System Design (Kiến trúc hệ thống)
Khi bạn đã nắm chắc 3 trụ cột trên ở cấp độ 1 server máy chủ, đây là lúc vươn ra cấp độ nhiều máy chủ (Distributed Systems).
* **Kiến thức trọng tâm:**
    * Caching Strategy (Redis, Memcached).
    * Distributed Lock (Khóa phân tán khi chạy nhiều server cùng lúc).
    * Message Brokers (Kafka, RabbitMQ) -> *Cái này áp dụng ngay cho bài toán bắn Notification FCM của bạn để không làm chậm luồng chính.*
* **Tài liệu chuẩn mực:**
    * 📖 Sách: **"Designing Data-Intensive Applications" (DDIA)** của Martin Kleppmann. (Tuyệt phẩm. Đọc xong cuốn này tư duy hệ thống của bạn sẽ vươn lên tầm Tech Lead).
    * 🎓 Khóa học: **"Grokking the System Design Interview"** trên Educative.io.

---

### Chiến lược học dành cho bạn lúc này

Bạn đang ôm dự án WalkMate, không thể vứt đó để đi đọc sách 3 tháng được. Hãy áp dụng chiến lược **"Vết dầu loang" (Just-In-Time Learning)**:

1.  **Dừng lại ở "Từ khóa":** Khi đụng phải bài toán ở WalkMate (ví dụ: `sweepExpiredProposals` báo lỗi khóa), thay vì bảo AI "Fix bug cho tao", hãy bảo AI: *"Khái niệm lõi đằng sau bug này là gì? Nêu tên sách hoặc chương tài liệu nói về nó"*.
2.  **Đọc xoáy vào đúng chương đó:** Lấy cuốn *High-Performance Java Persistence* ra, lật đúng chương nói về Transaction Management để đọc. Mất khoảng 2 tiếng.
3.  **Quay lại đập AI:** Dùng kiến thức vừa đọc được để review lại giải pháp mà Claude đề xuất. 

Bạn có muốn tôi cung cấp một list các câu hỏi "tự sát hạch" (Checklist) để bạn tự kiểm tra xem kiến thức nền tảng Java/Spring của mình đang hổng ở chỗ nào nhất không? Từ đó mình sẽ chọn ra 1 điểm để ưu tiên học trước.

Việc bạn làm chủ cả 2 stack **Node.js** và **Spring Boot (Java)** là một lợi thế cực kỳ lớn. Nhìn từ góc độ kiến trúc, đây là hai trường phái thiết kế hoàn toàn đối lập nhau. 

Khi bạn "nhảy" qua lại giữa 2 framework này, rủi ro lớn nhất không phải là cú pháp ngôn ngữ, mà là bạn mang **tư duy (mental model)** của bên này ốp sang bên kia. 

Dưới đây là 4 điểm khác biệt cốt lõi về "kiến thức chung" bạn bắt buộc phải ghim vào đầu mỗi khi switch context giữa Node.js và Spring Boot:

### 1. Mô hình thực thi: Đội quân (Java) vs. Trạm điều phối (Node.js)

* **Spring Boot (Thread-per-request):** Mỗi khi có 1 request gửi tới, Spring Boot sẽ cử 1 "anh thợ" (Thread) ra tiếp. Nếu có 100 request, sẽ có 100 anh thợ chạy song song.
    * **Lưu ý:** Vì các anh thợ chạy song song trong cùng một chương trình, nếu họ cùng sửa chung 1 biến (ví dụ: biến toàn cục), họ sẽ dẫm chân lên nhau (Memory Race Condition).
* **Node.js (Event Loop):** Chỉ có đúng **1 anh thợ (Single Thread)** tiếp nhận mọi request. Khi gặp việc nặng (như gọi Database), anh ta ném việc đó ra sau (Event Queue) và quay ra tiếp khách khác. Khi DB trả kết quả, anh ta mới quay lại xử lý tiếp.
    * **Lưu ý:** Tuyệt đối không bắt anh thợ duy nhất này làm phép toán nặng (CPU-bound) như nén ảnh, mã hóa file. Nếu anh ta bận tính toán, toàn bộ server sẽ bị "treo", không ai tiếp khách mới cả!

### 2. Bóng ma "Race Condition" trong Node.js

Nhiều người lầm tưởng: *"Node.js chạy Single Thread (1 luồng) nên chắc chắn không bị Race Condition như Java!"*. Đây là sai lầm chết người.

Node.js không bị đụng độ ở bộ nhớ RAM, nhưng cực kỳ dễ dính **Logical Race Condition** do cơ chế `async/await`.

* Giả sử trong Node.js bạn viết: 
    1. Đọc user rảnh (`await db.getUser()`)
    2. Check rảnh thì gán ghép (`await db.matchUser()`)
* **Vấn đề:** Giữa bước 1 và bước 2, khi hàm đang tạm dừng (await), Event Loop sẽ nhảy sang phục vụ request của người khác. Người khác đó hoàn toàn có thể lách vào khe hở này để đọc cùng một user.
* **Bài học:** Dù bạn dùng Node.js hay Java, khi chạm vào Database, nguyên tắc về **Pessimistic Lock** hay **Optimistic Lock** là bất di bất dịch. Cơ sở dữ liệu nằm ngoài code của bạn, nên code nào gọi vào cũng phải khóa cửa đàng hoàng.

### 3. Sự "ma thuật" của Transaction

* **Spring Boot:** Cực kỳ nhàn. Bạn chỉ cần gắn `@Transactional` lên đầu hàm. Bên dưới, Java dùng một cơ chế gọi là `ThreadLocal` (biến cục bộ của riêng từng Thread) để âm thầm mang theo cái connection từ đầu đến cuối luồng chạy.
* **Node.js:** Không có phép màu này (ít nhất là với các ORM cũ). Khi bạn mở một Transaction trong Node.js (ví dụ dùng Sequelize, Prisma hay Knex), bạn **bắt buộc phải truyền tay** biến transaction (`trx`) đó vào mọi hàm con. Nếu bạn gọi hàm con mà quên truyền `trx`, hàm con sẽ tự động chạy độc lập ngoài transaction, dẫn đến tình trạng "thằng ngoài rollback, thằng trong vẫn commit".

### 4. Lựa chọn vũ khí cho từng trận đánh

Với tư cách là Tech Lead, khi nhận một yêu cầu mới, bạn phải biết chọn dùng stack nào:
* **Chọn Node.js khi:** Bài toán nặng về I/O (Input/Output). Ví dụ: Làm server Chat realtime (như bạn đang định cắm MongoDB chat), Streaming, làm API Gateway, các ứng dụng gọi nhiều API của bên thứ 3. Node.js xử lý hàng vạn kết nối I/O cực kỳ mượt mà và ít tốn RAM.
* **Chọn Spring Boot khi:** Bài toán phức tạp về nghiệp vụ (Business Logic), nặng về tính toán (CPU-bound), hoặc đòi hỏi tính nhất quán dữ liệu tài chính cực cao (như Banking, Payment). Hệ sinh thái Java sinh ra để làm những hệ thống khổng lồ và chặt chẽ.