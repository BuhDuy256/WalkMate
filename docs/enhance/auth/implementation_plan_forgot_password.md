# Implementation Plan: Forgot Password with Email OTP

## 1. Tổng Quan

### Mục tiêu
Cho phép user đã đăng ký bằng Email/Password reset mật khẩu thông qua mã OTP 6 số gửi qua email.

### Luồng User
1. Từ Login → nhấn "Forgot password?" → nhập email → nhận OTP qua email
2. Nhập OTP 6 số → xác minh → đặt mật khẩu mới → quay về Login

### Ngoài phạm vi
- Google Sign-In users không cần reset password (trừ khi đã link LOCAL credentials)
- Thay đổi email
- Two-factor authentication

---

## 2. Product Flow

```
Login Page ──[Forgot password?]──► ForgotPasswordActivity
                                        │
                                   EmailInputFragment
                                   (nhập email, nhấn Send OTP)
                                   (Có helper text: "Nếu bạn dùng Google để đăng nhập, hãy quay lại... Nếu dùng Email/Password, mã OTP sẽ được gửi.")
                                        │
                                   OtpVerifyFragment
                                   (nhập OTP 6 số, reuse OtpInputView)
                                        │
                                   NewPasswordFragment
                                   (nhập + xác nhận mật khẩu mới)
                                        │
                                   Success → finish() → quay về Login
```

**States:**
- **Loading**: Button hiện spinner, disable input
- **Error**: Toast cho lỗi chung, inline cho lỗi field
- **Success**: Chuyển fragment tiếp theo hoặc finish Activity

---

## 3. Backend Implementation Plan

### 3.1 API Endpoints (thêm vào `UserController`)

| Method | Path | Mô tả |
|--------|------|--------|
| POST | `/api/v1/auth/password-reset/request` | Gửi OTP về email |
| POST | `/api/v1/auth/password-reset/verify` | Xác minh OTP |
| POST | `/api/v1/auth/password-reset/confirm` | Đặt mật khẩu mới |

### 3.2 Request/Response DTOs (`presentation/dto/request/user/`)

```java
// RequestPasswordResetRequest.java
public record RequestPasswordResetRequest(
    @NotBlank @Email String email
) {}

// VerifyPasswordResetRequest.java
public record VerifyPasswordResetRequest(
    @NotBlank @Email String email,
    @NotBlank @Size(min=6, max=6) String otp
) {}

// ConfirmPasswordResetRequest.java
public record ConfirmPasswordResetRequest(
    @NotBlank String resetToken,
    @NotBlank @Size(min=8) String newPassword
) {}
```

**Response DTO** (`presentation/dto/response/user/`):
```java
// PasswordResetTokenResponse.java
public record PasswordResetTokenResponse(String resetToken) {}
```

### 3.3 Application Commands (`application/user/`)

```java
public record RequestPasswordResetCommand(String email) {}
public record VerifyPasswordResetCommand(String email, String otp) {}
public record ConfirmPasswordResetCommand(String resetToken, String newPassword) {}
```

### 3.4 Application Service — thêm method vào `UserCommandService`
*(Lưu ý: Nếu codebase hiện tại đặt logic auth trong `AuthController`/`AuthCommandService`, hãy đổi tên class theo đúng auth package convention hiện tại)*

