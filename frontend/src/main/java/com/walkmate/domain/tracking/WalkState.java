package com.walkmate.domain.tracking;

/**
 * State machine for the GPS walk lifecycle.
 *
 * <pre>
 *   READY ──[start]──► ACTIVE ──[pause]──► PAUSED
 *                        ▲                    │
 *                        └──────[resume]───────┘
 *                        │
 *                   [finish]
 *                        │
 *                        ▼
 *                    FINISHED
 * </pre>
 */
public enum WalkState {
    /** Screen just opened; GPS not yet requested. Waiting for user to tap Start. */
    READY,

    /** GPS running, stopwatch ticking, points being written to Room. */
    ACTIVE,

    /** GPS paused (no new points), stopwatch frozen; polyline remains on map. */
    PAUSED,

    /** Complete tapped; API call in progress. Awaiting server confirmation. */
    FINISHING,

    /** Walk ended; summary stats available. GPS service stopped. */
    FINISHED
}
