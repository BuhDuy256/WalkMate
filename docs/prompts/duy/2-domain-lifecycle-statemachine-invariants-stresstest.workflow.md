# Cách xác định **Domain → Lifecycle → State Machine → Invariants → Stress Test** từ yêu cầu implement Feature Flow

Tài liệu này mô tả một **quy trình tư duy có hệ thống** để chuyển từ **yêu cầu feature / business flow** sang **mô hình domain rõ ràng và có thể kiểm chứng được**.

Mục tiêu của quy trình này là:

- Tránh code theo cảm tính
- Xác định đúng **domain model**
- Làm rõ **state transitions**
- Bảo vệ hệ thống bằng **invariants**
- Kiểm tra thiết kế bằng **stress test**

Quy trình gồm 5 bước:

```
Feature Flow
    ↓
Domain
    ↓
Lifecycle
    ↓
State Machine
    ↓
Invariants
    ↓
Stress Test
```

---

# 1. Xác định Domain

## Mục tiêu

Tìm ra **những đối tượng cốt lõi của hệ thống**.

Domain không phải là:

- table
- API
- service

Domain là **concept của business**.

## Câu hỏi cần trả lời

```
Hệ thống đang quản lý cái gì?
```

Ví dụ:

### Feature

User có thể đặt hàng.

### Domain Objects

```
User
Order
Product
Payment
Inventory
```

Trong đó cần xác định:

```
Entity
Value Object
Aggregate
```

Ví dụ:

```
Order (Aggregate)
    OrderItem (Entity)
    Money (Value Object)
```

## Kết quả của bước này

Bạn phải có được:

```
Domain Model
```

Ví dụ:

```
User
Order
Product
Payment
Inventory
```

---

# 2. Xác định Lifecycle

## Mục tiêu

Hiểu **vòng đời của mỗi Domain Entity**.

Câu hỏi quan trọng:

```
Đối tượng này được tạo khi nào?
Nó thay đổi như thế nào?
Nó kết thúc khi nào?
```

### Ví dụ: Order

Lifecycle:

```
Created
↓
Confirmed
↓
Paid
↓
Shipped
↓
Completed
```

Hoặc:

```
Created
↓
Cancelled
```

## Kết quả

Ta có:

```
Order Lifecycle
```

---

# 3. Xây dựng State Machine

Sau khi có lifecycle, ta formalize nó thành **state machine**.

## State

```
CREATED
CONFIRMED
PAID
SHIPPED
COMPLETED
CANCELLED
```

## Transitions

```
createOrder()
confirmOrder()
payOrder()
shipOrder()
completeOrder()
cancelOrder()
```

### State Machine Diagram

```
CREATED
   │
   ├── confirm → CONFIRMED
   │
   └── cancel → CANCELLED

CONFIRMED
   │
   └── pay → PAID

PAID
   │
   └── ship → SHIPPED

SHIPPED
   │
   └── complete → COMPLETED
```

## Mục tiêu

State machine giúp:

- tránh trạng thái không hợp lệ
- làm rõ business logic
- giảm bug

---

# 4. Xác định Invariants

Invariant là **điều luôn phải đúng trong domain**.

Nó là **hợp đồng của domain model**.

## Ví dụ

### Order invariants

```
1. Order phải có ít nhất 1 item
2. Tổng tiền >= 0
3. Không thể ship nếu chưa pay
4. Không thể pay nếu order đã cancelled
5. Không thể modify order sau khi shipped
```

## Tại sao Invariant quan trọng

Invariant:

- bảo vệ domain
- giảm bug
- giúp reasoning

Nếu invariant bị phá vỡ → system đã sai.

---

# 5. Stress Test Design

Sau khi có:

```
Domain
Lifecycle
State Machine
Invariants
```

ta cần **tấn công thiết kế** để xem nó có vỡ không.

## Các câu hỏi stress test

### 1. Concurrency

```
Nếu 2 request payOrder cùng lúc thì sao?
```

### 2. Retry

```
Nếu payment retry 3 lần thì sao?
```

### 3. Partial failure

```
Payment thành công nhưng DB fail thì sao?
```

### 4. Race conditions

```
cancelOrder và payOrder cùng lúc?
```

### 5. Distributed system

```
payment service delay?
inventory service timeout?
```

---

# Ví dụ Stress Test

### Case

```
payOrder()
```

Stress:

```
double payment
retry payment
payment success nhưng save fail
payment timeout
```

## Kết quả

Từ đây ta phát hiện cần:

```
idempotency
transaction boundary
eventual consistency
```

---

# Tổng kết

Quy trình đầy đủ:

```
1. Feature Flow
      ↓
2. Domain
      ↓
3. Lifecycle
      ↓
4. State Machine
      ↓
5. Invariants
      ↓
6. Stress Test
```

Ý nghĩa của từng bước:

| Step          | Mục tiêu                    |
| ------------- | --------------------------- |
| Domain        | xác định business objects   |
| Lifecycle     | hiểu vòng đời               |
| State Machine | formalize state transitions |
| Invariants    | bảo vệ domain               |
| Stress Test   | kiểm tra thiết kế           |

---

# Lợi ích của quy trình này

Áp dụng quy trình này giúp:

```
clear domain model
predictable system behaviour
less bugs
better system design
```

Đây là cách nhiều system architect và domain-driven design practitioners sử dụng để **thiết kế hệ thống trước khi viết code**.

---

# Gợi ý áp dụng

Trước khi implement feature, hãy viết nhanh:

```
Domain
Lifecycle
State Machine
Invariants
Edge cases
```

Chỉ cần 10–15 phút, nhưng nó giúp **clarify bài toán rất mạnh**.

---