```java
// 1. requestPasswordReset(RequestPasswordResetCommand)
//    - Normalize email, tìm user (LOCAL provider, có passwordHash).
//      *LƯU Ý: Nếu không thấy user hoặc Google-only, return success luôn, KHÔNG tạo OTP record.*
//    - Với user hợp lệ, kiểm tra cooldown (query OTP record gần nhất).
//      *Nếu khoảng cách < 60s: silent drop (return success, không gửi email).*
//    - BẮT BUỘC TRANSACTION (cho các thao tác DB):
//      + Gọi repository.invalidateActiveByEmail(email) để vô hiệu hoá OTP cũ
//      + Generate OTP 6 số, hash bằng passwordEncoder, tạo PasswordResetOtp mới, lưu vào DB
//      + Giữ lại raw OTP trong local variable của method. Không persist/log raw OTP.
//    - SAU KHI TRANSACTION ĐÃ COMMIT THÀNH CÔNG:
//      + Mới gọi EmailProvider gửi email bằng raw OTP
//    - Trả về generic success (tránh email enumeration)

// 2. verifyPasswordReset(VerifyPasswordResetCommand) → String resetToken
//    - BẮT BUỘC TRANSACTION:
//      + Tìm OTP record active latest (findActiveLatestByEmail)
//      + Generate resetToken (UUID raw) và hash nó (resetTokenHash - dùng SHA-256 để dễ lookup)
//      + Tính resetTokenExpiresAt = now + 10 phút
//      + Gọi otp.verifyOtp(rawOtp, passwordEncoder::matches, resetTokenHash, resetTokenExpiresAt, now)
//        *LƯU Ý: Nếu bắt được DomainException(USER_OTP_INVALID), bắt buộc gọi repository.save(otp) để persist attempt_count trước khi rethrow.*
//      + Lưu record
//    - Trả về raw resetToken cho client

// 3. confirmPasswordReset(ConfirmPasswordResetCommand)
//    - BẮT BUỘC TRANSACTION:
//      + Hash resetToken từ client gửi lên (dùng SHA-256)
//      + Tìm record: repository.findByResetTokenHash(tokenHash)
//      + Gọi otp.validateResetToken(now)
//      + Load user bằng otp.userId. Nếu userId null hoặc user không tồn tại -> throw USER_RESET_TOKEN_INVALID
//      + Validate password policy cho newPassword
//      + Hash password mới, update user.passwordHash
//      + Gọi otp.consume(now) để đánh dấu đã dùng (invalidated)
//      + (Optional) Revoke tất cả refresh tokens của user nếu hệ thống đã có refresh token/session store.
```

### 3.5 Domain Changes

**`User.java`** — thêm method:
```java
public void resetPassword(String newPasswordHash) {
    if (this.provider == AuthProvider.GOOGLE && this.passwordHash == null) {
        throw new DomainException(UserErrorCode.USER_PASSWORD_RESET_NOT_ALLOWED);
    }
    this.passwordHash = requireText(newPasswordHash, "Password hash is required");
}

public static void validatePasswordStrength(String rawPassword) {
    if (rawPassword.length() < 8) throw new DomainException(UserErrorCode.USER_PASSWORD_TOO_WEAK);
    // thêm rule: ít nhất 1 uppercase, 1 số
}
```

**`UserErrorCode.java`** — thêm **3 entries còn thiếu** (4 OTP codes + `USER_OTP_RATE_LIMITED` đã tồn tại sẵn, không thêm lại):
```java
USER_PASSWORD_RESET_NOT_ALLOWED("Password reset is not available for this account"),
USER_PASSWORD_TOO_WEAK("Password must be at least 8 characters with uppercase and number"),
USER_RESET_TOKEN_INVALID("Reset token is invalid or expired")
// ⚠️ Đã có: USER_OTP_ALREADY_USED, USER_OTP_EXPIRED,
//            USER_OTP_ATTEMPTS_EXCEEDED, USER_OTP_INVALID, USER_OTP_RATE_LIMITED
```

### 3.6 Domain Entity mới: `PasswordResetOtp` (`domain/user/`)

