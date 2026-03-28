# Kiến Trúc Luồng API (Backend Flow)

Tài liệu này tổng hợp 5 loại luồng (Flows) cơ bản nhất xảy ra trong dự án WalkMate, từ lúc client gửi Request cho đến lúc server trả về Response (Bao gồm cả cấu trúc Lỗi).

---

## 🟢 Flow 1: Nhánh Hoàn Hảo (Happy Path - Success)

Luồng đi tiêu chuẩn khi dữ liệu hợp lệ và thực hiện Use Case thành công.

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant CommandService
    participant DomainEntity as User (Entity)
    participant Repo as UserJdbcRepository

    %% Step 1: Presentation
    Client->>Controller: POST /api/v1/auth/login {LoginUserRequest}
    Note right of Controller: Spring Web tự động check @Valid (OK)
    
    %% Step 2: Application
    Controller->>CommandService: loginUser(LoginUserCommand)
    
    %% Step 3: Domain DB interaction
    CommandService->>Repo: findByEmail()
    Repo-->>CommandService: Optional<User> (Found)
    
    %% Step 4: Rich Domain Auth
    CommandService->>DomainEntity: user.authenticate(rawPassword, matcher)
    Note right of DomainEntity: Mật khẩu chính xác!
    
    %% Step 5: Save State
    CommandService->>Repo: save(user)
    
    %% Step 6: Presentation mapping & Response
    CommandService-->>Controller: LoginResult
    Controller-->>Client: 200 OK - ApiResponse<LoginUserResponse>
```

---

## 🟡 Flow 2: Lỗi Validation Ngay Từ Cửa (Presentation Level 422)

Luồng kiểm duyệt DTO ngay tại cửa ngõ Controller, không cần đụng đến logic bên dưới.

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant GI as GlobalExceptionHandler

    Client->>Controller: POST (Thiếu Email, Password quá ngắn...)
    Note right of Controller: @Valid triggers MethodArgumentNotValidException!
    Controller--xGI: Xử lý ngoại lệ!
    
    GI-->>Client: 422 Unprocessable Entity
    Note over Client: {<br/>  success: false,<br/>  error: { <br/>    code: "VALIDATION_ERROR", <br/>    message: "email: must not be blank"<br/>  }<br/>}
```

---

## 🟠 Flow 3: Lỗi Điều Phối - Trạng Thái Hệ Thống (Application Level - HTTP Status Động)

Lỗi do Application bắt vì mâu thuẫn dữ liệu từ Repository (VD: Không tìm thấy, Xung đột dữ liệu).

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant CommandService
    participant Repo
    participant GI as GlobalExceptionHandler

    Client->>Controller: POST (Login sai Email)
    Controller->>CommandService: loginUser(Command)
    
    CommandService->>Repo: findByEmail("sai_email@email.com")
    Repo-->>CommandService: Optional.empty!
    
    Note right of CommandService: Không tìm thấy ai!
    CommandService--xGI: throw DomainException(USER_INVALID_CREDENTIALS)
    
    GI-->>Client: 401 Unauthorized
    Note over Client: {<br/>  success: false,<br/>  error: { <br/>    code: "USER_INVALID_CREDENTIALS", <br/>    message: "Invalid email or password"<br/>  }<br/>}
```

---

## 🔴 Flow 4: Lỗi Nghiệp Vụ Xâu Xa Nằm Trong Lõi (Rich Domain Level - HTTP Status Động)

Lỗi xảy ra sâu bên trong nội tại do bản thân Domain Entity tự bảo vệ mình và từ chối.

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant CommandService
    participant DomainEntity as User (Entity)
    participant GI as GlobalExceptionHandler

    Client->>Controller: POST (Login sai Password)
    Controller->>CommandService: loginUser(Command)
    CommandService->>DomainEntity: user.authenticate(sai_password, matcher)
    
    Note right of DomainEntity: Check Password hash FAILED!
    DomainEntity--xGI: throw DomainException(USER_INVALID_CREDENTIALS)
    
    GI-->>Client: 401 Unauthorized
    Note over Client: {<br/>  success: false,<br/>  error: { <br/>    code: "USER_INVALID_CREDENTIALS", <br/>    message: "Invalid email or password"<br/>  }<br/>}
```

---

## 💥 Flow 5: Lỗi Chết Chóc / Bất Ngờ (Global Fallback 500)

Lỗi phát sinh do Server bị ngắt mạng Database, null pointer exception vô tình chưa kiểm soát...

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant CommandService
    participant DB
    participant GI as GlobalExceptionHandler

    Client->>Controller: Bất kỳ Request nào
    Controller->>CommandService: Invoke Service
    CommandService->>DB: Trích xuất Data
    
    Note right of DB: Cáp quang cá mập cắn! Connection Time Out! JDBC failed!
    DB--xGI: throw Unexpected/SQLException
    
    GI-->>Client: 500 Internal Server Error
    Note over Client: {<br/>  success: false,<br/>  error: { <br/>    code: "INTERNAL_ERROR", <br/>    message: "An unexpected error occurred"<br/>  }<br/>}
```
