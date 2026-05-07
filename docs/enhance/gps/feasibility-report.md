# Báo Cáo Tính Khả Thi: Passive Partner Path Overlay

**Feature:** Current-user-first GPS Tracking với Passive Partner Path Overlay trong WalkSession  
**Ngày:** 2026-05-07  
**Dựa trên proposal:** `feature-improvement-suggestion.md` + review từ ChatGPT

---

## 1. Định nghĩa Feature (Đã Điều Chỉnh)

> **Partner path là overlay phụ — không bao giờ block luồng chính của user hiện tại.**

User hiện tại luôn có thể bắt đầu đi bộ, trace đường của mình và giữ camera focus vào bản thân bất kể partner đã xuất hiện hay chưa. Đường đi của partner được render như một lớp thông tin bổ sung khi dữ liệu có sẵn.

Tên gọi trong báo cáo đồ án:

> **Theo dõi hành trình chính của người dùng hiện tại, đồng thời đồng bộ và hiển thị hành trình của đối tác như một lớp phụ khi có dữ liệu.**

---

## 2. Kết luận tổng thể

**Khả thi cao, không cần thêm bảng DB mới.** Infrastructure hiện tại đã xây sẵn ~60% feature này. Phần còn lại là 1 backend endpoint mới + frontend polling loop + render second polyline. Scope MVP có thể hoàn thành trong 1–2 ngày dev.

---

## 3. Hiện trạng codebase — Những gì đã có sẵn

### 3.1 Backend

| Component | Hiện trạng | Ghi chú |
|---|---|---|
| `POST /api/v1/tracking/sync` | ✅ Hoàn chỉnh | Nhận GPS batch của **user hiện tại**, lưu vào `session_point_chunks` |
| `session_point_chunks.user_id` | ✅ Có sẵn | Mỗi chunk đã được tag theo `user_id` — dữ liệu của 2 người đã được **tách biệt sẵn trong DB** |
| `TrackingChunkRepository.findPolylinesBySessionAndUser()` | ✅ Có sẵn | Query lấy polylines theo `(session_id, user_id)` đã được implement |
| `TrackingQueryService.getSessionRoute()` | ✅ Có sẵn | Trả về cả 2 path — nhưng **chỉ cho terminal session** |
| Idempotent sync (R2) | ✅ Có sẵn | `sync_request_id` UNIQUE constraint chống duplicate |
| Authorization per participant | ✅ Có sẵn | `TrackingCommandService` đã validate caller là participant |
| `walk_session.user_a_status` / `user_b_status` | ✅ Có sẵn | Personal status của từng người — dùng để trả `partner_status` về frontend |

**Điểm mấu chốt:** DB schema và query infrastructure đã hỗ trợ multi-user path. Chỉ thiếu 1 endpoint GET để expose partner data trong lúc session còn ACTIVE.

### 3.2 Frontend

| Component | Hiện trạng | Ghi chú |
|---|---|---|
| GPS foreground service (`WalkTrackerService`) | ✅ Hoàn chỉnh | Ghi GPS mỗi 5s, periodic sync mỗi 30s |
| Room offline-first (`RoutePointEntity`) | ✅ Hoàn chỉnh | Lưu điểm của **mình** với `isSynced` flag |
| Polyline rendering (own path) | ✅ Hoàn chỉnh | `TrackingScreenActivity` render 1 polyline cam, camera follow user |
| `TrackingUiState` — own stats | ✅ Hoàn chỉnh | distance, pace, elapsed, mapPoints |
| `partnerName` + `partnerId` trong ViewModel | ✅ Có sẵn | Đã được truyền vào từ Intent, đủ để gọi backend endpoint mới |

---

## 4. Gap Analysis — Những gì còn thiếu

### 4.1 Backend gap (nhỏ)

**Thiếu:** Endpoint GET để fetch partner path trong lúc session đang ACTIVE, trả về cả `partner_status` để frontend phân biệt các trạng thái.

`TrackingQueryService.getSessionRoute()` hiện tại reject nếu session chưa terminal — không tái sử dụng được. Cần endpoint mới độc lập:

```
GET /api/v1/tracking/partner-path?session_id={id}&after_chunk_index={n}
```

