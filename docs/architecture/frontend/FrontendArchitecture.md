# WalkMate Frontend Architecture (MVVM-lite)

**Platform:** Android Native (Java)  
**Style:** MVVM Lightweight - Pragmatic & Simple  
**Date:** March 6, 2026

---

## 1. Overview

WalkMate mobile app có 3 phase chính:

1. **Coordination Phase** — User tạo WalkIntent, browse matches, xác nhận partner
2. **Lifecycle Phase** — Theo dõi WalkSession (PENDING → ACTIVE → COMPLETED)
3. **Rating Phase** — Đánh giá partner sau khi hoàn thành

**Core Rule:**  
Backend là **single source of truth** cho business state (`WalkSession.status`).  
Frontend chỉ hiển thị và trigger actions.

---

## 2. Package Structure

```
app/src/main/java/com/walkmate/

ui/                              # UI Layer (Activity + ViewModel)
    intent/
        IntentActivity.java      # Tạo walk intent
        IntentViewModel.java
    session/
        SessionActivity.java     # Theo dõi session lifecycle
        SessionViewModel.java
    rating/
        RatingActivity.java      # Đánh giá partner
        RatingViewModel.java

data/                            # Data Layer
    api/
        WalkMateApi.java         # Retrofit interface
    repository/
        IntentRepository.java    # Wrap API calls, return Result<T>
        SessionRepository.java
    model/
        WalkIntentDto.java       # DTO từ API
        WalkSessionDto.java

domain/                          # Domain Models
    model/
        WalkIntent.java          # Domain model (app dùng)
        WalkSession.java
        SessionStatus.java       # Enum: PENDING/ACTIVE/COMPLETED/NO_SHOW/CANCELLED
        Location.java
        Distance.java

common/                          # Utilities
    Result.java                  # Wrapper: Success<T> | Error
    UiState.java                 # Base UiState (optional, có thể để trong từng feature)
```

### Trách nhiệm từng layer

