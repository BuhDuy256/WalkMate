# Notification Flow Report

**Project:** WalkMate Android  
**Date:** 2026-04-28  
**Author:** Senior Full-stack Software Engineer  

---

## 1. Luồng đẩy Notification đến User

Hệ thống thông báo của WalkMate hoạt động qua hai kênh song song (dual-channel), được phối hợp bởi `NotificationPublisherImpl`. Mỗi khi một sự kiện nghiệp vụ quan trọng xảy ra, cả hai kênh đều được kích hoạt độc lập — failure của một kênh không ảnh hưởng kênh còn lại.

### 1.1 Các sự kiện kích hoạt Notification

| Sự kiện | Service kích hoạt | Loại Notification | Người nhận |
|---|---|---|---|
| Match tìm thấy (public flow) | `MatchingCommandService.findOrCreateProposal()` | `PROPOSAL_RECEIVED` | User được match |
| Private invite tạo | `WalkIntentCommandService.createPrivateInviteIntent()` | `INVITE_SENT` | Sender |
| Private invite tạo | `WalkIntentCommandService.createPrivateInviteIntent()` | `PROPOSAL_RECEIVED` | Receiver (friend được mời) |
| Cả hai chấp nhận proposal | `MatchingCommandService.acceptProposal()` | `SESSION_CONFIRMED` | Cả hai user |
| User đến điểm hẹn (activate) | `SessionCommandService.activateSession()` | `SESSION_ACTIVE` | Cả hai user |
| Session hoàn thành | `SessionCommandService.completeSession()` | `REVIEW_REQUESTED` | Cả hai user |
| Session auto-complete (scheduler) | `SessionCommandService.handleExpiredSessions()` | `REVIEW_REQUESTED` | Cả hai user |
| Gửi lời mời kết bạn | `FriendCommandService.sendFriendRequest()` | `FRIEND_REQUEST_RECEIVED` | Người nhận lời mời |
| Chấp nhận lời mời kết bạn | `FriendCommandService.acceptFriendRequest()` | `FRIEND_REQUEST_ACCEPTED` | Người gửi lời mời |
| Từ chối lời mời kết bạn | `FriendCommandService.declineFriendRequest()` | `FRIEND_REQUEST_DECLINED` | Người gửi lời mời |

### 1.2 Luồng từ Business Event → Màn hình User

```
[Business Logic tại Application Layer]
  e.g. MatchingCommandService, SessionCommandService, FriendCommandService
        │
        │  notificationPublisher.publish(Notification.create(...))
        ▼
[NotificationPublisher (domain interface / port)]
        │
        │  implements → NotificationPublisherImpl (infrastructure)
        ▼
[NotificationPublisherImpl] ──── Dual-channel dispatch ────
        │                                                   │
        │ Channel 1 (DB persist)              Channel 2 (FCM push)
        │                                                   │
        ▼                                                   ▼
[NotificationJdbcRepository.save()]        [UserRepository.findById()]
  INSERT INTO notification table              → lấy user.fcm_token
  (in-app feed — nguồn chân lý)                             │
                                         PushNotificationProvider.sendPush()
                                                   │
                                                   ▼
                                         [FcmNotificationProvider]
                                           → Firebase Admin SDK
                                           → FCM servers (data-only message)
                                                   │
                                                   ▼
                                          [Device của User]
                                          WalkMateFcmService.onMessageReceived()
                                                   │
                             ┌─────────────────────┴──────────────────────┐
                             │ Foreground                       Background │
                             ▼                                             ▼
                     AppEventBus.post()               showTrayNotification()
                     → MainActivity observes           System notification tray
                     → routeToDestination()            → User tap → MainActivity
                     (immediate navigation)              handleFcmIntent()
                                                        → routeToDestination()
```

### 1.3 Kênh đọc Notification (In-App Feed)

Ngoài push notification real-time, user còn có thể đọc toàn bộ notification lịch sử qua màn hình **Notification Center** (`NotificationFragment`):

