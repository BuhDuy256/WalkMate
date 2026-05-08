Có. Mình propose một hướng giải quyết **không cần làm lại toàn bộ AI Matching**, nhưng biến nó từ “weighted scoring đơn giản” thành một flow ổn định hơn, chống bug tốt hơn, và vẫn phù hợp giai đoạn app gần hoàn thành.

## Đề xuất tổng thể: tách AI Matching thành 4 lớp bảo vệ

Thay vì để AI score quyết định trực tiếp, ta nên chia thành:

```text
Hard Filter
→ Safety / Lifecycle Guard
→ Scoring Engine
→ Learning Engine
```

Trong đó:

* **Hard Filter** giữ các điều kiện bắt buộc: hotspot, thời gian, age, gender, block.
* **Safety / Lifecycle Guard** chặn các case sai trạng thái, re-match người vừa reject, private intent lọt vào public pool, intent đã cancel/expire.
* **Scoring Engine** tính điểm nhưng phải có cap, penalty, cooldown.
* **Learning Engine** học từ review/report nhưng có chống spam, rollback, decay và không để time overlap bị “chết dần”.

Đây là hướng improve an toàn vì không phá kiến trúc hiện tại: backend vẫn theo DDD-lite, business rule nên nằm trong domain/service thay vì controller hoặc infrastructure. 

---

# 1. Sửa scoring trước: đảm bảo điểm luôn nằm trong 0–100

## Vấn đề hiện tại

`S_time` và `S_tags` bị cap 100, nhưng `S_trust = Candidate Trust Score / 10` có thể vượt 100 nếu trust score vượt 1000. Khi đó `S_trust` phá vỡ giới hạn ảnh hưởng của `weightBehavior`.

## Giải pháp

Tạo một rule cứng:

```text
Mọi score component bắt buộc nằm trong [0, 100]
```

Cụ thể:

```java
S_trust = clamp(candidateTrustScore / 10.0, 0.0, 100.0);
S_time = clamp(computeTimeScore(...), 0.0, 100.0);
S_tags = clamp(computeTagScore(...), 0.0, 100.0);
```

Như vậy `MAX_WEIGHT_CAP = 0.70` mới thật sự có ý nghĩa.

**Mức ưu tiên:** rất cao.
**Độ khó:** thấp.
**Ảnh hưởng UX:** không phá flow hiện tại.

---

# 2. Sửa time overlap: không để 60 phút là “perfect” cho mọi case

## Vấn đề hiện tại

Overlap 60 phút, 90 phút, 180 phút đều bằng 100. Điều này sai trong case user muốn đi lâu hơn.

## Giải pháp tốt hơn

Chấm time score dựa trên **tỷ lệ overlap so với thời lượng mong muốn**, không chỉ so với 60 phút.

Ví dụ:

```text
desiredDuration = min(userIntentDuration, candidateIntentDuration)
overlapRatio = overlapMinutes / desiredDuration
S_time = clamp(overlapRatio × 100, 0, 100)
```

Ví dụ:

* User A muốn đi 180 phút.
* Candidate B overlap 60 phút → `33 điểm`.
* Candidate C overlap 180 phút → `100 điểm`.

Nếu muốn vẫn ưu tiên match tối thiểu nhanh, có thể dùng công thức hybrid:

```text
S_time = 70% × overlapRatioScore + 30% × minimumViabilityScore
```

Nhưng với app gần release, mình đề xuất dùng bản đơn giản:

```text
S_time = overlapMinutes / desiredDuration × 100
```

Có thể thêm minimum guarantee vì hard filter đã đảm bảo `overlap >= MIN_WALK_DURATION`.

**Mức ưu tiên:** cao.
**Độ khó:** thấp đến trung bình.
**Ảnh hưởng UX:** match hợp lý hơn rõ rệt.

---

# 3. Sửa empty profile: không thưởng cho hồ sơ rỗng

## Vấn đề hiện tại

Nếu cả hai user không có tag, `S_tags = 50`. Điều này vô tình tạo điểm tốt cho incomplete profile.

## Giải pháp

Đổi rule:

```text
Nếu cả hai đều không có tag → S_tags = 0 hoặc 20
Nếu một bên không có tag → S_tags = 0 hoặc 10
Nếu cả hai có tag → dùng Jaccard similarity
```

