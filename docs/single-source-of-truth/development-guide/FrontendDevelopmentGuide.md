# Frontend Engineering Standards & Workflow (Android MVVM-lite Edition)

This document defines the strict engineering standards, architectural patterns, and development workflows for our Android application. It serves as the single source of truth for all frontend development, ensuring consistency, maintainability, and production readiness across the team.

**Tech Stack context:** Android Native (Java), MVVM-lite (pragmatic), Retrofit, LiveData + UiState, Repository pattern returning `Result<T>`. "Backend as Single Source of Truth".

---

## 1. Project Structure & Architecture

We adhere to a pragmatic **MVVM-lite** architecture.

### 1.1 Folder Tree

```text
com.walkmate
├── core                  # Base classes, Utilities, Globals
│   ├── common            # Result wrappers, Base UI classes
│   └── util              # Date formatters, Validators, SharedPreferences helpers
├── data                  # Data sources and mapping
│   ├── local             # Room / SharedPreferences implementation (if any)
│   ├── model             # DTOs (Data Transfer Objects for Network)
│   ├── remote            # Retrofit interfaces, interceptors
│   └── repository        # Implementations of Domain repositories
├── domain                # Core business models
│   ├── model             # UI-agnostic Domain models
│   └── repository        # Interfaces for repositories
└── ui                    # Presentation Layer (Activities, ViewModels, States)
    ├── intent
    ├── main
    ├── rating
    └── session
```

### 1.2 Layer Diagram & Data Flow

```text
User Action (Click/Input)
       │
       ▼
[ UI Layer - Activity/Fragment ]
  Captures event, calls ViewModel. Re-renders on State change.
       │
       ▼
[ Presentation Layer - ViewModel ]
  Triggers business logic, manages UiState.
       │
       ▼
[ Domain Layer - Repository Interface & Models ]
  Defines contracts and pure models.
       │
       ▼
[ Data Layer - Repository Impl, API, DTOs ]
  Fetches from Retrofit, maps DTO to Domain Model, returns Result<T>.
```

### 1.3 Separation of Responsibilities & Strict Rules
- **UI (Activity)**: *Dumb renderer.* Only interacts with the ViewModel. Never calls Retrofit, Never modifies data directly.
- **ViewModel**: *State Manager.* Coordinates between UI and Repository. Maps `Result<T>` to `UiState`. Holds no Android `Context`.
- **Domain**: *Pure Models & Definitions.* Java only. No Android dependencies. No serialization annotations.
- **Data (Repository & Network)**: *Data Fetcher & Mapper.* Converts JSON DTOs to Domain Models. Handles API errors. Only layer that knows about Retrofit.

---

## 2. Folder-by-Folder Standards

### 2.1 UI Layer (`ui/...`)

#### Activity / Fragment
- **Purpose**: Render the `UiState` to the screen and route user inputs to the ViewModel.
- **Rules**: Must observe a single `UiState`. Navigation must happen by observing state or distinct navigation events.
- **DO/DON'T**:
  - **DO**: Delegate logic to ViewModel immediately.
  - **DON'T**: Call a Repository. Access SharedPreferences directly. Store business state in variables.

#### ViewModel
- **Purpose**: Expose state to the UI and handle UI actions.
- **Rules**: Expose a single `LiveData<UiState>`. No references to Views.
- **DO/DON'T**:
  - **DO**: Launch coroutines/threads to call Repositories.
  - **DON'T**: Pass `Context` to ViewModel (use `AndroidViewModel` strictly if needed, but prefer avoiding).

#### UiState
- **Purpose**: Represent the entire state of a screen at any given moment.

---

### 2.2 Data Layer (`data/...`)

#### Repository (`data/repository`)
- **Purpose**: Abstract data sources (Network/Local) from the ViewModel.
- **Rules**: Must implement a Domain interface. Must return `Result<DomainModel>`.
- **DO/DON'T**:
  - **DO**: Map backend DTOs to Domain Models before returning.
  - **DON'T**: Leak Retrofit `Call` or `Response` objects to the ViewModel.

#### Retrofit API (`data/remote`)
- **Purpose**: Define HTTP endpoints.
- **Rules**: Use standard Retrofit annotations.

#### DTO Model (`data/model`)
- **Purpose**: Network payload representation (JSON structure).
- **Rules**: Can contain `@SerializedName`. Must not be used in the UI layer.

---

### 2.3 Domain Layer (`domain/...`)

#### Domain Model (`domain/model`)
- **Purpose**: Pure Java representation of business concepts used by the UI.
- **Rules**: Must NOT contain `@SerializedName` or any Android-specific imports.

#### Result Wrapper (`core/common`)
- **Purpose**: Safely encapsulate success or failure.

---

## 3. Data Flow Contract

To avoid confusing responsibilities, ALL feature requests MUST follow this mandatory flow without skipping steps.

