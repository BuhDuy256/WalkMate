Có, **render đường đi của Partner là một feature đủ “khó” nếu bạn làm đúng scope**, không chỉ là “vẽ thêm polyline thứ hai”. Nó chạm vào **GPS, realtime sync, lifecycle session, offline/poor-network handling, state consistency, UI rendering, và performance**. Nếu triển khai bài bản, nó hoàn toàn có thể được trình bày như một chức năng độ khó cao trong tiêu chí chấm.

## 1. Vì sao tính năng này khó hơn GPS Path Tracing hiện tại?

Hiện tại bạn đang trace đường đi của **user hiện tại**, tức là dữ liệu GPS nằm local trên máy user:

```text
GPS hiện tại -> lưu Room / gửi backend -> render Polyline của mình
```

Khi render đường đi của Partner, bài toán chuyển thành:

```text
GPS của Partner
-> gửi backend gần realtime
-> backend lưu / phát tán
-> máy mình nhận dữ liệu
-> đồng bộ vào local cache
-> render Polyline của Partner lên bản đồ
```

Tức là bạn phải giải quyết thêm các vấn đề:

| Vấn đề                                                       | Độ khó           |
| ------------------------------------------------------------ | ---------------- |
| Đồng bộ tọa độ giữa 2 thiết bị                               | Cao              |
| Partner gửi GPS chậm, mất mạng, app background               | Cao              |
| Render 2 đường đi khác nhau trên map                         | Trung bình       |
| Phân biệt trạng thái người đang đi / đã hoàn thành / no-show | Cao              |
| Lưu local để không mất path khi mạng yếu                     | Cao              |
| Chống spam request GPS mỗi 3 giây                            | Trung bình - cao |
| Đồng bộ với lifecycle `WalkSession`                          | Cao              |

Tài liệu frontend của bạn đã xác định GPS polyline là luồng cần **Room offline-first** và ghi tọa độ liên tục khoảng 3 giây/lần, nên việc mở rộng sang partner path là rất hợp context kiến trúc hiện tại. 

## 2. Nó có được tính là “feature khó” không?

**Có, nhưng phụ thuộc cách bạn đóng gói tính năng.**

Nếu bạn chỉ làm:

```text
Gọi API lấy danh sách tọa độ partner -> vẽ polyline
```

thì nó chỉ là feature mức **trung bình**, vì giống “fetch data rồi render”.

Nhưng nếu bạn làm thành:

> **Realtime Dual Path Tracking trong WalkSession**

thì đây là feature khó.

Scope nên có:

```text
1. User A và User B đều gửi GPS track point theo chu kỳ.
2. Backend lưu track point theo session_id + user_id.
3. App của User A render:
   - Đường của mình
   - Đường của Partner
   - Marker vị trí hiện tại của mình
   - Marker vị trí hiện tại của Partner
4. Nếu partner mất mạng:
   - vẫn giữ path cũ
   - hiển thị trạng thái "Partner cập nhật lần cuối X giây trước"
5. Nếu app reconnect:
   - fetch bù các điểm còn thiếu
6. Khi session completed:
   - đóng path, không nhận thêm GPS mới
```

Với scope này, nó đáp ứng tốt tiêu chí **“chức năng có độ khó và phức tạp cao”, “xử lý lỗi và ngoại lệ”, “hiệu năng”, “tiện dụng”**.

## 3. Tính khả thi kỹ thuật

### Phương án khả thi nhất cho đồ án

Mình đề xuất không làm WebSocket ngay từ đầu. Hãy làm theo hướng **Polling thông minh + local cache**, vì phù hợp với Android Java thuần và dễ kiểm soát hơn.

Flow:

```text
Mỗi 3 giây:
- App gửi vị trí hiện tại của mình lên backend.

Mỗi 5-10 giây:
- App gọi API lấy track points mới của partner kể từ last_point_id hoặc last_timestamp.

Frontend:
- Lưu cả my points và partner points vào Room.
- Render 2 polyline trên map.
```

Đây là cách đủ tốt cho demo, ít rủi ro hơn WebSocket, và vẫn được xem là realtime-ish.

### API gợi ý

```text
POST /sessions/{sessionId}/track-points
GET  /sessions/{sessionId}/track-points?partnerOnly=true&afterId=123
GET  /sessions/{sessionId}/latest-locations
```

Entity backend có thể là:

```text
SessionTrackPoint
- id
- session_id
- user_id
- latitude
- longitude
- accuracy
- speed
- recorded_at
- created_at
```

Frontend Room entity:

```text
TrackPointEntity
- id
- sessionId
- userId
- latitude
- longitude
- accuracy
- recordedAt
- syncStatus
- source: ME / PARTNER
```

Cách này vẫn bám đúng kiến trúc hiện tại: UI observe `LiveData<UiState>`, ViewModel dùng `ExecutorService`, Repository xử lý API/Room, DTO không leak ra UI. 

## 4. Những case khó nên xử lý để “nâng status” feature

