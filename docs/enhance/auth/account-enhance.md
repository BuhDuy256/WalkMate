Các app lớn thường **không coi “Google account” và “Email/Password account” là 2 user khác nhau nếu cùng email đã được xác minh**. Họ tách khái niệm:

```text
User account = hồ sơ chính của user trong app
Login methods / providers = các cách đăng nhập vào cùng user đó
```

Ví dụ một user có thể có:

```text
user_id = 123
email = user@gmail.com
providers = GOOGLE, LOCAL_PASSWORD
password_hash = ...
google_sub = ...
```

Firebase/Auth platform cũng đi theo hướng này: một user có thể link nhiều provider như Google, email/password, Facebook vào cùng một account, và sau khi link thì user có thể đăng nhập bằng bất kỳ provider nào mà vẫn vào cùng dữ liệu. ([Firebase][1]) Google Identity Platform cũng có flow xử lý trường hợp “account exists with different credential” và yêu cầu link account thay vì tạo trùng. ([Google Cloud Documentation][2])

## Trường hợp của bạn hiện tại

Hiện WalkMate đang xử lý kiểu:

```text
Google Sign-In tạo user bằng email A
Sau đó Register bằng password với email A
→ báo email đã tồn tại
```

Về mặt kỹ thuật, lỗi này **không sai**. Nhưng UX chưa tốt, vì user thật sự đang muốn “thêm mật khẩu cho account hiện có”, không phải tạo account mới.

Với WalkMate, mình khuyên xử lý theo 2 hướng sau.

## Cách các app lớn thường làm

### 1. Nếu user đang đăng nhập rồi: cho “Set Password” trong Profile/Security

Đây là cách sạch nhất.

Flow:

```text
User đăng nhập bằng Google
→ vào Profile / Account Security
→ thấy mục Password: Not set
→ bấm Set Password
→ app gửi OTP email hoặc yêu cầu re-auth Google
→ user nhập password mới
→ account được link thêm LOCAL_PASSWORD
```

Sau đó user có thể đăng nhập bằng cả:

```text
Continue with Google
hoặc
Email + Password
```

Đây là hướng rất phổ biến trong các app có nhiều login methods: user quản lý provider trong phần “Account”, “Security”, “Login methods”, “Connected accounts”.

Với school project, bạn có thể làm đơn giản:

```text
Profile → Security → Set Password
```

Không cần làm màn hình “Connected Accounts” phức tạp ngay.

### 2. Nếu user chưa đăng nhập và bấm Register bằng email đã tồn tại

Đừng chỉ hiện “Email already exists”. Nên hiện message có định hướng:

```text
Email này đã có tài khoản WalkMate.
Vui lòng đăng nhập bằng Google, hoặc dùng Forgot Password nếu bạn đã từng đặt mật khẩu.
```

Nếu account đó là Google-only, có thể hiện:

```text
Email này đã được đăng ký bằng Google. Vui lòng chọn Continue with Google.
Sau khi đăng nhập, bạn có thể đặt thêm mật khẩu trong Profile.
```

Nhưng lưu ý: nếu bạn muốn tránh lộ thông tin account, message càng generic càng tốt. Với school project, hiện rõ như trên vẫn ổn và dễ demo.

### 3. Nếu user đăng nhập Google bằng email đã có password account

Trường hợp ngược lại:

```text
User đã đăng ký email/password trước
Sau đó bấm Continue with Google cùng email
```

App lớn thường **link Google vào account cũ**, miễn là email từ Google là verified. Không nên tạo user mới. Google Sign-In trả về email đã xác minh từ Google, nên dùng được để chứng minh user sở hữu email đó ở mức hợp lý.

Flow nên là:

```text
Google login success
→ backend nhận googleIdToken
→ verify token
→ lấy email + googleSub + emailVerified
→ tìm user theo email
→ nếu user tồn tại:
    - nếu chưa có GOOGLE provider → link googleSub vào user đó
    - login user đó
→ nếu user chưa tồn tại:
    - tạo user mới với GOOGLE provider
```

## Data model nên đổi như nào?

Hiện vấn đề của bạn đến từ việc `users` có thể đang dùng một field provider kiểu:

```text
provider = GOOGLE hoặc LOCAL
```

Cách này sẽ bí khi một user có cả hai.

Với app lớn, model đúng hơn là:

```text
users
- user_id
- email
- email_verified
- password_hash nullable
- display_name
- avatar_url
- created_at
- updated_at
```

Và bảng provider riêng:

```text
user_auth_providers
- id
- user_id
- provider_type: LOCAL | GOOGLE
- provider_user_id nullable
- provider_email
- created_at
```

Nhưng với school project, bạn có thể làm đơn giản hơn:

```text
users
- user_id
- email
- email_verified
- password_hash nullable
- google_sub nullable
- has_password boolean hoặc suy ra từ password_hash != null
```

Không cần bảng `user_auth_providers` ngay nếu chỉ có 2 provider: Google và password.

## Policy mình đề xuất cho WalkMate

Nên chọn policy này:

