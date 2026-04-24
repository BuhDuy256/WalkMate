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
 * Every DB operation is scoped to {@code (sessionId, userId)} so that two
 * users who share a device and participate in the same session each maintain
 * isolated timer state without overwriting each other.
 *
 * All DB operations are offloaded to a single background thread so the
 * main thread is never blocked.
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
                entity.userId              = state.getUserId() != null ? state.getUserId() : "";
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
    public void loadState(String sessionId, String userId, DomainCallback<TrackingRuntimeState> callback) {
        executor.execute(() -> {
            try {
                String scopedUser = userId != null ? userId : "";
                TrackingStateEntity entity = dao.getBySessionAndUser(sessionId, scopedUser);
                if (entity == null) {
                    callback.onSuccess(null);
                    return;
                }
                WalkState walkState;
                try {
                    walkState = WalkState.valueOf(entity.walkState);
                } catch (IllegalArgumentException e) {
                    walkState = WalkState.READY;
                }
                callback.onSuccess(new TrackingRuntimeState(
                        entity.sessionId,
                        entity.userId,
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
    public void deleteState(String sessionId, String userId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                String scopedUser = userId != null ? userId : "";
                dao.deleteBySessionAndUser(sessionId, scopedUser);
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }

    @Override
    public void clearForUser(String userId, DomainCallback<Void> callback) {
        executor.execute(() -> {
            try {
                if (userId != null && !userId.isEmpty()) {
                    dao.deleteByUserId(userId);
                }
                callback.onSuccess(null);
            } catch (Exception e) {
                callback.onError(e);
            }
        });
    }
}