Mình đề xuất:

```java
if (userTags.isEmpty() && candidateTags.isEmpty()) return 20.0;
if (userTags.isEmpty() || candidateTags.isEmpty()) return 10.0;
return jaccardSimilarity * 100.0;
```

Không nên trả 0 tuyệt đối nếu app chưa bắt buộc onboarding tag, vì sẽ làm user mới khó match. Nhưng `20` là đủ thấp để không “thưởng” profile rỗng.

Product-wise, nên hiển thị nhẹ ở UI:

```text
Thêm sở thích để WalkMate ghép bạn phù hợp hơn.
```

**Mức ưu tiên:** cao.
**Độ khó:** thấp.
**Ảnh hưởng UX:** tăng động lực hoàn thiện hồ sơ.

---

# 4. Chống re-match ngay sau reject/pass

## Vấn đề hiện tại

Theo lifecycle, public proposal bị reject/expired có thể đưa intent từ `MATCHING → OPEN`. Nhưng nếu AI vẫn chấm người kia cao nhất, hệ thống có thể ghép lại ngay cùng người đó. Public recovery `MATCHING → OPEN` chỉ hợp lệ cho public intent, và caller cần exclude partner vừa reject. 

## Giải pháp

Thêm bảng hoặc record nhẹ:

```text
match_exclusion
- user_id
- excluded_user_id
- reason: REJECTED | PASSED | EXPIRED | REPORTED | BLOCKED_LITE
- expires_at
```

Rule:

* Nếu user reject/pass candidate → exclude 24h hoặc đến hết time window của intent hiện tại.
* Nếu proposal expired do bên kia không phản hồi → exclude ngắn hơn, ví dụ 15–30 phút.
* Nếu report → exclude lâu hơn hoặc vĩnh viễn tùy policy.
* Nếu block thật → dùng block table hiện tại, không cần match_exclusion.

Trong AI matching hard filter hoặc lifecycle guard, loại các candidate nằm trong exclusion list.

**Mức ưu tiên:** rất cao.
**Độ khó:** trung bình.
**Ảnh hưởng UX:** tránh cảm giác “app ngu, vừa từ chối lại hiện lại”.

---

# 5. Thêm Lifecycle Guard trước khi tạo proposal

## Vấn đề hiện tại

AI có thể tính điểm xong nhưng trước lúc tạo proposal thì intent đã bị cancel, expire, hoặc bị một transaction khác lock.

Tài liệu state lifecycle đã cảnh báo race condition và đề xuất optimistic locking/versioning cho `WalkIntent`, `MatchProposal`, `WalkSession`. 

## Giải pháp

Không cho `AiWeightedMatchingStrategy` tạo proposal trực tiếp theo kiểu “tìm thấy là tạo”. Nên có service trung gian:

```text
MatchingOrchestrator
```

Flow:

```text
1. Query candidates
2. Score candidates
3. Pick best candidate
4. Re-load both intents inside transaction
5. Validate:
   - both intents still OPEN
   - both are public if public matching
   - not expired
   - not cancelled
   - no schedule conflict
   - not excluded
   - version unchanged
6. Move both OPEN → MATCHING
7. Create MatchProposal PENDING
8. Commit
```

Nếu bước 5 fail, thử candidate kế tiếp trong ranked list thay vì trả lỗi ngay.

Đây là điểm rất quan trọng: **AI chỉ đề xuất ranking, domain transaction mới quyết định có được match thật hay không.**

**Mức ưu tiên:** rất cao.
**Độ khó:** trung bình đến cao.
**Ảnh hưởng UX:** giảm bug “match ma”, “đã cancel vẫn nhận proposal”.

---

# 6. Khóa private invite khỏi public matching tuyệt đối

## Vấn đề hiện tại

Private intent có lifecycle riêng: được tạo ở `MATCHING`, không được quay lại `OPEN`, không thuộc public pool. Nếu dùng chung recovery logic với public intent sẽ rất dễ bug. 

## Giải pháp

Thêm invariant ở cả query và domain:

```text
Public matching chỉ query intent_type = PUBLIC AND status = OPEN
Private intent không bao giờ xuất hiện trong public matching query
MATCHING → OPEN chỉ được phép nếu intent_type = PUBLIC
```

