# WalkSession Implementation Proposal

Tai lieu nay la de xuat implement day du de build tinh nang WalkSession theo huong MVVM-lite, dong bo contract backend hien tai, va co the code ngay theo tung buoc.

## 1. Muc tieu

- Build man hinh WalkSession chay duoc end-to-end:
  - Activate session
  - Start GPS tracking qua Foreground Service
  - Hien thi route realtime tren map ma khong recreate layer
  - Batch append points
  - Complete, Abort, Cancel
- UI state dung 1 enum state chinh (khong dung nhieu boolean xung dot).
- Frontend API contract khop backend ApiResponse.

## 2. Scope va Out of Scope

Scope:

- Android frontend implementation cho WalkSession.
- Goi dung cac endpoint backend da co.
- Tracking bang Foreground Service.
- Repository map dung thanh cong va loi theo backend response.

Out of scope:

- Room local cache
- Auth interceptor
- Advanced retry policy
- Background tracking policy mo rong
- Refactor kien truc lon ngoai MVVM-lite

## 3. API contract chuan backend

Backend response dang dung:

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorDetails error,
    String timestamp)
```

Frontend phai parse theo dung schema tren.

### 3.1 DTO can co

```java
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

### 3.2 Nguyen tac map ket qua API

- HTTP fail hoac body null -> Result.error
- body.success = true va body.data != null -> Result.success
- body.success = false -> Result.error voi message tu body.error.message

## 4. Endpoint frontend su dung

- POST /api/v1/sessions/{id}/activate
- POST /api/v1/sessions/{id}/points:append
- POST /api/v1/sessions/{id}/complete
- POST /api/v1/sessions/{id}/abort
- POST /api/v1/sessions/{id}/cancel

## 5. Cau truc package de implement

```text
com.walkmate
├── core
│   ├── common
│   │   ├── Result.java
│   │   └── ResultCallback.java
│   └── tracking
│       ├── TrackingCommand.java
│       ├── TrackingServiceContract.java
│       ├── TrackingMath.java
│       └── LocationTrackingService.java
├── data
│   ├── model
│   │   ├── ApiResponseDto.java
│   │   ├── SessionResponseDto.java
│   │   ├── SessionTrackingResponseDto.java
│   │   ├── AppendPointItemDto.java
│   │   ├── AppendSessionPointsRequestDto.java
│   │   └── CompleteSessionRequestDto.java
│   ├── remote
│   │   ├── SessionApi.java
│   │   └── ApiClient.java
│   └── repository
│       └── SessionRepositoryImpl.java
├── domain
│   ├── model
│   │   ├── RoutePoint.java
│   │   └── SessionModel.java
│   └── repository
│       └── SessionRepository.java
└── ui
    └── session
        ├── SessionScreenStatus.java
        ├── SessionUiState.java
        ├── SessionViewModel.java
        └── SessionActivity.java
```

## 6. State machine cho UI

### 6.1 Enum state chinh