```text
Một email verified chỉ tương ứng với một WalkMate user.
Một WalkMate user có thể có nhiều cách đăng nhập.
```

Cụ thể:

```text
Google-only user:
- password_hash = null
- google_sub != null
- email_verified = true
- Có thể login bằng Google
- Không login được bằng password
- Có thể Set Password trong Profile

Password user:
- password_hash != null
- google_sub = null hoặc not null
- Có thể login bằng password
- Nếu login Google cùng email verified → link Google vào cùng user

Linked user:
- password_hash != null
- google_sub != null
- Có thể login bằng cả hai cách
```

Điều này rất hợp với WalkMate vì toàn bộ dữ liệu như profile, bạn bè, matching, lịch sử đi bộ, reputation phải nằm dưới **một user_id duy nhất**. Nếu tạo 2 account riêng cho cùng email, sau này dữ liệu matching/session sẽ bị phân mảnh, rất khó sửa.

## Vậy Forgot Password nên xử lý sao?

Forgot Password chỉ áp dụng cho account có `password_hash != null`.

Nếu user nhập email Google-only ở Forgot Password:

```text
Return 200 generic
Không gửi OTP reset password
UI có helper text:
"Nếu bạn dùng Google để đăng nhập, hãy quay lại và chọn Continue with Google."
```

Điều này đang khá khớp với plan Forgot Password hiện tại: plan đã ghi Google Sign-In users không cần reset password trừ khi đã link LOCAL credentials, và flow Forgot Password bắt đầu từ Login Page bằng Email OTP. 

## Profile nên thêm gì?

Mình khuyên thêm một section nhỏ:

```text
Profile / Account Security

Email
- user@gmail.com
- Verified

Login methods
- Google: Connected
- Password: Not set / Set / Change
```

Button logic:

```text
Nếu password_hash == null:
  Show "Set Password"

Nếu password_hash != null:
  Show "Change Password"

Nếu google_sub == null:
  Show "Connect Google" hoặc chưa cần làm
```

Với school project, chỉ cần làm:

```text
Password: Not set
[Set Password]
```

và:

```text
Password: Set
[Change Password]
```

## Flow “Set Password” cho Google-only user

Có 2 cách:

### Cách đơn giản cho school project

User đang login bằng Google rồi, nghĩa là đã xác thực. Cho họ set password trực tiếp:

```text
Profile → Set Password
→ nhập new password + confirm
→ backend update password_hash
```

Backend yêu cầu JWT, nên chỉ chính user đang đăng nhập mới gọi được.

Đây là đủ cho school project.

### Cách chắc hơn

Yêu cầu xác thực lại:

```text
Profile → Set Password
→ gửi OTP về email
→ nhập OTP
→ set password
```

Cách này an toàn hơn nhưng nhiều việc hơn, gần giống Forgot Password.

## Backend endpoint nên có thêm

Ngoài Forgot Password, nên thêm:

```http
POST /api/v1/users/me/password
```

hoặc nếu auth nằm trong auth controller:

```http
POST /api/v1/auth/me/password
```

Request:

```json
{
  "newPassword": "NewPass123"
}
```

Yêu cầu JWT.

Logic:

```text
currentUserId từ JWT
→ load user
→ validate password strength
→ hash password
→ user.setPasswordHash(...)
→ nếu chưa có LOCAL provider thì add LOCAL provider
→ save
```

Nếu sau này có Change Password cho user đã có password:

```http
PUT /api/v1/users/me/password
```

Request:

```json
{
  "currentPassword": "OldPass123",
  "newPassword": "NewPass456"
}
```

Với school project, có thể chỉ cần một endpoint:

```text
setOrChangePassword
```

Nếu `password_hash == null` thì không cần currentPassword. Nếu `password_hash != null` thì yêu cầu currentPassword.

## Kết luận

Các app lớn thường dùng **account linking**, không tách Google và Password thành hai account riêng. Với WalkMate, mình đề xuất:

```text
1. Giữ email unique trong users.
2. Cho một user có nhiều login methods.
3. Google login cùng email với password account → link vào user cũ.
4. Register password bằng email đã Google-only → không tạo user mới; hướng user login Google rồi Set Password trong Profile.
5. Thêm Profile → Security → Set/Change Password.
6. Forgot Password chỉ dành cho account đã có password_hash.
```

Nếu chỉ làm school project, bạn có thể implement MVP theo thứ tự:

```text
Phase 1: Sửa Google login để link vào user có cùng email.
Phase 2: Thêm Set Password trong Profile cho Google-only user.
Phase 3: Làm Forgot Password cho user có password_hash.
```

Điều này sẽ giải quyết sạch vấn đề “email đã tồn tại” mà không làm vỡ dữ liệu user/matching/session hiện có.

[1]: https://firebase.google.com/docs/auth/web/account-linking?utm_source=chatgpt.com "Link Multiple Auth Providers to an Account Using Java"
[2]: https://docs.cloud.google.com/identity-platform/docs/link-accounts?utm_source=chatgpt.com "Linking multiple providers to an account | Identity Platform | Google ..."