Trong domain entity:

```java
public void reopenAfterProposalFailed() {
    if (this.type != IntentType.PUBLIC) {
        throw new DomainException(PRIVATE_INTENT_CANNOT_REOPEN);
    }
    if (this.status != MATCHING) {
        throw new DomainException(INVALID_INTENT_STATE);
    }
    this.status = OPEN;
}
```

Và private flow dùng method riêng:

```java
cancelPrivatePairAfterProposalFailed()
```

Không dùng chung method “unlock intent”.

**Mức ưu tiên:** rất cao.
**Độ khó:** thấp đến trung bình nếu code đã có type public/private.
**Ảnh hưởng UX:** tránh private invite biến thành public availability sai.

---

# 7. Proposal timeout ngắn để tránh “giam” user

## Vấn đề hiện tại

Nếu User A accept nhưng User B không phản hồi, cả hai intent bị treo ở `MATCHING`. Tài liệu lifecycle đã đề xuất timeout ngắn 2–5 phút cho proposal. 

## Giải pháp

Policy:

```text
Public proposal timeout: 2–3 phút
Private invite timeout: 5–10 phút
Last-minute proposal timeout: min(defaultTimeout, startTime - now - safetyBuffer)
```

Khi timeout:

* Public proposal: `PENDING → EXPIRED`, public intents `MATCHING → OPEN`
* Private proposal: `PENDING → EXPIRED`, private intents `MATCHING → CANCELLED`

Thêm job định kỳ:

```text
expirePendingProposals()
expireLastMinuteIntents()
```

Và push notification:

* Khi proposal sắp hết hạn: nhắc nhẹ
* Khi hết hạn: báo “Người kia chưa phản hồi, WalkMate đang tìm người khác cho bạn”

**Mức ưu tiên:** rất cao.
**Độ khó:** trung bình.
**Ảnh hưởng UX:** giảm cảm giác bị kẹt.

---

# 8. Sửa learning: không để `weightTimeOverlap` chết dần

## Vấn đề hiện tại

Training chỉ tăng `weightInterest` và `weightBehavior`, nên `weightTimeOverlap` giảm dần sau mỗi lần normalize. 

## Giải pháp

Có 2 hướng. Mình đề xuất hướng an toàn hơn cho app gần release:

## Hướng A — đặt minimum floor cho time weight

```text
weightTimeOverlap không được thấp hơn 0.25
```

Ví dụ:

```java
MIN_TIME_WEIGHT = 0.25
MAX_WEIGHT_CAP = 0.70
```

Sau normalize, nếu `weightTimeOverlap < 0.25`, kéo nó về `0.25`, rồi phân phối phần còn lại cho interest/behavior.

Vì WalkMate là app đi dạo theo lịch, time overlap không nên tụt quá thấp.

## Hướng B — học positive signal cho time

Sau mỗi session thành công:

* Nếu hai người đều arrive đúng giờ
* Session hoàn thành
* Không có no-show/cancel
* Review tích cực

Thì tăng nhẹ `weightTimeOverlap +0.02`.

Nhưng hướng B phức tạp hơn vì cần định nghĩa “session đúng giờ”. Vì vậy MVP nên làm:

```text
Bắt buộc có floor 0.25 cho weightTimeOverlap.
```

**Mức ưu tiên:** rất cao.
**Độ khó:** thấp.
**Ảnh hưởng UX:** tránh AI học lệch khỏi bản chất scheduling.

---

# 9. Chống review spam / click-through bias

## Vấn đề hiện tại

User bấm nhiều review tag thì mỗi tag đều train model. Bấm đại cũng làm model học sai. 

## Giải pháp

Đổi từ “mỗi tag tăng weight” sang “mỗi review chỉ tạo một tín hiệu có giới hạn”.

Rule mới:

```text
Một review chỉ được training tối đa 1 lần.
Mỗi nhóm signal có cap.
Không cộng tuyến tính theo số lượng tag quá nhiều.
```

Ví dụ:

```java
interestSignal = hasAnyInterestTag ? 0.03 : 0.0;
behaviorSignal = hasAnyBehaviorTag ? 0.03 : 0.0;
```

