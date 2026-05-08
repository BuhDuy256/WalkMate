# WalkMate — Walk Result Post MVP Spec v2

## 1. Tên tính năng

**Walk Result Post**

Tên hiển thị trong UI:

```text
Đăng kết quả đi bộ lên Profile
Post to Profile
Walk Activity
Recent Walks
```

## 2. Mục tiêu

Cho phép người dùng đăng kết quả của một buổi đi dạo đã hoàn thành lên Profile cá nhân dưới dạng một hoạt động đi bộ.

Tính năng này giúp:

```text
- Làm Profile có nội dung hoạt động thật.
- Phân biệt rõ My Profile và Public Profile.
- Tận dụng dữ liệu thật từ WalkSession / Walk History.
- Tạo cảm giác thành tích sau mỗi buổi đi dạo.
- Tăng độ tin cậy khi người khác xem Profile.
- Giữ Review, Report và Post là các hành động hậu-session độc lập.
```

## 3. Product Decision Quan Trọng

### 3.1 Walk Complete không phải nơi trigger chính cho Post

Walk Complete chỉ là màn tóm tắt sau khi user hoàn thành buổi đi dạo.

Walk Complete **không chứa**:

```text
- Review flow
- Report flow
- Share your achievement section
- Post to Profile flow chính
- Save to history only
```

Lý do:

```text
- Completed walk luôn tự động được lưu vào Walk History.
- Review / Report hiện đã được thiết kế ở History Item Card.
- Post to Profile là một hành động hậu-session, nên đặt cùng Review / Report ở Walk History.
- Không ép user quyết định share ngay sau khi vừa complete.
```

### 3.2 Walk History Item Card là trigger chính

Walk History là nơi quản lý các hành động sau buổi đi dạo:

```text
- Review
- Report
- Post to Profile
- View Post
- View Review
- View Details
```

### 3.3 Review, Report và Post độc lập

Các rule product:

```text
1. Post to Profile không yêu cầu Review trước.
2. Review không yêu cầu Post.
3. Report không yêu cầu Review hoặc Post.
4. User có thể Post kết quả của chính mình nếu personal status của user là COMPLETED.
5. User vẫn có thể Post My Walk nếu partner chưa hoàn thành.
6. Cancelled / No-show / Failed session không được Post.
```

---

# 4. Phạm vi MVP

## 4.1 Có trong MVP

MVP bao gồm:

```text
1. Walk History Card hiển thị action Post to Profile / Post My Walk.
2. User có thể tạo Walk Result Post từ một completed History Item.
3. User nhập caption ngắn.
4. User chọn visibility:
   - Public
   - Friends
   - Only me
5. User có thể bật/tắt:
   - Show companion
   - Show route map
   - Show walk stats
6. User chỉ được tạo tối đa 1 post cho mỗi completed walk.
7. Sau khi post thành công, History Card đổi từ Post to Profile sang View Post.
8. My Profile có entry Walk Activity.
9. Walk Activity screen hiển thị tất cả post của chính chủ.
10. Walk Activity có filter:
   - All
   - Public
   - Friends
   - Only me
11. Public Profile / Recent Walks chỉ hiển thị post mà viewer có quyền xem.
12. User có thể đổi visibility của post.
13. User có thể xóa post.
```

## 4.2 Không nằm trong MVP

Không làm trong MVP:

```text
- Like bài đăng
- Comment bài đăng
- Feed toàn app
- Share ra ngoài app
- Upload ảnh riêng cho bài đăng
- AI caption
- AI suggestion
- Achievement system phức tạp
- Privacy setting chi tiết cho toàn bộ profile
- Route map thật nếu dữ liệu map chưa ổn định
- Edit caption nâng cao sau khi post
- Notification khi người khác post
- Report riêng cho post
```

---

# 5. Core Flow

## 5.1 Flow tổng quát

```text
Tracking
→ Complete Walk
→ Walk Complete Summary
→ Back to Home / View in History
→ Walk History
→ Post to Profile / Post My Walk
→ Create Walk Post
→ Publish
→ Walk History hoặc Walk Activity
```