```java
@Getter
public class PasswordResetOtp {
    private UUID otpId;
    private UUID userId;          // Bắt buộc có vì chỉ tạo OTP cho LOCAL user hợp lệ
    private String email;
    private String codeHash;      // BCrypt hash of 6-digit OTP
    private Instant otpExpiresAt;
    private int attemptCount;
    private Instant verifiedAt;
    private String resetTokenHash;
    private Instant resetTokenExpiresAt;
    private Instant consumedAt;
    private Instant createdAt;

    // Factory
    public static PasswordResetOtp create(String email, UUID userId, String codeHash, Instant otpExpiresAt) { ... }

    // Domain behaviour
    public void verifyOtp(String rawOtp, PasswordMatcher matcher, String resetTokenHash, Instant resetTokenExpiresAt, Instant now) {
        if (this.consumedAt != null || this.verifiedAt != null) throw new DomainException(UserErrorCode.USER_OTP_ALREADY_USED);
        if (now.isAfter(this.otpExpiresAt)) throw new DomainException(UserErrorCode.USER_OTP_EXPIRED);
        if (this.attemptCount >= 5) throw new DomainException(UserErrorCode.USER_OTP_ATTEMPTS_EXCEEDED);
        this.attemptCount++;
        if (!matcher.matches(rawOtp, this.codeHash)) {
            throw new DomainException(UserErrorCode.USER_OTP_INVALID);
        }
        this.verifiedAt = now;
        this.resetTokenHash = resetTokenHash;
        this.resetTokenExpiresAt = resetTokenExpiresAt;
    }

    public void validateResetToken(Instant now) {
        if (this.verifiedAt == null || this.consumedAt != null)
            throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
        if (this.resetTokenHash == null)
            throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
        if (now.isAfter(this.resetTokenExpiresAt))
            throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
    }
    
    public void consume(Instant now) {
        if (this.consumedAt != null) throw new DomainException(UserErrorCode.USER_RESET_TOKEN_INVALID);
        this.consumedAt = now;
    }
}
```

### 3.7 Repository Interface (`domain/user/`)

```java
public interface PasswordResetOtpRepository {
    void save(PasswordResetOtp otp);
    Optional<PasswordResetOtp> findActiveLatestByEmail(String email);
    Optional<PasswordResetOtp> findByResetTokenHash(String resetTokenHash);
    void invalidateActiveByEmail(String email);
}
```

### 3.8 Infrastructure

**`PasswordResetOtpJdbcRepository`** (`infrastructure/repository/user/`) — JDBC impl.

**`EmailProvider`** interface (`application/user/`):
```java
public interface EmailProvider {
    void sendOtp(String toEmail, String otpCode);
}
```

**`SmtpEmailProvider`** (`infrastructure/email/`) — dùng Spring `JavaMailSender`:
```java
@Component @RequiredArgsConstructor
public class SmtpEmailProvider implements EmailProvider {
    private final JavaMailSender mailSender;
    public void sendOtp(String toEmail, String otpCode) {
        // Build MimeMessage with HTML template
    }
}
```

### 3.9 Security Config
Thêm các endpoint password-reset vào whitelist (không yêu cầu JWT):
```
/api/v1/auth/password-reset/**  → permitAll()
```

---

## 4. Database Changes

### 4.1. Database Migration Execution
- **Công cụ:** Project đang dùng công cụ migration (ví dụ Flyway).
- **Vị trí file:** Đặt file ở `backend/src/main/resources/db/migration/`.
- **Naming convention:** Tiền tố `V124__` dựa theo latest migration (không hardcode 120 nếu đã có version lớn hơn).
- **Thực thi:** Khởi động backend service để auto apply (hoặc chạy manual tùy config).
- **Verify:** Kiểm tra local database có bảng mới với đầy đủ columns và indexes.

### 4.2. Migration: `V124__add_password_reset_otp.sql`

```sql
CREATE TABLE IF NOT EXISTS public.password_reset_otp (
    otp_id                  uuid         NOT NULL DEFAULT uuid_generate_v4(),
    user_id                 uuid         NOT NULL,
    email                   varchar      NOT NULL,
    code_hash               text         NOT NULL,
    otp_expires_at          timestamptz  NOT NULL,
    attempt_count           integer      NOT NULL DEFAULT 0,
    verified_at             timestamptz  NULL,
    reset_token_hash        text         NULL,
    reset_token_expires_at  timestamptz  NULL,
    consumed_at             timestamptz  NULL,
    created_at              timestamptz  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT password_reset_otp_pkey PRIMARY KEY (otp_id)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_otp_email
    ON public.password_reset_otp (email, created_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS ux_password_reset_otp_token_hash
    ON public.password_reset_otp (reset_token_hash)
    WHERE reset_token_hash IS NOT NULL;
```

