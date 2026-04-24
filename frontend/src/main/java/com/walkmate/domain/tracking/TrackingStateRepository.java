package com.walkmate.domain.tracking;

import com.walkmate.domain.shared.DomainCallback;

/**
 * Repository interface for persisting runtime tracking state.
 *
 * Allows the screen to exit and re-enter without losing timer epoch values
 * and walk state. All operations are scoped to {@code (sessionId, userId)}
 * so two users who share a device and participate in the same session each
 * maintain independent, isolated timer state.
 *
 * All operations are asynchronous and report results via
 * {@link DomainCallback} on a background thread.
 */
public interface TrackingStateRepository {

    /**
     * Inserts or replaces the runtime state for the (session, user) pair.
     * The userId is carried inside {@link TrackingRuntimeState}.
     */
    void saveState(TrackingRuntimeState state, DomainCallback<Void> callback);

    /**
     * Loads the runtime state for the given (session, user) pair.
     * Calls {@code onSuccess(null)} if no record exists.
     */
    void loadState(String sessionId, String userId, DomainCallback<TrackingRuntimeState> callback);

    /**
     * Deletes the runtime state record for the (session, user) pair.
     * Must be called after a successful walk completion or forced finish.
     */
    void deleteState(String sessionId, String userId, DomainCallback<Void> callback);

    /**
     * Deletes all tracking state rows for a given user. Call this on logout
     * to prevent stale timer data from being restored by the next account.
     */
    void clearForUser(String userId, DomainCallback<Void> callback);
}