## 5.2 Flow sau khi hoàn thành walk

```text
User bấm Complete Walk
→ App lưu kết quả vào Walk History
→ Hiển thị Walk Complete Summary
→ User bấm Back to Home hoặc View in History
```

## 5.3 Flow tạo post

```text
Walk History
→ User bấm Post to Profile hoặc Post My Walk
→ Create Walk Post Screen
→ User nhập caption
→ User chọn visibility
→ User chọn display toggles
→ User bấm Post to Profile
→ Backend tạo WalkPost
→ UI success
→ History Card hiển thị View Post + POSTED chip
```

---

# 6. User Stories

## US-01 — Post từ Walk History

Là người dùng đã hoàn thành một buổi đi dạo,
tôi muốn đăng kết quả đó từ Walk History,
để lưu lại hoạt động lên Profile của mình.

## US-02 — Post My Walk khi partner chưa hoàn thành

Là người dùng đã hoàn thành phần đi bộ của mình,
tôi muốn đăng kết quả của chính tôi dù partner chưa hoàn thành,
để không bị phụ thuộc vào trạng thái của người kia.

## US-03 — Chọn quyền riêng tư bài đăng

Là người dùng,
tôi muốn chọn ai có thể xem bài đăng,
để kiểm soát dữ liệu cá nhân.

## US-04 — Quản lý bài đã đăng trong Walk Activity

Là chính chủ Profile,
tôi muốn xem và quản lý các bài đi bộ đã chia sẻ,
để đổi visibility hoặc xóa bài khi cần.

## US-05 — Người khác xem Recent Walks

Là người xem Profile của người khác,
tôi chỉ nên thấy những Walk Result Post mà tôi có quyền xem.

## US-06 — Review độc lập với Post

Là người dùng,
tôi muốn có thể post kết quả đi bộ mà không cần review trước,
vì Review người đồng hành và Post thành tích là hai hành động khác nhau.

---

# 7. Walk Complete Screen

## 7.1 Vai trò

Walk Complete chỉ có vai trò:

```text
- Xác nhận user đã hoàn thành buổi đi dạo.
- Hiển thị summary ngắn.
- Cho phép user quay về Home hoặc xem trong History.
```

## 7.2 Nội dung UI

Walk Complete hiển thị:

```text
- Icon celebration
- Title: Walk Complete!
- Subtitle: Great walk with [Partner Name]!
- Stats card:
  - Duration
  - Distance
  - Pace hoặc Steps nếu có
- Partner card
- Primary CTA: Back to Home
- Secondary link/button: View in History
```

## 7.3 Không hiển thị

Walk Complete không hiển thị:

```text
- Leave a Review
- Report
- Share your achievement
- Post to Profile as main CTA
- Save to history only
```

---

# 8. Walk History Card

## 8.1 Vai trò

Walk History Card là nơi trigger chính cho hành động sau buổi đi dạo.

Mỗi card cần hiển thị:

```text
- Date
- Hotspot name
- Session status badge
- Partner row
- Current user row
- Distance của từng người nếu có
- Duration của từng người nếu có
- Action area
```

## 8.2 Dữ liệu cần có cho mỗi History Item

Frontend cần biết:

```text
sessionId
hotspotName
sessionStatus
currentUserPersonalStatus
partnerPersonalStatus
currentUserDistanceKm
currentUserDurationSeconds
partnerDistanceKm
partnerDurationSeconds
partnerName
partnerAvatarUrl
currentUserHasReviewed
currentUserReviewId
currentUserHasPosted
currentUserPostId
canPost
canReview
canReport
canChat
```

## 8.3 State variants

### A. Active / partner waiting + current user completed

Điều kiện:

```text
currentUserPersonalStatus = COMPLETED
partnerPersonalStatus = PENDING hoặc ACTIVE hoặc WAITING
```

UI:

```text
Badge: ACTIVE
Action: Post My Walk
Action phụ: Report
```

