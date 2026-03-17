Tôi đang thiết kế hệ thống WalkMate với WalkSession là Aggregate chính.

Hãy tạo 3 file tài liệu (Markdown, tiếng Việt có dấu):

1. Backend State Machine
- Liệt kê đầy đủ các trạng thái của WalkSession (domain state)
- Với mỗi transition:
  + Trigger (API / system event)
  + Guard conditions (invariants)
  + Side effects
- Phân biệt:
  + Command-triggered transitions
  + Time-based transitions
- Đảm bảo tuân thủ invariants:
  + Mutual confirmation
  + Single active session

2. Frontend State Machine
- Liệt kê các UI state (bao gồm loading, syncing, error)
- Phân biệt:
  + UI state
  + Derived state từ backend
- Mô tả hành vi UI khi chuyển state

3. State Mapping
- Mapping giữa Backend state và Frontend state
- Chỉ rõ:
  + 1-n mapping
  + Derived/composite states
- Chỉ rõ mismatch cases (eventual consistency, syncing delay)

4. Use Case Data Flow
- Cho từng use case:
  + activate
  + cancel
  + abort
  + complete
  + append points
- Mô tả:
  + Actor
  + Input
  + Flow (FE → BE → Domain → DB)
  + Side effects
  + Failure cases

Yêu cầu:
- Tuân thủ DDD (Application Service không chứa business rule)
- Domain là nơi enforce invariant
- Backend là source of truth
- Frontend là eventual consistent replica