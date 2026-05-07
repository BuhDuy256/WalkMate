package com.walkmate.domain.tracking;

/**
 * Represents the current state of the partner path overlay on the tracking map.
 * Drives the partner status label below the partner avatar in the bottom panel.
 */
public enum PartnerOverlayState {
    /** Partner personal status is PENDING — they have not arrived yet. */
    WAITING_FOR_PARTNER,
    /** Partner is ACTIVE but no GPS chunks have reached the backend yet. */
    WAITING_FOR_GPS,
    /** Partner has GPS data — polyline is visible on the map. */
    SHOWING_PATH,
    /** Partner personal status is COMPLETED — path is frozen. */
    PARTNER_COMPLETED,
    /** Partner personal status is NO_SHOW — partner never arrived. */
    PARTNER_NO_SHOW
}