Nếu user chọn 1 tag interest hay 5 tag interest, vẫn chỉ tăng `+0.03`.

Có thể thêm quality gate:

```text
Nếu user chọn quá nhiều tag cùng lúc, ví dụ >= 5 tag, giảm confidence còn 50%.
Nếu review gửi quá nhanh sau khi mở màn hình, giảm confidence còn 50%.
Nếu có comment meaningful, giữ confidence 100%.
```

Công thức:

```text
finalBump = baseBump × confidence
```

MVP đơn giản:

```text
Không train theo số lượng tag.
Chỉ train theo loại tag có xuất hiện hay không.
```

**Mức ưu tiên:** cao.
**Độ khó:** thấp đến trung bình.
**Ảnh hưởng UX:** giảm nhiễu model.

---

# 10. Report chưa verified thì không nên train mạnh ngay

## Vấn đề hiện tại

User report là hệ thống tăng ngay `weightBehavior`. Nếu report giả và admin reject sau đó, model không rollback. 

## Giải pháp

Tách report thành 2 loại tín hiệu:

```text
provisional signal
confirmed signal
```

Khi user submit report:

```text
- Không tăng weightBehavior mạnh ngay
- Chỉ tạo provisional bump nhỏ, ví dụ +0.03
- Hoặc không train preference, chỉ exclude candidate khỏi matching của reporter
```

Khi admin xác nhận report hợp lệ:

```text
- Apply confirmed behavior bump, ví dụ +0.10 hoặc +0.15
- Giảm trust/reputation của người bị report nếu policy cho phép
```

Khi admin reject report:

```text
- Rollback provisional bump nếu đã apply
- Không ảnh hưởng model
```

Cần thêm bảng log:

```text
preference_training_event
- id
- user_id
- source_type: REVIEW | REPORT | SESSION_OUTCOME
- source_id
- delta_time
- delta_interest
- delta_behavior
- status: APPLIED | REVERTED | PENDING
- created_at
```

Đây là bước rất đáng làm, vì có log thì sau này debug AI dễ hơn rất nhiều.

**Mức ưu tiên:** cao.
**Độ khó:** trung bình.
**Ảnh hưởng UX:** tăng độ tin cậy, giảm thao túng.

---

# 11. Thêm decay để model thích nghi theo thời gian

## Vấn đề hiện tại

Model chỉ cộng dồn. Nếu user từng có giai đoạn rất quan tâm behavior, hệ thống có thể giữ bias đó quá lâu.

## Giải pháp

Không cần ML phức tạp. Chỉ cần decay nhẹ về default.

Default:

```text
W_time = 0.34
W_interest = 0.33
W_behavior = 0.33
```

Mỗi tuần hoặc mỗi N training event:

```text
newWeight = currentWeight × 0.95 + defaultWeight × 0.05
```

Ý nghĩa: model không quên ngay, nhưng sẽ dần quay về cân bằng nếu không có tín hiệu mới.

Có thể chạy khi training event xảy ra, không cần cron riêng:

```text
Trước khi apply bump mới:
- decay current weights based on days since last_training_at
- rồi apply new signal
- normalize
```

**Mức ưu tiên:** trung bình.
**Độ khó:** trung bình.
**Ảnh hưởng UX:** model mềm hơn, ít bị “kẹt tính cách cũ”.

---

# 12. Ranking nên trả top N, không chỉ best 1

## Vấn đề hiện tại

Nếu AI chọn best candidate nhưng transaction fail vì người đó vừa cancel hoặc bị lock, flow có thể lỗi.

## Giải pháp

Scoring engine nên trả về danh sách ranked candidates:

```text
candidate_1: 87 điểm
candidate_2: 82 điểm
candidate_3: 78 điểm
```

Orchestrator thử lần lượt trong transaction:

```text
for candidate in rankedCandidates:
    tryCreateProposal(candidate)
    if success: return proposal
return noMatch
```

Như vậy AI scoring không bị coupling với trạng thái tức thời của DB.

**Mức ưu tiên:** cao.
**Độ khó:** trung bình.
**Ảnh hưởng UX:** giảm no-match giả.

---

# 13. Đề xuất công thức scoring mới