Không dùng label `Post to Profile`, vì partner chưa hoàn thành. Dùng `Post My Walk` để nhấn mạnh user chỉ đăng kết quả của chính mình.

### B. Completed + not reviewed + not posted

Điều kiện:

```text
sessionStatus = COMPLETED
currentUserPersonalStatus = COMPLETED
currentUserHasReviewed = false
currentUserHasPosted = false
```

UI:

```text
Badge: COMPLETED
Actions:
- Leave a Review
- Post to Profile
- Report
```

### C. Completed + reviewed + not posted

Điều kiện:

```text
currentUserHasReviewed = true
currentUserHasPosted = false
```

UI:

```text
Badge: COMPLETED
Actions:
- View Review
- Post to Profile
- Chat, nếu đã có chat sau session
- Report
```

### D. Completed + not reviewed + posted

Điều kiện:

```text
currentUserHasReviewed = false
currentUserHasPosted = true
```

UI:

```text
Badges:
- COMPLETED
- POSTED

Actions:
- Leave a Review
- View Post
- Report
```

### E. Completed + reviewed + posted

Điều kiện:

```text
currentUserHasReviewed = true
currentUserHasPosted = true
```

UI:

```text
Badges:
- COMPLETED
- POSTED

Actions:
- View Review
- View Post
- Chat, nếu có
- Report
```

### F. Cancelled / No-show / Failed

Điều kiện:

```text
sessionStatus = CANCELLED hoặc NO_SHOW hoặc FAILED
```

UI:

```text
Badge: CANCELLED / NO SHOW / FAILED
Actions:
- View Details
- Report
```

Không hiển thị Post.

---

# 9. Điều kiện tạo Walk Result Post

User chỉ được tạo post nếu thỏa mãn tất cả điều kiện:

```text
1. User đã đăng nhập.
2. WalkSession tồn tại.
3. User là participant của WalkSession.
4. Personal status của user trong WalkSession là COMPLETED.
5. Session không thuộc trạng thái CANCELLED / FAILED.
6. User chưa tạo post cho session này.
```

Lưu ý quan trọng:

```text
Không bắt buộc partner phải COMPLETED.
Nếu current user đã COMPLETED, user có thể Post My Walk.
```

---

# 10. Visibility Rules

## 10.1 Visibility values

DB values:

```text
PUBLIC
FRIENDS
PRIVATE
```

UI labels:

```text
Public
Friends
Only me
```

## 10.2 Ý nghĩa

| DB value  | UI label | Ý nghĩa                           |
| --------- | -------- | --------------------------------- |
| `PUBLIC`  | Public   | Mọi người có thể xem trên profile |
| `FRIENDS` | Friends  | Chỉ bạn bè đã kết nối có thể xem  |
| `PRIVATE` | Only me  | Chỉ chủ bài đăng xem được         |

## 10.3 Rule xem bài

| Viewer          | PUBLIC | FRIENDS | PRIVATE |
| --------------- | -----: | ------: | ------: |
| Owner           |     Có |      Có |      Có |
| Accepted friend |     Có |      Có |   Không |
| Stranger        |     Có |   Không |   Không |
| Blocked user    |  Không |   Không |   Không |

Nếu MVP chưa có block system thì bỏ qua blocked rule.

---

# 11. Display Toggles

Khi tạo post, user có thể bật/tắt:

```text
showCompanion
showRouteMap
showStats
```

## 11.1 showCompanion

Nếu `true`:

```text
Hiển thị companion chip / companion row.
```

Nếu `false`:

```text
Không hiển thị người đồng hành trên post.
```

## 11.2 showRouteMap

Nếu `true`:

```text
Hiển thị route map preview nếu có dữ liệu.
Nếu chưa có map thật, hiển thị placeholder.
```

Nếu `false`:

```text
Không hiển thị map preview.
```

## 11.3 showStats

Nếu `true`:

```text
Hiển thị duration, distance, points.
```

Nếu `false`:

```text
Ẩn stats row.
```

---

# 12. Data Model MVP

