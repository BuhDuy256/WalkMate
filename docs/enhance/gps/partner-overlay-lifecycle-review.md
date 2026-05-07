# Review Report: Partner Overlay Lifecycle Update Proposal

**Phạm vi**: Tracking flow — `TrackingViewModel`, `TrackingUiState`, `TrackingScreenActivity`  
**Ngày**: 2026-05-07  
**Loại**: Correctness review + change specification  

---

## 1. Tóm tắt Proposal

Proposal đề xuất các cải tiến sau cho luồng Tracking sau khi feature Passive Partner Path Overlay được implement:

| # | Nội dung |
|---|---|
| P1 | Không dừng partner polling khi current user completes — chỉ dừng own GPS tracking |
| P2 | Mở rộng state label matrix: hiển thị label theo tổ hợp (current user state × partner state) |
| P3 | Toast một lần khi partner status đổi (PENDING → ACTIVE, ACTIVE → COMPLETED, v.v.) |
| P4 | Implement Toast via `partnerNoticeMessage` field trong `TrackingUiState` + `consumePartnerNotice()` |

---

## 2. Đánh giá tính đúng đắn

### 2.1 P1 — Polling lifecycle bug ✅ Đúng

**Bug được identify đúng.** Hiện tại trong `TrackingViewModel.java`:

```java
// requestCompleteWalk() — line 332
stopPartnerPolling();  // ← SAI: dừng quá sớm
stopGpsService();
walkStateLiveData.setValue(WalkState.FINISHING);

// finishWalk() — line 299
stopPartnerPolling();  // ← ĐÚNG: abort / force teardown
stopGpsService();
```

**Scenario bị ảnh hưởng:**
1. User 1 complete lúc T=0 → `requestCompleteWalk()` gọi `stopPartnerPolling()`.
2. User 2 activate lúc T=+2 phút.
3. UI của User 1 bị đóng băng tại label `"Partner hasn't arrived yet"` vì không còn polling.

**Đánh giá giải pháp:**  
Tách biệt hai lifecycle là đúng về mặt kiến trúc:

- **Own GPS tracking**: dừng khi user hiện tại complete/abort → đúng, không cần gửi GPS nữa.
- **Partner overlay polling**: chỉ dừng khi session terminal toàn cục (`SESSION_TERMINAL|...`), Activity bị destroy (`onCleared()`), hoặc user thoát màn hình.

**Lưu ý về `finishWalk()`**: `finishWalk()` được dùng cho emergency abort / force teardown — ở đây vẫn nên gọi `stopPartnerPolling()` vì đây là trường hợp rời màn hình hoàn toàn. Proposal không phân biệt rõ điểm này, nhưng đây là edge case đúng cần giữ nguyên.

---

### 2.2 P2 — Extended state label matrix ✅ Đúng

**Vấn đề hiện tại:**  
`computePartnerOverlayState()` chỉ đọc `partnerPersonalStatus` và `partnerAccumulatedPoints`, không đọc `walkStateLiveData` của current user. Khi current user đã FINISHED, label vẫn hiển thị `WAITING_FOR_PARTNER` — sai về mặt ngữ nghĩa.

**Matrix hiện tại (chưa đủ):**

| `partnerPersonalStatus` | `partnerAccumulatedPoints` | `PartnerOverlayState` |
|---|---|---|
| `PENDING` | any | `WAITING_FOR_PARTNER` |
| `ACTIVE` | empty | `WAITING_FOR_GPS` |
| `ACTIVE` | non-empty | `SHOWING_PATH` |
| `COMPLETED` / `NO_SHOW` | any | `PARTNER_COMPLETED` |

**Matrix đề xuất — bổ sung dimension current user state:**

| Current user | Partner | Label |
|---|---|---|
| `ACTIVE`/`PAUSED` | `PENDING` | "Partner hasn't arrived yet" |
| `ACTIVE`/`PAUSED` | `ACTIVE` (no GPS) | "Waiting for partner's location…" |
| `ACTIVE`/`PAUSED` | `ACTIVE` (has GPS) | *(label hidden / last updated)* |
| `ACTIVE`/`PAUSED` | `COMPLETED` | "Partner has finished" |
| `FINISHED` | `PENDING` | "You've completed. Waiting for partner…" |
| `FINISHED` | `ACTIVE` | "Partner is walking" |
| `FINISHED` | `COMPLETED` | "Both have finished" |
| `FINISHED` | `NO_SHOW` | "Partner didn't show up" |