**Ghi chú:**
- `code_hash`: BCrypt hash, không lưu plain text
- `otp_expires_at`: OTP hết hạn sau 5 phút
- `attempt_count`: Max 5 lần thử sai
- `reset_token_hash`: Hash của token UUID sinh ra sau khi verify OTP thành công, không lưu plain text
- `verified_at` / `consumed_at`: Đánh dấu các mốc hoàn thành trạng thái của OTP flow
- Bảng `otp_record` cũ (V5) dành cho Phone OTP → không tái sử dụng

---

## 5. Frontend Implementation Plan

### 5.1 Package Structure

```
ui/auth/forgotpassword/
├── ForgotPasswordActivity.java          // Container, quản lý fragment navigation
├── ForgotPasswordFlowViewModel.java     // Container-level shared ViewModel (chứa email, resetToken). Không cần Factory — chỉ giữ state thuần, không có injected dependency.
├── email/
│   ├── EmailInputFragment.java
│   ├── EmailInputViewModel.java
│   ├── EmailInputViewModelFactory.java
│   └── EmailInputUiState.java
├── otp/
│   ├── OtpVerifyFragment.java           // Reuse OtpInputView từ core/designsystem
│   ├── OtpVerifyViewModel.java
│   ├── OtpVerifyViewModelFactory.java
│   └── OtpVerifyUiState.java
└── newpassword/
    ├── NewPasswordFragment.java
    ├── NewPasswordViewModel.java
    ├── NewPasswordViewModelFactory.java
    └── NewPasswordUiState.java
```

### 5.2 Sửa file hiện có

**`AuthActivity.java`** — thay TODO bằng:
```java
tvForgotPassword.setOnClickListener(v ->
    startActivity(new Intent(this, ForgotPasswordActivity.class)));
```

**`UserRepository.java`** (frontend domain) — thêm 3 method:
```java
void requestPasswordReset(String email, DomainCallback<Void> callback);
void verifyPasswordReset(String email, String otp, DomainCallback<String> callback);
void confirmPasswordReset(String resetToken, String newPassword, DomainCallback<Void> callback);
```

**`UserRepositoryImpl.java`** — implement 3 method trên.

**`AuthApiService.java`** — thêm 3 endpoint Retrofit:
```java
@POST("api/v1/auth/password-reset/request")
Call<ApiResponse<Void>> requestPasswordReset(@Body RequestPasswordResetDto body);

@POST("api/v1/auth/password-reset/verify")
Call<ApiResponse<PasswordResetTokenDto>> verifyPasswordReset(@Body VerifyPasswordResetDto body);

@POST("api/v1/auth/password-reset/confirm")
Call<ApiResponse<Void>> confirmPasswordReset(@Body ConfirmPasswordResetDto body);
```

**`UserErrorMessageMapper.java`** — thêm case cho OTP errors:
```java
case "USER_OTP_EXPIRED":               return new ErrorResult(R.string.error_otp_expired, ActionType.TOAST);
case "USER_OTP_INVALID":               return new ErrorResult(R.string.error_otp_invalid, ActionType.TOAST);
case "USER_OTP_ALREADY_USED":          return new ErrorResult(R.string.error_otp_already_used, ActionType.TOAST);
case "USER_OTP_ATTEMPTS_EXCEEDED":     return new ErrorResult(R.string.error_otp_attempts, ActionType.TOAST);
case "USER_PASSWORD_TOO_WEAK":         return new ErrorResult(R.string.error_password_weak, ActionType.FIELD_ERROR);
case "USER_RESET_TOKEN_INVALID":       return new ErrorResult(R.string.error_reset_token, ActionType.TOAST);
case "USER_PASSWORD_RESET_NOT_ALLOWED": return new ErrorResult(R.string.error_password_reset_not_allowed, ActionType.TOAST);
```

### 5.3 DTOs mới (`data/datasource/remote/dto/`)

**Request** (`request/user/`):
- `RequestPasswordResetDto.java` — `{ email }`
- `VerifyPasswordResetDto.java` — `{ email, otp }`
- `ConfirmPasswordResetDto.java` — `{ resetToken, newPassword }`

**Response** (`response/user/`):
- `PasswordResetTokenDto.java` — `{ resetToken }`

### 5.4 UiState Design