## 12.1 Bảng mới: `walk_post`

```sql
CREATE TABLE public.walk_post (
  post_id uuid NOT NULL DEFAULT uuid_generate_v4(),
  session_id uuid NOT NULL,
  author_id uuid NOT NULL,

  caption text,
  visibility character varying NOT NULL DEFAULT 'PUBLIC',

  show_companion boolean NOT NULL DEFAULT true,
  show_route_map boolean NOT NULL DEFAULT true,
  show_stats boolean NOT NULL DEFAULT true,

  distance_km numeric NOT NULL DEFAULT 0 CHECK (distance_km >= 0),
  duration_seconds bigint NOT NULL DEFAULT 0 CHECK (duration_seconds >= 0),
  points_earned integer NOT NULL DEFAULT 0 CHECK (points_earned >= 0),

  route_preview_url text,

  created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,

  CONSTRAINT walk_post_pkey PRIMARY KEY (post_id),
  CONSTRAINT walk_post_session_fkey FOREIGN KEY (session_id) REFERENCES public.walk_session(session_id),
  CONSTRAINT walk_post_author_fkey FOREIGN KEY (author_id) REFERENCES public.user_account(user_id),

  CONSTRAINT walk_post_unique_author_session UNIQUE (session_id, author_id),
  CONSTRAINT walk_post_visibility_check CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'))
);
```

## 12.2 Optional status field

Nếu project đang dùng soft delete nhiều nơi, có thể thêm:

```sql
status character varying NOT NULL DEFAULT 'PUBLISHED',
CONSTRAINT walk_post_status_check CHECK (status IN ('PUBLISHED', 'DELETED'))
```

MVP có thể hard delete nếu muốn đơn giản, nhưng khuyến nghị soft delete nếu có pattern sẵn.

## 12.3 Vì sao cần bảng riêng?

Không lưu post trực tiếp trong `walk_session`, vì:

```text
- Một session có 2 người, mỗi người có quyền đăng riêng.
- User A có thể post, User B có thể không post.
- User A có thể PUBLIC, User B có thể PRIVATE.
- Caption và visibility là dữ liệu presentation/social layer, không phải lifecycle của session.
- Sau này có thể mở rộng like/comment/report post mà không đụng WalkSession.
```

---

# 13. Backend Domain

## 13.1 Domain package đề xuất

```text
domain/walkpost/
  WalkPost.java
  WalkPostRepository.java
  WalkPostErrorCode.java
  PostVisibility.java
```

## 13.2 Application package đề xuất

```text
application/walkpost/
  WalkPostCommandService.java
  WalkPostQueryService.java
  CreateWalkPostCommand.java
  UpdateWalkPostVisibilityCommand.java
```

## 13.3 Error codes

```text
WALK_POST_NOT_FOUND
WALK_POST_SESSION_NOT_FOUND
WALK_POST_AUTHOR_NOT_PARTICIPANT
WALK_POST_SESSION_NOT_COMPLETED
WALK_POST_SESSION_NOT_POSTABLE
WALK_POST_DUPLICATED
WALK_POST_INVALID_VISIBILITY
WALK_POST_CAPTION_TOO_LONG
WALK_POST_FORBIDDEN
```

## 13.4 Domain rules

```text
1. Author phải là participant của session.
2. Author personal status phải là COMPLETED.
3. Không yêu cầu partner completed.
4. Mỗi author chỉ có 1 post cho mỗi session.
5. Caption nullable.
6. Nếu có caption, trim trước khi lưu.
7. Caption max length: 150 ký tự.
8. Visibility chỉ nhận PUBLIC / FRIENDS / PRIVATE.
9. Author mới được update visibility.
10. Author mới được delete post.
```

---

# 14. Backend API MVP

## 14.1 Tạo Walk Result Post

```http
POST /walk-sessions/{sessionId}/posts
```

Request:

```json
{
  "caption": "Great morning walk! Fresh air and met a new friend.",
  "visibility": "PUBLIC",
  "showCompanion": true,
  "showRouteMap": true,
  "showStats": true
}
```