Rule authorization:
- Caller là participant → OK
- Global session status là ACTIVE → OK
- **Partner personal status bất kỳ → không block** — trả về `chunks = []` kèm `partner_status`

### 4.2 Frontend gap (vừa)

| Gap | Effort |
|---|---|
| Polling loop 8s fetch partner path trong ViewModel | Trung bình |
| Decode encoded polyline → `List<LatLng>` (`PolyUtil.decode()`) | Thấp |
| Render second polyline (partner) màu khác trên map | Thấp |
| Track `lastPartnerChunkIndex` để fetch incremental | Thấp |
| `PartnerOverlayState` enum + `partnerLastUpdatedSeconds` trong `TrackingUiState` | Thấp |
| Hiển thị partner status label theo trạng thái | Thấp |
| Guard: camera KHÔNG follow partner update | Thấp (đã đúng hướng, cần document rõ) |
| Guard: dừng polling khi user hoàn thành session | Thấp |

---

## 5. Thiết Kế Chi Tiết

### 5.1 UX Flow tổng thể

```text
User hiện tại bắt đầu đi bộ (partner chưa cần xuất hiện)
→ App trace và render path của user hiện tại như bình thường
→ Box nhỏ cạnh avatar partner: "Đối tác chưa xuất hiện"
→ App polling mỗi 8 giây
   ├── partner_status = PENDING + chunks rỗng
   │     → giữ box "Đối tác chưa xuất hiện"
   ├── partner_status = ACTIVE + chunks rỗng
   │     → box: "Đang chờ dữ liệu vị trí từ đối tác..."
   ├── partner_status = ACTIVE + có chunks
   │     → ẩn box, vẽ partner polyline xanh + "Cập nhật Xs trước"
   ├── partner_status = COMPLETED
   │     → giữ path cuối, hiện "Đối tác đã hoàn thành"
   └── Poll thất bại (network)
         → KHÔNG xóa path cũ, chỉ tăng lastUpdatedSeconds
```

**Camera rule (cứng):** Partner path update KHÔNG BAO GIỜ trigger camera animation. Camera chỉ follow user hiện tại. User muốn xem partner phải chủ động bấm nút (tính năng nâng điểm).

### 5.2 Partner Overlay States

```java
// Enum dùng trong UiState — thay cho boolean isPartnerNotStarted
public enum PartnerOverlayState {
    WAITING_FOR_PARTNER,   // partner_status = PENDING, chưa có GPS data nào
    WAITING_FOR_GPS,       // partner_status = ACTIVE, chưa có chunk nào
    SHOWING_PATH,          // partner có path, đang hiển thị bình thường
    PARTNER_COMPLETED      // partner đã complete personal walk
}
```

| PartnerOverlayState | Hiển thị trên UI |
|---|---|
| `WAITING_FOR_PARTNER` | `"Đối tác chưa xuất hiện"` |
| `WAITING_FOR_GPS` | `"Đang chờ dữ liệu vị trí từ đối tác..."` |
| `SHOWING_PATH` + < 15s | Partner polyline, ẩn label |
| `SHOWING_PATH` + 15–60s | Partner polyline + `"Cập nhật 30s trước"` |
| `SHOWING_PATH` + > 60s | Partner polyline + `"Có thể mất kết nối"` |
| `PARTNER_COMPLETED` | Partner polyline cuối + `"Đối tác đã hoàn thành"` |

---

## 6. Kế hoạch Implement

### 6.1 Backend — 1 endpoint mới

**Step B-1:** Tạo Response DTO

```
presentation/dto/response/tracking/PartnerPathResponse.java
```

```java
public record PartnerPathResponse(
    @JsonProperty("chunks")          List<PartnerChunk> chunks,
    @JsonProperty("last_chunk_at")   long lastChunkCreatedAtMs,  // epoch ms; 0 nếu chưa có chunk
    @JsonProperty("partner_status")  String partnerStatus        // personal status: PENDING/ACTIVE/COMPLETED/NO_SHOW
) {
    public record PartnerChunk(
        @JsonProperty("chunk_index") int    chunkIndex,
        @JsonProperty("polyline")    String polyline
    ) {}
}
```

**Step B-2:** Thêm method vào `TrackingChunkRepository` và `TrackingChunkJdbcRepository`