**Đánh giá:** Hợp lý, phản ánh đúng ngữ nghĩa trạng thái của session.

---

### 2.3 P3 — Toast on partner status transition ✅ Đúng, với điều kiện

**Cần thiết không?** Có. Label tĩnh trên màn hình không đảm bảo user nhìn thấy khi partner đổi state (user có thể đang nhìn map). Toast làm tăng khả năng nhận biết sự kiện quan trọng.

**Quan trọng:** Toast chỉ nên hiện khi có **transition**, không phải mỗi lần poll. Nếu poll 8s/lần mà toast mỗi lần sẽ spam UI.

**Mapping transition → message:**

| Transition | Toast |
|---|---|
| `PENDING → ACTIVE` | "Partner has started walking" |
| `ACTIVE → COMPLETED` | "Partner has finished" |
| `PENDING → NO_SHOW` | "Partner didn't show up" |
| Poll fail sau khi đã có data | Không toast — chỉ update label |

---

### 2.4 P4 — `partnerNoticeMessage` pattern ✅ Đúng về kiến trúc, với 1 sửa nhỏ

**Pattern đề xuất:** Thêm nullable `partnerNoticeMessage` vào `TrackingUiState`. Activity show toast khi field != null, sau đó gọi `viewModel.consumePartnerNotice()` để clear.

**Đánh giá:** Pattern này nhất quán với `completionErrorLiveData` hiện đang dùng trong codebase (line 129, `TrackingViewModel.java`). Tránh được `UiEffect` / `SingleLiveEvent` phức tạp hơn, phù hợp với MVVM tinh gọn của project.

**Correction (từ secondary review):** `consumePartnerNotice()` phải gọi `rebuildUiState()` ngay sau khi clear field. Nếu không, nếu Activity bị re-render lại với state cũ trước khi timer tick tiếp theo chạy, notice chưa được clear sẽ được deliver lại → Toast hiện lần thứ hai. Gọi `rebuildUiState()` ngay lập tức đảm bảo state tiếp theo có `partnerNoticeMessage = null`.

---

## 3. Gap Analysis — Hiện trạng vs Cần làm

| File | Dòng | Vấn đề | Thay đổi cần thực hiện |
|---|---|---|---|
| `PartnerOverlayState.java` | enum | `NO_SHOW` bị gộp vào `PARTNER_COMPLETED` — sai ngữ nghĩa | Thêm enum value `PARTNER_NO_SHOW` |
| `TrackingViewModel.java` | 332 | `stopPartnerPolling()` trong `requestCompleteWalk()` | **Xóa** dòng này — chỉ stop GPS, không stop partner polling |
| `TrackingViewModel.java` | `computePartnerOverlayState()` | Không đọc current `WalkState`; `NO_SHOW` trả `PARTNER_COMPLETED` | Thêm `WalkState` parameter; trả `PARTNER_NO_SHOW` cho `NO_SHOW` |
| `TrackingViewModel.java` | *(mới)* | Không track partner status transition | Thêm `lastKnownPartnerStatus`, emit `pendingPartnerNotice` khi thay đổi |
| `TrackingViewModel.java` | *(mới)* | `consumePartnerNotice()` không rebuildUiState | Thêm method; gọi `rebuildUiState()` sau khi clear |
| `TrackingUiState.java` | constructor | Chưa có `partnerNoticeMessage` | Thêm field nullable `String partnerNoticeMessage` (parameter thứ 13) |
| `TrackingScreenActivity.java` | `renderState()` | Không check `partnerNoticeMessage` | Thêm Toast logic + gọi `consumePartnerNotice()` |
| `TrackingScreenActivity.java` | `updatePartnerStatusLabel()` | Chỉ nhận `PartnerOverlayState`, không nhận `WalkState`; thiếu `PARTNER_NO_SHOW` case | Thêm `WalkState` parameter; add case `PARTNER_NO_SHOW`; handle `PARTNER_COMPLETED` × FINISHED = "Both finished" |
| `strings.xml` | *(mới)* | Thiếu 4 strings cho FINISHED matrix và NO_SHOW | Thêm đủ 4 strings (see section 4.5) |

