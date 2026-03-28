1. Write contract in DOMAIN_CONTRACTS.md  (A0 from TEST_PROMPTS)
         ↓
2. TEST_PROMPTS A1 → generate Fixture + domain tests (they will be RED — entity doesn't exist yet)
         ↓
3. VIBE_CODING_GUIDE Prompt 1 → generate the domain entity → tests go GREEN
         ↓
4. VIBE_CODING_GUIDE Prompt 3 → generate CommandService
         ↓
5. TEST_PROMPTS A2 → generate service tests → GREEN
         ↓
6. VIBE_CODING_GUIDE Prompt 5 → generate Controller + DTOs
         ↓
7. TEST_PROMPTS A3 → generate controller tests → GREEN
         ↓
8. TEST_PROMPTS A4 → architecture violation check → all CLEAN
         ↓
9. VIBE_CODING_GUIDE Prompt 7 → generate Infrastructure repo