```java
// TrackingChunkRepository.java — thêm:
List<ChunkRow> findChunksAfterIndex(String sessionId, String userId, int afterChunkIndex);

// ChunkRow là lightweight record: chunkIndex + polyline + createdAt (epoch ms)
```

```sql
SELECT chunk_index, polyline, EXTRACT(EPOCH FROM created_at) * 1000 AS created_at_ms
FROM session_point_chunks
WHERE session_id = :sessionId AND user_id = :userId AND chunk_index > :afterChunkIndex
ORDER BY chunk_index ASC
```

**Step B-3:** Thêm method vào `TrackingQueryService`

```java
public PartnerPathResponse getPartnerPath(String sessionId, String callerId, int afterChunkIndex) {
    WalkSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new DomainException(SessionErrorCode.SESSION_NOT_FOUND));

    // Caller phải là participant
    if (!callerId.equals(session.getUserIdA()) && !callerId.equals(session.getUserIdB())) {
        throw new DomainException(SessionErrorCode.SESSION_NOT_PARTICIPANT);
    }

    // Chỉ serve khi global session đang ACTIVE — không block vì partner personal status
    if (session.getStatus() != SessionStatus.ACTIVE) {
        throw new DomainException(SessionErrorCode.SESSION_NOT_ACTIVE);
    }

    boolean isCallerUserA = callerId.equals(session.getUserIdA());
    String  partnerId     = isCallerUserA ? session.getUserIdB() : session.getUserIdA();

    // Partner personal status — trả về để frontend phân biệt trạng thái overlay
    SessionStatus partnerPersonalStatus = isCallerUserA
            ? session.getUserBStatus() : session.getUserAStatus();

    // Partner chưa ACTIVE → trả chunks rỗng, không lỗi
    List<ChunkRow> rows = (partnerPersonalStatus == SessionStatus.ACTIVE
                           || partnerPersonalStatus == SessionStatus.COMPLETED)
            ? chunkRepository.findChunksAfterIndex(sessionId, partnerId, afterChunkIndex)
            : List.of();

    long lastChunkMs = rows.isEmpty() ? 0L :
            rows.get(rows.size() - 1).createdAtMs();

    List<PartnerPathResponse.PartnerChunk> chunks = rows.stream()
            .map(r -> new PartnerPathResponse.PartnerChunk(r.chunkIndex(), r.polyline()))
            .toList();

    return new PartnerPathResponse(chunks, lastChunkMs, partnerPersonalStatus.name());
}
```

> **Nguyên tắc chốt:** `partner_status = PENDING` → `chunks = []` + HTTP 200. Frontend không xử lý lỗi giả, chỉ đọc `partner_status` để chọn label hiển thị.

**Step B-4:** Thêm endpoint vào `TrackingController`

```java
@GetMapping("/partner-path")
public ResponseEntity<ApiResponse<PartnerPathResponse>> getPartnerPath(
        @AuthenticationPrincipal UserPrincipal principal,
        @RequestParam("session_id")                              String sessionId,
        @RequestParam(value = "after_chunk_index", defaultValue = "-1") int afterChunkIndex) {

    PartnerPathResponse response = trackingQueryService.getPartnerPath(
            sessionId, principal.userId(), afterChunkIndex);
    return ResponseEntity.ok(ApiResponse.success(response));
}
```

> **Không cần migration DB mới.** `session_point_chunks` đã đủ cột. `walk_session.user_b_status` đã có sẵn.

---

### 6.2 Frontend — Polling + Render

**Step F-1:** Thêm API service interface

```
data/datasource/remote/api/PartnerPathApiService.java
```

**Step F-2:** Mở rộng `TrackingRepository` và `TrackingRepositoryImpl`

```java
// TrackingRepository.java — thêm:
void fetchPartnerPath(String sessionId, int afterChunkIndex,
                      DomainCallback<PartnerPathResult> callback);
```

`PartnerPathResult` là domain object:
```java
public class PartnerPathResult {
    private final List<LatLng> newPoints;        // decoded từ polylines mới
    private final int lastChunkIndex;            // chunk_index lớn nhất nhận được
    private final long lastChunkCreatedAtMs;     // epoch ms của chunk cuối
    private final String partnerStatus;          // "PENDING" / "ACTIVE" / "COMPLETED" / "NO_SHOW"
}
```