Response:

```json
{
  "success": true,
  "data": {
    "postId": "uuid",
    "sessionId": "uuid",
    "authorId": "uuid",
    "authorName": "Luân Trần",
    "authorAvatarUrl": "...",
    "caption": "Great morning walk! Fresh air and met a new friend.",
    "visibility": "PUBLIC",
    "hotspotName": "Tao Dan Park",
    "distanceKm": 2.4,
    "durationSeconds": 1680,
    "pointsEarned": 120,
    "showCompanion": true,
    "showRouteMap": true,
    "showStats": true,
    "companionName": "Nguyen Minh",
    "routePreviewUrl": null,
    "createdAt": "2026-04-29T09:41:00"
  }
}
```

Backend behavior:

```text
1. Lấy current user từ auth context.
2. Tìm WalkSession theo sessionId.
3. Kiểm tra current user là participant.
4. Kiểm tra current user personal status = COMPLETED.
5. Kiểm tra session có postable hay không.
6. Kiểm tra chưa có walk_post với session_id + author_id.
7. Snapshot distance/duration/points theo đúng current user.
8. Tạo WalkPost.
9. Trả WalkPostResponse.
```

## 14.2 Lấy post của chính chủ

```http
GET /profiles/me/posts
```

Optional query:

```http
GET /profiles/me/posts?visibility=PUBLIC
```

MVP có thể không cần query param. Backend trả tất cả, frontend filter local.

Behavior:

```text
- Trả tất cả post của current user.
- Bao gồm PUBLIC / FRIENDS / PRIVATE.
- Dùng cho Walk Activity screen.
```

## 14.3 Lấy post trên profile người khác

```http
GET /profiles/{userId}/posts
```

Behavior:

```text
- Nếu viewer là owner: trả tất cả.
- Nếu viewer là accepted friend: trả PUBLIC + FRIENDS.
- Nếu viewer là stranger: trả PUBLIC.
- Không trả PRIVATE cho người không phải owner.
```

## 14.4 Đổi visibility

```http
PATCH /walk-posts/{postId}/visibility
```

Request:

```json
{
  "visibility": "FRIENDS"
}
```

Behavior:

```text
- Chỉ author được đổi visibility.
- Visibility phải hợp lệ.
```

## 14.5 Xóa post

```http
DELETE /walk-posts/{postId}
```

Behavior:

```text
- Chỉ author được xóa.
- MVP có thể hard delete.
- Nếu dùng status, chuyển status sang DELETED.
```

## 14.6 Update Walk History API

Walk History API cần trả thêm metadata để render đúng card.

Ví dụ field cần bổ sung cho mỗi item:

```json
{
  "sessionId": "uuid",
  "sessionStatus": "COMPLETED",
  "currentUserPersonalStatus": "COMPLETED",
  "partnerPersonalStatus": "COMPLETED",
  "currentUserHasReviewed": true,
  "currentUserReviewId": "uuid",
  "currentUserHasPosted": true,
  "currentUserPostId": "uuid",
  "canPost": true,
  "canReview": true,
  "canReport": true,
  "canChat": true
}
```

Rule:

```text
History Card không nên tự suy luận bằng nhiều API phụ.
Backend nên trả hasPosted / postId / hasReviewed / reviewId trong response history.
```

---

# 15. Frontend MVP

## 15.1 Screens cần có / cập nhật

### A. Walk Complete Screen

Cập nhật nhẹ:

```text
- Không thêm share section.
- Không thêm review section.
- Thêm View in History nếu cần.
```

### B. Walk History Screen

Cập nhật quan trọng:

```text
- Completed card có Review / Post / Report.
- Posted card có View Review / View Post / Report.
- Active nhưng current user completed có Post My Walk.
- Cancelled card không có Post.
```

### C. Create Walk Post Screen

Màn mới.

Nội dung:

