# Weekly Report – Group 09

---

## General Information

| Field            | Value                   |
| ---------------- | ----------------------- |
| **Group ID**     | Group 09                |
| **Project Name** | WalkMate                |
| **Date Range**   | 2026-02-23 – 2026-03-07 |

---

## Tasks Completed This Week

### 23127179 – Nguyễn Bảo Duy

- Task 1 description: Develop a Core Workflow Sample Application to validate matching algorithms and improve system maintainability.
- **Evidence:**
  - [Jira screenshot](https://drive.google.com/file/d/1IBj2NPaG2DviUc6C4UBoDbGo-rhXIIxt/view?usp=sharing)
  - [Source code](https://github.com/BuhDuy256/WalkMate/tree/prototype/validate-core-workflow)

### 23127006 – Trần Nguyễn Khải Luân

- Task 1 description: UX & User Flow Blueprint
- **Evidence:**
  - [Jira screenshot](https://drive.google.com/drive/folders/1CxsdSZ5iYPjM9fj8jEWDWeDWrnvq1JZn?usp=sharing)
  - [Documents](https://drive.google.com/drive/folders/1HKWVBmYvnX1w2oqf_2OLrK18aoQ9hgiX?usp=sharing)

### 23127438 – Đặng Trường Nguyên

- Task 1 description: Design System Rules and Behavior Specification (Logic Contract)
- **Evidence:**
  - [Jira screenshot](https://drive.google.com/drive/folders/1fxn3zlH_QT3QwKYGaum2qj_aLx95YlMJ?usp=sharing)
  - [Documents](https://docs.google.com/document/d/1_iCMY4vKG-FHLPeiTKaozYYVD3y7g-AWRaEaY86Ifpw/edit?usp=sharing)

### 23127539 – Nguyễn Thanh Tiến

- **Tasks:**
  - Task 1: Database Design Documentation – Analyzed the WalkMate ERD diagrams and created a comprehensive database design report covering conceptual data model (ER model, entities, relationships), business rules, logical data model (3NF normalization), foreign keys, indexing strategy, and constraints
- **Evidence:**
  - [Conceptual Schema](https://app.diagrams.net/#G1-SD-eaywOR6Y8V6BCY40wYd37AzwIbvo#%7B%22pageId%22%3A%22yB81tSgXabls4k2ftjdi%22%7D)
  - [Logic Schema](https://app.diagrams.net/#G1cAhyszSCWaz_nH2MtlfBGmB0kE6wtgyb#%7B%22pageId%22%3A%227CWx2EnKrPZ8Q6bgTWRD%22%7D)
  - [Jira screenshot](https://drive.google.com/file/d/1fmN8FkQmd7j14J2YTOVRWqexC9F81Sgy/view?usp=sharing)

---

## AI Usage Declaration

### 23127179 – Nguyễn Bảo Duy

| #   | Tool & Version | Access Time | Prompt Used                                                       | Purpose                                                                       | Content Generated                                                                  | Student Validation                                                                                                 |
| --- | -------------- | ----------- | ----------------------------------------------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| 1   | ChatGPT 5.2    | 04/03/2026  | Cho tôi biết ý nghĩa của State Machine bên trong DDD?             | Understand the purpose of State Machine when developing aggregate root in DDD | How can State Machine protect business rules and consistency in the aggregate root | Ask for more clearly explanation about the purpose of understanding the state machine and help the code more SOLID |
| 1   | ChatGPT 5.2    | 04/03/2026  | Đề xuất thiết kế Backend Architecture để áp dụng DDD-lite cho tôi | Imagine about how can we apply the DDD to backend architecture                | Recommended Backend Architecture                                                   | Ask for the developing flow with the generated architecture and why isn't it over-engineered?                      |

**Screenshots / Chat History:** [ChatGPT Conversation](https://chatgpt.com/share/e/69abd1ff-d588-800a-ab3a-e2d0ce2ebb85)

### 23127006 – Trần Nguyễn Khải Luân

| #   | Tool & Version | Access Time | Prompt Used                                                                                                                                                                                                                                                                                                                                 | Purpose                                                                                                                      | Content Generated                                             | Student Validation                                                                                 |
| --- | -------------- | ----------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- |
| 1   | Gemini 3       | 2026-03-04  | "[project prosal included] đây là kế hoạch về mobile app của tôi. Tôi đang trong quá trình design UI, giờ giúp tôi xác định được phong cách UI cho app", "bạn hãy giúp tôi code ra một trang web ví dụ đơn giản, có nút chuyển đổi giữa các theme để tôi có thể dễ dàng hình dung và quyết định" | AI with generate some UI theme style for the app, then I will ask my teammates to choose the theme that they like the most | A demo app which can change among themes                      | I can check the color theme from AI suggestion, then me and my teammates choosing the best theme    |
| 2   | Gemini 3       | 2026-03-04  | "tôi chọn phong cách friendly, giờ hãy tạo ra một file markdown theme.md: Nội dung: mô tả chi tiết về phong cách UI app (là phong cách friendly mà bạn đã đề xuất). Mục đích: Tôi sẽ đưa file này cho gemini, và tôi sẽ yêu cầu gemini tạo ra một prompt dành cho figma make để nó có thể giúp tôi tạo ra được UI cho cách scene cho app" | Note the context to generate prompt for figma make and ensure the consistency                                                  | WalkMate Friendly UI Theme.md which describes about the theme color and rules for the theme | Review the generated file and started for figma prompts                                             |
| 3   | Gemini 3       | 2026-03-04  | "đây là file mô tả về theme mà tôi sẽ sử dụng trong app của tôi, UI nên đơn giản tối ưu hóa hiệu năng. Task: cho tôi prompt dành cho figma make để nó tạo ra bộ UI Kit để sử dụng trong app WalkMate" | Prepare the UI kit for the app                                                                                               | Prompt for figma make                                         | Copy the prompt and paste on figma, then it generated the corresponding components                  |
| 4   | Figma Make     | 2026-03-04  | Used all the prompts generated from Gemini                                                                                                                                                                                                                                                                                                  | Create UI screen                                                                                                             | UI screen generated                                           | Review the generated UI and shared them to the teammates                                            |

**Screenshots / Chat History:** _[Screenshots about the completing task process](https://drive.google.com/drive/folders/1ULwT8VJT7zf9GjtrZtGJ-0knAxWuoeA8?usp=sharing)_

### 23127438 – Đặng Trường Nguyên

| #   | Tool & Version       | Access Time | Prompt Used                                                                                                                        | Purpose                                                                                     | Content Generated                                                                                         | Student Validation                                                                                                 |
| --- | -------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| 1   | Gemini Pro           | 2026-03-02  | "Vẽ bảng transition của các state này dùng mermaid" | Visualize the state transition flow using mermaid code. | AI generated mermaid code to use with mermaid.ai | Reviewed the code, used it with mermaid.ai and check the logic with the designed state table.                        |
| 2   | ChatGPT           | 2026-03-02  | "Dựa vào bảng này viết chi tiết các TH gồm purpose, action, logic" | Detail section for the designed table. | AI generated purpose, action and logic section for each case of given table in each rule section (Including Timeout rules, Cancellation rule, NO_SHOW logic and Reliability Scoring logic). | Reviewed the suggestions and selected the relevant specification that match the requirements                        |
| 3   | ChatGPT           | 2026-03-02  | "Check đoạn code mermaid." | Check the mermaid code syntax | AI fixed and generated the new mermaid code to match the requirements. (The old code contained the state that are not proposed in the state transition table) | Reviewed the new code and replace it with the current code.                        |

**Chat History:** 
  - [ChatGPT Conversation Log](https://chatgpt.com/share/69abe2f9-2c24-800b-a46d-75eb3c31aea6)
  - [Gemini Conversation Log](https://gemini.google.com/share/7359766f7d53)


### 23127539 – Nguyễn Thanh Tiến

| #   | Tool & Version       | Access Time | Prompt Used                                                                                                                                                    | Purpose                                           | Content Generated                                                                                                                                                                                                     | Student Validation                                                                                                 |
| --- | -------------------- | ----------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| 1   | Gemini Pro           | 2026-03-02  | "Suggest necessary database tables for a walking companion matching system with authentication, location, AI matching, chat, scheduling, and review features." | Identify required database tables for the system  | AI suggested several groups of tables for user management, matching, walking sessions, chat, reviews, AI data, and achievements.                                                                                      | Reviewed the suggestions and adapted them to design the final WalkMate database schema.                            |
| 2   | Gemini Pro           | 2026-03-07  | "Suggest business rules for the walking companion matching system."                                                                                            | Identify possible business rules for the system   | AI suggested rules such as: users must authenticate before matching, a walking session connects two users, chat rooms are created after a successful match, and reviews can only be submitted after a completed walk. | Selected relevant rules and refined them to fit the final system design.                                           |
| 3   | Chat GPT (temporary) | 2026-03-07  | "Check grammar and improve the wording of the database design and system description."                                                                         | Improve English grammar and clarity in the report | AI suggested corrections for grammar, sentence structure, and wording in several sections of the documentation.                                                                                                       | Reviewed all suggestions and manually edited the text to ensure the meaning and technical accuracy were preserved. |

**Chat History:** [Gemini Conversation Log](https://gemini.google.com/share/391c439b0261)

---

## Tasks Planned for Next Week

- **Sprint Goal:** Develop MVP application with authentication and rating features.
- **Current Status:** Conducting in-depth research and technical analysis to finalize implementation approach.
- **Planning:**
  - Detailed task breakdown will be finalized by Sunday (08/03/2026).