Mình đề xuất bản này cho WalkMate:

```text
TotalScore =
  W_time     × S_time
+ W_interest × S_tags
+ W_behavior × S_trust
- Penalties
+ Bonuses
```

Trong đó:

```text
S_time = clamp(overlapMinutes / desiredDuration × 100, 0, 100)

S_tags =
  20 nếu cả hai đều không có tag
  10 nếu một trong hai không có tag
  Jaccard × 100 nếu cả hai có tag

S_trust = clamp(candidateTrustScore / 10, 0, 100)
```

Penalties:

```text
- profileIncompletePenalty: 5–15 điểm
- recentExpiredNoResponsePenalty: 10–20 điểm
- lowReputationPenalty: 10–40 điểm
```

Bonuses:

```text
+ friendBonus nếu match với bạn bè: 5–10 điểm
+ successfulPastWalkBonus nếu từng đi tốt với nhau: 5–10 điểm
```

Nhưng cần cẩn thận: nếu có “bạn bè” và “private invite”, đừng để friendBonus đưa private intent vào public pool. Private/public vẫn phải do lifecycle guard kiểm soát.

---

# 14. Đề xuất MatchingPreference rule mới

Hiện tại:

```text
default = 0.333 / 0.333 / 0.333
max cap = 0.70
```

Đề xuất:

```text
default:
W_time = 0.40
W_interest = 0.30
W_behavior = 0.30

constraints:
MIN_TIME_WEIGHT = 0.25
MIN_INTEREST_WEIGHT = 0.10
MIN_BEHAVIOR_WEIGHT = 0.10
MAX_ANY_WEIGHT = 0.65 hoặc 0.70
```

Lý do: WalkMate là app theo thời gian thực và gặp người thật, nên time và behavior đều quan trọng. Nhưng time không thể bị học mất.

---

# 15. Roadmap implement theo mức ưu tiên

## Phase 1 — Chặn bug nghiêm trọng trước release

Làm ngay:

1. Clamp toàn bộ score về `[0, 100]`.
2. Sửa `S_time` theo tỷ lệ overlap/duration.
3. Đổi empty tags từ `50` xuống `20`.
4. Thêm exclusion sau reject/pass/expired.
5. Đảm bảo public matching query chỉ lấy `PUBLIC + OPEN`.
6. Thêm proposal timeout.
7. Tạo proposal trong transaction + optimistic locking.

Đây là nhóm cần làm trước vì ảnh hưởng trực tiếp đến bug và trải nghiệm.

## Phase 2 — Sửa AI learning cho đỡ học sai

Làm tiếp:

1. Thêm `MIN_TIME_WEIGHT = 0.25`.
2. Review chỉ train theo nhóm tag, không train theo số lượng tag.
3. Report chuyển sang provisional/confirmed signal.
4. Thêm `preference_training_event` để audit và rollback.
5. Thêm decay nhẹ về default.

## Phase 3 — Cải thiện chất lượng matching

Làm sau:

1. Bonus/penalty nâng cao.
2. Friend bonus.
3. Past successful walk bonus.
4. No-show penalty.
5. Explainable matching UI: “Ghép vì cùng khung giờ, cùng sở thích, độ tin cậy cao.”

---

# 16. Kết luận proposal

Mình không đề xuất thay AI hiện tại bằng mô hình ML phức tạp. Với WalkMate hiện tại, giải pháp tốt nhất là:

```text
Giữ weighted scoring engine,
nhưng thêm guardrail, cap, timeout, exclusion, audit log và learning policy tốt hơn.
```

Nếu làm theo hướng này, chúng ta giải quyết được gần như toàn bộ vấn đề đã nêu:

* Time overlap không bị quên.
* Trust không phá trần.
* Profile rỗng không được thưởng.
* Reject xong không bị match lại ngay.
* Private intent không lọt vào public pool.
* Proposal không treo user quá lâu.
* Review spam không làm bẩn model.
* Report giả có thể rollback.
* Race condition được giảm bằng transaction/versioning.

Đề xuất bước tiếp theo: mình sẽ giúp bạn thiết kế chi tiết **AI Matching v2 spec** gồm domain rules, DB tables cần thêm, API behavior, và checklist test case để dev có thể implement trực tiếp.
