# Kiến Trúc Backend

DDD-lite + Layered | Dự án WalkMate Android

## Tổng Quan

Mô hình: kiến trúc DDD-lite + Layered (Hướng tới **Rich Domain Model**).

Định hướng tổ chức:

- `domain/` - domain-oriented (tổ chức theo aggregate root)
- `application/` - feature-oriented (tổ chức theo use case)

Mục tiêu:

- Tách business logic khỏi technical implementation (Dependency Inversion).
- Giữ domain độc lập với framework, DB và HTTP. Entity tự bảo vệ trạng thái (Rich Domain).
- Dễ mở rộng khi thêm domain hoặc feature mới.
- Gom cụm xử lý Exception duy nhất tại Presentation.

## 1. Cấu Trúc Thư Mục Chuẩn

```text
src/main/java/com/walkmate/
├── application/
│   └── <domain-name>/
│       ├── <Domain>CommandService.java
│       ├── <Domain>QueryService.java
│       ├── <Verb><Domain>Command.java (VD: LoginUserCommand)
│       └── <Name>Provider.java (Interface, VD: TokenProvider)
├── domain/
│   ├── <domain-name>/
│   │   ├── <AggregateRoot>.java (Rich Domain Entity)
│   │   ├── <Domain>Repository.java
│   │   ├── <Domain>ErrorCode.java
│   │   └── <ValueObject>.java / <EnumOrPolicy>.java
│   └── shared/
│       └── exception/
│           ├── DomainException.java
│           └── ErrorCode.java
├── infrastructure/
│   ├── repository/
│   │   └── <domain-name>/
│   │       └── <Domain><Tech>Repository.java (VD: UserJdbcRepository)
│   ├── security/
│   │   └── jwt/
│   │       └── JwtTokenProvider.java (Impl)
│   └── config/
└── presentation/
    ├── controller/
    │   └── <domain-name>/
    │       └── <Domain>Controller.java
    ├── dto/
    │   ├── request/
    │   │   └── <domain-name>/
    │   │       └── <Verb><Domain>Request.java
    │   └── response/
    │       ├── ApiResponse.java (Generic Response Wrapper)
    │       └── <domain-name>/
    │           └── <Domain>Response.java
    ├── mapper/
    └── exception/
        └── GlobalExceptionHandler.java
```

## 2. Trách Nhiệm Từng Layer

| Layer             | Định hướng       | Trách nhiệm                                                                                                            |
| ----------------- | ---------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `application/`    | Feature-oriented | Điều phối use case, định nghĩa Boundary & Transaction, gọi Domain Repository/Interface. Nhận Command objects từ Controller. Không chứa business rule nội tại. |
| `domain/`         | Domain-oriented  | **Rich Domain Model**: Entity chứa logic nghiệp vụ, tự vệ và ném `DomainException` nếu vi phạm. Chứa repository/provider contracts, domain-scoped errors. |
| `infrastructure/` | Technical        | Hiện thực DB/jwt/framework/security. Repository implements domain interface (bằng JDBC/jOOQ). Chỉ chứa implementation chi tiết công nghệ. |
| `presentation/`   | HTTP entry point | Controller, mapping DTO sang Command, HTTP validation (`@Valid`), gom Exception tại `GlobalExceptionHandler`. |

Lưu ý: Không leak framework HTTP vào Application/Domain và ngược lại, không leak logic Database vào Domain. Tầng `domain/shared/` chỉ dùng cho value object và exception tái sử dụng.

## 3. Quy Ước Đặt Tên

Tên domain là trục chính để đặt tên xuyên suốt các layer. Nhất quán từ Thư mục -> Class.

### 3.1 Đặt tên class theo layer

| Layer                        | Mẫu tên                        | Ví dụ                        |
| ---------------------------- | ------------------------------ | ---------------------------- |
| `domain/` aggregate          | `<Domain>.java`                | `Intent.java`                |
| `domain/` repo interface     | `<Domain>Repository.java`      | `IntentRepository.java`      |
| `domain/` error codes        | `<Domain>ErrorCode.java`       | `IntentErrorCode.java`       |
| `application/` write         | `<Domain>CommandService.java`  | `IntentCommandService.java`  |
| `application/` internal cmd  | `<Verb><Domain>Command.java`   | `LoginUserCommand.java`      |
| `application/` / `domain/` interface  | `<Name>Provider.java` / `Matcher`   | `TokenProvider.java`         |
| `infrastructure/` repo impl  | `<Domain><Tech>Repository.java`| `IntentJdbcRepository.java`  |
| `infrastructure/` tech impl  | `<Tech><Name>Provider.java`    | `JwtTokenProvider.java`      |
| `presentation/` controller   | `<Domain>Controller.java`      | `IntentController.java`      |
| `presentation/` request DTO  | `<Verb><Domain>Request.java`   | `CreateIntentRequest.java`   |

### 3.2 Request DTO vs Application Command

- **DTO (`presentation`)**: `LoginUserRequest` chứa annotation `@Valid`, dùng riêng cho Spring Web. Tên phải có động từ.
- **Command (`application`)**: `LoginUserCommand` là pure Java `record`, dùng để gom nhóm tham số cho Use Case. Không chứa annotation framework, an toàn xuyên suốt layer.

### 3.3 Đặt tên method service - tôn trọng tách CQRS

Method trong `CommandService` phải là write. Method trong `QueryService` phải là read.

