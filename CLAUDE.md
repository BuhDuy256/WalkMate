# WalkMate AI Developer Guidelines

## 📚 Project Architecture & Context (Single Source of Truth)
**CRITICAL INSTRUCTION:** Before implementing new features, modifying existing logic, or when you need to understand the codebase, you **MUST** read the relevant documentation located in `docs/single-source-of-truth/`. This folder is the absolute source of truth for the project.

* **Architecture & Flow:** `docs/single-source-of-truth/architecture/`
    * Read `Frontend_VN.md` for UI/Client-side structures.
    * Read `Backend_VN.md` and `Backend_Flow_VN.md` for server-side logic, API structure, and data flow.
* **Features List:** `docs/single-source-of-truth/features-list/` (Consult these to understand feature requirements and scoping).
* **App Lifecycle:** `docs/single-source-of-truth/lifecycle/` (Consult for state management and intent-proposal-session flows).

---

## 🛠 Workflow Orchestration

### 1. Plan Mode Default
* **Trigger:** Enter plan mode for ANY non-trivial task (3+ steps or architectural decisions).
* **Pivoting:** If something goes sideways, **STOP** and re-plan immediately—don't keep pushing.
* **Verification:** Use plan mode for verification steps, not just building.
* **Clarity:** Write detailed specs upfront to reduce ambiguity.

### 2. Subagent Strategy
* **Context Management:** Use subagents liberally to keep the main context window clean.
* **Delegation:** Offload research, exploration, and parallel analysis to subagents.
* **Compute:** For complex problems, throw more compute at it via subagents.
* **Focus:** One task per subagent for focused execution.

### 3. Modular Programming (Anti-Spaghetti Rule)
* **File Size:** Actively avoid creating massive, monolithic files. If a file grows beyond its primary responsibility, break it down.
* **Single Responsibility:** Each file, class, or function should do exactly one thing well.
* **Refusal:** If asked to add complex logic to an already large file, propose splitting it into smaller components or utility files first.

### 4. Proactive Refactoring (Tech Debt Control)
* **Clean As You Go:** Do not just append code to make things work. Look for opportunities to consolidate duplicate logic.
* **Cleanup Prompts:** When the user says "Refactor" or "Clean up", rigorously scan the target files to remove unused variables, dead code, and optimize performance.
* **Elegant over Hacky:** Re-evaluate temporary fixes after a feature is stable. Transform "hacky" solutions into elegant, maintainable code.

### 5. Self-Improvement Loop
* **Feedback:** After **ANY** correction from the user, update `tasks/lessons.md` with the pattern.
* **Prevention:** Write rules for yourself that prevent the same mistake.
* **Iteration:** Ruthlessly iterate on these lessons until the mistake rate drops.
* **Review:** Review lessons at the session start for the relevant project.

### 6. Verification Before Done
* **Proof:** Never mark a task complete without proving it works.
* **Comparison:** Diff behavior between the main branch and your changes when relevant.
* **Quality Control:** Ask yourself: "Would a staff engineer approve this?"
* **Validation:** Run tests, check logs, and demonstrate correctness.

### 7. Autonomous Bug Fixing
* **Action:** When given a bug report, just fix it. Don't ask for hand-holding.
* **Evidence:** Point at logs, errors, and failing tests, then resolve them.
* **Efficiency:** Zero context switching required from the user.
* **Proactivity:** Go fix failing CI tests without being told how.

---

## 📋 Task Management

1.  **Plan First:** Write a plan to `tasks/todo.md` with checkable items.
2.  **Verify Plan:** Check in before starting implementation.
3.  **Track Progress:** Mark items complete as you go.
4.  **Explain Changes:** Provide a high-level summary at each step.
5.  **Document Results:** Add a review section to `tasks/todo.md`.
6.  **Capture Lessons:** Update `tasks/lessons.md` after corrections.

---

## 🎯 Core Principles

* **Simplicity First:** Make every change as simple as possible. Impact minimal code.
* **No Laziness:** Find root causes. No temporary fixes. Maintain senior developer standards.
* **Keep It Small:** Favor small, composable modules over large, complex ones to preserve context window efficiency.
* **Minimal Impact:** Changes should only touch what's necessary. Avoid introducing bugs.