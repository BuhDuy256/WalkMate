# Weekly Report – Group 09

---

## General Information

| Field            | Value                                          |
| ---------------- | ---------------------------------------------- |
| **Group ID**     | Group 09                                       |
| **Project Name** | WalkMate                                       |
| **Date Range**   | 2026-03-30 – 2026-04-04 _(Monday to Saturday)_ |

---

## Tasks Completed This Week

### 23127179 – Nguyễn Bảo Duy

- Implement push notification => Finish frontend, backend just set up FCM.
- Implement invitation management => Finish frontend, backend didn't handle failure cases in state transition of domain object.
- **Evidence:**
    - [Jira Task](https://duybaonguyendev.atlassian.net/browse/KAN-30?atlOrigin=eyJpIjoiN2MwMzYzMjIwYzdhNDMxYmEzOGNmMjNmNjJlY2ZiZWMiLCJwIjoiaiJ9)
    - [Github](https://github.com/BuhDuy256/WalkMate/commits/implement/realtime/)

### 23127006 – Trần Nguyễn Khải Luân

- Implement chatroom domain => Pnly finish frontend, backend is not implemented
- **Evidence:**
    - [Jira Task](https://duybaonguyendev.atlassian.net/browse/KAN-29)
    - [Github](https://github.com/BuhDuy256/WalkMate/tree/feature/chatroom)

### 23127438 – Đặng Trường Nguyên

- Create presentation slide for the seminar
- **Evidence:** 
    - [Canva](https://www.canva.com/design/DAHFhLsqK48/fl1szqHMhPuPEFm1HXhpcg/edit)
### 23127539 – Nguyễn Thanh Tiến

- Task 1: Implement Profile Flow
- Specifically:
  - Frontend: Enhance the UI for profile screen, implement the logic for fetching and displaying user profile data from backend, and implement the logic for updating user profile information.
  - Backend: Implement the API endpoints for fetching and updating user profile data, and implement the logic for handling user profile data in the backend. However, I haven't implemented the avatar upload feature yet, which is a part of the profile flow, because I need to research more about the best approach to implement this feature in our current architecture.

- **Evidence:**
  [Jira Task](https://duybaonguyendev.atlassian.net/browse/KAN-32)
  [GitHub](https://github.com/BuhDuy256/WalkMate/tree/feature/profile)
  [Smart Profile UI](https://drive.google.com/file/d/1TMSGL3gD3JlpEwEuUIPZBqIzQFc1qbmY/view?usp=sharing)
  [Update Profile With Postman](https://drive.google.com/file/d/1TysBqdLp2-0XTzGvlsHFK7Q0bV_xvooM/view?usp=sharing)

---

## AI Usage Declaration

### 23127179 – Nguyễn Bảo Duy
- I removed commits to clean workspace. I don't have evidence. I just prompts to ask about the feature flow of push notification.

### 23127006 – Trần Nguyễn Khải Luân

- Prompt 1: bạn hãy xem domain chatroom (session 9) nó có relate đến những cái domain nào khác không
- Prompt 2: tôi đang làm tính năng chat message cho app walkmate của tôi, bạn hãy đọc các code base và rút ra các phần cần reuse như button, color, theme, size, font, ... bạn có thể tự rút ra thêm. Hãy cho tôi biết những tư duy đúng, tư duy sai. bạn có thể hỏi ngược lại tôi và đề xuất cải thiện để có thể hướng tới UX tốt

cho tôi hỏi, cái prompt trên nó có tối ưu để có thể cho tôi một thiết kế tốt, nâng cấp về tư duy hay là đạt được kết quả đúng ý và tối ưu không
- **Evidence:**
    - [Gemini](https://gemini.google.com/share/854de9682a6c)
    - [Gemini](https://gemini.google.com/share/afced4efc7f3)

### 23127539 – Nguyễn Thanh Tiến

- Prompt 1: Tôi đang làm app mobile về walk match. tôi được giao dev profile feature. Tuy nhiên tôi lại bị kẹt phần người dùng upload avatar. Tôi nên làm như thế nào
- **Evidence:** https://chatgpt.com/share/e/69d0f87e-2b4c-8007-98a0-88742f45dc81

---

## Tasks Planned for Next Week

- Didn't have plan, may be tommorrow.

---

## Issues

| #   | Issue                                                                    | Raised By         | Status  | Resolution / Reason Not Resolved                                                                                                                                                                             |
| --- | ------------------------------------------------------------------------ | ----------------- | ------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 1   | Having trouble finding the right library to upload avatars to Cloudinary | Nguyễn Thanh Tiến | Pending | Not resolved yet. I am still having trouble identifying the correct library/package to handle avatar uploads to Cloudinary for this project. Because of that, the implementation has not been completed yet. |
| 2   |                                                                          |                   |         |                                                                                                                                                                                                              |