---

## 4. Specification thay đổi cụ thể

### 4.0 `PartnerOverlayState.java` — Thêm `PARTNER_NO_SHOW`

`NO_SHOW` không nên map vào `PARTNER_COMPLETED` vì Activity dùng tên enum để quyết định text hiển thị. Nếu gộp chung, `PARTNER_COMPLETED` sẽ hiện `"Partner has finished"` cho cả hai trường hợp hoàn thành và vắng mặt — sai về ngữ nghĩa.

```java
public enum PartnerOverlayState {
    WAITING_FOR_PARTNER,
    WAITING_FOR_GPS,
    SHOWING_PATH,
    PARTNER_COMPLETED,
    PARTNER_NO_SHOW   // ← mới
}
```

---

### 4.1 `TrackingViewModel.java`

**Change 1 — Xóa `stopPartnerPolling()` khỏi `requestCompleteWalk()`:**

```java
// BEFORE (line 330-334):
stopTimer();
stopPartnerPolling();  // ← XÓA dòng này
stopGpsService();
walkStateLiveData.setValue(WalkState.FINISHING);

// AFTER:
stopTimer();
stopGpsService();
walkStateLiveData.setValue(WalkState.FINISHING);
```

> `finishWalk()` giữ nguyên `stopPartnerPolling()` vì đó là emergency abort.

---

**Change 2 — Thêm transition tracking + notice:**

Thêm 2 fields vào `TrackingViewModel`:

```java
private String lastKnownPartnerStatus = "PENDING";
private String pendingPartnerNotice   = null;   // consumed by Activity after show
```

Cập nhật `addSource` observer cho `partnerResultLiveData` (hiện tại từ line 163):

```java
uiStateLiveData.addSource(partnerResultLiveData, result -> {
    if (result != null) {
        String newStatus = result.getPartnerStatus();
        // Detect transition and schedule one-time notice
        if (!newStatus.equals(lastKnownPartnerStatus)) {
            pendingPartnerNotice = mapPartnerStatusTransitionToNotice(
                    lastKnownPartnerStatus, newStatus);
            lastKnownPartnerStatus = newStatus;
        }
        partnerPersonalStatus = newStatus;
        if (!result.getNewPoints().isEmpty()) {
            partnerAccumulatedPoints.addAll(result.getNewPoints());
            lastFetchedPartnerChunkIndex = result.getLastChunkIndex();
            partnerLastChunkMs           = result.getLastChunkCreatedAtMs();
        }
    }
    rebuildUiState();
});
```

Thêm helper + consume method:

```java
/** Maps a partner status transition to a one-time UI notice string, or null if no notice needed. */
private static String mapPartnerStatusTransitionToNotice(String from, String to) {
    if ("PENDING".equals(from) && "ACTIVE".equals(to))     return "Partner has started walking";
    if ("ACTIVE".equals(from)  && "COMPLETED".equals(to))  return "Partner has finished";
    if ("PENDING".equals(from) && "NO_SHOW".equals(to))    return "Partner didn't show up";
    return null;
}

/**
 * Called by Activity after it has displayed the pending partner notice.
 * Calls rebuildUiState() immediately so the next delivered snapshot has
 * partnerNoticeMessage = null — prevents the Toast from re-appearing if the
 * Activity re-renders with a cached state before the timer tick runs.
 */
public void consumePartnerNotice() {
    pendingPartnerNotice = null;
    rebuildUiState();
}
```

---

**Change 3 — Cập nhật `computePartnerOverlayState()` để nhận WalkState và phân tách NO_SHOW:**

