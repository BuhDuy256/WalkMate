# Session Frontend Proposal (Resolve Conflicts)

Muc tieu cua tai lieu nay:

- Giai quyet 2 mau thuan chinh trong de xuat hien tai.
- Khong mo rong scope production-level.
- Giu dung MVVM-lite: Activity render + delegate, ViewModel giu state, Repository call API.

## 1. Cac mau thuan can xu ly

1. State UI dang dung nhieu boolean roi rac (`isLoading`, `isTracking`, `isPaused`, `isCompleted`) de gay to hop state vo nghia.
2. `ApiResponseDto` trong frontend de xuat khong khop backend thuc te.

Backend hien tai tra ve:

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorDetails error,
    String timestamp)
```

Frontend de xuat cu lai la:

```java
public class ApiResponseDto<T> {
    public T data;
    public String message;
}
```

=> Khong dong bo contract.

## 2. Decision chinh

1. Thay bo boolean state bang 1 enum state chinh.
2. Sua `ApiResponseDto` theo dung contract backend (`success`, `data`, `error`, `timestamp`).
3. Repository doc `success` va `error.message` de map sang `Result.error(...)`.
4. Khong them cac hang muc production-level (Room, interceptor, retry policy, map controller tach rieng, background location, ...).

## 3. UI state theo enum

## 3.1 SessionScreenStatus

```java
package com.walkmate.ui.session;

public enum SessionScreenStatus {
    IDLE,
    ACTIVATING,
    TRACKING,
    PAUSED,
    COMPLETING,
    COMPLETED,
    ERROR
}
```

## 3.2 SessionUiState

```java
package com.walkmate.ui.session;

import com.walkmate.core.tracking.TrackingCommand;
import com.walkmate.domain.model.RoutePoint;

import java.util.ArrayList;
import java.util.List;

public class SessionUiState {
    public final SessionScreenStatus status;
    public final List<RoutePoint> route;
    public final double distanceMeters;
    public final long durationSeconds;
    public final String errorMessage;
    public final TrackingCommand pendingCommand;
    public final long commandVersion;

    public SessionUiState(
            SessionScreenStatus status,
            List<RoutePoint> route,
            double distanceMeters,
            long durationSeconds,
            String errorMessage,
            TrackingCommand pendingCommand,
            long commandVersion
    ) {
        this.status = status;
        this.route = route != null ? route : new ArrayList<>();
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
        this.errorMessage = errorMessage;
        this.pendingCommand = pendingCommand;
        this.commandVersion = commandVersion;
    }

    public static SessionUiState idle() {
        return new SessionUiState(
                SessionScreenStatus.IDLE,
                new ArrayList<>(),
                0.0,
                0L,
                null,
                TrackingCommand.NONE,
                0L
        );
    }
}
```

## 3.3 Render theo status (khong theo boolean)

```java
private void render(SessionUiState state) {
    switch (state.status) {
        case IDLE:
            btnStart.setVisibility(View.VISIBLE);
            btnPause.setVisibility(View.GONE);
            btnResume.setVisibility(View.GONE);
            btnEnd.setVisibility(View.GONE);
            break;

        case ACTIVATING:
        case COMPLETING:
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.GONE);
            btnResume.setVisibility(View.GONE);
            btnEnd.setVisibility(View.GONE);
            break;

        case TRACKING:
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.VISIBLE);
            btnResume.setVisibility(View.GONE);
            btnEnd.setVisibility(View.VISIBLE);
            break;

        case PAUSED:
            btnStart.setVisibility(View.GONE);
            btnPause.setVisibility(View.GONE);
            btnResume.setVisibility(View.VISIBLE);
            btnEnd.setVisibility(View.VISIBLE);
            break;

        case COMPLETED:
        case ERROR:
            btnStart.setVisibility(View.VISIBLE);
            btnPause.setVisibility(View.GONE);
            btnResume.setVisibility(View.GONE);
            btnEnd.setVisibility(View.GONE);
            break;
    }

    tvDistance.setText(String.format("%.1f m", state.distanceMeters));
    tvDuration.setText(state.durationSeconds + " s");

    if (state.errorMessage != null) {
        Toast.makeText(this, state.errorMessage, Toast.LENGTH_SHORT).show();
    }

    renderRoute(state.route);
    executeTrackingCommand(state);
}
```

## 4. API response dong bo backend

## 4.1 ApiResponseDto moi

```java
package com.walkmate.data.model;

public class ApiResponseDto<T> {
    public boolean success;
    public T data;
    public ErrorDto error;
    public String timestamp;

    public static class ErrorDto {
        public String code;
        public String message;
    }
}
```

## 4.2 Nguyen tac xu ly trong Repository

Voi moi API call:

1. Neu HTTP fail hoac body null => `Result.error(...)`.
2. Neu `body.success == true` va `body.data != null` => `Result.success(...)`.
3. Neu `body.success == false` => lay `body.error.message` (neu co), map thanh `Result.error(new IllegalStateException(message))`.

Vi du helper:

```java
private String backendErrorMessage(ApiResponseDto<?> body) {
    if (body != null && body.error != null && body.error.message != null) {
        return body.error.message;
    }
    return "Unknown backend error";
}
```

## 5. Transition de xuat cho ViewModel

Bang transition ngan gon:

1. `IDLE -> ACTIVATING -> TRACKING` khi activate success.
2. `TRACKING -> PAUSED` khi pause.
3. `PAUSED -> TRACKING` khi resume.
4. `TRACKING|PAUSED -> COMPLETING -> COMPLETED` khi complete success.
5. Bat ky buoc nao loi => `ERROR` (giu route/distance/duration hien tai).

Luu y: `pendingCommand` va `commandVersion` van duoc giu de trigger side-effect cho Service, nhung state man hinh chi con 1 nguon su that la `status`.

## 6. Scope co y khong lam trong proposal nay

Khong dua vao file nay cac de xuat production-level trong Design.md:

- Khong them Room cache.
- Khong them auth interceptor.
- Khong them camera smoothing, debounce toast, background-location policy.
- Khong tach them layer phuc tap (MapController rieng, etc.).

## 7. Ket qua mong doi

Sau khi ap dung proposal:

1. UI state ro rang, khong con xung dot boolean.
2. Frontend parse response dung contract backend hien tai.
3. Code van giu dung pham vi skeleton MVVM-lite ban dau, de implement nhanh va it sai lech.