```text
- Header: Create Walk Post
- Preview post card
- Caption input
- Visibility selector: Public / Friends / Only me
- Toggles:
  - Show companion
  - Show route map
  - Show walk stats
- CTA: Post to Profile
- CTA phụ: Cancel
```

### D. My Profile Screen

Không nhét post card trực tiếp vào Profile chính.

Chỉ thêm menu item:

```text
Walk Activity
Manage your shared walk posts
```

### E. Walk Activity Screen

Màn mới.

Nội dung:

```text
- Header: Walk Activity
- Filter chips:
  - All
  - Public
  - Friends
  - Only me
- Count: 3 posts
- Walk Result Post Cards
- Overflow menu trên mỗi card:
  - Change visibility
  - Delete post
```

Empty state:

```text
No shared walks yet
Post a completed walk from your History.

CTA: Go to History
```

### F. Public Profile Screen

Nếu Public Profile đã có sẵn, thêm section:

```text
Recent Walks
```

Chỉ render posts backend trả về.

Không hiển thị:

```text
- Edit Profile
- Admin Dashboard
- Security
- Owner-only menu
- PRIVATE posts
```

---

# 16. Frontend Package Gợi Ý

```text
ui/walkpost/create/
  CreateWalkPostFragment.java
  CreateWalkPostViewModel.java
  CreateWalkPostUiState.java
  CreateWalkPostViewModelFactory.java

ui/profile/activity/
  WalkActivityFragment.java
  WalkActivityViewModel.java
  WalkActivityUiState.java
  WalkActivityViewModelFactory.java

domain/walkpost/
  WalkPost.java
  WalkPostRepository.java
  PostVisibility.java

data/datasource/remote/dto/request/walkpost/
  CreateWalkPostRequest.java
  UpdateWalkPostVisibilityRequest.java

data/datasource/remote/dto/response/walkpost/
  WalkPostResponse.java

data/mapper/
  WalkPostMapper.java

data/repository/
  WalkPostRepositoryImpl.java
```

Reusable custom views:

```text
core/designsystem/view/WalkResultPostCard.java
core/designsystem/view/VisibilityChipView.java
core/designsystem/view/VisibilitySelectorView.java
core/designsystem/view/RoutePreviewView.java
```

---

# 17. UI State

## 17.1 CreateWalkPostUiState

```text
isLoading
isSuccess
errorMessage
sessionId
caption
selectedVisibility
showCompanion
showRouteMap
showStats
previewPost
```

States:

```text
Default
Loading
Success
Error
```

Error copy:

```text
Couldn’t post this walk. Please try again.
You already posted this walk.
This walk is not completed yet.
You don’t have permission to post this walk.
```

## 17.2 WalkActivityUiState

```text
isLoading
errorMessage
selectedFilter
posts
filteredPosts
empty
```

Filters:

```text
ALL
PUBLIC
FRIENDS
PRIVATE
```

---

# 18. Walk Result Post Card

Card cần hỗ trợ các phần:

```text
- Author avatar
- Author name
- Hotspot name
- Created time
- Visibility chip
- Caption
- Route preview, nếu showRouteMap = true
- Stats row, nếu showStats = true
- Companion chip, nếu showCompanion = true
- Overflow menu nếu viewer là owner
```

Variants:

```text
Owner full card
Owner compact card
Public viewer card
Without route map
Without companion
Without stats
```

---

# 19. Acceptance Criteria

## AC-01 — Walk Complete không tạo post trực tiếp

Given user vừa complete walk,
When user ở Walk Complete screen,
Then screen chỉ hiển thị summary và Back to Home / View in History.

## AC-02 — Completed walk xuất hiện trong History

Given user complete walk thành công,
When user mở Walk History,
Then session vừa hoàn thành xuất hiện trong danh sách.

## AC-03 — Post từ History Item

Given user có completed walk chưa post,
When user bấm Post to Profile trên History Card,
Then app mở Create Walk Post screen với dữ liệu của walk đó.

## AC-04 — Post My Walk khi partner chưa hoàn thành

