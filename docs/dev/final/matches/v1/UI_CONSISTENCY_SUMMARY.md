# UI Consistency Proposal Summary

The goal is to unify the UI of the three match-related fragments (`Finding`, `Proposal`, and `Session`) by adopting a shared 5-zone "Match Card Box" anatomy.

## Key Changes
*   **Unified Structure:** All cards will now use a standardized layout with five specific zones (Header, Identity, Time/Place, Tags, Actions).
*   **MatchCardHeaderView:** A new custom view handles the top header (status and optional countdown) consistently across all cards.
*   **Visual Alignment:**
    *   **Finding Card:** Replaces raw UUIDs with readable hotspot names and aligns time display with the Proposal card's "warm pill" style.
    *   **Proposal Card:** Integrates the header into the unified structure and clarifies sub-states (Decide Now vs. Waiting) via the status badge.
    *   **Session Card:** Removes the problematic 130dp map placeholder and replaces it with the unified header, correctly displaying partner names and hotspot locations instead of raw data.
*   **Action Hierarchy:** Establishes a formal tier system (Primary, Secondary, Destructive) for all buttons to ensure predictable interactions.

This refactor is structural and does not require new data or features, aiming solely for a coherent, professional user experience.
