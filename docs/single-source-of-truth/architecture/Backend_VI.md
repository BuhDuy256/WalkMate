# Kiến Trúc Backend

DDD-lite + Layered | Dự án WalkMate Android

## Tổng Quan

Mô hình: kiến trúc DDD-lite + Layered.

Định hướng tổ chức:

- `domain/` - domain-oriented (tổ chức theo aggregate root)
- `application/` - feature-oriented (tổ chức theo use case)

Mục tiêu:

- Tách business logic khỏi technical implementation
- Giữ domain độc lập với framework, DB và HTTP
- Dễ mở rộng khi thêm domain hoặc feature mới

## 1. Cấu Trúc Thư Mục Chuẩn

```text
src/main/java/com/walkmate/walkmate/
├── application/
│   └── <domain-name>/
│       ├── <Domain>CommandService.java
│       └── <Domain>QueryService.java
├── domain/
│   ├── <domain-name>/
│   │   ├── <AggregateRoot>.java
│   │   ├── <Domain>Repository.java
│   │   ├── <Domain>ErrorCode.java
│   │   ├── <ValueObject>.java
│   │   └── <EnumOrPolicy>.java
│   └── shared/
│       ├── exception/
│       │   ├── DomainException.java
│       │   └── ErrorCode.java
│       └── valueobject/
├── infrastructure/
│   ├── repository/
│   │   └── <domain-name>/
│   │       └── <Domain>JooqRepository.java
│   ├── config/
│   ├── security/
│   └── exception/
├── presentation/
│   ├── controller/
│   │   └── <domain-name>/
│   │       └── <Domain>Controller.java
│   ├── dto/
│   │   ├── request/
│   │   │   └── <domain-name>/
│   │   │       └── <Verb><Domain>Request.java
│   │   └── response/
│   │       ├── <Domain>Response.java
│   │       └── <Domain>SummaryResponse.java
│   ├── mapper/
│   └── exception/
└── Application.java
```

## 2. Trách Nhiệm Từng Layer

| Layer             | Định hướng       | Trách nhiệm                                                                                                            |
| ----------------- | ---------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `application/`    | Feature-oriented | Điều phối use case, quản lý transaction boundary, gọi domain repository interface. Không chứa business rule.           |
| `domain/`         | Domain-oriented  | Chứa business rule cốt lõi, entity/value object/policy, repository contract, domain-scoped errors.                     |
| `infrastructure/` | Technical        | Hiện thực DB/query/framework/security. Repository implements domain interface bằng jOOQ. Chỉ chứa technical exception. |
| `presentation/`   | HTTP entry point | Controller, mapping DTO, validation, chuyển exception sang response.                                                   |

Lưu ý về `domain/shared/`: chỉ dùng cho value object và exception được tái sử dụng bởi nhiều domain. Không dùng như thư mục gom tạp.

## 3. Quy Ước Đặt Tên

Tên domain là trục chính để đặt tên xuyên suốt các layer. Nếu domain là `intent`, mọi layer dùng `intent` làm tên thư mục và `Intent` làm class prefix.

### 3.1 Đặt tên class theo layer

| Layer                        | Mẫu tên                        | Ví dụ                        |
| ---------------------------- | ------------------------------ | ---------------------------- |
| `domain/` aggregate          | `<Domain>.java`                | `Intent.java`                |
| `domain/` repo interface     | `<Domain>Repository.java`      | `IntentRepository.java`      |
| `domain/` error codes        | `<Domain>ErrorCode.java`       | `IntentErrorCode.java`       |
| `application/` write         | `<Domain>CommandService.java`  | `IntentCommandService.java`  |
| `application/` read          | `<Domain>QueryService.java`    | `IntentQueryService.java`    |
| `infrastructure/` repo impl  | `<Domain>JooqRepository.java`  | `IntentJooqRepository.java`  |
| `presentation/` controller   | `<Domain>Controller.java`      | `IntentController.java`      |
| `presentation/` request DTO  | `<Verb><Domain>Request.java`   | `CreateIntentRequest.java`   |
| `presentation/` response DTO | `<Domain>Response.java`        | `IntentResponse.java`        |
| `presentation/` list DTO     | `<Domain>SummaryResponse.java` | `IntentSummaryResponse.java` |