```java
// EmailInputUiState
public class EmailInputUiState {
    private final boolean isLoading;
    private final String error;
    private final boolean otpSent;  // chuyển sang OtpVerifyFragment
}

// OtpVerifyUiState
public class OtpVerifyUiState {
    private final boolean isLoading;
    private final String error;
    private final String resetToken;       // != null → chuyển NewPasswordFragment
    private final int resendCooldownSec;   // countdown 60s
}

// NewPasswordUiState
public class NewPasswordUiState {
    private final boolean isLoading;
    private final String error;
    private final boolean isSuccess;       // → finish Activity, quay về Login
}
```

### 5.5 Layout Files (`res/layout/`)

- `activity_forgot_password.xml` — FrameLayout container cho fragments
- `fragment_email_input.xml` — WalkMateInputField(email) + WalkMateButton(Send OTP) + back arrow
- `fragment_otp_verify.xml` — OtpInputView + countdown TextView + WalkMateButton(Verify) + Resend link
- `fragment_new_password.xml` — 2x WalkMateInputField(password, confirm) + WalkMateButton(Reset)

### 5.6 Navigation Flow

```
ForgotPasswordActivity.onCreate()
  └── Khởi tạo ForgotPasswordFlowViewModel (shared state)
  └── show EmailInputFragment

EmailInputFragment → otpSent=true
  └── Ghi email vào FlowViewModel
  └── Activity replace → OtpVerifyFragment

OtpVerifyFragment → resetToken != null
  └── Ghi resetToken vào FlowViewModel
  └── Activity replace → NewPasswordFragment

NewPasswordFragment → isSuccess=true
  └── Toast "Password reset successfully" → Activity.finish()
  └── User quay về AuthActivity (Login)
```

### 5.7 Resend OTP & Countdown
- **Reuse `CountdownTimerView`** (đã có sẵn tại `core/designsystem/view/`) thay vì dùng `Handler` + `Runnable` trong ViewModel.
- Trong `fragment_otp_verify.xml`: khai báo `<com.walkmate.core.designsystem.view.CountdownTimerView>`.
- Khi gửi/resend OTP thành công: gọi `countdownView.startCountdown(System.currentTimeMillis() + 60_000L)`.
- Disable nút "Resend" khi bắt đầu đếm, dùng `countdownView.setOnExpiredListener(() -> btnResend.setEnabled(true))` để re-enable.
- `CountdownTimerView` tự cancel timer trong `onDetachedFromWindow()` — **không có memory leak, không cần cleanup trong ViewModel**.
- Gọi lại `viewModel.requestPasswordReset(email)` khi resend.

---

## 6. API Contract

### 6.1 `POST /api/v1/auth/password-reset/request`

**Request:**
```json
{ "email": "user@example.com" }
```

**Response 200 (luôn trả success để tránh email enumeration):**
```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": "2026-05-07T..."
}
```

**Error cases:**
| Code | HTTP | Khi nào |
|------|------|---------|
| `VALIDATION_ERROR` | 422 | Email format sai |

*Ghi chú: Lỗi rate-limit (`USER_OTP_RATE_LIMITED`) chỉ dùng nội bộ hoặc log, API sẽ silent drop email nhưng vẫn trả về 200 OK để tránh enumeration.*

### 6.2 `POST /api/v1/auth/password-reset/verify`

**Request:**
```json
{ "email": "user@example.com", "otp": "123456" }
```

**Response 200:**
```json
{
  "success": true,
  "data": { "resetToken": "550e8400-e29b-..." },
  "error": null,
  "timestamp": "2026-05-07T..."
}
```

**Error cases:**
| Code | HTTP | Khi nào |
|------|------|---------|
| `USER_OTP_INVALID` | 400 | Sai mã OTP |
| `USER_OTP_EXPIRED` | 400 | OTP hết hạn (>5 phút) |
| `USER_OTP_ALREADY_USED` | 400 | OTP đã dùng |
| `USER_OTP_ATTEMPTS_EXCEEDED` | 400 | Sai ≥5 lần |

### 6.3 `POST /api/v1/auth/password-reset/confirm`

*Lưu ý: `confirmPassword` (nhập lại mật khẩu) chỉ validate ở frontend, API chỉ nhận `newPassword`.*