Given current user personal status = COMPLETED và partner chưa completed,
When user xem History Card,
Then card hiển thị Post My Walk và user có thể tạo post cho kết quả của chính mình.

## AC-05 — Không cần review trước khi post

Given user chưa review companion,
When user bấm Post to Profile,
Then app vẫn cho tạo post.

## AC-06 — Tạo post thành công

Given user là participant và personal status = COMPLETED,
When user nhập caption hợp lệ và chọn visibility,
Then backend tạo WalkPost thành công.

## AC-07 — Không tạo trùng post

Given user đã post cho một session,
When user cố tạo post lần nữa cho cùng session,
Then backend từ chối và UI hiển thị trạng thái đã post.

## AC-08 — History Card đổi trạng thái sau khi post

Given user đã post thành công,
When user quay lại Walk History,
Then card hiển thị View Post thay vì Post to Profile và có chip POSTED.

## AC-09 — Cancelled session không được post

Given session bị CANCELLED,
When user xem History Card,
Then card không hiển thị Post action.

## AC-10 — Owner thấy tất cả post trong Walk Activity

Given user mở Walk Activity của mình,
Then user thấy tất cả posts của mình gồm Public, Friends, Only me.

## AC-11 — Walk Activity filter hoạt động

Given user có nhiều posts visibility khác nhau,
When user chọn filter Public / Friends / Only me,
Then danh sách chỉ hiển thị posts tương ứng.

## AC-12 — Stranger chỉ thấy Public posts

Given User A có PUBLIC, FRIENDS, PRIVATE posts,
When User B không phải bạn bè xem profile User A,
Then User B chỉ thấy PUBLIC posts.

## AC-13 — Friend thấy Public + Friends posts

Given User B là accepted friend của User A,
When User B xem profile User A,
Then User B thấy PUBLIC và FRIENDS posts, không thấy PRIVATE.

## AC-14 — Toggle ẩn thông tin đúng

Given author tắt showStats,
When viewer xem post,
Then stats row không hiển thị.

Given author tắt showCompanion,
When viewer xem post,
Then companion chip không hiển thị.

Given author tắt showRouteMap,
When viewer xem post,
Then route preview không hiển thị.

## AC-15 — Owner đổi visibility được

Given user là author của post,
When user đổi visibility,
Then post cập nhật visibility thành công.

## AC-16 — Non-author không đổi visibility được

Given user không phải author,
When user gọi API đổi visibility,
Then backend trả forbidden.

---

# 20. Test Checklist

## 20.1 Backend

```text
[ ] Tạo post thành công với completed participant.
[ ] Không tạo post nếu session không tồn tại.
[ ] Không tạo post nếu user không thuộc session.
[ ] Không tạo post nếu current user personal status chưa COMPLETED.
[ ] Cho tạo post nếu current user COMPLETED nhưng partner chưa completed.
[ ] Không tạo post nếu session CANCELLED / FAILED.
[ ] Không tạo post trùng session_id + author_id.
[ ] Caption quá 150 ký tự bị từ chối.
[ ] Visibility không hợp lệ bị từ chối.
[ ] Owner xem được tất cả posts.
[ ] Stranger chỉ xem được PUBLIC posts.
[ ] Friend xem được PUBLIC + FRIENDS posts.
[ ] PRIVATE chỉ owner xem được.
[ ] Author đổi visibility được.
[ ] Non-author không đổi visibility được.
[ ] Author xóa post được.
[ ] Non-author không xóa post được.
[ ] Walk History response trả hasPosted/postId đúng.
```

## 20.2 Frontend

```text
[ ] Walk Complete không hiển thị Share section.
[ ] Walk Complete có Back to Home.
[ ] View in History mở Walk History nếu có.
[ ] History Card chưa post hiển thị Post to Profile.
[ ] Partner waiting + current user completed hiển thị Post My Walk.
[ ] Completed posted card hiển thị View Post + POSTED chip.
[ ] Cancelled card không hiển thị Post.
[ ] Bấm Post mở Create Walk Post.
[ ] Caption input hoạt động.
[ ] Visibility selector hoạt động.
[ ] Toggles update preview.
[ ] Loading khi đang post.
[ ] Error hiển thị khi post thất bại.
[ ] Success sau khi post.
[ ] Walk Activity hiển thị post mới.
[ ] Filter All/Public/Friends/Only me hoạt động.
[ ] Visibility chip hiển thị đúng.
[ ] Overflow menu có Change visibility / Delete.
[ ] Public Profile chỉ render posts backend trả về.
```

