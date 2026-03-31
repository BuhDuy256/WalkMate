# Weekly Report – Group 09

---

## General Information

| Field            | Value                                          |
| ---------------- | ---------------------------------------------- |
| **Group ID**     | Group 09                                       |
| **Project Name** | WalkMate                                       |
| **Date Range**   | 2026-03-22 – 2026-03-28 _(Monday to Saturday)_ |

---

## Tasks Completed This Week

### 23127179 – Nguyễn Bảo Duy

- Task 1: Implement Push Notification
- Task 2: Merge Authentication - Walk intent matching - Walk session - Rating

- Status:
  - Unfinished: Task 1 and 50% Task 2.
  - Done: Merge authentication and adjust to fit with backend architecture which used DDD; Implement Walk intent UI with DDD-based MVVM architecture; Optimize UX for the UI.
  - Current Problem:
    - The UX design when user creates intent and finds match isn't clear enough => I also re-design the UX and will implement in next week.
    - I optimized the drawing logic to O(1) complexity but didn't solve the case when user pause the walk and re-active the tracing feature.
- **Evidence:**
  - [Jira Task](https://duybaonguyendev.atlassian.net/browse/KAN-30?atlOrigin=eyJpIjoiMmIwODI1ZjliYjg3NDc0NmEzMjQ0ODllZGMzODZkODMiLCJwIjoiaiJ9)
  - [GitHub](https://github.com/BuhDuy256/WalkMate/tree/flow/main)
  - See the images of screen in folder images (If I put these into here, the file is really long)

### 23127006 – Trần Nguyễn Khải Luân

- Task 1: Implement chat message
- Task 2: Implement walk intent frontend

- Status:
  - Task 1: done for the documentation and database migration which serve chat feature, not implemented yet.
  - Task 2: on reviewing the UI, thinking the strategy to vibe coding, currently done the backend but not tested yet => don't know that feature works ot not.
  - Researching about the testing strategy, currently done the testing document for backend
  - Done preparing document which support vibe coding (these files are in the folder docs/prompt engineering/).
- **Evidence:**
  - [Jira Task](https://duybaonguyendev.atlassian.net/browse/KAN-29)
  - [Github](https://github.com/BuhDuy256/WalkMate/tree/ui-mapping)
  - [Github](https://github.com/BuhDuy256/WalkMate/tree/feature/chat)

### 23127438 – Đặng Trường Nguyên

- Task 1 description

- Task: Implement Walk History
- Status:
  - Unfinished (50% done)
  - Done: Implement the UI for walk history screen and partially implement the logic for fetching walk history data from backend.
- **Evidence:**
  [Jira Task](https://duybaonguyendev.atlassian.net/browse/KAN-31?atlOrigin=eyJpIjoiMTI0NTk2NmViYzMwNGIzYjgzNGU5NzVkNDRmMjdhNDUiLCJwIjoiaiJ9)
  [GitHub](https://github.com/BuhDuy256/WalkMate/tree/feature/history)
  [Screenshots](https://drive.google.com/drive/folders/1a4_9G6ZmW_ieqdfKkhZGX1nrzWE3LU2F?usp=sharing)

### 23127539 – Nguyễn Thanh Tiến

- Task 1: Implement Profile Flow
- Status:
  - Unfinished (50% done)
  - Done: Implement the UI for profile flow, including profile screen and edit profile screen. And also partially implement the logic for updating user profile.
- **Evidence:**
  [Jira Task](https://duybaonguyendev.atlassian.net/browse/KAN-32)
  [GitHub](https://github.com/BuhDuy256/WalkMate/tree/feature/profile)

---

## AI Usage Declaration

### 23127179 – Nguyễn Bảo Duy

- Prompt 1:

```
Danh sach van de:

1. Hiện tại thì findMatch trả về người valid nhất => Theo như thiết kế thì khi tìm được Match thì phải tạo ra Proposal và tùy vào User quết định Accept hay Pass thì update status => Phải sinh ra domain "Proposal". => Hàm findMatch của intent hiện tại chưa có logic tạo ra proposal.
2. Hiện tại thì chưa rõ là response của findMatch trả về User mà nó tìm được có đúng ko hay là trả về proposal.
Hiện tại thì /intents/ đang trả về user valid. Cân nhắc 2 options là trả về "proposal" cho /intents/ hoặc di chuyển cái findMatch sang /proposal/ API. Hoặc có cách thiết kế khác.
Đây là các vấn đề của tôi. hãy check và sau đó chỉ ra lỗi sai tư duy, tư duy đúng và dùng nó làm cơ sở cho lập luận của câu tả lời. App của tôi là WalKMate.
```

- **Evidence:** [Gemini Link](https://gemini.google.com/share/0c0fa0796e91)

### 23127006 – Trần Nguyễn Khải Luân

- Prompt 1: tôi đang làm tính năng chat message cho app walkmate của tôi, bạn hãy đọc các code base và rút ra các phần cần reuse như button, color, theme, size, font, ... bạn có thể tự rút ra thêm. Hãy cho tôi biết những tư duy đúng, tư duy sai. bạn có thể hỏi ngược lại tôi và đề xuất cải thiện để có thể hướng tới UX tốt
- Prompt 2: is there any conflicts between these files about the backend implementation. Should I use vibe_coding and then use test_prompts or I just use test_prompts only in case of the feature is not implemented yet
- **Evidence:**
  - [Gemini Link](https://gemini.google.com/share/ad4252176724)
  - [Claude Link](https://claude.ai/share/90647cea-ede1-49ac-b7ac-ab7e2d1c67b0)
  - [Claude Link](https://claude.ai/share/c5804def-6c45-47e9-9636-693992ba1a0a)

### 23127438 – Đặng Trường Nguyên

- Prompt 1: Hãy tách badge ra thành 1 component riêng biệt và có thể tái sử dụng được. App của tôi là WalkMate.
- Prompt 2: Hãy tách walk session history ra thành 1 component riêng biệt và có thể tái sử dụng được. App của tôi là WalkMate.
- **Evidence:** [ChatGPT Link](https://chatgpt.com/share/69c7cfd0-e5c8-8322-ba94-23ace409b51e)

### 23127539 – Nguyễn Thanh Tiến

---

## Tasks Planned for Next Week

- Finish uncompleted feature and prepare for mid-term report.