**Request:**
```json
{ "resetToken": "550e8400-e29b-...", "newPassword": "NewPass123" }
```

**Response 200:**
```json
{
  "success": true,
  "data": null,
  "error": null,
  "timestamp": "2026-05-07T..."
}
```

**Error cases:**
| Code | HTTP | Khi nào |
|------|------|---------|
| `USER_RESET_TOKEN_INVALID` | 400 | Token sai hoặc hết hạn |
| `USER_PASSWORD_TOO_WEAK` | 400 | Password không đủ mạnh |

---

## 7. Security Rules

| Rule | Chi tiết |
|------|----------|
| OTP lưu dạng hash | BCrypt hash, không lưu plain text trong DB |
| OTP expiration | 5 phút |
| Attempt limit | Max 5 lần thử sai → khoá OTP đó |
| Resend cooldown / Rate limit | Với user hợp lệ: 60s/request (kiểm tra record gần nhất trong `password_reset_otp`). Với email không tồn tại/Google-only: trả 200 generic, không gửi email, không tạo record. (Không cần implement per-IP rate limit hay cache riêng cho school project). |
| Tránh email enumeration | `/request` luôn trả 200 OK dù có bị rate limited hay không (nếu rate limited thì silent drop email) |
| Reset token lưu dạng hash | Trả plain text 1 lần duy nhất, DB chỉ lưu hash (SHA-256) |
| Reset token expiry | 10 phút sau khi verify OTP thành công |
| Invalidate sau khi dùng | Gọi `invalidateActiveByEmail` để vô hiệu hóa OTP trước đó, dọn dẹp các reset token khi confirm. `consumed_at != null` nghĩa là record không còn usable (do đã confirm hoặc bị invalidate bởi request mới). |
| Scheduled Cleanup (Polish) | Nên có job dọn rác định kỳ xoá các bản ghi `password_reset_otp` đã expired/consumed quá 7-30 ngày. |
| Force re-login | (Optional) Nếu dự án có refresh token store: Revoke tất cả refresh tokens. Nếu chỉ dùng JWT stateless thì bỏ qua. |
| HTTPS only | Endpoint không yêu cầu JWT nhưng bắt buộc HTTPS |

---

## 8. Edge Cases

| Case | Xử lý |
|------|--------|
| Email không tồn tại | Trả success giả (tránh enumeration), không gửi email. Vẫn bị áp dụng rate-limit. |
| Email đăng ký qua Google only | Trả success giả, không gửi email. Frontend cần có dòng hướng dẫn chung: "Nếu tài khoản của bạn đăng nhập bằng Google, vui lòng quay lại..." |
| OTP hết hạn | Báo lỗi `USER_OTP_EXPIRED`, user nhấn Resend |
| Sai OTP ≥5 lần | Báo `USER_OTP_ATTEMPTS_EXCEEDED`, phải request OTP mới |
| User gửi nhiều OTP | OTP cũ tự invalidate khi tạo mới (chỉ lấy latest) |
| Password quá yếu | Validate ở Domain, trả `USER_PASSWORD_TOO_WEAK` |
| Network failure (FE) | ViewModel catch exception, hiện Toast, giữ nguyên state |
| App bị kill giữa flow | User mở lại app → về Login → nhấn Forgot lại từ đầu |
| Concurrent reset | Mỗi lần verify tạo resetToken mới, token cũ tự invalid |

---

## 9. Testing Plan

### 9.1 Backend Unit Tests

- `PasswordResetOtpTest` — verify(), attempt limit, expiry, token validation
- `UserTest` — resetPassword(), validatePasswordStrength()
- `UserCommandServiceTest` — mock repo + emailProvider, test 3 use cases

### 9.2 Backend Integration Tests

- `PasswordResetControllerTest`:
  - Happy path: request → verify → confirm
  - Email không tồn tại → vẫn 200
  - OTP sai → 400 (đảm bảo attempt_count vẫn được persist)
  - OTP hết hạn → 400
  - Attempt exceeded → 400
  - Rate limited → Request twice within cooldown -> both return 200, only first email is sent (Mock EmailProvider to verify)
  - Weak password → 400