1. **User Action** → User clicks a button in the `Activity`.
2. **Activity** → Calls `viewModel.submitForm()` (no logic, just delegation).
3. **ViewModel** → Calls `repository.doAction()`, setting `UiState` to Loading.
4. **Repository** → Calls `api.doAction()` via Retrofit.
5. **API & DTO** → Receives network response and deserializes into DTO.
6. **Mapper** → Repository translates DTO to `Domain Model`.
7. **Result<T>** → Repository returns `Result.Success(DomainModel)` or `Result.Error` to ViewModel.
8. **ViewModel** → Evaluates `Result`. Updates LiveData to `UiState(Content)` or `UiState(Error)`.
9. **Activity render()** → Observer in Activity receives new state and updates UI bindings.

**Skipping layers (e.g., Activity -> Retrofit Api) is strictly forbidden.**

---

## 4. State Management Rules

### Single UiState per Screen
We do not use multiple LiveData objects for loading, error, and data (e.g., `isLoading`, `errorString`, `userData`). Instead, use a single state class object representing the 4 possible states:

1. **Idle**: Initial state.
2. **Loading**: Waiting for data.
3. **Content**: Data successfully loaded.
4. **Error**: Operation failed.

### Rules
- **No storing lambda inside state**: State must be data-only.
- **No navigation inside ViewModel**: Do not call `startActivity` in ViewModel. Have the ViewModel update a specific state that triggers the Activity to resolve navigation.

**Example Code:**
```java
// UiState
public class SessionUiState {
    public final boolean isLoading;
    public final SessionDomainModel session;
    public final String errorMessage;

    public SessionUiState(boolean isLoading, SessionDomainModel session, String errorMessage) {
        this.isLoading = isLoading;
        this.session = session;
        this.errorMessage = errorMessage;
    }
}

// ViewModel
public class SessionViewModel extends ViewModel {
    private final MutableLiveData<SessionUiState> state = new MutableLiveData<>(new SessionUiState(false, null, null));
    
    public LiveData<SessionUiState> getState() { return state; }

    public void loadSession(String id) {
        state.setValue(new SessionUiState(true, null, null));
        repository.loadSession(id, result -> {
            if (result.isSuccess()) {
                state.postValue(new SessionUiState(false, result.getData(), null));
            } else {
                state.postValue(new SessionUiState(false, null, result.getError().getMessage()));
            }
        });
    }
}
```

---

## 5. Networking & Error Handling Standards

### API Rules
- **Repository Must Return `Result<T>`**: Repositories act as the anti-corruption layer.
- **Never expose `Retrofit Call`**: Once data leaves `data/`, it must be standard Java types + `Result`.
- **All error parsing centralized**: HTTP 400s, network failures, timeouts must be parsed in the Repository (or interceptor) and translated into standard `ErrorDomainModel` inside the `Result.Error`.
- **No try/catch in Activity**: The UI handles the `UiState.Error`, nothing more.
- **Retry Pattern**: Do not implement infinite retries inside the UI. UI provides a visual "Retry" button that simply re-invokes the ViewModel method to restart the state flow.

---

## 6. Development Workflow (Step-by-Step)

When adding a new feature (e.g., "End Session"):

1. **Add API endpoint**: Add the Retrofit `@POST` definition in `SessionApi`.
2. **Create DTO**: Define `EndSessionResponseDto` in `data/model`.
3. **Update Domain Model**: Define or update `SessionModel` in `domain/model`.
4. **Create Repository method**: Add `endSession()` to `SessionRepository` interface and implementation. Map DTO to `SessionModel` and return `Result<SessionModel>`.
5. **Create ViewModel method**: Introduce `endSession()` in `SessionViewModel`.
6. **Implement UiState rendering**: Define states (Idle, Loading, Content, Error) for the End Session action.
7. **Create Activity UI binding**: Wire the "End" button to the ViewModel. Observe the `UiState` to show loaders, error toasts, and navigation.

**Enforced File Placement Example:**
```text
data/
    model/SessionDto.java
    remote/SessionApi.java
    repository/SessionRepositoryImpl.java

domain/
    model/SessionModel.java
    repository/SessionRepository.java

ui/
    session/
        SessionActivity.java
        SessionViewModel.java
        SessionUiState.java
```

---

## 7. Golden Rules (Critical Constraints)

| Rule | Description | Do | Don't |
| :--- | :--- | :--- | :--- |
| **No API call in Activity** | UI delegates network logic. | `viewModel.fetchUser()` | `api.getUser().enqueue()` |
| **No business logic in Activity** | UI only displays data. | Ask ViewModel to calculate. | `if(data.score > 10) showBadge()` |
| **No direct DTO use in UI** | UI only knows Domain Models. | Pass `UserDomainModel` to UI. | Pass `UserDto` to Activity. |
| **No modifying backend state locally** | Backend is single source of truth. | Refetch from API on success. | `session.setStatus(COMPLETED)` locally |
| **No multiple LiveData per screen** | Single source of truth for UI state. | `LiveData<ScreenState>` | `LiveData<String> err`, `LiveData<Boolean> loading` |
| **No global mutable state** | Use Intent extras or ViewModel. | Pass ID via Intent. | `public static Session currentSession` |
| **No static context usage** | Prevents memory leaks | Inject Context or interface | `App.getContext().getString()` |
| **No navigation inside Repository** | Repository only deals with data | Return `Result.Error` | `startLoginScreen()` inside repo |

