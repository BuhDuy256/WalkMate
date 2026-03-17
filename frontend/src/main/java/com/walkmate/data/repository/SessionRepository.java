package com.walkmate.data.repository;

import androidx.lifecycle.LiveData;

import com.walkmate.core.ResultCallback;
import com.walkmate.data.local.entity.SessionPointLocalEntity;

import java.util.List;

public interface SessionRepository {
    LiveData<List<SessionPointLocalEntity>> observePoints(String sessionId);

    void initLocalSession(String sessionId);

    int getNextPointOrder(String sessionId);

    void updateLocalStats(String sessionId, double distance, long duration, int lastPointOrder);

    int countUnsynced(String sessionId);

    double getLocalDistance(String sessionId);

    long getLocalDuration(String sessionId);

    void activate(String sessionId, ResultCallback<String> callback);

    void cancel(String sessionId, String reason, ResultCallback<String> callback);

    void abort(String sessionId, String reason, ResultCallback<String> callback);

    void complete(String sessionId, double distance, long duration, ResultCallback<String> callback);
}