### 9.3 Frontend ViewModel Tests

- `EmailInputViewModelTest` — validate email, call repo, handle error
- `OtpVerifyViewModelTest` — verify OTP, countdown timer, resend
- `NewPasswordViewModelTest` — validate match, call confirm, handle success/error

### 9.4 Manual QA Checklist

- [ ] Nhấn "Forgot password?" từ Login → mở đúng màn hình
- [ ] Nhập email hợp lệ → nhận OTP trong email
- [ ] Nhập OTP đúng → chuyển sang đặt mật khẩu mới
- [ ] Nhập OTP sai 5 lần → hiện lỗi attempts exceeded
- [ ] Đợi OTP hết hạn → hiện lỗi expired
- [ ] Resend OTP → nhận email mới, cooldown 60s
- [ ] Đặt mật khẩu mới thành công → quay về Login
- [ ] Đăng nhập bằng mật khẩu mới → thành công
- [ ] Đăng nhập bằng mật khẩu cũ → thất bại
- [ ] Email Google-only → không nhận OTP (silent)
- [ ] Nhấn Back giữa flow → quay lại bước trước
- [ ] Xoay màn hình → state giữ nguyên

---

## 10. Implementation Order

### Phase 1: Backend Core (2-3 ngày)
1. Migration `V124__add_password_reset_otp.sql`
2. Domain: `PasswordResetOtp` entity + `PasswordResetOtpRepository` interface
3. Domain: Thêm `UserErrorCode` mới + `User.resetPassword()`
4. Application: `EmailProvider` interface + 3 Commands
5. Application: Thêm 3 methods vào `UserCommandService`
6. Infrastructure: `PasswordResetOtpJdbcRepository`
7. Infrastructure: `SmtpEmailProvider` (thêm `spring-boot-starter-mail`)
8. Presentation: 3 Request DTOs + 1 Response DTO
9. Presentation: 3 endpoints trong `UserController`
10. Security: Whitelist password-reset endpoints

### Phase 2: Backend Testing (1 ngày)
11. Unit tests cho `PasswordResetOtp` + `UserCommandService`
12. Integration tests cho 3 endpoints

### Phase 3: Frontend (2-3 ngày)
13. **[Prerequisite]** Tạo `OtpInputView` tại `core/designsystem/view/` (6-digit OTP input: auto-focus-advance + backspace-to-prev, API: `getOtp()`, `clear()`, `setEnabled()`). *`OtpInputView` không tồn tại trong codebase — phải tạo trước khi build `OtpVerifyFragment`.*
14. DTOs: 3 request + 1 response
15. `AuthApiService`: 3 Retrofit methods
16. `UserRepository` interface: 3 methods
17. `UserRepositoryImpl`: implement 3 methods
18. `UserErrorMessageMapper`: thêm OTP error cases (7 cases bao gồm `USER_OTP_ALREADY_USED` và `USER_PASSWORD_RESET_NOT_ALLOWED`)
19. `ForgotPasswordActivity` + 3 Fragments + ViewModels + UiStates
20. Layout XML files (4 files)
21. String resources (error messages)
22. `AuthActivity`: wire up "Forgot password?" click

### Phase 4: Polish (1 ngày)
22. Countdown timer UI
23. Email HTML template
24. Manual QA

---

## 11. Files Checklist

### Backend — Tạo mới

| File | Layer | Trách nhiệm |
|------|-------|-------------|
| `V124__add_password_reset_otp.sql` | DB | Bảng password_reset_otp |
| `domain/user/PasswordResetOtp.java` | Domain | Rich entity: verify, validate |
| `domain/user/PasswordResetOtpRepository.java` | Domain | Interface |
| `application/user/RequestPasswordResetCommand.java` | Application | Command record |
| `application/user/VerifyPasswordResetCommand.java` | Application | Command record |
| `application/user/ConfirmPasswordResetCommand.java` | Application | Command record |
| `application/user/EmailProvider.java` | Application | Interface gửi email |
| `infrastructure/repository/user/PasswordResetOtpJdbcRepository.java` | Infra | JDBC impl |
| `infrastructure/email/SmtpEmailProvider.java` | Infra | JavaMailSender impl |
| `presentation/dto/request/user/RequestPasswordResetRequest.java` | Presentation | DTO |
| `presentation/dto/request/user/VerifyPasswordResetRequest.java` | Presentation | DTO |
| `presentation/dto/request/user/ConfirmPasswordResetRequest.java` | Presentation | DTO |
| `presentation/dto/response/user/PasswordResetTokenResponse.java` | Presentation | DTO |