| Layer       | Trách nhiệm                                                 | Không làm                                  |
| ----------- | ----------------------------------------------------------- | ------------------------------------------ |
| **ui/**     | Hiển thị UiState, bắt user action, trigger ViewModel method | Không gọi API, không business logic        |
| **data/**   | Gọi API, parse DTO → Domain model, wrap result              | Không UI logic, không navigation           |
| **domain/** | Model đơn giản (data class), validation nhẹ (nếu cần)       | Không business rules phức tạp (backend lo) |
| **common/** | Utility classes (Result wrapper, base classes nếu cần)      | —                                          |

---

## 3. Data Flow Examples

### 3.1 Create Intent Flow

```
1. User fills form → clicks "Create Intent"
2. Activity: binding.btnCreate.setOnClickListener(() -> viewModel.createIntent(time, location))
3. ViewModel:
   - setState(Loading)
   - intentRepository.createIntent(...)
4. Repository:
   - api.createIntent(dto)
   - map response → WalkIntent
   - return Result.Success(intent) hoặc Result.Error(error)
5. ViewModel:
   - onSuccess: setState(Content(intent))
   - onError: setState(Error(message))
6. Activity:
   - observe(uiState) → render(state)
   - if Content: navigate to Match screen
   - if Error: show Snackbar + retry button
```

**Mã mẫu:**

```java
// ViewModel
public void createIntent(LocalDateTime time, Location location) {
    uiState.setValue(UiState.loading());

    intentRepository.createIntent(time, location, result -> {
        if (result instanceof Result.Success) {
            uiState.setValue(UiState.content(result.data));
            // Navigate được trigger từ Activity (observe state change)
        } else {
            uiState.setValue(UiState.error(result.error.getMessage()));
        }
    });
}

// Activity
viewModel.getUiState().observe(this, this::render);

private void render(UiState state) {
    if (state instanceof UiState.Loading) {
        showLoading();
    } else if (state instanceof UiState.Content) {
        hideLoading();
        navigateToMatchScreen();
    } else if (state instanceof UiState.Error) {
        hideLoading();
        showError(state.getMessage());
    }
}
```

---

### 3.2 Activate Session Flow

```
1. User clicks "Start Walk" (khi trong activation window)
2. Activity: viewModel.activateSession()
3. ViewModel:
   - Check current state = Content(session with PENDING status)
   - Guard: validate activation window (-15min to +30min)
   - setState(Loading)
   - sessionRepository.activateSession(sessionId)
4. Repository:
   - api.activateSession(sessionId)
   - Backend validates & transitions PENDING → ACTIVE
   - map response → WalkSession
   - return Result.Success(session)
5. ViewModel:
   - setState(Content(session)) // session.status = ACTIVE
6. Activity:
   - render(Content): show active walk UI (map tracking, timer, distance)
```

**Key point:** UI **không tự chuyển** status. Chỉ hiển thị status từ backend response.

---

## 4. State Handling (UiState)

### 4.1 Minimal UiState

Mỗi screen có **4 state cơ bản**:

```java
public abstract class UiState<T> {

    public static class Idle<T> extends UiState<T> {}

    public static class Loading<T> extends UiState<T> {}

    public static class Content<T> extends UiState<T> {
        public final T data;
        public Content(T data) { this.data = data; }
    }

    public static class Error<T> extends UiState<T> {
        public final String message;
        public Error(String message) { this.message = message; }
    }

    // Factory methods
    public static <T> UiState<T> idle() { return new Idle<>(); }
    public static <T> UiState<T> loading() { return new Loading<>(); }
    public static <T> UiState<T> content(T data) { return new Content<>(data); }
    public static <T> UiState<T> error(String message) { return new Error<>(message); }
}
```

**Ví dụ cụ thể:**

```java
// SessionViewModel
private MutableLiveData<UiState<WalkSession>> uiState = new MutableLiveData<>(UiState.idle());

public void loadSession(UUID sessionId) {
    uiState.setValue(UiState.loading());

    sessionRepository.getSession(sessionId, result -> {
        if (result instanceof Result.Success) {
            uiState.setValue(UiState.content(result.data));
        } else {
            uiState.setValue(UiState.error(result.error.getMessage()));
        }
    });
}

public void activateSession() {
    UiState<WalkSession> current = uiState.getValue();
    if (!(current instanceof UiState.Content)) return; // Guard

    WalkSession session = ((UiState.Content<WalkSession>) current).data;
    if (session.getStatus() != SessionStatus.PENDING) return; // Guard

    uiState.setValue(UiState.loading());

    sessionRepository.activateSession(session.getId(), result -> {
        if (result instanceof Result.Success) {
            uiState.setValue(UiState.content(result.data)); // data.status = ACTIVE
        } else {
            uiState.setValue(UiState.error(result.error.getMessage()));
        }
    });
}
```

---

### 4.2 Activity Render Pattern

**Rule:** Activity chỉ observe + render. Không gọi API trực tiếp.

```java
public class SessionActivity extends AppCompatActivity {

    private ActivitySessionBinding binding;
    private SessionViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySessionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(SessionViewModel.class);

        // Observe UiState
        viewModel.getUiState().observe(this, this::render);

        // Bind user actions
        binding.btnStartWalk.setOnClickListener(v -> viewModel.activateSession());
        binding.btnComplete.setOnClickListener(v -> viewModel.completeSession());
        binding.btnRetry.setOnClickListener(v -> viewModel.loadSession(sessionId));

        // Load initial data
        UUID sessionId = UUID.fromString(getIntent().getStringExtra("SESSION_ID"));
        viewModel.loadSession(sessionId);
    }

    private void render(UiState<WalkSession> state) {
        if (state instanceof UiState.Idle) {
            // Initial state, do nothing
        } else if (state instanceof UiState.Loading) {
            binding.progressBar.setVisibility(View.VISIBLE);
            binding.contentContainer.setVisibility(View.GONE);
            binding.errorContainer.setVisibility(View.GONE);
        } else if (state instanceof UiState.Content) {
            binding.progressBar.setVisibility(View.GONE);
            binding.contentContainer.setVisibility(View.VISIBLE);
            binding.errorContainer.setVisibility(View.GONE);

            WalkSession session = ((UiState.Content<WalkSession>) state).data;
            renderContent(session);
        } else if (state instanceof UiState.Error) {
            binding.progressBar.setVisibility(View.GONE);
            binding.contentContainer.setVisibility(View.GONE);
            binding.errorContainer.setVisibility(View.VISIBLE);
            binding.tvError.setText(((UiState.Error<WalkSession>) state).message);
        }
    }

    private void renderContent(WalkSession session) {
        binding.tvSessionId.setText(session.getId().toString());
        binding.tvScheduledTime.setText(formatTime(session.getScheduledStartTime()));

        // Hiển thị UI dựa trên backend status
        switch (session.getStatus()) {
            case PENDING:
                binding.btnStartWalk.setVisibility(View.VISIBLE);
                binding.btnComplete.setVisibility(View.GONE);
                binding.activeWalkContainer.setVisibility(View.GONE);
                break;
            case ACTIVE:
                binding.btnStartWalk.setVisibility(View.GONE);
                binding.btnComplete.setVisibility(View.VISIBLE);
                binding.activeWalkContainer.setVisibility(View.VISIBLE);
                startLocationTracking(session);
                break;
            case COMPLETED:
                binding.btnStartWalk.setVisibility(View.GONE);
                binding.btnComplete.setVisibility(View.GONE);
                showCompletionSummary(session);
                navigateToRating(session.getId());
                break;
            case NO_SHOW:
            case CANCELLED:
                showTerminalState(session.getStatus());
                break;
        }
    }
}
```

---

## 5. Networking & Error Handling

### 5.1 Result Wrapper

```java
public abstract class Result<T> {

    public static class Success<T> extends Result<T> {
        public final T data;
        public Success(T data) { this.data = data; }
    }

    public static class Error<T> extends Result<T> {
        public final ApiError error;
        public Error(ApiError error) { this.error = error; }
    }
}

public class ApiError {
    private final int httpStatus;
    private final String errorCode;
    private final String message;

    public ApiError(int status, String code, String message) {
        this.httpStatus = status;
        this.errorCode = code;
        this.message = message;
    }

    public String getMessage() { return message; }
}
```

---

### 5.2 Repository Pattern

```java
public class SessionRepository {

    private final WalkMateApi api;

    public SessionRepository(WalkMateApi api) {
        this.api = api;
    }

    public void getSession(UUID sessionId, Callback<Result<WalkSession>> callback) {
        api.getSession(sessionId).enqueue(new retrofit2.Callback<WalkSessionDto>() {
            @Override
            public void onResponse(Call<WalkSessionDto> call, Response<WalkSessionDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WalkSession session = SessionMapper.toDomain(response.body());
                    callback.onResult(new Result.Success<>(session));
                } else {
                    ApiError error = parseError(response);
                    callback.onResult(new Result.Error<>(error));
                }
            }

            @Override
            public void onFailure(Call<WalkSessionDto> call, Throwable t) {
                ApiError error = new ApiError(0, "NETWORK_ERROR", t.getMessage());
                callback.onResult(new Result.Error<>(error));
            }
        });
    }

    public void activateSession(UUID sessionId, Callback<Result<WalkSession>> callback) {
        api.activateSession(sessionId).enqueue(new retrofit2.Callback<WalkSessionDto>() {
            @Override
            public void onResponse(Call<WalkSessionDto> call, Response<WalkSessionDto> response) {
                if (response.isSuccessful() && response.body() != null) {
                    WalkSession session = SessionMapper.toDomain(response.body());
                    callback.onResult(new Result.Success<>(session));
                } else {
                    ApiError error = parseError(response);
                    callback.onResult(new Result.Error<>(error));
                }
            }

            @Override
            public void onFailure(Call<WalkSessionDto> call, Throwable t) {
                callback.onResult(new Result.Error<>(new ApiError(0, "NETWORK_ERROR", t.getMessage())));
            }
        });
    }

    private ApiError parseError(Response<?> response) {
        // Parse error body nếu có
        return new ApiError(response.code(), "API_ERROR", "Request failed");
    }
}

// Callback interface
public interface Callback<T> {
    void onResult(T result);
}
```

---

### 5.3 API Interface (Retrofit)

```java
public interface WalkMateApi {

    @GET("api/v1/sessions/{id}")
    Call<WalkSessionDto> getSession(@Path("id") UUID sessionId);

    @POST("api/v1/sessions/{id}/activate")
    Call<WalkSessionDto> activateSession(@Path("id") UUID sessionId);

    @POST("api/v1/sessions/{id}/complete")
    Call<WalkSessionDto> completeSession(@Path("id") UUID sessionId);

    @POST("api/v1/intents")
    Call<WalkIntentDto> createIntent(@Body CreateIntentRequest request);
}
```

---

### 5.4 Retry Pattern

**Rule:** Retry button gọi lại action trong ViewModel. **Không** nhét lambda vào state.

```java
// Activity
binding.btnRetry.setOnClickListener(v -> {
    // Retry last action (ViewModel tự nhớ context)
    viewModel.loadSession(sessionId);
});

// ViewModel
public void loadSession(UUID sessionId) {
    this.currentSessionId = sessionId; // Remember for retry
    uiState.setValue(UiState.loading());
    sessionRepository.getSession(sessionId, this::handleSessionResult);
}

private void handleSessionResult(Result<WalkSession> result) {
    if (result instanceof Result.Success) {
        uiState.setValue(UiState.content(((Result.Success<WalkSession>) result).data));
    } else {
        uiState.setValue(UiState.error(((Result.Error<WalkSession>) result).error.getMessage()));
    }
}
```

---

## 6. Authentication (JWT from Supabase)

### 6.1 Store Token

```java
public class AuthTokenProvider {

    private final SharedPreferences prefs;
    private static final String KEY_JWT = "jwt_token";

    public void saveToken(String jwt) {
        prefs.edit().putString(KEY_JWT, jwt).apply();
    }

    public String getToken() {
        return prefs.getString(KEY_JWT, null);
    }

    public void clearToken() {
        prefs.edit().remove(KEY_JWT).apply();
    }
}
```

---

### 6.2 Attach JWT to Requests (Interceptor)

```java
public class AuthInterceptor implements Interceptor {

    private final AuthTokenProvider tokenProvider;

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String token = tokenProvider.getToken();

        if (token == null) {
            return chain.proceed(original); // No auth for public endpoints
        }

        Request authenticated = original.newBuilder()
            .header("Authorization", "Bearer " + token)
            .build();

        return chain.proceed(authenticated);
    }
}

// Setup Retrofit
OkHttpClient client = new OkHttpClient.Builder()
    .addInterceptor(new AuthInterceptor(tokenProvider))
    .build();

Retrofit retrofit = new Retrofit.Builder()
    .baseUrl("https://your-backend.com/")
    .client(client)
    .addConverterFactory(GsonConverterFactory.create())
    .build();
```

---

## 7. Location Tracking (Session ACTIVE)

### 7.1 Simple LocationManager Usage

```java
// SessionViewModel
public void startLocationTracking() {
    // Trigger LocationService hoặc LocationManager
}

// SessionActivity (khi status = ACTIVE)
private void startLocationTracking(WalkSession session) {
    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
        return;
    }

    LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    locationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER,
        5000, // 5 seconds
        10,   // 10 meters
        new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                updateDistance(location);
                updateMap(location);
            }
        }
    );
}

private void updateDistance(Location location) {
    // Calculate distance from last point
    // Update UI counter
}
```

**Note:** Không viết service architecture chi tiết. Chỉ mention "có thể dùng ForegroundService nếu cần tracking khi app background".

---

## 8. What We Intentionally Do NOT Do

Để giữ frontend đơn giản, chúng ta **tránh**:

| Pattern/Practice               | Why We Skip It                                                  |
| ------------------------------ | --------------------------------------------------------------- |
| MVI (full-blown)               | Quá phức tạp cho app đơn giản; MVVM + UiState là đủ             |
| Global Store/Redux             | Không cần shared state phức tạp; mỗi screen độc lập             |
| Event Bus                      | Gây coupling; dùng Activity Result API nếu cần data giữa screen |
| Multi-module Gradle            | Overkill cho team nhỏ; single module đủ                         |
| Navigation Component           | Optional; dùng startActivity đơn giản cũng ok                   |
| Clean Architecture (5+ layers) | Quá nhiều abstraction; 3 layers (ui/data/domain) là đủ          |
| StateFlow/SharedFlow           | LiveData đủ dùng; không cần reactive stream phức tạp            |
| Detailed State Machines        | Backend lo business state; frontend chỉ cần 4 UI states         |

**Philosophy:** Dùng đủ pattern để code maintainable, nhưng không over-engineer.

---

## 9. Configuration (Build Variants)

```gradle
// build.gradle (app)
android {
    buildTypes {
        debug {
            buildConfigField "String", "API_BASE_URL", "\"https://dev-api.walkmate.com/\""
        }
        release {
            buildConfigField "String", "API_BASE_URL", "\"https://api.walkmate.com/\""
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

// Usage
Retrofit retrofit = new Retrofit.Builder()
    .baseUrl(BuildConfig.API_BASE_URL)
    .build();
```

**Secrets:** Đừng commit vào Git. Dùng `local.properties` (gitignored):

```properties
# local.properties
SUPABASE_ANON_KEY=your-key
GOOGLE_MAPS_API_KEY=your-key
```

```gradle
// build.gradle
def localProperties = new Properties()
localProperties.load(new FileInputStream(rootProject.file("local.properties")))

android {
    defaultConfig {
        manifestPlaceholders = [googleMapsApiKey: localProperties['GOOGLE_MAPS_API_KEY']]
    }
}
```

---

## 10. Summary

### Core Principles

1. **Backend = Source of Truth** — UI hiển thị backend state, không tự suy diễn
2. **Single UiState per Screen** — Idle/Loading/Content/Error
3. **ViewModel orchestrates** — Activity chỉ observe + render
4. **Repository wraps API** — Return Result<T>, map DTO → Domain
5. **Minimal layers** — ui/data/domain, không thêm tầng nặng

### Checklist khi thêm feature

- [ ] Xác định screen + API cần gọi
- [ ] Define UiState (4 states cơ bản)
- [ ] Add API method trong `WalkMateApi.java`
- [ ] Add Repository method (return Result)
- [ ] Write ViewModel action (Loading → repo → Content/Error)
- [ ] Write Activity render + bind clicks
- [ ] Test: ViewModel state transitions + Repository mapping

---

**END OF DOCUMENT**
