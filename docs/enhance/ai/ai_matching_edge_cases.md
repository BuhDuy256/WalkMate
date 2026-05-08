# AI Matching Logic Edge Cases and Potential False Behaviors

This report details edge cases, potential flaws, and scenarios where the AI-weighted matching algorithm might learn incorrectly or produce suboptimal matches based on the current implementation.

## 1. The Decay of Time Overlap Importance (`weightTimeOverlap`)
The `AiTrainingService` explicitly increments `weightInterest` (from review tags) and `weightBehavior` (from review tags and reports). However, it **never explicitly increments `weightTimeOverlap`**. 

**The Flaw**: Because weights are normalized to sum to `1.0`, every time `weightInterest` or `weightBehavior` is bumped, the relative value of `weightTimeOverlap` goes down. Over time, for an active user who regularly leaves reviews with tags, their `weightTimeOverlap` will systematically decay to its absolute mathematical minimum. The system will "forget" that having a good time overlap is important to the user.

## 2. No Negative Reinforcement (Additive Only)
The AI training logic only uses additive bumps (`+0.05`, `+0.10`). A user cannot explicitly train the model to *care less* about a specific factor. 

**The Flaw**: If a user has a string of bad experiences and reports users, their `weightBehavior` will skyrocket to the `0.70` cap. If they later decide they want to prioritize "Interests" over "Behavior", it will take a massive amount of positive interest tags in reviews to slowly dilute the `weightBehavior` back down. The system is slow to adapt to changing user priorities because it only relies on relative dilution rather than direct decrements.

## 3. Time Overlap Scoring Ceiling (`S_time`)
The formula for time overlap is `min(overlapMinutes / 60.0 × 100, 100.0)`.

**The Flaw**: Any overlap of 60 minutes or more gets a perfect score of `100.0`. If User A wants to walk for 3 hours, and candidate B overlaps for 1 hour while candidate C overlaps for 3 hours, the AI treats both B and C as completely identical in terms of time preference. The system cannot distinguish or reward "perfect" long matches over "sufficiently long" 1-hour matches.

## 4. The "Empty Profile" Tag Echo Chamber (`S_tags`)
In `AiWeightedMatchingStrategy.scoreTags()`, if both users have completely empty tags, the method returns a default score of `50.0`.

**The Flaw**: If a user fails to set tags (or bypasses onboarding logic), they receive a `50.0` score when matching with other users who also have no tags. This creates a weird synergy where incomplete profiles are moderately matched together.

## 5. Trust Score Unbounded Scaling (`S_trust`)
The behavior score is calculated as `Candidate Trust Score / 10.0`.

**The Flaw**: If the `TrustScore` in the system is strictly bounded between `0` and `1000`, this formula works perfectly, returning a score between `0.0` and `100.0`. However, if the Trust Score can exceed `1000` (e.g., highly active and praised users getting up to 1500), `S_trust` will exceed `100.0`. Since `S_time` and `S_tags` are strictly capped at `100.0`, an unbounded `S_trust` would allow trust to mathematically overpower the `MAX_WEIGHT_CAP`, breaking the `0.70` influence limit.

## 6. Review Spamming / Click-through Bias
During post-session reviews, users receive `+0.05` to their `weightInterest` or `weightBehavior` for every tag they select. 

**The Flaw**: A user who just wants to dismiss the review screen quickly might tap all available tags randomly. This "spamming" drastically alters their AI matching weights. Because the training happens unconditionally on the tags selected without considering the length of the comment or the validity of the review, "lazy" UI interactions directly pollute the AI preference model.

## 7. Exploiting Reports for Self-Preference
When a user submits a report, their `weightBehavior` jumps by `+0.10` or `+0.15`. 

**The Flaw**: If a malicious user submits a fake report (e.g., falsely selecting `SAFETY_CONCERN` because they were slightly annoyed), the system immediately rewards the reporter by increasing their own behavior weight, assuming it was a valid signal of their preferences. The AI model has no mechanism to revert these weights if an Admin later rejects the report as invalid/false.
