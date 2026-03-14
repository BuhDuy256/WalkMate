# Cách xác định Use Cases khi được yêu cầu implement một feature flow

Yêu cầu: Tự nhìn vào feature và xác định theo các step dưới (không nhờ AI làm thế được)

# 1️⃣ Bắt đầu từ Actor

Trước tiên hỏi:

```text
Ai đang tương tác với hệ thống?
```

Actor có thể là:

- User
- Admin
- External service
- Scheduled job

Ví dụ:

```
Customer
Admin
Payment service
```

---

# 2️⃣ Hỏi: Actor muốn đạt được mục tiêu gì?

Đây là câu hỏi quan trọng nhất.

```text
Actor muốn đạt outcome gì?
```

Ví dụ:

Customer muốn:

```
Place order
Cancel order
Track order
```

Admin muốn:

```
Approve refund
Update product price
```

Mỗi **goal** thường chính là một **Use Case**.

---

# 3️⃣ Viết Use Case dưới dạng hành động

Use Case luôn là **verb phrase**.

Ví dụ:

| Use Case       | Actor    |
| -------------- | -------- |
| Register User  | User     |
| Place Order    | Customer |
| Approve Refund | Admin    |

Nếu nó không phải hành động thì thường **không phải Use Case**.

Sai:

```
UserService
EmailValidator
PriceCalculator
```

Đúng:

```
RegisterUser
CreateOrder
PayInvoice
```

---

# 4️⃣ Kiểm tra: Use Case có tạo ra thay đổi trong hệ thống không?

Một Use Case thường:

- tạo dữ liệu
- thay đổi trạng thái
- kích hoạt workflow

Ví dụ:

```
PlaceOrder
```

Flow:

```
create order
reserve inventory
calculate total
save order
send event
```

---

# 5️⃣ Xác định System Boundary

Use Case là **ranh giới giữa external world và domain**.

Trong **Clean Architecture** flow sẽ như sau:

```
Controller
   ↓
Use Case
   ↓
Domain Entities
   ↓
Repositories
```

Use Case:

- nhận input
- điều phối domain logic
- trả kết quả

---

# 6️⃣ Một cách thực hành rất mạnh

Hãy viết **User Story**.

Format:

```
As a <actor>
I want to <action>
So that <goal>
```

Ví dụ:

```
As a customer
I want to place an order
So that I can purchase products
```

→ **PlaceOrder** là Use Case.

---

# 7️⃣ Ví dụ đầy đủ

Hệ thống e-commerce.

Actors:

```
Customer
Admin
Payment service
```

Use Cases:

```
RegisterUser
LoginUser
PlaceOrder
CancelOrder
PayOrder
RefundOrder
UpdateProduct
```

Mỗi cái này trở thành:

```
PlaceOrderUseCase
CancelOrderUseCase
```

---

# 8️⃣ Một rule rất dễ nhớ

Nếu hành động trả lời câu hỏi:

```
User đang làm gì với hệ thống?
```

→ đó là **Use Case**.

Nếu hành động trả lời:

```
Hệ thống tính toán như thế nào?
```

→ đó là **Domain logic**.
