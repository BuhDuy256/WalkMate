package com.walkmate.domain.walkpost;

public enum RoutePreviewStatus {
    /** Preview generation has not started yet (just created). */
    PENDING,
    /** Preview image generated, uploaded, and URL stored. */
    READY,
    /** Session had no GPS data; no image generated. */
    NO_ROUTE,
    /** External provider or storage call failed; URL is null. */
    FAILED
}