```java
private PartnerOverlayState computePartnerOverlayState(WalkState currentState) {
    if ("NO_SHOW".equals(partnerPersonalStatus)) {
        return PartnerOverlayState.PARTNER_NO_SHOW;       // tách riêng, không gộp COMPLETED
    }
    if ("COMPLETED".equals(partnerPersonalStatus)) {
        return PartnerOverlayState.PARTNER_COMPLETED;
    }
    if ("ACTIVE".equals(partnerPersonalStatus) && !partnerAccumulatedPoints.isEmpty()) {
        return PartnerOverlayState.SHOWING_PATH;
    }
    if ("ACTIVE".equals(partnerPersonalStatus)) {
        return PartnerOverlayState.WAITING_FOR_GPS;
    }
    return PartnerOverlayState.WAITING_FOR_PARTNER;
    // Note: WAITING_FOR_PARTNER covers both "current user active, partner PENDING"
    // and "current user FINISHED, partner PENDING". The 2D label is handled in
    // Activity's updatePartnerStatusLabel() by checking currentUserState there.
}
```

Cập nhật call site trong `rebuildUiState()`:

```java
PartnerOverlayState overlayState = computePartnerOverlayState(state);
```

---

**Change 4 — Cập nhật `rebuildUiState()` để include `pendingPartnerNotice`:**

```java
uiStateLiveData.setValue(new TrackingUiState(
        state,
        mapPoints,
        distanceKm,
        elapsedSeconds,
        paceMinPerKm,
        partnerName,
        cameraFollowing,
        completeTooEarlySeconds,
        isSaving,
        partnerSnapshot,
        overlayState,
        partnerLastUpdatedSecs,
        pendingPartnerNotice    // ← parameter mới thứ 13
));
```

---

### 4.2 `TrackingUiState.java`

Thêm field và parameter:

```java
/** One-time notice to show as Toast when partner status transitions. Null if no pending notice. */
private final String partnerNoticeMessage;

// Constructor: thêm parameter thứ 13
public TrackingUiState(
        ...,
        long partnerLastUpdatedSeconds,
        String partnerNoticeMessage) {   // ← mới
    ...
    this.partnerNoticeMessage = partnerNoticeMessage;
}

public String getPartnerNoticeMessage() { return partnerNoticeMessage; }
```

---

### 4.3 `TrackingScreenActivity.java`

**Change 1 — Handle `partnerNoticeMessage` trong `renderState()`:**

```java
private void renderState(TrackingUiState state) {
    updatePartnerHeader(state.getPartnerName());
    updateStats(state);
    updateControls(state);
    // ... map / polyline / camera ...
    updatePartnerStatusLabel(state.getPartnerOverlayState(),
            state.getPartnerLastUpdatedSeconds(),
            state.getWalkState());   // ← pass WalkState

    // One-time Toast for partner status transition
    String notice = state.getPartnerNoticeMessage();
    if (notice != null) {
        Toast.makeText(this, notice, Toast.LENGTH_SHORT).show();
        viewModel.consumePartnerNotice();
    }

    if (state.getWalkState() == WalkState.FINISHED && !finishDialogShown) { ... }
}
```

---

**Change 2 — Cập nhật `updatePartnerStatusLabel()` với 2D matrix:**

```java
private void updatePartnerStatusLabel(PartnerOverlayState overlayState,
                                       long lastUpdatedSecs,
                                       WalkState currentUserState) {
    if (txtPartnerStatus == null) return;
    boolean userFinished = (currentUserState == WalkState.FINISHED
            || currentUserState == WalkState.FINISHING);

    switch (overlayState) {
        case WAITING_FOR_PARTNER:
            txtPartnerStatus.setVisibility(View.VISIBLE);
            txtPartnerStatus.setText(userFinished
                    ? R.string.tracking_self_done_partner_pending
                    : R.string.tracking_partner_waiting);
            break;
        case WAITING_FOR_GPS:
            txtPartnerStatus.setVisibility(View.VISIBLE);
            txtPartnerStatus.setText(R.string.tracking_partner_waiting_gps);
            break;
        case SHOWING_PATH:
            if (userFinished) {
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText(R.string.tracking_self_done_partner_active);
            } else if (lastUpdatedSecs < 15) {
                txtPartnerStatus.setVisibility(View.GONE);
            } else if (lastUpdatedSecs < 60) {
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText(
                        getString(R.string.tracking_partner_last_updated, lastUpdatedSecs));
            } else {
                txtPartnerStatus.setVisibility(View.VISIBLE);
                txtPartnerStatus.setText(
                        getString(R.string.tracking_partner_disconnected, lastUpdatedSecs));
            }
            break;
        case PARTNER_COMPLETED:
            txtPartnerStatus.setVisibility(View.VISIBLE);
            // "Both have finished" when current user is also done; else "Partner has finished"
            txtPartnerStatus.setText(userFinished
                    ? R.string.tracking_both_finished
                    : R.string.tracking_partner_completed);
            break;
        case PARTNER_NO_SHOW:
            txtPartnerStatus.setVisibility(View.VISIBLE);
            txtPartnerStatus.setText(R.string.tracking_partner_no_show);
            break;
    }
}
```