### 3.2 Request DTO - luôn có động từ

Tên chung như `IntentRequest` không thể hiện rõ hành động nghiệp vụ. Hãy dùng động từ làm prefix.

| Tránh            | Dùng thay thế                               |
| ---------------- | ------------------------------------------- |
| `IntentRequest`  | `CreateIntentRequest`                       |
| `SessionRequest` | `StartSessionRequest` / `EndSessionRequest` |
| `RatingRequest`  | `SubmitRatingRequest`                       |

### 3.3 Response DTO - phân biệt chi tiết và danh sách

Tách response đầy đủ và response rút gọn để tránh over-fetching ở endpoint danh sách.

```java
// Đối tượng đơn - đầy đủ chi tiết
IntentResponse.java

// Rút gọn - dùng cho card/list
IntentSummaryResponse.java
```

### 3.4 Repository - suffix phải thể hiện công nghệ

Không dùng suffix chung chung `Impl`. Tên công nghệ (`Jooq`) giúp người đọc biết ngay implementation đang dùng gì.

| Tránh                  | Dùng thay thế          |
| ---------------------- | ---------------------- |
| `IntentRepositoryImpl` | `IntentJooqRepository` |

### 3.5 Đặt tên method service - tôn trọng tách CQRS

Method trong `CommandService` phải là write. Method trong `QueryService` phải là read.

```java
// IntentCommandService.java
createIntent(...)
cancelIntent(...)

// IntentQueryService.java
findNearbyMatches(...)
getIntentById(...)
```

### 3.6 Error code - luôn có tiền tố domain

Mọi hằng số error code phải có tiền tố domain theo chuẩn `UPPER_SNAKE_CASE`.

```java
// IntentErrorCode.java
INTENT_NOT_FOUND
INTENT_ALREADY_CONFIRMED
INTENT_EXPIRED

// SessionErrorCode.java
SESSION_NOT_STARTED
SESSION_ALREADY_ENDED
```

## 4. Luồng Request Chuẩn

```text
Controller
-> <Domain>CommandService / <Domain>QueryService
-> Domain Model
-> <Domain>Repository (interface)
-> <Domain>JooqRepository (infrastructure implementation)
-> Database
```

## 5. Nguyên Tắc Cốt Lõi

- Domain là trung tâm. Infrastructure và presentation phụ thuộc vào domain, không theo chiều ngược lại.
- Ưu tiên tổ chức theo feature trước, chỉ tách theo type khi thực sự cần.
- DTO chỉ tồn tại ở presentation, không để leak vào domain hoặc application.
- Repository interface nằm trong `domain/`. Implementation nằm trong `infrastructure/repository/<domain-name>/` và dùng jOOQ.
- `domain/shared/` chỉ chứa thành phần tái sử dụng liên domain, không phải thư mục catch-all.
- `infrastructure/exception/` chỉ chứa technical exception (DB, external services).
- Business exception nằm trong `domain/<domain-name>/<Domain>ErrorCode.java`.

## 6. Bảng Tra Cứu Nhanh

| Token                     | Ý nghĩa                         | Ví dụ                                 |
| ------------------------- | ------------------------------- | ------------------------------------- |
| `<domain>`                | Tên thư mục domain viết thường  | `intent`, `session`, `user`, `rating` |
| `<Domain>`                | Prefix class theo PascalCase    | `Intent`, `Session`, `User`, `Rating` |
| `<Domain>Repository`      | Interface trong `domain/`       | `IntentRepository`                    |
| `<Domain>JooqRepository`  | Impl trong `infrastructure/`    | `IntentJooqRepository`                |
| `<Domain>CommandService`  | Nhóm write trong `application/` | `IntentCommandService`                |
| `<Domain>QueryService`    | Nhóm read trong `application/`  | `IntentQueryService`                  |
| `<Verb><Domain>Request`   | Request DTO                     | `CreateIntentRequest`                 |
| `<Domain>Response`        | DTO trả về đơn                  | `IntentResponse`                      |
| `<Domain>SummaryResponse` | DTO danh sách rút gọn           | `IntentSummaryResponse`               |
| `<DOMAIN>_<STATE>`        | Hằng số error code              | `INTENT_NOT_FOUND`                    |