Để thuyết phục giảng viên đây là feature khó, bạn nên xử lý các case sau:

### Case 1: Partner chưa đến nơi

Theo lifecycle, `WalkSession` có personal state riêng cho từng người. Một người có thể `ACTIVE`, người kia vẫn `PENDING`; global session vẫn có thể là `ACTIVE`. 

Vậy UI nên hiển thị:

```text
Bạn: Đang đi bộ
Partner: Chưa bắt đầu / chưa đến hotspot
```

Lúc này không nên render partner path rỗng như lỗi, mà nên có trạng thái rõ ràng.

### Case 2: Partner mất mạng

Không xóa marker partner. Thay vào đó:

```text
Partner cập nhật lần cuối 42 giây trước
```

Nếu quá lâu:

```text
Partner có thể đang mất kết nối
```

Đây là điểm ăn “xử lý ngoại lệ” và “tính tiện dụng”.

### Case 3: App reconnect

Khi app mở lại hoặc mạng trở lại:

```text
GET partner track points after lastSyncedPointId
```

Không fetch lại toàn bộ path từ đầu. Cái này giúp bạn nói về **hiệu năng và tốc độ**.

### Case 4: Không render quá nhiều điểm

Nếu user đi 30 phút, mỗi 3 giây có 600 điểm/người. Hai người là 1200 điểm. Vẫn không quá lớn, nhưng nếu demo lâu hơn sẽ nặng.

Nên có tối ưu đơn giản:

```text
- Chỉ append điểm mới vào polyline.
- Không redraw toàn bộ map mỗi lần.
- Có thể bỏ qua điểm nếu khoảng cách quá gần, ví dụ < 3-5m.
```

### Case 5: Session đã completed

Khi cả hai người terminal state thì global session mới `COMPLETED`. Không nên tiếp tục gửi GPS sau khi personal state của user đã completed. Logic này khớp với invariant trong tài liệu lifecycle. 

## 5. Đánh giá theo thang điểm

Nếu triển khai như dưới đây, mình đánh giá feature này có thể được trình bày ở mức **khó cao**:

| Mức triển khai                                                 |     Độ khó | Đánh giá                       |
| -------------------------------------------------------------- | ---------: | ------------------------------ |
| Chỉ vẽ path của mình                                           |  Thấp - TB | Chưa đủ feature khó            |
| Vẽ path partner bằng API fetch toàn bộ                         | Trung bình | Có cải tiến nhưng chưa nổi bật |
| Vẽ path partner realtime-ish bằng polling + Room cache         |        Cao | Đủ feature khó                 |
| Có reconnect, last update, trạng thái partner, chống duplicate |       Cao+ | Rất đáng đưa vào báo cáo       |
| WebSocket realtime + conflict handling + background tracking   |    Rất cao | Có thể quá tốn thời gian       |

Với đồ án, mình khuyên chọn mức:

> **Realtime-ish Partner Path Rendering với Polling + Room Offline Cache + Last Seen Status**

Đây là sweet spot: đủ khó để nâng điểm, nhưng vẫn khả thi.

## 6. Cách đặt tên feature để nghe “xịn” hơn trong báo cáo

Đừng ghi là:

> Render đường đi của Partner

Nên ghi là:

> **Dual Realtime Path Tracing trong WalkSession**

Hoặc:

> **Đồng bộ và hiển thị hành trình song song của hai người đi bộ theo thời gian gần thực**

Mô tả trong báo cáo:

```text
Tính năng cho phép hai người trong cùng WalkSession theo dõi hành trình của nhau trên bản đồ. Ứng dụng ghi nhận GPS định kỳ, lưu cục bộ bằng Room để hỗ trợ mất mạng, đồng bộ track point lên backend, tải incremental path của partner, và render hai polyline độc lập trên bản đồ. Hệ thống xử lý các trạng thái partner chưa bắt đầu, mất kết nối, hoàn thành trước/sau và không tải lại toàn bộ dữ liệu nhằm tối ưu hiệu năng.
```

## Kết luận

**Nên làm.** Đây là một trong những cách tốt nhất để biến GPS Path Tracing từ tính năng “bình thường” thành **feature khó thật sự**.

Mức scope mình đề xuất cho bạn:

```text
MVP bắt buộc:
- Gửi GPS của mình lên backend theo session.
- Lấy GPS partner theo afterId / afterTimestamp.
- Render partner polyline khác màu.
- Hiển thị marker partner hiện tại.
- Hiển thị "last updated".

Nâng điểm:
- Lưu partner path vào Room.
- Reconnect thì fetch bù.
- Không gửi GPS nếu user/session đã terminal.
- Không redraw toàn bộ polyline mỗi lần.
```

Với cách này, tính năng này có thể đứng ngang hàng với AI Matching như một **chức năng phức tạp cao**, đặc biệt vì nó liên quan trực tiếp đến realtime, GPS, dữ liệu phiên đi bộ và trải nghiệm người dùng.