```java
// IntentCommandService.java
createIntent(...)
cancelIntent(...)

// IntentQueryService.java
findNearbyMatches(...)
```

### 3.4 Error code - luôn có tiền tố domain

Mọi hằng số error code phải có tiền tố domain theo chuẩn `UPPER_SNAKE_CASE`.

```java
// UserErrorCode.java
USER_NOT_FOUND
USER_INVALID_CREDENTIALS
INVALID_USER_DATA
```

## 4. Luồng Xử Lý Lỗi (Exception Flow) Chuẩn

```text
Controller (Bắt đầu Request @PostMapping)
-> <Domain>CommandService (Application ném DomainException nếu lỗi liên kết Data: USER_NOT_FOUND)
-> Domain Model (Rich Domain tự ném DomainException nếu vi phạm Nội tại: INVALID_USER_DATA, USER_INVALID_CREDENTIALS)
```
Tất cả các `DomainException` trên sẽ **sủi bọt (bubble up)** về `presentation/exception/GlobalExceptionHandler` và được tự động map trả về thành `ApiResponse<Void>`. HTTP Status được xác định **động** bằng `ex.getErrorCode().httpStatus()` — không hardcode 400 cho tất cả lỗi. Ví dụ: `USER_NOT_FOUND` → 404, `USER_INVALID_CREDENTIALS` → 401, vi phạm nghiệp vụ → 400.

## 5. Nguyên Tắc Cốt Lõi

1. **Rich Domain Model**: Domain là trung tâm logic. Entity tự bảo vệ trạng thái của nó và tự ném phần lớn `DomainException`. Không để Application Service "móc ruột" Entity tự kiểm tra (Tránh Anemic Domain Model).
2. **Dependency Inversion**: Thuật toán băm mật khẩu, sinh JWT, gọi API ngoài... đều phải thông qua giao diện (Interface) khai báo ở Application/Domain. Tầng Infrastructure chỉ implements các Interface này.
3. **One Global Exception Handler**: Tầng Presentation bắt buộc có `GlobalExceptionHandler` để handle Catch-all `DomainException`, lỗi Validation `@Valid` 422, và fallback 500. JSON Response phải bọc chung một chuẩn `ApiResponse<T>`.
4. **DTO Boundaries**: DTO chỉ sống từ Presentation chạm đến Controller. Khi đi vào tầng Application, dữ liệu phải được convert sang Parameter hoặc Pure Java Command (`record` không chứa logic).
5. **Technology Suffix**: Tên class ở Infrastructure phải thể hiện công nghệ thao tác (ví dụ: `UserJdbcRepository` cho JDBC, `UserJooqRepository` cho jOOQ, `JwtTokenProvider` cho JWT).

## 6. Bảng Tra Cứu Nhanh

| Token                     | Ý nghĩa                         | Ví dụ                                 |
| ------------------------- | ------------------------------- | ------------------------------------- |
| `<Domain>`                | Prefix class theo PascalCase    | `Intent`, `Session`, `User`           |
| `<Domain>Repository`      | Interface trong `domain/`       | `IntentRepository`                    |
| `<Domain><Tech>Repository`| Impl trong `infrastructure/`    | `IntentJdbcRepository`                |
| `<Tech><Name>Provider`    | Technical Impl                  | `JwtTokenProvider`                    |
| `<Domain>CommandService`  | Nhóm write trong `application/` | `IntentCommandService`                |
| `<Verb><Domain>Command`   | Khối lệnh cho Application       | `LoginUserCommand`                    |
| `<Verb><Domain>Request`   | Request DTO của Web             | `CreateIntentRequest`                 |
| `<DOMAIN>_<STATE>`        | Hằng số error code              | `USER_NOT_FOUND`                      |

## 7. Các Ràng Buộc Kiến Trúc Cốt Lõi (Hard Constraints)

Đây là các **ràng buộc bắt buộc (Constraints)** định hình toàn bộ kiến trúc của dự án. **Tuyệt đối không được vi phạm** dưới mọi hình thức:

| Tiêu chuẩn Ràng Buộc | Trạng thái | Yêu cầu Kỷ luật |
| -------------------- | ---------- | --------------- |
| **Domain Entity có chứa logic?** | ✅ BẮT BUỘC (Rich Model) | Nếu Entity chỉ có getter/setter -> **Vi phạm Anemic Domain!** Bắt buộc phải đẩy business logic (chứa cả các hàm validate/authenticate) vào trong Entity để nó tự bảo vệ tính toàn vẹn. |
| **Infrastructure biết về Web?** | ❌ NGHIÊM CẤM | Tầng `infrastructure` chỉ quan tâm đến CSDL/công nghệ và implements các Interface của Application/Domain. Tuyệt đối không import hay dính dáng đến bất kỳ annotation HTTP nào. |
| **Application chứa logic DB?** | ❌ NGHIÊM CẤM | Tầng `application` thao tác dữ liệu phải mỏng. Nhiệm vụ duy nhất là điều phối Use Case: (1) Lấy data từ Repo -> (2) Đưa Entity tự xử lý logic -> (3) Ra lệnh Repo cập nhật. Cấm viết các logic xử lý nghiệp vụ phức tạp ở đây. |
| **Controller ném / catch Exception?** | ❌ NGHIÊM CẤM | Controller không được phép dùng `try-catch` để tự trả về HTTP Code. Mọi Exception bắn ra bắt buộc phải được đẩy lên để `GlobalExceptionHandler` tóm gọn và tự động response. |