---

## 8. Anti-Patterns (Strictly Forbidden)

Pull Requests containing these will be rejected immediately:
- ❌ **Calling API directly in Activity**: Bypasses architecture completely.
- ❌ **Updating Domain State locally**: (e.g., `WalkSession.status = "COMPLETED"` without verifying with the server). Backend is the single source of truth.
- ❌ **Multiple `UiState` objects per screen**: Causes race conditions and chaotic state machines.
- ❌ **Complex state machines in frontend**: Let the backend govern states. The UI simply reflects what the backend says.
- ❌ **Using EventBus**: Obfuscates data flow and creates spaghetti code. Use callbacks or `LiveData`.
- ❌ **Introducing Redux/MVI without team decision**: Keep it pragmatic MVVM-lite. Avoid massive boilerplate.

---

## 9. Authentication Standards

- **Token Storage**: JWTs must be stored securely (e.g., `SharedPreferences` securely via wrapper, or EncryptedSharedPreferences).
- **Interceptor Attachment**: A Retrofit `AuthInterceptor` must automatically attach the `Authorization: Bearer <token>` header to all protected requests.
- **Never manually inject**: DO NOT pass tokens manually from UI to ViewModel to Repository to API. It must be completely transparent to the UI.
- **Logout Flow**: If the Interceptor detects HTTP 401 Unauthorized, it should broadcast an intent, return a specific error result, or trigger a central navigator to clear tokens and route the user to the Login screen.

---

## 10. Configuration & Environment

- **Debug vs Release base URL**: Use `build.gradle` build types (`debug`, `release`) to configure different API base URLs. Access them via `BuildConfig.BASE_URL`.
- **Secrets**: Use `local.properties` to store Maps API keys or environment secrets. Do not commit this file to version control.
- **No hardcoded keys**: Never hardcode API keys or URLs inside Activity or Network classes.

---

## 11. Testing Strategy

- **ViewModel Unit Test**: Use JUnit + Mockito. Mock the Repository. Verify that calling a ViewModel method correctly cycles through `UiState.Loading` to `UiState.Content`. Mandatory for state transition testing.
- **Repository Mock Test**: Test DTO-to-Domain mapping and Result wrapper logic. No actual network calls.
- **UI Behavior Test**: (Optional/Espresso). Only test visual bindings.
- **No testing Retrofit directly**: Do not write tests verifying Retrofit's internal HTTP parsing. Only test the Mapper logic.

---

## 12. PR Review Checklist

Reviewers MUST verify:
- [ ] Does Activity strictly only observe state and render?
- [ ] Does ViewModel orchestrate the flow without containing Android `Context` imports?
- [ ] Is `UiState` representing a single source of truth for the screen?
- [ ] Is backend treated as the single source of truth (no local optimistic fake states)?
- [ ] Are API errors caught gracefully via `Result<T>` and not leaking raw Exceptions?
- [ ] Is repository mapping isolating DTOs from the Domain/UI layers?
- [ ] Are API client classes explicitly abstracted behind Repository interfaces?
- [ ] Are retry patterns implemented correctly by re-invoking ViewModel actions?
- [ ] Are secrets hidden properly in `local.properties`?

---

## 13. Quick Reference Section

**Standard ViewModel Method Signature**
```java
public void fetchDetails(String id) {
    state.postValue(new DetailUiState(true, null, null));
    repository.getDetails(id, result -> {
        if (result.isSuccess()) {
            state.postValue(new DetailUiState(false, result.getData(), null));
        } else {
            state.postValue(new DetailUiState(false, null, result.getError().getMessage()));
        }
    });
}
```

**Standard Repository Method Signature Pattern**
```java
public void getDetails(String id, Callback<Result<DomainModel>> callback) {
    api.getDetails(id).enqueue(new retrofit2.Callback<Dto>() {
        @Override
        public void onResponse(Call<Dto> call, Response<Dto> response) {
            if (response.isSuccessful() && response.body() != null) {
                callback.onResult(Result.success(Mapper.toDomain(response.body())));
            } else {
                callback.onResult(Result.error(new Exception("API Error")));
            }
        }
        @Override
        public void onFailure(Call<Dto> call, Throwable t) {
            callback.onResult(Result.error(t));
        }
    });
}
```

**Standard Activity Render Method Pattern**
```java
viewModel.getState().observe(this, state -> {
    binding.progressBar.setVisibility(state.isLoading ? View.VISIBLE : View.GONE);
    
    if (state.errorMessage != null) {
        showError(state.errorMessage);
    } else if (state.session != null) {
        renderData(state.session);
    }
});
```
