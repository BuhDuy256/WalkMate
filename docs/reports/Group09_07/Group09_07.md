# Weekly Report – Group 09

---

## General Information

| Field            | Value                   |
| ---------------- | ----------------------- |
| **Group ID**     | Group 09                |
| **Project Name** | WalkMate                |
| **Date Range**   | 2026-03-15 – 2026-03-21 |

---

## Tasks Completed This Week

### 23127179 – Nguyễn Bảo Duy

- Task: Implement end-to-end Walk Session domain flow
- Status:
  - Backend: Finish
  - Frontend: Finish GPS Path Tracing Feature with local DB. Didn't complete connecting Frontend with Backend API
- **Evidence:**
  - [Jira task](https://duybaonguyendev.atlassian.net/browse/KAN-26)
  - [Github branch - Backend of WalkSession](https://github.com/BuhDuy256/WalkMate/tree/features/full-walk-session)
  - [Github branch - Frontend of WalkSession](https://github.com/BuhDuy256/WalkMate/tree/feature/fe/gps-path-tracing)
  - ![alt text](images/image.png)

### 23127006 – Trần Nguyễn Khải Luân

- Task 1: get the api key for google maps from google
- Task 2: determine the current problem in the project and brainstorm the folder structure
- Status: Still just finish the backend. Haven't finish the frontend yet.
- **Evidence:**
  - ![alt text](images/api.png)
  - [Jira task](https://duybaonguyendev.atlassian.net/browse/KAN-25)
  - [Github branch](https://github.com/BuhDuy256/WalkMate/tree/features/walk-intent)

### 23127438 – Đặng Trường Nguyên

- Task: Login API, Login Flow
- Task description: Implement Login API and Login Flow. The API is implemented and tested with Postman, and connected with Frontend. access token is stored in Shared Preferences and used for authentication in other API calls. Refresh token is stored in Supabase DB and used to refresh access token when it expires.
- **Evidence:**
- [Jira task](https://duybaonguyendev.atlassian.net/browse/KAN-24)
- [Github branch](https://github.com/BuhDuy256/WalkMate/tree/feature/auth_activity)
- [Login/Postman/Supabase Screenshot](https://drive.google.com/drive/folders/1CQ1Vkzui3kZ_-L61a_depbZTgTvKFc8V?usp=drive_link)

### 23127539 – Nguyễn Thanh Tiến

Task: Implement Rating Flow
Task description: Implement end-to-end rating feature, including UI for submitting ratings, API integration, data validation, and backend processing to store and retrieve user ratings for walk sessions.

- **Evidence:**
- [Jira task](https://duybaonguyendev.atlassian.net/browse/KAN-27)
- [Github branch](https://github.com/BuhDuy256/WalkMate/tree/feature/rating)
- [Rating Feature Screenshot](https://drive.google.com/file/d/194o3kjbrTl_9V3wsVT0aMqRFEdgGGWM3/view?usp=sharing)

---

## AI Usage Declaration

### 23127179 – Nguyễn Bảo Duy

- **Prompt:**

```
Đây là danh sách câu hỏi của tôi:

- 1. Tôi nên làm giao diện như thế nào? Tôi đang có ý tưởng với option 1 với layouts là 2 tầng: 1 bên trên là Map và 2 bên dưới là các nút: sẽ có 2 button, trạng thái button start, pause, resume, end tùy theo State của Walk Session. Option 2 là maps ở phía sau và chèn một cái lớp chứa button lên trên (kiểu có 2 lớp UI chồng lên nhau và toi nghĩ nó sẽ dễ kiểm soát hơn vì chỉ cần reset cái lớp UI trên chứa button còn cái GPS Path Tracing Layer vẫn work bth).
- 2. Dùng API của Google Maps để code Map? Có API nào free ko?
- 3. Hiện tại tôi đang dùng các component gốc của Android Java chứ ko dùng lib. Nên là tôi ko rõ cần dùng lib gì để làm giao diện đẹp hơn ko?
- 4. Theo như bạn tôi nói là cái Maps nó chỉ cho location thôi, còn muốn vẽ đẹp lên thì cần lib khác.

=> Cho tôi câu trả lời? Tôi ko chỉ muốn có "Được" với "Không" mà bạn cần nhận ra tư duy sai sót ben trong và giảng lại lý thuyết cơ bản nếu tư duy tôi sai để như một bước đệm cho câu trả lời của bạn.
```

_Evidence:_ https://gemini.google.com/share/0d33f1a80b2a

### 23127006 – Trần Nguyễn Khải Luân

- **Evidence:**
  - [Claude](https://claude.ai/share/2751e52f-51a2-40fc-a498-d09217451ec0)
  - [Claude](https://claude.ai/share/7414cb1f-c041-40c4-a148-de35c8243c05)
  - [Claude](https://claude.ai/share/b78bf65e-f9cb-4292-8425-ac7e7b52721a)

### 23127438 – Đặng Trường Nguyên

- Prompt 1: hãy cho t 1 flow code đăng nhập, ví dụ hồi xưa t có làm web là đăng nhập -> gửi email + pass -> giải mã -> so sánh db -> tạo token bằng jwt -> gửi qua web bằng cookie
- **Evidence:** [ChatGPT conversation](https://chatgpt.com/share/69be61c9-7030-800b-b47b-8b7da4b9083d)

## Tasks Planned for Next Week

- **Goal:**
  - Finish uncompleted tasks in current week
  - Implement Chat, Noti, History, Profile flow

## Issues

| Issue                                                                                                                                                       | Raised by         | Status / Resolution                                                                                           |
| ----------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------- | ------------------------------------------------------------------------------------------------------------- |
| Lack of experience with Java (Android) and Spring Boot, leading to difficulties in implementing features, debugging, and integrating frontend with backend. | Nguyễn Thanh Tiến | In Progress: Actively learning, seeking support, practicing, and using AI tools to overcome these challenges. |