```
NotificationFragment.onResume()
  → viewModel.startPolling()
      → loadNotifications() [ngay lập tức]
      → mainHandler.postDelayed(pollRunnable, 30_000ms) [lặp mỗi 30s]
          │
          ▼
NotificationRepository.getNotifications()
  → GET /api/v1/notifications
      │
      ▼
NotificationController.listNotifications()
  → NotificationCommandService.listForUser()
  → NotificationJdbcRepository.findByUserId()
      SELECT * FROM notification WHERE user_id = ? ORDER BY created_at DESC
          │
          ▼
NotificationUiState.ready(list) → LiveData update
  → adapter.submitList() → RecyclerView render
  → unreadCount (badge)

[User tap notification]
  → NotificationAdapter.OnReadListener
  → viewModel.markRead(notificationId) [nếu chưa đọc]
      → POST /api/v1/notifications/{id}/read
  → navigateForNotification() → NavController deep-link
```

### 1.4 Bảng Deep-link Navigation khi tap Notification

| Loại Notification | Đích điều hướng |
|---|---|
| `PROPOSAL_RECEIVED`, `INVITE_SENT`, `PROPOSAL_ACCEPTED` | `matchesFragment` → tab Proposal |
| `SESSION_CONFIRMED`, `SESSION_ACTIVE` | `matchesFragment` → tab Session |
| `FRIEND_REQUEST_RECEIVED` | `friendsFragment` → tab Incoming |
| `FRIEND_REQUEST_ACCEPTED` | `friendsFragment` → tab Friends |
| `FRIEND_REQUEST_DECLINED` | `friendsFragment` → tab Outgoing |
| `REVIEW_REQUESTED` | Không điều hướng — chỉ mark read |

---

## 2. FCM hoạt động như thế nào trong Flow này

### 2.1 Kiến trúc FCM của WalkMate

WalkMate sử dụng **data-only FCM messages** (không có `notification` block). Đây là lựa chọn kiến trúc có chủ đích:

> **Tại sao data-only?** Khi FCM message có `notification` block, Android OS sẽ tự hiển thị thông báo khi app ở background — và `onMessageReceived()` **KHÔNG được gọi** khi app bị kill. Với data-only payload, `onMessageReceived()` **LUÔN LUÔN được gọi** bất kể app đang foreground, background hay bị kill, giúp client kiểm soát hoàn toàn cách hiển thị thông báo.

### 2.2 Vòng đời FCM Token

```
[App khởi động lần đầu / FCM rotate token]
        │
        ▼
WalkMateFcmService.onNewToken(token)
  → UserRepository.updateFcmToken(token)
  → PATCH /api/v1/users/me/fcm-token
  → UPDATE user_account SET fcm_token = ? WHERE user_id = ?

[Backend cần gửi push]
  → UserRepository.findById(userId)
  → user.getFcmToken()
  → FcmNotificationProvider.sendPush(token, type, payload)
```

Token được lưu tại cột `fcm_token` trong bảng `user_account`. FCM tự động rotate token định kỳ; mỗi lần rotate, `onNewToken` được gọi lại và token mới được đồng bộ lên backend.

### 2.3 Payload FCM và cách Android xử lý

Backend gửi một data-only message qua **Firebase Admin SDK** (`FcmNotificationProvider`):

```json
// Ví dụ payload PROPOSAL_RECEIVED
{
  "token": "<device-fcm-token>",
  "data": {
    "type": "PROPOSAL_RECEIVED",
    "proposalId": "<uuid>",
    "intentId": "<uuid>",
    "senderUserId": "<uuid>"
  }
}
```

Android nhận message tại `WalkMateFcmService.onMessageReceived()`:

```
onMessageReceived(remoteMessage)
  → data.get("type") → resolveEventType(type) → AppEvent.Type
  → AppEventBus.get().post(new AppEvent(eventType, payload))   // foreground path
  → showTrayNotification(eventType, payload)                   // background path
```

**Foreground path:** `MainActivity` đang observe `AppEventBus.observe()` (LiveData). Event được marshal lên main thread và `routeToDestination()` điều hướng ngay lập tức.

**Background/Killed path:** `showTrayNotification()` hiển thị notification trên system tray. `PendingIntent` mang theo `EXTRA_FCM_TYPE`, `EXTRA_FCM_PROPOSAL_ID`, `EXTRA_FCM_SESSION_ID`. Khi user tap, `MainActivity.onCreate()` hoặc `onNewIntent()` đọc extras qua `handleFcmIntent()` và gọi `routeToDestination()`.

### 2.4 Failure Isolation (Phân tách lỗi)

`NotificationPublisherImpl` và `FcmNotificationProvider` đều wrap tất cả exception trong try-catch và chỉ log — **không bao giờ re-throw**. Điều này đảm bảo:
- Một FCM delivery failure **không bao giờ rollback** business transaction (tạo proposal, session,...).
- DB persist failure không block FCM dispatch.
- Token lookup failure không block DB persist.