Decode polyline trong `TrackingRepositoryImpl`:
```java
List<LatLng> decoded = new ArrayList<>();
for (PartnerChunkDto chunk : dto.chunks()) {
    decoded.addAll(PolyUtil.decode(chunk.polyline())); // maps-utils đã là dependency
}
```

**Step F-3:** Mở rộng `TrackingUiState`

```java
// Thêm vào constructor và getters:
private final List<LatLng>       partnerMapPoints;        // full accumulated partner path
private final PartnerOverlayState partnerOverlayState;    // enum 4 trạng thái
private final long               partnerLastUpdatedSeconds; // giây kể từ chunk cuối; 0 nếu chưa có
```

**Step F-4:** Thêm polling logic vào `TrackingViewModel`

```java
// Thêm fields:
private final List<LatLng> partnerAccumulatedPoints = new ArrayList<>();
private int    lastFetchedPartnerChunkIndex = -1;
private long   partnerLastChunkMs           = 0L;
private String partnerPersonalStatus        = "PENDING"; // cập nhật từng poll

private final ScheduledExecutorService partnerPollExecutor =
        Executors.newSingleThreadScheduledExecutor();
private ScheduledFuture<?> partnerPollFuture;
```

Polling — bắt đầu cùng lúc với `startWalk()`, dừng trong `finishWalk()` và `requestCompleteWalk()`:

```java
private void startPartnerPolling() {
    partnerPollFuture = partnerPollExecutor.scheduleAtFixedRate(
            this::pollPartnerPath, 3L, 8L, TimeUnit.SECONDS);
}

private void pollPartnerPath() {
    if (sessionId == null) return;
    repository.fetchPartnerPath(sessionId, lastFetchedPartnerChunkIndex,
            new DomainCallback<PartnerPathResult>() {
                @Override
                public void onSuccess(PartnerPathResult result) {
                    partnerPersonalStatus = result.getPartnerStatus();
                    if (!result.getNewPoints().isEmpty()) {
                        partnerAccumulatedPoints.addAll(result.getNewPoints());
                        lastFetchedPartnerChunkIndex = result.getLastChunkIndex();
                        partnerLastChunkMs           = result.getLastChunkCreatedAtMs();
                    }
                    rebuildUiState(); // postValue — safe từ background thread
                }

                @Override
                public void onError(Exception e) {
                    // Poll thất bại → KHÔNG xóa path cũ, KHÔNG reset status
                    // Camera không bị ảnh hưởng
                    Log.w("TrackingVM", "Partner poll failed: " + e.getMessage());
                    // Chỉ rebuild để cập nhật partnerLastUpdatedSeconds
                    rebuildUiState();
                }
            });
}

private void stopPartnerPolling() {
    if (partnerPollFuture != null) {
        partnerPollFuture.cancel(false);
        partnerPollFuture = null;
    }
}
```

Thêm `stopPartnerPolling()` vào `finishWalk()` và `requestCompleteWalk()`:
```java
public void finishWalk() {
    stopTimer();
    stopPartnerPolling(); // ← thêm dòng này
    stopGpsService();
    walkStateLiveData.setValue(WalkState.FINISHED);
    clearPersistedState();
}
```

Logic tính `PartnerOverlayState` trong `rebuildUiState()`:

```java
private PartnerOverlayState computePartnerOverlayState() {
    if ("COMPLETED".equals(partnerPersonalStatus) || "NO_SHOW".equals(partnerPersonalStatus)) {
        return PartnerOverlayState.PARTNER_COMPLETED;
    }
    if ("ACTIVE".equals(partnerPersonalStatus) && !partnerAccumulatedPoints.isEmpty()) {
        return PartnerOverlayState.SHOWING_PATH;
    }
    if ("ACTIVE".equals(partnerPersonalStatus)) {
        return PartnerOverlayState.WAITING_FOR_GPS;
    }
    return PartnerOverlayState.WAITING_FOR_PARTNER; // PENDING hoặc chưa fetch lần nào
}
```

**Step F-5:** Mở rộng `TrackingScreenActivity` — render second polyline

```java
// Thêm field:
private Polyline partnerPolyline;
private static final int PARTNER_POLYLINE_COLOR = 0xFF4A90E2; // xanh dương

// Trong renderState() — thêm:
updatePartnerOverlay(state);
```

