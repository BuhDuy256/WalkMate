package com.walkmate.data.repository;

import com.walkmate.data.datasource.local.dao.TrackingStateDao;
import com.walkmate.data.datasource.local.entity.TrackingStateEntity;
import com.walkmate.domain.shared.DomainCallback;
import com.walkmate.domain.tracking.TrackingRuntimeState;
import com.walkmate.domain.tracking.TrackingStateRepository;
import com.walkmate.domain.tracking.WalkState;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Room-backed implementation of {@link TrackingStateRepository}.
 *
 * All DB operations are offloaded to a single background thread so the
 * main thread is never blocked. Callbacks are delivered on that same
 * background thread — callers that need main-thread delivery must use
 * {@link androidx.lifecycle.MutableLiveData#postValue}.
 */
public class TrackingStateRepositoryImpl implements TrackingStateRepository {

    private final TrackingStateDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public TrackingStateRepositoryImpl(TrackingStateDao dao) {
        this.dao = dao;
    }

    @Override
    public void saveState(TrackingRuntimeState state, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                TrackingStateEntity entity = new TrackingStateEntity();
                entity.sessionId           = state.getSessionId();
                entity.walkState           = state.getWalkState().name();
                entity.walkStartEpochMs    = state.getWalkStartEpochMs();
                entity.pausedAccumulatedMs = state.getPausedAccumulatedMs();
                entity.pauseStartEpochMs   = state.getPauseStartEpochMs();
                entity.updatedAt           = state.getUpdatedAt();
                dao.upsert(entity);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void loadState(String sessionId, DomainCallback<TrackingRuntimeState> callback) {
        executor.execute(() -> {
            try {
                TrackingStateEntity entity = dao.getBySessionId(sessionId);
                if (entity == null) {
                    callback.onSuccess(null);
                    return;
                }
                WalkState walkState;
                try {
                    walkState = WalkState.valueOf(entity.walkState);
                } catch (IllegalArgumentException e) {
                    // Unknown enum value (e.g. from a future schema) — treat as READY.
                    walkState = WalkState.READY;
                }
                callback.onSuccess(new TrackingRuntimeState(
                        entity.sessionId,
                        walkState,
                        entity.walkStartEpochMs,
                        entity.pausedAccumulatedMs,
                        entity.pauseStartEpochMs,
                        entity.updatedAt
                ));
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void deleteState(String sessionId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                dao.deleteBySessionId(sessionId);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
}