### Backend — Sửa

| File | Thay đổi |
|------|----------|
| `domain/user/User.java` | Thêm `resetPassword()`, `validatePasswordStrength()` |
| `domain/user/UserErrorCode.java` | Thêm 3 error codes mới |
| `application/user/UserCommandService.java` | Thêm 3 methods |
| `presentation/controller/user/UserController.java` | Thêm 3 endpoints |
| `infrastructure/config/SecurityConfig.java` | Whitelist endpoints |
| `build.gradle` (backend) | Thêm `spring-boot-starter-mail` |

### Frontend — Tạo mới

| File | Layer | Trách nhiệm |
|------|-------|-------------|
| `core/designsystem/view/OtpInputView.java` | Core | 6-digit OTP input; auto-focus-advance + backspace-to-prev. API: `getOtp()`, `clear()`, `setEnabled()`. **Prerequisite cho OtpVerifyFragment.** |
| `ui/auth/forgotpassword/ForgotPasswordActivity.java` | UI | Container activity |
| `ui/auth/forgotpassword/email/EmailInputFragment.java` | UI | Nhập email |
| `ui/auth/forgotpassword/email/EmailInputViewModel.java` | UI | Xử lý gửi OTP |
| `ui/auth/forgotpassword/email/EmailInputViewModelFactory.java` | UI | DI |
| `ui/auth/forgotpassword/email/EmailInputUiState.java` | UI | State |
| `ui/auth/forgotpassword/otp/OtpVerifyFragment.java` | UI | Nhập OTP (dùng OtpInputView) |
| `ui/auth/forgotpassword/otp/OtpVerifyViewModel.java` | UI | Verify + countdown |
| `ui/auth/forgotpassword/otp/OtpVerifyViewModelFactory.java` | UI | DI |
| `ui/auth/forgotpassword/otp/OtpVerifyUiState.java` | UI | State |
| `ui/auth/forgotpassword/newpassword/NewPasswordFragment.java` | UI | Đặt mật khẩu mới |
| `ui/auth/forgotpassword/newpassword/NewPasswordViewModel.java` | UI | Confirm reset |
| `ui/auth/forgotpassword/newpassword/NewPasswordViewModelFactory.java` | UI | DI |
| `ui/auth/forgotpassword/newpassword/NewPasswordUiState.java` | UI | State |
| `data/datasource/remote/dto/request/user/RequestPasswordResetDto.java` | Data | DTO |
| `data/datasource/remote/dto/request/user/VerifyPasswordResetDto.java` | Data | DTO |
| `data/datasource/remote/dto/request/user/ConfirmPasswordResetDto.java` | Data | DTO |
| `data/datasource/remote/dto/response/user/PasswordResetTokenDto.java` | Data | DTO |
| `res/layout/activity_forgot_password.xml` | Resource | Container layout |
| `res/layout/fragment_email_input.xml` | Resource | Email input UI |
| `res/layout/fragment_otp_verify.xml` | Resource | OTP verify UI |
| `res/layout/fragment_new_password.xml` | Resource | New password UI |

### Frontend — Sửa

| File | Thay đổi |
|------|----------|
| `ui/auth/AuthActivity.java` | Wire "Forgot password?" → ForgotPasswordActivity |
| `domain/user/UserRepository.java` | Thêm 3 method interface |
| `data/repository/UserRepositoryImpl.java` | Implement 3 methods |
| `data/datasource/remote/api/AuthApiService.java` | Thêm 3 Retrofit calls |
| `core/util/UserErrorMessageMapper.java` | Thêm OTP error mappings |
| `AndroidManifest.xml` | Đăng ký ForgotPasswordActivity |
| `res/values/strings.xml` | Thêm error + UI strings |