```java
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

### 6.2 Transition

- IDLE -> ACTIVATING -> TRACKING khi activate thanh cong
- TRACKING -> PAUSED khi pause
- PAUSED -> TRACKING khi resume
- TRACKING or PAUSED -> COMPLETING -> COMPLETED khi complete thanh cong
- Bat ky state nao gap loi network or backend -> ERROR

### 6.3 SessionUiState

State data goi y:

- status: SessionScreenStatus
- route: List<RoutePoint>
- distanceMeters: double
- durationSeconds: long
- errorMessage: String
- pendingCommand: TrackingCommand
- commandVersion: long

Ly do giu pendingCommand va commandVersion:

- Activity can trigger side-effect he thong den Service 1 lan theo event.

## 7. Tracking Service contract

TrackingCommand:

- NONE
- START
- PAUSE
- RESUME
- STOP

TrackingServiceContract actions:

- ACTION_START
- ACTION_PAUSE
- ACTION_RESUME
- ACTION_STOP
- ACTION_POINT

Extras:

- EXTRA_SESSION_ID
- EXTRA_POINT_ORDER
- EXTRA_LAT
- EXTRA_LNG
- EXTRA_TIME

## 8. Luong nghiep vu end-to-end

### 8.1 Start walk

1. User bam Start
2. Activity goi viewModel.activateSession()
3. ViewModel set status ACTIVATING
4. Repository call activate API
5. Success -> ViewModel set status TRACKING + pendingCommand START
6. Activity thay command moi -> start Foreground Service
7. Service phat GPS points qua broadcast
8. Activity nhan point -> viewModel.onTrackingPoint(point)
9. ViewModel update route, distance, duration
10. Activity render map polyline bang setPoints

### 8.2 Pause and Resume

Pause:

- ViewModel set status PAUSED + pendingCommand PAUSE
- Activity gui action pause cho service

Resume:

- ViewModel set status TRACKING + pendingCommand RESUME
- Activity gui action resume cho service

### 8.3 End walk

1. User bam End
2. ViewModel set status COMPLETING
3. Flush appendBuffer cuoi qua points:append
4. Goi complete API
5. Success -> status COMPLETED + pendingCommand STOP
6. Activity gui action stop service

### 8.4 Abort and Cancel

- ViewModel goi API abort or cancel
- Success -> status COMPLETED + pendingCommand STOP
- Fail -> status ERROR

## 9. Yeu cau map rendering

- Khoi tao SupportMapFragment 1 lan trong onCreate
- Giu 1 GoogleMap instance
- Giu 1 Polyline instance
- Moi lan state route thay doi:
  - Neu polyline chua co -> addPolyline
  - Neu da co -> polyline.setPoints(newPoints)
- Khong recreate fragment, khong recreate map

## 10. SessionViewModel implementation checklist

- Bien noi bo:
  - route list
  - appendBuffer list
  - distanceMeters
  - durationSeconds
  - lastPoint
  - commandVersion
  - sessionId
- Ham chinh:
  - bindSession
  - activateSession
  - pauseWalk
  - resumeWalk
  - onTrackingPoint
  - completeSession
  - abortSession
  - cancelSession
  - flushAppendBuffer
  - flushAppendBufferThenComplete
  - callCompleteNow
- Batch append rule:
  - APPEND_BATCH_SIZE = 20

## 11. SessionRepositoryImpl implementation checklist

- Moi method API can:
  - Check response.isSuccessful
  - Check body khac null
  - Check body.success
  - Check body.data neu can
  - Map error message tu body.error.message
- Khong nuot loi silent
- Tra Result.success or Result.error ro rang

## 12. SessionActivity implementation checklist

- onCreate:
  - Bind views
  - Khoi tao api, repository, viewModel
  - Bind sessionId
  - Setup map fragment async
  - Setup button listener
  - Observe state
- onStart:
  - registerReceiver cho ACTION_POINT
- onStop:
  - unregisterReceiver
- render:
  - Hien thi button theo status
  - update distance and duration
  - show error toast neu co
  - renderRoute
  - executeTrackingCommand
- permission:
  - check ACCESS_FINE_LOCATION
  - check FOREGROUND_SERVICE_LOCATION voi Android 14+

## 13. Foreground service implementation checklist

- onCreate:
  - setup fused location client
  - setup location callback
  - create notification channel
- onStartCommand:
  - ACTION_START: reset state + startForeground + startLocationUpdates
  - ACTION_PAUSE: isPaused = true
  - ACTION_RESUME: isPaused = false
  - ACTION_STOP: stop updates + stop foreground + stop self
- accept location rule:
  - bo diem accuracy kem
  - bo diem qua gan de giam noise
- emit point:
  - sendBroadcast ACTION_POINT kem order, lat, lng, time

## 14. Ke hoach implement theo buoc

Buoc 1: Tao model va contract

- Result, RoutePoint, SessionModel, SessionRepository interface
- ApiResponseDto va cac DTO request/response

Buoc 2: Tao Retrofit layer

- SessionApi
- ApiClient
- SessionRepositoryImpl

Buoc 3: Tao tracking layer

- TrackingCommand
- TrackingServiceContract
- TrackingMath
- LocationTrackingService

Buoc 4: Tao UI state layer

- SessionScreenStatus
- SessionUiState
- SessionViewModel

Buoc 5: Tao Activity va XML

- SessionActivity
- activity_session.xml
- wiring map, permission, render, command execution

Buoc 6: Verify end-to-end

- Test Start, Pause, Resume, End
- Test Abort, Cancel
- Test error mapping

## 15. Acceptance criteria

- Start thanh cong thi service duoc start va map ve duong realtime.
- Pause khong them point moi vao route.
- Resume tiep tuc them point vao cung route cu.
- End se flush batch cuoi roi complete session.
- Complete thanh cong se stop service.
- API error hien thi message backend neu co.
- Khong ton tai state xung dot do dung enum status.

## 16. Risk canh bao va cach giam

- Risk: Activity mat do lifecycle -> receiver leak.
  - Giam: register onStart, unregister onStop.
- Risk: command bi execute lap do render nhieu lan.
  - Giam: commandVersion de deduplicate side-effect.
- Risk: API tra success false nhung HTTP 200.
  - Giam: Repository bat buoc check body.success.

## 17. Deliverable list

- Source code cac class theo package structure muc 5
- AndroidManifest update permission va service
- Layout activity_session.xml
- Tai lieu state transition ngan gon cho team

Sau khi hoan tat cac deliverable tren, WalkSession co the build va chay duoc theo dung flow nghiep vu hien tai.
