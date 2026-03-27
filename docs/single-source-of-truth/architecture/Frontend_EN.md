# Frontend Architecture

MVVM + UiState + DDD | WalkMate Android Project

## Overview

**Language: Java** (no Kotlin). All source files use `.java` extensions.

Model: MVVM at the UI layer, DDD-lite at the domain layer.

Organizational orientation:

- `ui/` - feature-oriented (organized by screens)
- `domain/` - domain-oriented (organized by business domain, aligned with backend mindset)
- `data/` - technical implementation (datasource + mapping + repository implementation)

State mechanism: `LiveData<UiState>` backed by `MutableLiveData` inside each `ViewModel`.
Async mechanism: `DomainCallback<T>` (callback interface) — no coroutines, no RxJava, no StateFlow.

Goals:

- UI only renders state and does not contain business rules
- ViewModel coordinates UI events -> domain service -> UiState
- Domain remains independent from UI framework/Android APIs
- Frontend and backend share a unified architecture language (service-oriented)

## 1. Standard Folder Structure

```text
frontend/src/main/java/com/walkmate/
├── core/
│   ├── common/
│   ├── util/
│   └── designsystem/
├── ui/
│   ├── main/
│   │   └── MainActivity.java
│   └── <feature-name>/
│       ├── <Feature>Screen.java
│       ├── <Feature>ViewModel.java
│       ├── <Feature>ViewData.java
│       ├── <Feature>UiState.java
│       ├── <Feature>UiEvent.java
│       ├── <Feature>UiEffect.java
│       └── component/
├── domain/
│   ├── <domain-name>/
│   │   ├── <Domain>.java
│   │   ├── <Domain>Repository.java
│   │   ├── <Domain>ErrorCode.java
│   │   ├── <Domain>Service.java
│   │   ├── <ValueObject>.java
│   │   └── <EnumOrPolicy>.java
│   └── shared/
│       ├── exception/
│       └── valueobject/
└── data/
    ├── datasource/
    │   ├── remote/
    │   │   ├── api/
    │   │   └── dto/
    │   └── local/
    │       ├── dao/
    │       └── entity/
    ├── mapper/
    └── repository/
        └── <Domain>RepositoryImpl.java
```

## 2. Layer Responsibilities

| Layer     | Orientation      | Responsibility                                                                                  |
| --------- | ---------------- | ----------------------------------------------------------------------------------------------- |
| `ui/`     | Feature-oriented | Renders `UiState`, emits `UiEvent`, handles `UiEffect`. No business rules.                      |
| `domain/` | Domain-oriented  | Contains core domain model, repository contract, domain service, and business validation/rules. |
| `data/`   | Technical        | Calls API/local DB, maps DTO/entity <-> domain, and implements domain repository interfaces.    |
| `core/`   | Shared technical | Shared utilities: helpers, extensions, design system, constants.                                |

Note: `domain/shared/` is only for cross-domain reusable components. Do not use it as a junk drawer.

Note: do not default to `usecase/` with one-file-per-use-case. Only split out if a domain service becomes too large.

## 3. Naming Conventions

### 3.1 UI with MVVM + UiState

| Component | Naming pattern            | Example                |
| --------- | ------------------------- | ---------------------- |
| Screen    | `<Feature>Screen.java`    | `IntentScreen.java`    |
| ViewModel | `<Feature>ViewModel.java` | `IntentViewModel.java` |
| ViewData  | `<Feature>ViewData.java`  | `IntentViewData.java`  |
| State     | `<Feature>UiState.java`   | `IntentUiState.java`   |
| Event     | `<Feature>UiEvent.java`   | `IntentUiEvent.java`   |
| Effect    | `<Feature>UiEffect.java`  | `IntentUiEffect.java`  |

Note: keep `ViewData` at the same level as `ViewModel` in `ui/<feature-name>/` by default (no dedicated folder unless needed).

### 3.2 Domain with DDD-lite (backend-aligned)

| Component            | Naming pattern            | Example                 |
| -------------------- | ------------------------- | ----------------------- |
| Domain model         | `<Domain>.java`           | `Intent.java`           |
| Repository interface | `<Domain>Repository.java` | `IntentRepository.java` |
| Domain service       | `<Domain>Service.java`    | `IntentService.java`    |
| Domain error code    | `<Domain>ErrorCode.java`  | `IntentErrorCode.java`  |