### 2.5 Cấu hình Firebase (Backend)

Backend dùng **Firebase Admin SDK** với `ServiceAccount` credentials, được resolve theo thứ tự ưu tiên:

1. **Environment variable `FIREBASE_CREDENTIALS`** — dành cho staging/production (toàn bộ JSON content).
2. **Classpath `firebase-service-account.json`** — dành cho local dev.

Cả backend (`walkmate-3ef5f` project) và frontend (`google-services.json`) đều trỏ đến cùng một Firebase project `walkmate-3ef5f`, đảm bảo token registration và message delivery nhất quán.

---

## 3. Tại sao tôi không nhận được Push Notification?

Có nhiều nguyên nhân tiềm ẩn, được phân loại theo từng tầng của hệ thống. Kiểm tra theo thứ tự từ trên xuống dưới.

### 3.1 FCM Token chưa được đồng bộ lên Backend

**Triệu chứng:** Backend log hiển thị `token == null` hoặc `token.isBlank()`, FCM dispatch bị bỏ qua hoàn toàn (không có log lỗi, chỉ im lặng).

**Nguyên nhân:**
- `WalkMateFcmService.onNewToken()` gọi `UserRepository.updateFcmToken()`, nhưng method này silently ignore lỗi khi user chưa đăng nhập (`onError` là no-op).
- Nếu token được sinh ra **trước khi user đăng nhập** (common on first launch), token sẽ không được gửi lên backend vì chưa có access token để authenticate request.
- Token có thể đã bị FCM rotate sau lần đăng nhập cuối, và lần rotate mới xảy ra trong khi token JWT hết hạn.

**Cách kiểm tra:** Truy vấn DB: `SELECT fcm_token FROM user_account WHERE user_id = '<your-uuid>'`. Nếu `NULL` hoặc rỗng → token chưa được đăng ký.

**Fix:** Cần chủ động gọi `FirebaseMessaging.getInstance().getToken()` và update lên backend ngay sau khi login thành công, không chỉ phụ thuộc vào `onNewToken` callback.

---

### 3.2 Android POST_NOTIFICATIONS Permission bị từ chối (API 33+)

**Triệu chứng:** FCM message nhận được bình thường (log trong `onMessageReceived` chạy), nhưng notification không hiện trên system tray.

**Nguyên nhân:** Trên Android 13+ (API 33), `POST_NOTIFICATIONS` là runtime permission — user phải chủ động cấp phép. `MainActivity` chỉ hỏi permission sau khi user **đã có access token** (`hasUsableAccessToken()`). Nếu user dismiss dialog hoặc chọn "Don't ask again", `NotificationManagerCompat.from(this).notify(...)` trong `showTrayNotification()` sẽ bị hệ thống chặn im lặng.

**Cách kiểm tra:** Vào Settings → Apps → WalkMate → Notifications. Kiểm tra xem permission có được bật không.

**Fix:** Không cần code change — user tự vào Settings cấp phép. Tuy nhiên, UI nên có hướng dẫn rõ hơn thay vì chỉ show Toast.

---

### 3.3 Notification Channel bị tắt hoặc bị xóa

**Triệu chứng:** Permission granted, FCM nhận được, nhưng vẫn không có notification.

**Nguyên nhân:** `WalkMateFcmService.ensureNotificationChannel()` tạo channel `walkmate_push` với `IMPORTANCE_HIGH`. Nhưng một khi channel đã được tạo, Android OS bỏ qua mọi lần gọi `createNotificationChannel()` tiếp theo — kể cả thay đổi `IMPORTANCE`. Nếu user vào Settings và tắt channel này (hoặc giảm importance xuống NONE), toàn bộ notification từ channel đó sẽ biến mất.

**Cách kiểm tra:** Settings → Apps → WalkMate → Notifications → WalkMate Notifications → đảm bảo bật và importance là "Urgent" hoặc "High".

---

### 3.4 App bị Battery Optimization / Doze Mode chặn

**Triệu chứng:** Nhận được notification khi cắm sạc hoặc dùng app liên tục, nhưng mất notification khi màn hình tắt hoặc sau vài giờ không dùng.

