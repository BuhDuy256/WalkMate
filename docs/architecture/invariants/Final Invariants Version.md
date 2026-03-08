# 🎯 CHỐT INVARIANT CUỐI CÙNG

## 🟥 Invariant 1 – Session Non-Overlapping (Core Invariant)

> Một user không được có hai WalkSession có time window overlap với status ∈ (PENDING, ACTIVE).

### Formal Definition

Không tồn tại hai session S1, S2 sao cho:

```text
S1 ≠ S2
AND
U ∈ participants(S1)
AND
U ∈ participants(S2)
AND
S1.status ∈ (PENDING, ACTIVE)
AND
S2.status ∈ (PENDING, ACTIVE)
AND
overlap(S1.time_window, S2.time_window)
```

Nếu không overlap → hoàn toàn hợp lệ.

---

## 🟥 Invariant 2 – Intent Non-Overlapping (Coordination Guard)

> Một user không được có hai WalkIntent có time window overlap với status = OPEN.

Mục tiêu:

- Tránh conflict sớm
- Giảm proposal thừa
- Improve UX

Nhưng đây không phải invariant cuối cùng.

---

## 🟥 Invariant 3 – Proposal Exclusivity

> Một WalkIntent chỉ được có tối đa 1 MatchProposal ở trạng thái PENDING tại một thời điểm.

Mục tiêu:

- Tránh race condition
- Tránh double confirm

---

## 🟥 Invariant 4 – Session Creation Guard

Khi tạo WalkSession:

Phải kiểm tra lại Invariant 1.

Intent layer không đủ.

SessionService là last line of defense.

---

# 📌 Tóm lại: Chúng ta giữ đúng 4 invariant này

| #   | Invariant                              | Layer              |
| --- | -------------------------------------- | ------------------ |
| 1   | No overlapping sessions                | WalkSession (Core) |
| 2   | No overlapping OPEN intents            | WalkIntent         |
| 3   | One pending proposal per intent        | MatchProposal      |
| 4   | Session creation must re-check overlap | Domain Service     |

---

# 🔥 Điều quan trọng nhất

Invariant 1 là bất biến domain.

Các invariant còn lại chỉ là guard để:

- Giảm conflict
- Giảm race
- Improve UX

---

# 🧠 Lưu ý quan trọng

Chúng ta không còn dùng:

> “1 user chỉ có 1 active session”

Mà dùng:

> “Không có 2 session trùng time window”

Đây là version đúng với Scheduled Discovery.

---

# ✅ Chốt kiến trúc

Bây giờ bạn có:

- Core invariant rõ ràng
- Không mâu thuẫn với feature
- Không artificial constraint
- Không over-restrictive
- Không duplicate state
