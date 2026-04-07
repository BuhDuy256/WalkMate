Tuyệt vời! Việc tích hợp thêm MCP Grapuco (một dạng "Google Maps cho Codebase") vào quy trình làm việc với Claude là một nước đi cực kỳ "bén". Thay vì để AI đoán mò hoặc phải đọc hàng tá file code, Grapuco sẽ giúp Claude quét AST, nhìn thấu các Call Chains (chuỗi gọi hàm) và Data Flows (luồng dữ liệu) hiện tại trước khi đưa ra quyết định kiến trúc.

Dưới đây là 3 prompt đã được thiết kế lại, tuân thủ nghiêm ngặt các đường dẫn `@file` và tích hợp lệnh yêu cầu Claude sử dụng Grapuco. Bạn hãy copy-paste tuần tự từng prompt nhé.

---

### 🟢 Lần Prompt 1: Mở bài, Trace Code với Grapuco & Chốt Kiến Trúc
*Trong lần prompt đầu tiên này, chúng ta sẽ bắt Claude phải dùng Grapuco để "khám sức khỏe" hệ thống hiện tại trước khi đẻ ra thiết kế mới.*

**Copy đoạn dưới đây:**

> **Role:** You are an Expert System Architect and Backend Lead Engineer specializing in Java Spring Boot. You have access to the Grapuco MCP Server to analyze the codebase architecture.
> 
> **Context:**
> I have updated the invariants and state-transitions docs of my app, WalkMate. Current versions: 
> `@docs/single-source-of-truth/lifecycle/invariants.md`
> `@docs/single-source-of-truth/lifecycle/state-transitions.md`
> 
> Previously, you generated the initial plans:
> `@docs/dev/main-flow-v2/gap_analysis.md`
> `@docs/dev/main-flow-v2/implementation_plan.md`
> 
> **The Architectural Blocker:**
> In the new plan, we need to introduce **MongoDB Atlas** (for Chat flow) and **Firebase Cloud Messaging (FCM)** (for Notifications). We are currently using **Supabase** (for main relational DB/Auth). 
> *Constraint:* This task is STRICTLY for the Spring Boot Backend. Do NOT generate any Java Android Native frontend code/logic.
> 
> **Task 1: Codebase Analysis & Architecture Resolution**
> Before proposing any solutions, you must perform the following:
> 1. **Use Grapuco MCP:** Connect to the Grapuco MCP server to trace the current architecture graph. 
> 2. Inspect the existing API Endpoints, DB Models, and Functions related to the Main Flow (using Supabase). Trace the execution lifecycles to understand how data currently flows.
> 3. **Propose the Architecture:** Based on the Grapuco analysis, analyze how to connect the Main Flow (Supabase), Chat (MongoDB), and Notifications (FCM) in our Spring Boot environment without conflicts (e.g., how to handle transaction management, data synchronization, or cross-database references).
> 
> Please provide a brief Architectural Proposal based on your Grapuco findings. Do not update any markdown files yet. I will review your proposal first.

---

### 🟡 Lần Prompt 2: Cập nhật tài liệu Document
*Sau khi bạn đọc phương án của Claude ở Task 1 và thấy ưng ý (hoặc đã yêu cầu nó sửa lại cho ưng ý), hãy ném cho nó prompt thứ 2 này.*

**Copy đoạn dưới đây:**

> Excellent. The architectural proposal looks solid and aligns with the current codebase structure analyzed via Grapuco. 
> 
> Now, execute **Task 2: Update Documents**.
> Based strictly on the architecture approach we just agreed upon, please update the existing documentation to include the setup, configuration, and integration logic for MongoDB Atlas and FCM.
> 
> Please output the fully updated content for these two specific files:
> 1. `@docs/dev/main-flow-v2/gap_analysis.md`
> 2. `@docs/dev/main-flow-v2/implementation_plan.md`

---

### 🔴 Lần Prompt 3: Đóng gói Execution Playbook
*Sau khi Task 2 hoàn tất và bạn đã lưu 2 file `.md` kia lại, đây là lúc tạo ra "Cẩm nang bàn giao context" để bạn dùng cho các session chat sau này.*

**Copy đoạn dưới đây:**

> The implementation plan looks comprehensive. Now, execute **Task 3: Create the Execution Playbook**.
> 
> To help me execute this implementation plan smoothly across different chat sessions without losing context, I need a context-switching management document. 
> 
> Please create a new file named exactly: `@docs/dev/main-flow-v2/execution_playbook.md`. 
> 
> For each Phase defined in the newly updated `@docs/dev/main-flow-v2/implementation_plan.md`, provide the following structure:
> 1. **Phase Name.**
> 2. **Inputs:** Which specific markdown files (using their full `@file` paths) I must provide to the AI at the start of this phase.
> 3. **The Prompt:** The exact prompt I should copy-paste to instruct the AI to code this phase. The prompt must instruct the AI to use the **Grapuco MCP Server** to verify dependencies before writing code.
> 4. **Outputs:** The specific documentation, context files, or ADRs (Architecture Decision Records) the AI must generate at the end of this phase to serve as the *Input* for the subsequent phase.

---

**Tại sao bộ 3 Prompt này tối ưu?**
* **Prompt 1** đóng vai trò "Khảo sát hiện trạng". Bằng cách ép Claude dùng Grapuco trước khi thiết kế, bạn tránh được việc AI phác thảo một giải pháp "trên mây" không ăn nhập với code thực tế.
* **Prompt 2** tập trung 100% token vào việc viết Document.
* **Prompt 3** cực kỳ đặc biệt: Nó yêu cầu Claude tự viết ra các prompt tương lai cho chính nó, và *nhúng luôn lệnh gọi Grapuco* vào các prompt tương lai đó. Bằng cách này, mọi Phase code sau này của bạn đều được AI chủ động soi map (Grapuco) trước khi gõ phím.