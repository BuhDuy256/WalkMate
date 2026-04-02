### 📋 Template Prompt (Dùng cho Chat Mới của mỗi Phase)

**Role & Context**
You are a Senior Android/Java Developer. We are executing my project implementation plan phase by phase. We are currently starting a new chat session specifically for **[ĐIỀN TÊN PHASE - Vd: Phase 4: Scanning Flow & FCM]**.

To ensure focus and avoid context hallucination, I will only provide the plan and the relevant current code for this specific phase below. 

**1. The Plan for this Phase:**
```text
[COPY PASTE PHẦN PLAN CỦA PHASE HIỆN TẠI TỪ FILE implementation_proposal.md VÀO ĐÂY]
```

**2. Current Relevant Code:**
```java
[COPY PASTE CODE CỦA CÁC FILE LIÊN QUAN ĐẾN PHASE NÀY VÀO ĐÂY - Vd: ExploreViewModel.java, ExploreFragment.java]
```

**Tasks & Deliverables**
Please review the plan and the current code, then complete the following 3 tasks:

**Task 1: Generate the updated Code**
Write the complete, updated code for the files involved in this phase. Apply production-level best practices (e.g., proper lifecycle handling to avoid memory leaks, safe thread/async execution).

**Task 2: Create `optimization_decision.md`**
Generate a markdown file named `optimization_decision.md`. In this file, document any technical choices, trade-offs, or code improvements you made during this phase that weren't strictly detailed in the original plan (e.g., why you chose a specific way to handle a Handler, or how you prevented a memory leak).

**Task 3: Create `phase_summary.md` (For Context Switching)**
Generate a markdown file named `phase_summary.md`. I will use this file as the contextual input for the *next* chat session. It must concisely include:
* A checklist of what was successfully implemented in this phase.
* A list of exactly which files were modified or created.
* Any exported states, variables, or API triggers that the next phase needs to be aware of.

Please start by providing the code for Task 1.
