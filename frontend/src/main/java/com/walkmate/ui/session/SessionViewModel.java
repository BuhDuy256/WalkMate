package com.walkmate.ui.session;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.walkmate.core.Result;
import com.walkmate.data.local.entity.SessionPointLocalEntity;
import com.walkmate.data.repository.SessionRepository;
import com.walkmate.tracking.TrackingCommand;

import java.util.List;

public class SessionViewModel extends ViewModel {

    private final SessionRepository repository;
    private final MutableLiveData<SessionUiState> uiState = new MutableLiveData<>(SessionUiState.idle());

    private String sessionId;
    private long commandVersion = 0L;

    public SessionViewModel(SessionRepository repository) {
        this.repository = repository;
    }

    public LiveData<SessionUiState> getUiState() {
        return uiState;
    }

    public void bindSession(String sessionId) {
        this.sessionId = sessionId;
        repository.initLocalSession(sessionId);
    }

    public void activate() {
        SessionUiState current = safe();
        uiState.setValue(new SessionUiState(
                SessionScreenStatus.ACTIVATING,
                current.distanceMeters,
                current.durationSeconds,
                null,
                TrackingCommand.NONE,
                commandVersion));

        repository.activate(sessionId, result -> {
            if (result.isSuccess()) {
                commandVersion++;
                SessionUiState now = safe();
                uiState.postValue(new SessionUiState(
                        SessionScreenStatus.TRACKING_ACTIVE,
                        now.distanceMeters,
                        now.durationSeconds,
                        null,
                        TrackingCommand.START,
                        commandVersion));
            } else {
                postError(result.getError(), SessionScreenStatus.ERROR);
            }
        });
    }

    public void cancel(String reason) {
        repository.cancel(sessionId, reason, result -> {
            if (result.isSuccess()) {
                commandVersion++;
                SessionUiState now = safe();
                uiState.postValue(new SessionUiState(
                        SessionScreenStatus.COMPLETED,
                        now.distanceMeters,
                        now.durationSeconds,
                        null,
                        TrackingCommand.STOP,
                        commandVersion));
            } else {
                postError(result.getError(), SessionScreenStatus.ERROR);
            }
        });
    }

    public void abort(String reason) {
        repository.abort(sessionId, reason, result -> {
            if (result.isSuccess()) {
                commandVersion++;
                SessionUiState now = safe();
                uiState.postValue(new SessionUiState(
                        SessionScreenStatus.COMPLETED,
                        now.distanceMeters,
                        now.durationSeconds,
                        null,
                        TrackingCommand.STOP,
                        commandVersion));
            } else {
                postError(result.getError(), SessionScreenStatus.ERROR);
            }
        });
    }

    public void pause() {
        SessionUiState now = safe();
        commandVersion++;
        uiState.setValue(new SessionUiState(
                SessionScreenStatus.TRACKING_PAUSED,
                now.distanceMeters,
                now.durationSeconds,
                null,
                TrackingCommand.PAUSE,
                commandVersion));
    }

    public void resume() {
        SessionUiState now = safe();
        commandVersion++;
        uiState.setValue(new SessionUiState(
                SessionScreenStatus.TRACKING_ACTIVE,
                now.distanceMeters,
                now.durationSeconds,
                null,
                TrackingCommand.RESUME,
                commandVersion));
    }

    public void complete() {
        SessionUiState now = safe();
        uiState.setValue(new SessionUiState(
                SessionScreenStatus.COMPLETING,
                now.distanceMeters,
                now.durationSeconds,
                null,
                TrackingCommand.NONE,
                commandVersion));

        if (repository.countUnsynced(sessionId) > 0) {
            postError(new IllegalStateException("Dang dong bo diem GPS, vui long thu lai."), SessionScreenStatus.ERROR);
            return;
        }

        double distance = repository.getLocalDistance(sessionId);
        long duration = repository.getLocalDuration(sessionId);
        repository.complete(sessionId, distance, duration, result -> {
            if (result.isSuccess()) {
                commandVersion++;
                SessionUiState done = safe();
                uiState.postValue(new SessionUiState(
                        SessionScreenStatus.COMPLETED,
                        done.distanceMeters,
                        done.durationSeconds,
                        null,
                        TrackingCommand.STOP,
                        commandVersion));
            } else {
                postError(result.getError(), SessionScreenStatus.ERROR);
            }
        });
    }

    public void onPointsUpdated(List<SessionPointLocalEntity> points) {
        if (sessionId == null || points == null || points.isEmpty()) {
            return;
        }

        double distance = 0.0;
        long duration = 0L;

        SessionPointLocalEntity first = points.get(0);
        for (int i = 1; i < points.size(); i++) {
            SessionPointLocalEntity prev = points.get(i - 1);
            SessionPointLocalEntity curr = points.get(i);
            float[] result = new float[1];
            android.location.Location.distanceBetween(prev.lat, prev.lng, curr.lat, curr.lng, result);
            distance += result[0];
        }

        SessionPointLocalEntity last = points.get(points.size() - 1);
        duration = Math.max(0L, (last.time - first.time) / 1000L);
        repository.updateLocalStats(sessionId, distance, duration, last.pointOrder);

        SessionUiState now = safe();
        uiState.postValue(new SessionUiState(
                now.status,
                distance,
                duration,
                now.errorMessage,
                TrackingCommand.NONE,
                now.commandVersion));
    }

    public int getNextPointOrder() {
        return repository.getNextPointOrder(sessionId);
    }

    private SessionUiState safe() {
        SessionUiState state = uiState.getValue();
        return state != null ? state : SessionUiState.idle();
    }

    private void postError(Throwable t, SessionScreenStatus fallbackState) {
        SessionUiState now = safe();
        uiState.postValue(new SessionUiState(
                fallbackState,
                now.distanceMeters,
                now.durationSeconds,
                messageOf(t),
                TrackingCommand.NONE,
                now.commandVersion));
    }

    private String messageOf(Throwable t) {
        return t != null && t.getMessage() != null ? t.getMessage() : "Unknown error";
    }
}
