I am starting a new session to execute Phase 1 of the WalkMate Backend Integration Test Plan.

[attach files here]

Please read these files. Once ready, tell me which sub-task in Phase 1 we should start with (e.g., T01-1). Follow the 'Plan First' rule: briefly describe your implementation strategy for that specific sub-task before writing any code.




files to be attached:
todo.md (The current progress and task list).
auth_profile_use_cases.md (The source of truth for Auth & Profile logic).
appendix.md (Global error handling and invariant rules).
CLAUDE.md (Our working standards).




# After pass the test
Before we move to the next phase, please perform the post-task procedures according to the WalkMate AI Developer Guidelines:

Update the tasks/todo.md Review Section to mark this phase as complete with short notes.

Document any technical hurdles we faced (e.g., Spring Security configs, DB transaction issues, Mockito setups) into tasks/lessons.md so we don't repeat them.

Provide a brief summary of the changes made. Do not proceed to the next phase until I confirm.

# When there is failed test

Test [Task ID - e.g., T20-2] is failing.
Here is the error log / stack trace:
[Paste the log here]

According to the 'Autonomous Bug Fixing' rule: Do not ask for hand-holding. Analyze the logs, identify the root cause (is it a business logic flaw, a test setup issue, or a DB state problem?), and provide the exact code changes needed to fix it. Ensure the fix aligns with the Invariants in appendix.md

# Refactor after the test

The tests for [Phase X] passed, but the code feels messy.
Trigger the 'Proactive Refactoring' protocol from our guidelines. Review the test classes and the underlying main implementation we just touched. Look for duplicate logic, unused variables, and opportunities to extract setup data into the TestDataSeeder. Propose the refactored code. Make it elegant, not hacky.