---

### 4.5 `strings.xml` — 4 strings mới

```xml
<!-- Partner overlay — current user FINISHED dimension -->
<string name="tracking_self_done_partner_pending">Walk complete. Waiting for partner…</string>
<string name="tracking_self_done_partner_active">Partner is walking</string>
<string name="tracking_both_finished">Both have finished</string>
<string name="tracking_partner_no_show">Partner didn\'t show up</string>
```

> Strings `tracking_partner_waiting`, `tracking_partner_waiting_gps`, `tracking_partner_completed`, `tracking_partner_last_updated`, `tracking_partner_disconnected` đã có — không cần thêm.

---

### 4.4 `TrackingUiState.java` (unchanged numbering — nhưng file cần thêm enum case mới)

Không cần thay đổi gì thêm ngoài những gì đã spec ở trên. `PartnerOverlayState` được import sẵn; khi thêm `PARTNER_NO_SHOW` vào enum thì Activity switch tự cần thêm case — đã được xử lý ở section 4.3 Change 2.

---

## 5. Corrections từ secondary review

| # | Vấn đề trong report gốc | Correction đã áp dụng |
|---|---|---|
| C1 | `NO_SHOW` bị gộp vào `PARTNER_COMPLETED` — hiện nhầm "Partner has finished" | Thêm `PARTNER_NO_SHOW` vào enum; `computePartnerOverlayState()` trả `PARTNER_NO_SHOW` riêng |
| C2 | `strings.xml` spec chỉ có 2 string, matrix cần 4 | Thêm đủ `tracking_both_finished` + `tracking_partner_no_show` |
| C3 | `consumePartnerNotice()` không gọi `rebuildUiState()` — Toast có thể hiện lại | Method giờ gọi `rebuildUiState()` ngay sau clear |

**Non-blocking note (không bắt buộc trong scope này):** Khi `WalkState == FINISHING` (đang save), label hiện tương tự `FINISHED`. Có thể xử lý riêng `FINISHING` → `"Saving walk result…"` nhưng không ảnh hưởng đến correctness của feature này.

---

## 6. Tổng kết

| Proposal point | Verdict | Action |
|---|---|---|
| P1: Không stop partner polling khi complete | ✅ Đúng | Xóa `stopPartnerPolling()` tại `requestCompleteWalk():332` |
| P2: Extended label matrix (FINISHED dimension) | ✅ Đúng | Cập nhật `computePartnerOverlayState()` + `updatePartnerStatusLabel()` |
| P3: Toast khi partner status transition | ✅ Đúng | Thêm `lastKnownPartnerStatus` + `mapPartnerStatusTransitionToNotice()` |
| P4: `partnerNoticeMessage` pattern | ✅ Đúng (với correction C3) | Thêm field vào `TrackingUiState`; `consumePartnerNotice()` gọi `rebuildUiState()` |
| *(implicit)* `finishWalk()` vẫn stop polling | ✅ Giữ nguyên | Không thay đổi — đây là emergency abort |

**Tổng files cần thay đổi:** 5 (`PartnerOverlayState.java` + 4 files gốc)  
**Files mới:** 0  
**DB migration:** Không cần  

---

*Report đã được cập nhật sau secondary review. Implementation sẽ được thực hiện theo chỉ định riêng.*