```java
private void updatePartnerOverlay(TrackingUiState state) {
    // Cập nhật partner polyline — KHÔNG trigger camera
    List<LatLng> partnerPoints = state.getPartnerMapPoints();
    if (!partnerPoints.isEmpty()) {
        if (partnerPolyline == null) {
            partnerPolyline = googleMap.addPolyline(new PolylineOptions()
                    .color(PARTNER_POLYLINE_COLOR).width(8f)
                    .startCap(new RoundCap()).endCap(new RoundCap()));
        }
        partnerPolyline.setPoints(partnerPoints);  // chỉ update points, không animateCamera
    }

    // Cập nhật status label
    updatePartnerStatusLabel(state.getPartnerOverlayState(), state.getPartnerLastUpdatedSeconds());
}

private void updatePartnerStatusLabel(PartnerOverlayState overlayState, long lastUpdatedSecs) {
    switch (overlayState) {
        case WAITING_FOR_PARTNER:
            txtPartnerStatus.setVisibility(View.VISIBLE);
            txtPartnerStatus.setText("Đối tác chưa xuất hiện");
            break;
        case WAITING_FOR_GPS:
            txtPartnerStatus.setVisibility(View.VISIBLE);
            txtPartnerStatus.setText("Đang chờ dữ liệu vị trí từ đối tác...");
            break;
        case SHOWING_PATH:
            if (lastUpdatedSecs < 15) {
                txtPartnerStatus.setVisibility(View.GONE);
            } else if (lastUpdatedSecs < 60) {
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText("Cập nhật " + lastUpdatedSecs + "s trước");
            } else {
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText("Có thể mất kết nối (" + lastUpdatedSecs + "s trước)");
            }
            break;
        case PARTNER_COMPLETED:
            txtPartnerStatus.setVisibility(View.VISIBLE);
            txtPartnerStatus.setText("Đối tác đã hoàn thành");
            break;
    }
}
```

**Step F-6:** Layout — thêm `TextView` partner status

Thêm 1 `TextView` nhỏ (`txtPartnerStatus`) cạnh avatar partner trong `activity_tracking_screen.xml`. Muted color, font nhỏ — không cạnh tranh với stat chính.

---

## 7. Xử lý Edge Cases

### Case 1: Partner chưa đến / PENDING
Backend trả `partner_status = "PENDING"` + `chunks = []`. Frontend đọc `PartnerOverlayState.WAITING_FOR_PARTNER` → hiển thị "Đối tác chưa xuất hiện". Không vẽ polyline, không gây nhầm lẫn.

### Case 2: Partner đã ACTIVE nhưng chưa có GPS chunk
Xảy ra khi partner bấm Start Walk nhưng GPS service chưa kịp gửi chunk đầu tiên lên backend. Backend trả `partner_status = "ACTIVE"` + `chunks = []`. Frontend → `WAITING_FOR_GPS` → "Đang chờ dữ liệu vị trí...". Sau 1–2 poll tiếp theo sẽ có chunk.

### Case 3: Partner mất mạng giữa chừng
`partnerAccumulatedPoints` không bị xóa khi poll thất bại. Polyline giữ nguyên. `partnerLastUpdatedSeconds` tăng dần → label chuyển từ ẩn → "Cập nhật Xs trước" → "Có thể mất kết nối".

### Case 4: App reconnect — fetch bù incremental
`lastFetchedPartnerChunkIndex` tồn tại trong ViewModel suốt lifecycle Activity. Khi mạng phục hồi, `fetchPartnerPath(sessionId, lastFetchedChunkIndex, ...)` tự động chỉ lấy các chunk còn thiếu — không tải lại toàn bộ path từ đầu.

### Case 5: Partner hoàn thành trước
Backend trả `partner_status = "COMPLETED"`. Frontend → `PARTNER_COMPLETED` → giữ path cuối + "Đối tác đã hoàn thành". Polling tự dừng khi user hiện tại cũng complete (gọi `stopPartnerPolling()` trong `requestCompleteWalk()`).

### Case 6: Camera không bị kéo sang partner
Camera animation (`animateCamera`) chỉ được gọi trong `handleCameraUpdate()` khi `state.isCameraFollowingUser() == true`. Partner update chỉ gọi `partnerPolyline.setPoints()` — không bao giờ gọi `animateCamera`. Đây là invariant cứng cần giữ.