---

# 21. Implementation Phases

## Phase 1 — DB

```text
- Thêm bảng walk_post.
- Thêm unique constraint session_id + author_id.
- Thêm visibility check constraint.
- Optional: thêm status nếu dùng soft delete.
```

## Phase 2 — Backend Create Post

```text
- Tạo domain WalkPost.
- Tạo PostVisibility.
- Tạo WalkPostRepository.
- Tạo CreateWalkPostCommand.
- Tạo WalkPostCommandService.
- Implement POST /walk-sessions/{sessionId}/posts.
```

## Phase 3 — Backend Query Posts

```text
- Implement GET /profiles/me/posts.
- Implement GET /profiles/{userId}/posts.
- Apply visibility filtering.
- Join hotspot / author / companion data nếu cần response.
```

## Phase 4 — Update Walk History Backend

```text
- Bổ sung currentUserHasPosted.
- Bổ sung currentUserPostId.
- Bổ sung currentUserHasReviewed nếu chưa có.
- Bổ sung currentUserReviewId nếu cần.
- Bổ sung canPost / canReview / canReport nếu muốn frontend render dễ hơn.
```

## Phase 5 — Frontend Data Layer

```text
- CreateWalkPostRequest.
- UpdateWalkPostVisibilityRequest.
- WalkPostResponse.
- WalkPostMapper.
- WalkPostRepository.
- WalkPostRepositoryImpl.
- API interface methods.
```

## Phase 6 — Create Walk Post UI

```text
- CreateWalkPostFragment.
- CreateWalkPostViewModel.
- CreateWalkPostUiState.
- Preview card.
- Visibility selector.
- Toggles.
```

## Phase 7 — Walk History Integration

```text
- Update History Card action area.
- Add Post to Profile.
- Add Post My Walk.
- Add View Post.
- Add POSTED chip.
- Navigate to CreateWalkPost.
```

## Phase 8 — Walk Activity Screen

```text
- Add Profile menu item Walk Activity.
- Create WalkActivityFragment.
- Fetch /profiles/me/posts.
- Add filters All/Public/Friends/Only me.
- Render WalkResultPostCard.
- Add change visibility and delete.
```

## Phase 9 — Public Profile Recent Walks

```text
- Fetch /profiles/{userId}/posts.
- Render Recent Walks.
- Empty state if no visible posts.
```

## Phase 10 — QA / Regression

```text
- Test Walk Complete.
- Test Walk History.
- Test Review flow unaffected.
- Test Report flow unaffected.
- Test Profile screen unaffected.
- Test visibility rules.
- Test duplicate post.
```

---

# 22. Final MVP Definition

Tính năng được xem là hoàn thành khi:

```text
User hoàn thành một buổi đi dạo
→ kết quả tự động nằm trong Walk History
→ user bấm Post to Profile hoặc Post My Walk từ History Card
→ user tạo post với caption, visibility và display options
→ post xuất hiện trong Walk Activity
→ History Card đổi thành View Post + POSTED
→ người khác chỉ thấy post nếu có quyền
→ Review, Report, Walk Complete và Profile hiện tại không bị phá vỡ.
```

## 23. Product Summary

Phiên bản MVP này chốt hướng:

```text
Walk Complete = Summary
Walk History = After-walk actions
Walk Activity = Owner post management
Public Profile Recent Walks = Showcase
```

Đây là hướng sạch hơn vì không ép user review, không làm Walk Complete quá tải, không nhét post feed vào Profile chính, và vẫn tạo được khác biệt rõ giữa My Profile và Public Profile.