**Nguyên nhân:** Trên nhiều Android OEM (Xiaomi MIUI, Samsung One UI, Oppo ColorOS...), Battery Optimization aggressively kills background processes và thậm chí block FCM delivery cho các app không được whitelist. Android's Doze Mode cũng trì hoãn FCM delivery, nhưng FCM High Priority messages thường bypass Doze — tuy nhiên `FcmNotificationProvider.sendPush()` hiện **không set priority** trong `Message.builder()`, nên FCM có thể treat chúng là normal priority.

**Fix đề xuất:** Thêm `.setAndroidConfig(AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH).build())` trong `FcmNotificationProvider.sendPush()` để đảm bảo message bypass Doze Mode. Ngoài ra hướng dẫn user whitelist WalkMate khỏi battery optimization.

---

### 3.5 FCM Token đã bị Stale (hết hạn / invalid)

**Triệu chứng:** Backend log hiển thị `FirebaseMessagingException` với error code `UNREGISTERED` hoặc `INVALID_ARGUMENT`.

**Nguyên nhân:** FCM token có thể bị invalidate khi user uninstall/reinstall app, clear app data, hoặc FCM tự rotate. Nếu backend vẫn lưu token cũ, mọi message sẽ fail. Hiện tại hệ thống chỉ cập nhật token thụ động qua `onNewToken` — nếu lần rotate token xảy ra khi app đang kill và user không mở lại app sau đó, backend sẽ giữ token stale vô thời hạn.

**Cách kiểm tra:** Backend log sẽ có entry: `FCM push delivery failed: type=... code=UNREGISTERED`.

**Fix đề xuất:** Khi `FcmNotificationProvider` nhận `UNREGISTERED` error, nên xóa token cũ khỏi `user_account` để tránh spam FCM với token không hợp lệ. Và như đã đề cập ở 3.1, gọi `getToken()` proactively khi login.

---

### 3.6 Backend không gửi FCM (FCM dispatch bị skip)

**Triệu chứng:** Notification có trong DB (in-app feed hiển thị đúng), nhưng không có push notification trên device.

**Nguyên nhân:**`NotificationPublisherImpl` chỉ gọi FCM khi `user.getFcmToken() != null && !token.isBlank()`. Nếu token null, FCM dispatch bị bỏ qua hoàn toàn mà không log error — chỉ có DB persist được thực hiện.

**Cách kiểm tra:** Kiểm tra lại mục 3.1. Nếu token tồn tại mà vẫn không nhận được notification, kiểm tra backend log cho entry `FCM push dispatched` hay `FCM push delivery failed`.

---

### 3.7 Sai Firebase Project (Mismatch giữa backend và frontend)

**Triệu chứng:** Backend log `FCM push dispatched` thành công, nhưng device không nhận được gì.

**Nguyên nhân:** Backend dùng `firebase-service-account.json` với `project_id: walkmate-3ef5f`. Frontend dùng `google-services.json` với `project_id: walkmate-3ef5f`. Hiện tại **hai file đã đúng cùng project** — trường hợp này ít có khả năng xảy ra, nhưng cần kiểm tra nếu môi trường staging dùng một service account khác.

---

### 3.8 Tóm tắt Checklist Debug

| # | Kiểm tra | Cách verify | Khả năng xảy ra |
|---|---|---|---|
| 1 | FCM Token đã được lưu trong DB chưa? | `SELECT fcm_token FROM user_account WHERE user_id = ?` | **Rất cao** |
| 2 | POST_NOTIFICATIONS permission được cấp chưa? (API 33+) | Settings → Apps → WalkMate → Notifications | **Cao** |
| 3 | Notification channel `walkmate_push` có bị tắt không? | Settings → Apps → WalkMate → Notifications → WalkMate Notifications | **Trung bình** |
| 4 | Backend log có `FCM push dispatched` không? | `grep "FCM push" app.log` | **Cao** |
| 5 | Backend log có FCM error code không? | `grep "FCM push delivery failed" app.log` | **Trung bình** |
| 6 | App có bị battery optimization chặn không? | Settings → Battery → Battery Optimization → WalkMate | **Cao (OEM)** |
| 7 | FCM message có được set HIGH priority không? | Code review `FcmNotificationProvider` | **Trung bình** |

**Root cause phổ biến nhất trong môi trường dev:** FCM token chưa được đồng bộ lên backend (mục 3.1), kết hợp với POST_NOTIFICATIONS permission chưa được cấp (mục 3.2).
