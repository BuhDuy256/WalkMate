# Cách map các Domain → Lifecycle → State Machine → Invariants → Stress Test sang DB

Dưới đây là **cách map chuẩn từ Domain → Lifecycle → State Machine → Invariants → Stress Test → Database**.
Đây là pipeline thường dùng trong **DDD + Event-safe backend design** để đảm bảo DB **phản ánh đúng business rules**, không chỉ là nơi lưu dữ liệu.

Tôi sẽ trình bày theo **6 bước tuần tự**, kèm ví dụ với **WalkSession** để bạn thấy cách áp dụng.

---

# 1. Domain Model → Aggregate & Entities

Bước đầu tiên là xác định **Domain Objects**.

Ví dụ từ feature Walk:

**Aggregate Root**

```
WalkSession
```

**Entities**

```
WalkParticipant
WalkTrace
WalkSegment
```

**Value Objects**

```
Distance
Duration
Calories
StepCount
GeoPoint
```

### Mapping sang DB

| Domain         | DB               |
| -------------- | ---------------- |
| Aggregate Root | main table       |
| Entity         | child table      |
| Value Object   | embedded columns |

Ví dụ:

```
walk_session
walk_participant
walk_trace
```

---

# 2. Lifecycle → Domain States

Lifecycle là **các trạng thái hợp lệ của Aggregate**.

Ví dụ WalkSession lifecycle:

```
PENDING
READY
ACTIVE
PAUSED
ENDED
CANCELLED
```

### Mapping sang DB

Lifecycle → **ENUM column**

```
walk_session.status
```

Ví dụ:

```sql
CREATE TYPE walk_session_status AS ENUM (
  'PENDING',
  'READY',
  'ACTIVE',
  'PAUSED',
  'ENDED',
  'CANCELLED'
);
```

---

# 3. State Machine → Valid Transitions

State Machine xác định **transition hợp lệ**.

Ví dụ:

```
PENDING → READY
READY → ACTIVE
ACTIVE → PAUSED
PAUSED → ACTIVE
ACTIVE → ENDED
READY → CANCELLED
```

### Mapping sang DB

Có 3 cách phổ biến.

---

### Option A — enforce ở Application Layer (phổ biến nhất)

Backend UseCase kiểm tra:

```
if session.status != ACTIVE:
    reject
```

DB chỉ lưu state.

---

### Option B — CHECK constraint

Ví dụ:

```sql
CHECK (
   status != 'ACTIVE'
   OR started_at IS NOT NULL
)
```

---

### Option C — Transition table (hiếm nhưng rất mạnh)

```
state_transition
```

| from    | to     |
| ------- | ------ |
| PENDING | READY  |
| READY   | ACTIVE |
| ACTIVE  | PAUSED |

---

# 4. Invariants → Database Constraints

Invariants là **rules không bao giờ được vi phạm**.

Ví dụ:

### Invariant 1

```
User không được có 2 walk session ACTIVE
```

### Mapping

Postgres:

```sql
CREATE UNIQUE INDEX unique_active_session
ON walk_session(user_id)
WHERE status = 'ACTIVE';
```

---

### Invariant 2

```
Session phải có đúng 2 participants
```

Mapping:

Trigger hoặc constraint.

---

### Invariant 3

```
Session ended_at >= started_at
```

Mapping:

```sql
CHECK (ended_at >= started_at)
```

---

# 5. Stress Test → Race Conditions

Stress Test là **tìm các conflict concurrency**.

Ví dụ:

```
User A → pause
User B → end
```

cùng lúc.

### Mapping sang DB

3 kỹ thuật chính:

---

### 1️⃣ Optimistic Locking

DB column:

```
version
```

```
UPDATE walk_session
SET status='PAUSED', version=version+1
WHERE id=? AND version=?
```

---

### 2️⃣ Transaction isolation

```
SELECT FOR UPDATE
```

---

### 3️⃣ Idempotency keys

Để tránh duplicate actions.

---

# 6. Domain Events → Event Tables

Domain events nên được lưu lại.

Ví dụ:

```
WalkStarted
WalkPaused
WalkResumed
WalkEnded
```

### Mapping

```
walk_event
```

```
id
session_id
event_type
payload
created_at
```

---

# 7. Tổng hợp pipeline mapping

Pipeline đầy đủ:

```
Domain
 ↓
Aggregate
 ↓
Lifecycle
 ↓
State Machine
 ↓
Invariants
 ↓
Stress Test
 ↓
Database Schema
```

---

# 8. Ví dụ hoàn chỉnh (WalkSession)

### Domain

```
WalkSession
```

---

### Lifecycle

```
PENDING
READY
ACTIVE
PAUSED
ENDED
CANCELLED
```

---

### State Machine

```
READY → ACTIVE
ACTIVE → PAUSED
PAUSED → ACTIVE
ACTIVE → ENDED
READY → CANCELLED
```

---

### Invariants

```
User chỉ có 1 ACTIVE session
Session có 2 participants
Duration >= 0
```

---

### Stress Test

```
pause vs end
resume vs end
start vs cancel
```

---

### Database

```
walk_session
walk_participant
walk_trace
walk_event
```

---

# 9. Cheat Sheet (rất quan trọng)

| Domain concept | DB mapping       |
| -------------- | ---------------- |
| Aggregate      | main table       |
| Entity         | child table      |
| Value Object   | columns          |
| Lifecycle      | ENUM             |
| State machine  | app rules        |
| Invariant      | constraint/index |
| Domain event   | event table      |
| Concurrency    | version/locking  |

---

# 10. Quy tắc vàng

Database **không chỉ lưu dữ liệu**.

Database phải **bảo vệ invariants cuối cùng của domain**.

```
Application = first defense
Database = final defense
```
