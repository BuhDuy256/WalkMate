package com.walkmate.domain.tracking;

import com.walkmate.domain.shared.DomainCallback;

/**
 * Repository interface for persisting runtime tracking state.
 *
 * Allows the screen to exit and re-enter without losing timer
 * epoch values and walk state. All operations are asynchronous
 * and report results via {@link DomainCallback} on a background thread.
 */
public interface TrackingStateRepository {

    /**
     * Inserts or replaces the runtime state for the given session.
     */
    void saveState(TrackingRuntimeState state, DomainCallback<Void> callback);

    /**
     * Loads the runtime state for the given session.
     * Calls {@code onSuccess(null)} if no record exists.
     */
    void loadState(String sessionId, DomainCallback<TrackingRuntimeState> callback);

    /**
     * Deletes the runtime state record for the given session.
     * Must be called after a successful walk completion or forced finish.
     */
    void deleteState(String sessionId, DomainCallback<Void> callback);
}