---

## 8. Scope MVP vs Nâng Điểm

### Bắt buộc (MVP)
- User trace được path của mình dù partner chưa xuất hiện
- Poll partner path mỗi 8 giây, incremental bằng `afterChunkIndex`
- Khi partner có data → ẩn label, vẽ partner polyline màu khác
- Camera luôn follow user hiện tại, không nhảy sang partner
- Label partner status theo `PartnerOverlayState` (4 trạng thái)

### Nâng điểm
- Hiển thị "Cập nhật Xs trước" → "Có thể mất kết nối" khi partner offline
- "Đối tác đã hoàn thành" khi `partner_status = COMPLETED`
- Nút nhỏ "Xem đối tác" → `animateCamera` đến điểm cuối partner path (không auto)
- Nút "Quay lại tôi" → center camera về user hiện tại

---

## 9. Tóm tắt Thay Đổi Cần Làm

### Backend (không cần migration DB)

| File cần tạo/sửa | Loại thay đổi |
|---|---|
| `TrackingChunkRepository.java` | Thêm `findChunksAfterIndex()` + `ChunkRow` record |
| `TrackingChunkJdbcRepository.java` | Implement method trên |
| `PartnerPathResponse.java` (mới) | DTO với `chunks`, `last_chunk_at`, `partner_status` |
| `TrackingQueryService.java` | Thêm `getPartnerPath()` — không block theo partner personal status |
| `TrackingController.java` | Thêm `GET /partner-path` endpoint |

### Frontend (không cần Room migration)

| File cần tạo/sửa | Loại thay đổi |
|---|---|
| `PartnerPathApiService.java` (mới) | Retrofit interface |
| DTO classes cho partner path (mới) | `PartnerPathResponseDto`, `PartnerChunkDto` |
| `PartnerPathResult.java` (mới) | Domain object với `newPoints`, `lastChunkIndex`, `lastChunkCreatedAtMs`, `partnerStatus` |
| `PartnerOverlayState.java` (mới) | Enum 4 giá trị |
| `TrackingRepository.java` | Thêm `fetchPartnerPath()` |
| `TrackingRepositoryImpl.java` | Implement + decode polyline bằng `PolyUtil.decode()` |
| `TrackingUiState.java` | Thêm `partnerMapPoints`, `partnerOverlayState`, `partnerLastUpdatedSeconds` |
| `TrackingViewModel.java` | Thêm polling loop, partner state fields, `stopPartnerPolling()` |
| `TrackingScreenActivity.java` | Render second polyline + `updatePartnerStatusLabel()` |
| `activity_tracking_screen.xml` | Thêm `txtPartnerStatus` TextView |

---

## 10. Những Gì Proposal Gốc Đề Xuất nhưng Không Cần Làm

| Đề xuất | Quyết định | Lý do |
|---|---|---|
| Lưu partner path vào Room | **Bỏ qua** | Partner data đến từ network, không phải local GPS. In-memory trong ViewModel là đủ — khi mất mạng partner data cũng không cập nhật được, không có gì để "offline cache". |
| `TrackingEntity` mới với `source: ME/PARTNER` | **Bỏ qua** | Chỉ cần nếu lưu Room. |
| WebSocket | **Bỏ qua** | Polling 8s đủ cho demo đi bộ, ít rủi ro, không cần Spring WebSocket infrastructure. |

---

## 11. Đánh Giá Tổng

| Tiêu chí | Đánh giá |
|---|---|
| Tính khả thi kỹ thuật | **Cao** — infrastructure sẵn >60%, không cần migration DB |
| Thời gian ước tính | **1–2 ngày** cho MVP |
| Rủi ro | **Thấp** — logic mới hoàn toàn tách biệt, không sửa luồng GPS hiện tại |
| Giá trị demo | **Cao** — 2 polyline, 4 partner states, last-seen, camera discipline |
| Độ khó thuyết phục giảng viên | **Cao** — xử lý lifecycle state, incremental sync, UX edge cases |
| Phù hợp kiến trúc MVVM + DDD-lite | **Có** — tuân thủ đầy đủ ràng buộc kiến trúc |