### 3.3 Data implementation

| Component                 | Naming pattern                       | Example                        |
| ------------------------- | ------------------------------------ | ------------------------------ |
| Repository implementation | `<Domain>RepositoryImpl.java`        | `IntentRepositoryImpl.java`    |
| Remote DTO                | `<Domain>Dto.java`                   | `IntentDto.java`               |
| Local entity              | `<Domain>Entity.java`                | `IntentEntity.java`            |
| Mapper                    | `<Source>To<Destination>Mapper.java` | `IntentDtoToDomainMapper.java` |

### 3.4 DTO -> Mapper -> Domain (mandatory)

- DTO must exist only in `data/datasource/remote/dto/`
- Entity must exist only in `data/datasource/local/entity/`
- Mapper must exist in `data/mapper/`
- Domain must consume domain models only, never DTO/Entity directly

Standard mapping flow:

```text
DTO/Entity -> Mapper -> Domain Model -> Domain Service/Domain Method
```

## 4. Standard UiState Contract

`UiState` is an immutable Java POJO. Fields are `final`, set via constructor only. Provide a static `initial()` factory for the default/empty state.

```java
public final class IntentUiState {
    private final boolean loading;
    private final IntentViewData data;
    private final String error; // null means no error

    public IntentUiState(boolean loading, IntentViewData data, String error) {
        this.loading = loading;
        this.data = data;
        this.error = error;
    }

    public static IntentUiState initial() {
        return new IntentUiState(false, null, null);
    }

    public boolean isLoading() { return loading; }
    public IntentViewData getData() { return data; }
    public String getError() { return error; }
}
```

Standard `ViewModel` pattern with `LiveData` and `DomainCallback`:

```java
public class IntentViewModel extends ViewModel {
    private final MutableLiveData<IntentUiState> uiState =
            new MutableLiveData<>(IntentUiState.initial());

    public LiveData<IntentUiState> getUiState() { return uiState; }

    public void loadIntent(String intentId) {
        uiState.setValue(new IntentUiState(true, null, null));
        intentService.findById(intentId, new DomainCallback<IntentViewData>() {
            @Override public void onSuccess(IntentViewData data) {
                uiState.postValue(new IntentUiState(false, data, null));
            }
            @Override public void onError(Exception e) {
                uiState.postValue(new IntentUiState(false, null, e.getMessage()));
            }
        });
    }
}
```

`UiEvent`: user actions (click, refresh, retry).
`UiEffect`: one-time effects (toast, navigate, snackbar).

Optional: rename `UiEffect` to `UiSideEffect` if your team prefers clearer semantics.

## 5. Standard Request Flow

```text
User Action
-> UiEvent
-> ViewModel
-> <Domain>Service (domain)
-> Repository interface (domain)
-> RepositoryImpl (data)
-> DataSource remote/local
-> Mapper
-> Domain result
-> ViewModel reduce -> UiState
-> Screen render
```

## 6. Core Principles

- UI must not call API/DAO directly.
- ViewModel should not host complex business rules; rules belong to domain service/domain model.
- Data must not leak DTO/entity to UI; always map to domain or view data.
- Each screen should have its own `UiState`.
- Use `UiEffect` only for one-time actions, not persistent state.
- Repository interface belongs to `domain/`; implementation belongs to `data/repository/`.
- Domain service is the default approach instead of usecase-per-file.

## 7. Quick Reference

| Token                    | Meaning                               | Example                       |
| ------------------------ | ------------------------------------- | ----------------------------- |
| `<feature-name>`         | lowercase UI feature folder name      | `intent`, `session`, `rating` |
| `<Feature>`              | PascalCase UI class prefix            | `Intent`, `Session`, `Rating` |
| `<domain-name>`          | lowercase business domain folder name | `intent`, `session`, `user`   |
| `<Domain>`               | PascalCase domain class prefix        | `Intent`, `Session`, `User`   |
| `<Domain>Service`        | domain business coordinator           | `IntentService`               |
| `<Feature>UiState`       | screen state                          | `IntentUiState`               |
| `<Feature>UiEvent`       | user event                            | `IntentUiEvent`               |
| `<Feature>UiEffect`      | one-time effect                       | `IntentUiEffect`              |
| `<Domain>Repository`     | business contract                     | `IntentRepository`            |
| `<Domain>RepositoryImpl` | data implementation                   | `IntentRepositoryImpl`        |
