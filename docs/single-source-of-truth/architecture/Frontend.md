# Kiến Trúc Frontend

MVVM + UiState + DDD | Dự án WalkMate Android

## Tổng Quan

Mô hình: MVVM ở UI, DDD-lite ở domain.

Định hướng tổ chức:

- `ui/` - feature-oriented (tổ chức theo màn hình)
- `domain/` - domain-oriented (tổ chức theo domain nghiệp vụ, gần backend mindset)
- `data/` - technical implementation (datasource + mapping + repository impl)

Mục tiêu:

- UI chỉ render state, không chứa business rule
- ViewModel điều phối UI event -> domain service -> UiState
- Domain độc lập với framework UI/Android API
- Frontend và backend dùng chung ngôn ngữ kiến trúc (service-oriented)

## 1. Cấu Trúc Thư Mục Chuẩn

```text
frontend/src/main/java/com/walkmate/
├── core/
│   ├── common/
│   ├── util/
│   └── designsystem/
├── ui/
│   ├── main/
│   │   └── MainActivity.kt
│   └── <feature-name>/
│       ├── <Feature>Screen.kt
│       ├── <Feature>ViewModel.kt
│       ├── <Feature>UiState.kt
│       ├── <Feature>UiEvent.kt
│       ├── <Feature>UiEffect.kt
│       └── component/
├── domain/
│   ├── <domain-name>/
│   │   ├── <Domain>.kt
│   │   ├── <Domain>Repository.kt
│   │   ├── <Domain>ErrorCode.kt
│   │   ├── <Domain>Service.kt
│   │   ├── <ValueObject>.kt
│   │   └── <EnumOrPolicy>.kt
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
        └── <Domain>RepositoryImpl.kt
```

## 2. Trách Nhiệm Từng Layer

| Layer     | Định hướng       | Trách nhiệm                                                                                  |
| --------- | ---------------- | -------------------------------------------------------------------------------------------- |
| `ui/`     | Feature-oriented | Render `UiState`, phát `UiEvent`, nhận `UiEffect`. Không chứa business rule.                 |
| `domain/` | Domain-oriented  | Chứa model nghiệp vụ, repository contract, domain service, rule/validation nghiệp vụ.        |
| `data/`   | Technical        | Gọi API/DB local, mapping DTO/entity <-> domain, implements repository interface của domain. |
| `core/`   | Shared technical | Thành phần dùng chung: helper, extension, design system, constants.                          |

Lưu ý: `domain/shared/` chỉ chứa thành phần tái sử dụng liên domain, không làm thư mục gom tạp.

Lưu ý: không dùng `usecase/` theo kiểu mỗi use case một file mặc định. Chỉ tách ra khi một domain service trở nên quá lớn.

## 3. Quy Ước Đặt Tên

### 3.1 UI theo MVVM + UiState

| Thành phần | Mẫu tên                 | Ví dụ                |
| ---------- | ----------------------- | -------------------- |
| Screen     | `<Feature>Screen.kt`    | `IntentScreen.kt`    |
| ViewModel  | `<Feature>ViewModel.kt` | `IntentViewModel.kt` |
| State      | `<Feature>UiState.kt`   | `IntentUiState.kt`   |
| Event      | `<Feature>UiEvent.kt`   | `IntentUiEvent.kt`   |
| Effect     | `<Feature>UiEffect.kt`  | `IntentUiEffect.kt`  |

### 3.2 Domain theo DDD-lite (na ná backend)

| Thành phần           | Mẫu tên                 | Ví dụ                 |
| -------------------- | ----------------------- | --------------------- |
| Domain model         | `<Domain>.kt`           | `Intent.kt`           |
| Repository interface | `<Domain>Repository.kt` | `IntentRepository.kt` |
| Domain service       | `<Domain>Service.kt`    | `IntentService.kt`    |
| Domain error code    | `<Domain>ErrorCode.kt`  | `IntentErrorCode.kt`  |

### 3.3 Data implementation

| Thành phần      | Mẫu tên                            | Ví dụ                        |
| --------------- | ---------------------------------- | ---------------------------- |
| Repository impl | `<Domain>RepositoryImpl.kt`        | `IntentRepositoryImpl.kt`    |
| Remote DTO      | `<Domain>Dto.kt`                   | `IntentDto.kt`               |
| Local entity    | `<Domain>Entity.kt`                | `IntentEntity.kt`            |
| Mapper          | `<Source>To<Destination>Mapper.kt` | `IntentDtoToDomainMapper.kt` |

### 3.4 DTO -> Mapper -> Domain (bắt buộc)

- DTO chỉ tồn tại ở `data/datasource/remote/dto/`
- Entity chỉ tồn tại ở `data/datasource/local/entity/`
- Mapper tồn tại ở `data/mapper/`
- Domain chỉ nhận domain model, không phụ thuộc DTO/Entity

Luồng chuẩn:

```text
DTO/Entity -> Mapper -> Domain Model -> Domain Service/Domain Method
```

## 4. Contract Chuẩn cho UiState

`UiState` nên immutable và đủ để render 100% màn hình:

```kotlin
data class IntentUiState(
    val isLoading: Boolean = false,
    val data: IntentViewData? = null,
    val error: UiText? = null
)
```

`UiEvent`: action từ user (click, refresh, retry).  
`UiEffect`: one-time effect (toast, navigate, snackbar).

Có thể đổi tên thành `UiSideEffect` nếu team muốn rõ nghĩa hơn.

## 5. Luồng Request Chuẩn

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

## 6. Nguyên Tắc Cốt Lõi

- UI không gọi trực tiếp API/DAO.
- ViewModel không chứa rule nghiệp vụ phức tạp; rule nằm ở domain service/domain model.
- Data không leak DTO/entity ra UI; luôn map về domain hoặc view data.
- Mỗi màn hình có `UiState` riêng, tránh state dùng chung mơ hồ.
- `UiEffect` chỉ dùng cho one-time action, không nhét vào `UiState` lâu dài.
- Repository interface nằm ở `domain/`, implementation nằm ở `data/repository/`.
- Domain service là lựa chọn mặc định thay cho usecase-per-file.

## 7. Bảng Tra Cứu Nhanh

| Token                    | Ý nghĩa                             | Ví dụ                         |
| ------------------------ | ----------------------------------- | ----------------------------- |
| `<feature-name>`         | Tên feature UI viết thường          | `intent`, `session`, `rating` |
| `<Feature>`              | Prefix class UI theo PascalCase     | `Intent`, `Session`, `Rating` |
| `<domain-name>`          | Tên domain nghiệp vụ viết thường    | `intent`, `session`, `user`   |
| `<Domain>`               | Prefix class domain theo PascalCase | `Intent`, `Session`, `User`   |
| `<Domain>Service`        | Điều phối nghiệp vụ domain          | `IntentService`               |
| `<Feature>UiState`       | Trạng thái màn hình                 | `IntentUiState`               |
| `<Feature>UiEvent`       | Sự kiện từ user                     | `IntentUiEvent`               |
| `<Feature>UiEffect`      | One-time effect                     | `IntentUiEffect`              |
| `<Domain>Repository`     | Contract nghiệp vụ                  | `IntentRepository`            |
| `<Domain>RepositoryImpl` | Data implementation                 | `IntentRepositoryImpl`        |
