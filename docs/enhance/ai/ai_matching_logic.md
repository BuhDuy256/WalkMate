# AI Matching Logic Report

This report provides a comprehensive overview of how the AI-weighted matching engine works in the WalkMate application. The engine is responsible for finding the best possible walking partner based on a user's walk intent, preferences, and system interactions.

## 1. High-Level Architecture

The matching logic is implemented in `AiWeightedMatchingStrategy` (the primary `MatchingStrategy`), which evaluates candidates in two stages:
1. **DB-Level Hard Filtering**: Filtering candidates based on absolute constraints.
2. **AI-Weighted In-Memory Scoring**: Calculating a personalised score for each candidate and selecting the highest-scoring match.

Users' personalized matching weights are stored in the `matching_preference_model` table and are represented by the `MatchingPreference` domain object.

## 2. Stage 1: Hard Filtering

Before any AI scoring happens, the system queries the `WalkIntentRepository` for candidates that meet the base requirements:
- **Hotspot**: Candidates must be at the same hotspot.
- **Time Window**: There must be an overlap between the two time windows. The overlap must be at least the `MIN_WALK_DURATION`.
- **Age Constraints**: Candidates must fall within the user's specified age range min/max.
- **Gender Preference**: The candidate must match the preferred gender, if specified.
- **Blocked Users**: The system filters out any candidates that the caller has blocked or who have blocked the caller.

## 3. Stage 2: AI-Weighted Scoring

For the candidates that pass the hard filters, the engine calculates a `TotalScore` out of 100. 

### 3.1. The Scoring Formula
```text
TotalScore = (W_time × S_time) + (W_interest × S_tags) + (W_behavior × S_trust)
```

The final result is the candidate with the maximum `TotalScore`.

### 3.2. Weight Components (W)
The weights (`W_time`, `W_interest`, `W_behavior`) reflect what the user cares about most. 
- By default, a new user starts with equal thirds for each weight (`~0.333`).
- Weights are constantly normalized so they sum to exactly `1.0`.
- **Hard Cap**: No single weight is allowed to exceed `0.70` (70%). If a weight reaches the cap, the remaining `0.30` is distributed proportionally to the other two factors.

### 3.3. Score Components (S)
The scores (`S_time`, `S_tags`, `S_trust`) are calculated per candidate:

1. **Time Overlap Score (`S_time`)**: 
   - Formula: `min(overlapMinutes / 60.0 × 100, 100.0)`
   - Meaning: Up to 60 minutes of overlap, the score increases linearly. Any overlap of 60 minutes or more scores a perfect `100.0`.

2. **Interest / Tag Overlap Score (`S_tags`)**:
   - Formula: Jaccard Similarity of user profile tags. `(Intersection Size / Union Size) × 100.0`
   - Meaning: Calculates the percentage of shared tags. If both users have exactly the same tags, it's `100.0`. If both users have *no tags at all*, it defaults to `50.0`.

3. **Behavior / Trust Score (`S_trust`)**:
   - Formula: `Candidate Trust Score / 10.0`
   - Meaning: Directly converts the candidate's trust score into a `0-100` scale. A default trust score of `500` yields a score of `50.0`.

## 4. AI Training & Weight Adjustment

The AI engine "learns" user preferences implicitly based on the user's actions in the app. This is handled asynchronously by `AiTrainingService`.

### 4.1. Learning from Post-Session Reviews
When a user submits a review and selects tags:
- Every selected tag of type `POSITIVE_INTEREST` or `NEGATIVE_INTEREST` increases `weightInterest` by `+0.05`.
- Every selected tag of type `POSITIVE_BEHAVIOR` or `NEGATIVE_BEHAVIOR` increases `weightBehavior` by `+0.05`.

### 4.2. Learning from Incident Reports
Filing a report is a strong signal that the user prioritizes safe and good behavior.
- Submitting any report adds a base `+0.10` to `weightBehavior`.
- If the report reason is `SAFETY_CONCERN` or `PARTNER_MISCONDUCT`, an additional severity bonus of `+0.05` is added, resulting in a total `+0.15` bump to `weightBehavior`.

### 4.3. Normalization
After any of these adjustments, the `normalize()` method is called on the `MatchingPreference`:
1. It divides each weight by the new total sum to keep them proportional and summing to `1.0`.
2. It enforces the `0.70` max weight cap, preventing one single attribute from entirely dominating the matching algorithm.
