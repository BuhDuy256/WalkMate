package com.walkmate.data.worker;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.walkmate.data.local.dao.SessionLocalDao;
import com.walkmate.data.local.dao.SessionPointLocalDao;
import com.walkmate.data.local.db.WalkSessionDatabase;
import com.walkmate.data.local.entity.SessionLocalEntity;
import com.walkmate.data.local.entity.SessionPointLocalEntity;
import com.walkmate.data.remote.ApiClient;
import com.walkmate.data.remote.SessionApi;
import com.walkmate.data.remote.dto.ApiResponseDto;
import com.walkmate.data.remote.dto.AppendPointItemDto;
import com.walkmate.data.remote.dto.AppendSessionPointsRequestDto;
import com.walkmate.data.remote.dto.SessionTrackingResponseDto;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import retrofit2.Response;

public class SessionPointSyncWorker extends Worker {

    public static final String KEY_SESSION_ID = "session_id";
    private static final String USER_ID_HEADER = "c7a989f0-9f68-43f2-9ca8-160c4e301ce5";

    public SessionPointSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    public static void enqueueNow(Context context, String sessionId) {
        Data input = new Data.Builder().putString(KEY_SESSION_ID, sessionId).build();
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SessionPointSyncWorker.class)
                .setInputData(input)
                .build();
        WorkManager.getInstance(context).enqueue(request);
    }

    @NonNull
    @Override
    public Result doWork() {
        String sessionId = getInputData().getString(KEY_SESSION_ID);
        if (sessionId == null) {
            return Result.failure();
        }

        WalkSessionDatabase db = WalkSessionDatabase.getInstance(getApplicationContext());
        SessionPointLocalDao pointDao = db.sessionPointLocalDao();
        SessionLocalDao sessionDao = db.sessionLocalDao();
        SessionApi api = ApiClient.sessionApi();

        List<SessionPointLocalEntity> pending = pointDao.getPendingBatch(sessionId, 30);
        if (pending.isEmpty()) {
            return Result.success();
        }

        String batchToken = UUID.randomUUID().toString();
        List<Long> ids = new ArrayList<>();
        List<AppendPointItemDto> points = new ArrayList<>();

        for (SessionPointLocalEntity point : pending) {
            ids.add(point.localId);
            points.add(new AppendPointItemDto(point.pointOrder, point.lat, point.lng, point.time));
        }

        pointDao.markBatchStatus(ids, "SYNCING", batchToken, System.currentTimeMillis());

        SessionLocalEntity session = sessionDao.getById(sessionId);
        double distance = session == null ? 0.0 : session.totalDistanceMeters;
        long duration = session == null ? 0L : session.totalDurationSeconds;

        AppendSessionPointsRequestDto request = new AppendSessionPointsRequestDto(points, distance, duration);

        try {
            Response<ApiResponseDto<SessionTrackingResponseDto>> response = api
                    .appendPoints(sessionId, request, USER_ID_HEADER)
                    .execute();
            if (response.isSuccessful() && response.body() != null && response.body().success) {
                pointDao.markSyncedByBatch(batchToken, System.currentTimeMillis());
                return Result.success();
            }
            pointDao.requeueBatch(batchToken, System.currentTimeMillis());
            return Result.retry();
        } catch (Exception e) {
            pointDao.requeueBatch(batchToken, System.currentTimeMillis());
            return Result.retry();
        }
    }
}
